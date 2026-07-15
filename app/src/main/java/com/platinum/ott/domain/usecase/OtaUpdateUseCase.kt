package com.platinum.ott.domain.usecase

import com.platinum.ott.core.js.ScriptProvider
import com.platinum.ott.data.remote.ZenithApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OtaUpdateUseCase(private val scriptProvider: ScriptProvider, private val api: ZenithApiService) {
    data class OtaResult(val updated: Int, val skipped: Int, val failed: Int)

    suspend fun execute(): Result<OtaResult> = withContext(Dispatchers.IO) {
        try {
            val manifest = api.getScriptManifest()
            var updated = 0; var skipped = 0; var failed = 0
            for (entry in manifest) {
                try {
                    val existing = scriptProvider.getScript(entry.name)
                    val existingHash = existing?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { "%02x".format(it) } }
                    if (existingHash == entry.sha256) { skipped++; continue }
                    val resp = api.downloadScript(entry.name)
                    val content = resp.body()?.string() ?: throw Exception("Empty body")
                    scriptProvider.saveScript(entry.name, content); updated++
                } catch (_: Exception) { failed++ }
            }
            Result.success(OtaResult(updated, skipped, failed))
        } catch (e: Exception) { Result.failure(e) }
    }
}
