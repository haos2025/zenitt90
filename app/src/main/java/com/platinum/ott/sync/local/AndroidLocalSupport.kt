package com.platinum.ott.sync.local

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.reflect.TypeToken
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.platform.isTV
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Кто мы для других устройств: стабильный идентификатор установки, имя (то же, что в «Мои устройства») и тип. */
class AndroidLocalIdentity(private val appContext: Context, private val prefs: AuthPreferences) : LocalIdentity {
    override val deviceId: String
        get() = prefs.localDeviceId ?: UUID.randomUUID().toString().also { prefs.localDeviceId = it }

    override val deviceName: String
        get() {
            prefs.deviceName?.takeIf { it.isNotBlank() }?.let { return it.take(64) }
            val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = Build.MODEL.orEmpty()
            val full = if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank()) model else "$manufacturer $model"
            return full.ifBlank { if (deviceType == "tv") "Телевизор" else "Телефон" }.take(64)
        }

    override val deviceType: String get() = if (isTV(appContext)) "tv" else "phone"
}

/** Связанные устройства и их секреты. Лежат в EncryptedSharedPreferences (Keystore), не в обычной базе. */
class EncryptedPairedStore(context: Context) : PairedStore {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext, "zenith_paired_devices",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val gson = LocalProto.gson
    private val type = object : TypeToken<List<PairedDevice>>() {}.type
    private val lock = Any()

    private fun load(): MutableMap<String, PairedDevice> {
        val json = prefs.getString("devices", null) ?: return LinkedHashMap()
        val list: List<PairedDevice>? = try { gson.fromJson(json, type) } catch (_: Exception) { null }
        return LinkedHashMap<String, PairedDevice>().also { m -> list.orEmpty().forEach { if (it.id.isNotBlank()) m[it.id] = it } }
    }

    private fun save(m: Map<String, PairedDevice>) {
        prefs.edit().putString("devices", gson.toJson(m.values.toList())).apply()
    }

    override fun all(): List<PairedDevice> = synchronized(lock) { load().values.sortedBy { it.pairedAt } }
    override fun get(id: String): PairedDevice? = synchronized(lock) { load()[id] }
    override fun put(device: PairedDevice) = synchronized(lock) { val m = load(); m[device.id] = device; save(m) }
    override fun remove(id: String) = synchronized(lock) { val m = load(); if (m.remove(id) != null) save(m) }
}

/** Отправка запросов другому устройству по HTTP (шифрование — на уровне протокола, не транспорта). */
class OkHttpTransport : Transport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // push большого набора + применение на получателе
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun post(endpoint: Endpoint, path: String, body: String): TransportResponse {
        val host = endpoint.host.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        val url = HttpUrl.Builder().scheme("http").host(host).port(endpoint.port).encodedPath(path).build()
        val request = Request.Builder().url(url).post(body.toRequestBody(json)).build()
        return suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use { it.body?.string().orEmpty() }
                        cont.resume(TransportResponse(response.code, text))
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
            })
        }
    }
}

/** Разбор адреса, введённого вручную: «192.168.1.5» или «192.168.1.5:37221». */
object EndpointParser {
    fun parse(input: String): Endpoint? {
        val t = input.trim().removePrefix("http://").trimEnd('/')
        if (t.isEmpty()) return null
        val idx = t.lastIndexOf(':')
        if (idx > 0 && !t.contains("::") && t.count { it == ':' } == 1) {
            val port = t.substring(idx + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            val host = t.substring(0, idx)
            return if (valid(host)) Endpoint(host, port) else null
        }
        return if (valid(t)) Endpoint(t) else null
    }

    private fun valid(h: String) = h.isNotBlank() && h.length <= 253 && h.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}
