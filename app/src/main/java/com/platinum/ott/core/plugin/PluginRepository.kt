package com.platinum.ott.core.plugin

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.platinum.ott.data.local.dao.PluginDao
import com.platinum.ott.data.local.entity.PluginEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Репозиторий плагинов: установка из локального/удалённого каталога,
 * проверка обновлений, управление источниками.
 */
class PluginRepository(
    private val pluginDao: PluginDao,
    private val pluginManager: PluginManager,
    private val pluginApi: PluginApi
) {
    companion object {
        private const val TAG = "PluginRepository"
        // TODO: тот же паттерн, что был с baseUrl в RetrofitFactory.kt — это
        // несуществующий домен-заглушка. fetchCatalog() с дефолтным
        // параметром сейчас всегда падает на DNS до реального маркетплейса
        // плагинов. installFromUrl/installFromFile работают независимо от
        // этого и не затронуты. Заменить на реальный URL, когда/если
        // появится настоящий каталог плагинов.
        private const val DEFAULT_CATALOG = "https://zenith-plugins.example.com/catalog.json"
    }

    private val client = PluginApi.sharedClient
    private val gson = Gson()

    /** Запись каталога плагинов */
    data class CatalogEntry(
        val id: String,
        val name: String,
        val version: String,
        val description: String = "",
        val author: String = "",
        val type: String = "source",
        val icon: String? = null,
        val downloadUrl: String,
        val sha256: String? = null
    )

    /** Валидация URL — вынесена в PluginUrlValidator (раньше была продублирована здесь и в PluginApi.kt) */
    private fun isValidUrl(url: String): Boolean = PluginUrlValidator.isValid(url)

    suspend fun fetchCatalog(catalogUrl: String = DEFAULT_CATALOG): Result<List<CatalogEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isValidUrl(catalogUrl)) return@withContext Result.failure(Exception("Invalid catalog URL"))
                val req = Request.Builder().url(catalogUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    val body = resp.body?.string() ?: "[]"
                    val type = object : TypeToken<List<CatalogEntry>>() {}.type
                    val entries = gson.fromJson<List<CatalogEntry>>(body, type)
                    Result.success(entries)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки каталога", e)
                Result.failure(e)
            }
        }
    }

    /** Скачать и установить плагин из каталога */
    suspend fun installFromCatalog(entry: CatalogEntry): Result<PluginManifest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isValidUrl(entry.downloadUrl)) return@withContext Result.failure(Exception("Invalid download URL"))
                val req = Request.Builder().url(entry.downloadUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    val script = resp.body?.string()
                        ?: return@withContext Result.failure(Exception("Пустой ответ"))

                    // Проверка хеша
                    if (entry.sha256 != null) {
                        val hash = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(script.toByteArray())
                            .joinToString("") { "%02x".format(it) }
                        if (hash != entry.sha256) {
                            return@withContext Result.failure(Exception("SHA-256 не совпадает"))
                        }
                    }

                    pluginManager.installPlugin(script, entry.downloadUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка установки из каталога", e)
                Result.failure(e)
            }
        }
    }

    /** Проверить обновления для установленных плагинов */
    suspend fun checkForUpdates(catalogUrl: String = DEFAULT_CATALOG): Result<List<Pair<PluginEntity, CatalogEntry>>> {
        return try {
            val catalog = fetchCatalog(catalogUrl).getOrThrow()
            val updates = mutableListOf<Pair<PluginEntity, CatalogEntry>>()
            for (entry in catalog) {
                val installed = pluginDao.getById(entry.id) ?: continue
                if (installed.installedVersion != entry.version) {
                    updates.add(installed to entry)
                }
            }
            Result.success(updates)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Установить плагин из URL */
    suspend fun installFromUrl(url: String): Result<PluginManifest> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isValidUrl(url)) return@withContext Result.failure(Exception("Invalid URL"))
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                    val script = resp.body?.string()
                        ?: return@withContext Result.failure(Exception("Пустой ответ"))
                    pluginManager.installPlugin(script, url)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Установить плагин из локального файла */
    suspend fun installFromFile(filePath: String): Result<PluginManifest> {
        return withContext(Dispatchers.IO) {
            try {
                val script = java.io.File(filePath).readText()
                pluginManager.installPlugin(script, "file://$filePath")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
