package com.platinum.ott.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthPreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        context, "zenith_auth_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var type: String? get() = prefs.getString("type", null); set(v) = prefs.edit().putString("type", v).apply()
    var host: String? get() = prefs.getString("host", null); set(v) = prefs.edit().putString("host", v).apply()
    var username: String? get() = prefs.getString("username", null); set(v) = prefs.edit().putString("username", v).apply()
    var password: String? get() = prefs.getString("password", null); set(v) = prefs.edit().putString("password", v).apply()
    var m3uUrl: String? get() = prefs.getString("m3u_url", null); set(v) = prefs.edit().putString("m3u_url", v).apply()
    var syncToken: String? get() = prefs.getString("sync_token", null); set(v) = prefs.edit().putString("sync_token", v).apply()
    var lastSyncTimestamp: Long get() = prefs.getLong("last_sync_ts", 0); set(v) = prefs.edit().putLong("last_sync_ts", v).apply()

    /**
     * syncToken раньше объявлялся, но нигде не заполнялся и не отправлялся —
     * /sync и /sync/push не могли определить, чьи это данные. Здесь он
     * используется как устойчивый device id: генерируется один раз при
     * первом обращении (UUID), дальше переиспользуется из EncryptedSharedPreferences.
     * Не привязан к аккаунту (M3U/Xtream логин — это не пользовательский
     * аккаунт с токеном) — это идентификатор конкретной установки приложения.
     */
    fun getOrCreateSyncToken(): String {
        val existing = syncToken
        if (existing != null) return existing
        val generated = java.util.UUID.randomUUID().toString()
        syncToken = generated
        return generated
    }

    fun clear() { prefs.edit().clear().apply() }
    fun isLoggedIn(): Boolean = type != null && (m3uUrl != null || (host != null && username != null))
}
