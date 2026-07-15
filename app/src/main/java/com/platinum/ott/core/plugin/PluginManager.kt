package com.platinum.ott.core.plugin

import android.content.Context
import android.util.Log
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.platinum.ott.data.local.dao.PluginDao
import com.platinum.ott.data.local.entity.PluginEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PluginManager(
    private val context: Context,
    private val pluginDao: PluginDao,
    private val pluginApi: PluginApi
) {
    companion object {
        private const val TAG = "PluginManager"
        private val gson = Gson()
    }
    private val pluginsDir = File(context.filesDir, "plugins").apply { mkdirs() }
    private val loadedPlugins = ConcurrentHashMap<String, LoadedPlugin>()

    data class LoadedPlugin(val manifest: PluginManifest, val entity: PluginEntity, val quickJs: QuickJs? = null, val loadTime: Long = System.currentTimeMillis())

    fun getAllPlugins(): Flow<List<PluginEntity>> = pluginDao.getAll()
    fun getEnabledPlugins(): Flow<List<PluginEntity>> = pluginDao.getEnabled()
    fun getEnabledByType(type: PluginType): Flow<List<PluginEntity>> = pluginDao.getEnabledByType(type.key)
    suspend fun getPlugin(id: String): PluginEntity? = pluginDao.getById(id)

    suspend fun installPlugin(scriptContent: String, repoUrl: String = ""): Result<PluginManifest> = withContext(Dispatchers.IO) {
        try {
            val manifest = parseManifestFromScript(scriptContent) ?: return@withContext Result.failure(Exception("Cannot parse manifest"))
            for (dep in manifest.requires) { if (pluginDao.getById(dep)?.isEnabled != true) return@withContext Result.failure(Exception("Missing: $dep")) }
            val safeId = manifest.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            if (safeId.isBlank() || safeId.length > 100) return@withContext Result.failure(Exception("Bad ID"))
            if (scriptContent.length > 1_048_576) return@withContext Result.failure(Exception("Too large"))
            File(pluginsDir, "$safeId.js").writeText(scriptContent)
            val entity = PluginEntity(id = safeId, name = manifest.name, version = manifest.version, installedVersion = manifest.version, description = manifest.description, author = manifest.author, pluginType = manifest.type.key, iconUrl = manifest.icon, repoUrl = repoUrl, scriptContent = scriptContent)
            pluginDao.upsert(entity); loadPlugin(entity)
            Log.i(TAG, "Installed: ${manifest.name} v${manifest.version}"); Result.success(manifest)
        } catch (e: Exception) { Log.e(TAG, "Install error", e); Result.failure(e) }
    }

    suspend fun loadPlugin(entity: PluginEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            if (loadedPlugins.containsKey(entity.id)) return@withContext true
            val script = entity.scriptContent.ifEmpty { File(pluginsDir, "${entity.id}.js").takeIf { it.exists() }?.readText() ?: "" }
            if (script.isEmpty()) return@withContext false
            val manifest = parseManifestFromScript(script) ?: PluginManifest(entity.id, entity.name, entity.version)
            val quickJs = QuickJs.create()
            try { quickJs.evaluate(buildBridgeScript(entity.id)); quickJs.evaluate(script); try { quickJs.evaluate("typeof start==='function'?start():null") } catch (_: Exception) {} }
            catch (e: Exception) { quickJs.close(); throw e }
            loadedPlugins[entity.id] = LoadedPlugin(manifest, entity, quickJs); Log.i(TAG, "Loaded: ${entity.name}"); true
        } catch (e: Exception) { Log.e(TAG, "Load fail: ${entity.id}", e); false }
    }

    suspend fun unloadPlugin(pluginId: String) { withContext(Dispatchers.IO) { loadedPlugins.remove(pluginId)?.quickJs?.close() } }
    suspend fun setEnabled(pluginId: String, enabled: Boolean) { pluginDao.setEnabled(pluginId, enabled); if (enabled) pluginDao.getById(pluginId)?.let { loadPlugin(it) } else unloadPlugin(pluginId) }
    suspend fun uninstallPlugin(pluginId: String) { unloadPlugin(pluginId); pluginDao.deleteById(pluginId); File(pluginsDir, "$pluginId.js").delete(); context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE).edit().clear().apply() }
    suspend fun updatePlugin(pluginId: String, newScript: String): Result<PluginManifest> { uninstallPlugin(pluginId); return installPlugin(newScript) }

    suspend fun callPluginFunction(pluginId: String, functionName: String, vararg args: String): String? = withContext(Dispatchers.IO) {
        val qjs = loadedPlugins[pluginId]?.quickJs ?: return@withContext null
        try {
            val safe = args.map { a -> "'" + a.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'" }
            qjs.evaluate("(function(){return $functionName(${safe.joinToString(",")});})()") as? String
        } catch (e: Exception) { Log.e(TAG, "Call fail", e); null }
    }

    suspend fun loadAllEnabled() { try { pluginDao.getEnabled().first().forEach { if (!loadedPlugins.containsKey(it.id)) loadPlugin(it) } } catch (e: Exception) { Log.e(TAG, "Bulk fail", e) } }

    private fun buildBridgeScript(pluginId: String): String {
        val safeId = pluginId.replace("\\", "\\\\").replace("'", "\\'")
        val s = StringBuilder()
        s.appendLine("var Zenith={")
        s.appendLine("plugin:{id:'$safeId'}," )
        s.appendLine("storage:{")
        s.appendLine("get:function(key,def){var r=JSON.parse(_zenithCall('storage_get',JSON.stringify({key:key,def:def||''})));return r.value;}," )
        s.appendLine("set:function(key,val){_zenithCall('storage_set',JSON.stringify({key:key,value:val}));}," )
        s.appendLine("remove:function(key){_zenithCall('storage_remove',JSON.stringify({key:key}));}")
        s.appendLine("}," )
        s.appendLine("http:{")
        s.appendLine("get:function(url,headers){return _zenithCall('http_get',JSON.stringify({url:url,headers:headers||{}}));}," )
        s.appendLine("post:function(url,body,headers){return _zenithCall('http_post',JSON.stringify({url:url,body:body,headers:headers||{}}));}")
        s.appendLine("}," )
        s.appendLine("log:function(msg){_zenithCall('log',JSON.stringify({message:String(msg)}));}," )
        s.appendLine("notify:function(title,msg){_zenithCall('notify',JSON.stringify({title:title,message:msg}));}," )
        s.appendLine("cache:{_data:{},set:function(k,v,ttl){this._data[k]={v:v,e:Date.now()+(ttl||3600000)};},get:function(k){var e=this._data[k];return(e&&e.e>Date.now())?e.v:null;},clear:function(){this._data={};}}")
        s.appendLine("};")
        s.appendLine("var _zenithCall=function(action,data){return '{\"value\":\"\"}' ;};")
        return s.toString()
    }

    suspend fun processBridge(pluginId: String, action: String, dataJson: String): String = withContext(Dispatchers.IO) {
        try {
            val result: Any = when (action) {
                "storage_get" -> { val d = pluginApi.parseJsonMap(dataJson); mapOf("value" to pluginApi.storageGet(pluginId, d["key"] as? String ?: "", d["def"] as? String ?: "")) }
                "storage_set" -> { val d = pluginApi.parseJsonMap(dataJson); pluginApi.storageSet(pluginId, d["key"] as? String ?: "", d["value"] as? String ?: ""); mapOf("ok" to true) }
                "storage_remove" -> { val d = pluginApi.parseJsonMap(dataJson); pluginApi.storageRemove(pluginId, d["key"] as? String ?: ""); mapOf("ok" to true) }
                "http_get" -> { val d = pluginApi.parseJsonMap(dataJson); @Suppress("UNCHECKED_CAST") pluginApi.httpGet(d["url"] as? String ?: "", (d["headers"] as? Map<String, String>) ?: emptyMap()) }
                "http_post" -> { val d = pluginApi.parseJsonMap(dataJson); @Suppress("UNCHECKED_CAST") pluginApi.httpPost(d["url"] as? String ?: "", d["body"] as? String ?: "", (d["headers"] as? Map<String, String>) ?: emptyMap()) }
                "log" -> { val d = pluginApi.parseJsonMap(dataJson); pluginApi.log(pluginId, d["message"] as? String ?: ""); mapOf("ok" to true) }
                "notify" -> { val d = pluginApi.parseJsonMap(dataJson); pluginApi.notify(d["title"] as? String ?: "", d["message"] as? String ?: ""); mapOf("ok" to true) }
                else -> mapOf("error" to "unknown")
            }
            if (result is String) result else gson.toJson(result)
        } catch (e: Exception) { Log.e(TAG, "Bridge error: $action", e); gson.toJson(mapOf("error" to (e.message ?: "unknown"))) }
    }

    fun getLoadedPlugins(): Map<String, LoadedPlugin> = loadedPlugins.toMap()
    fun destroy() { loadedPlugins.values.forEach { it.quickJs?.close() }; loadedPlugins.clear() }
}
