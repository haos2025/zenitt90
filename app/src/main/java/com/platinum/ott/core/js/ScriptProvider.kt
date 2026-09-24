package com.platinum.ott.core.js

import android.content.Context
import app.cash.quickjs.QuickJs
import java.io.File
import java.security.MessageDigest

class ScriptProvider(private val context: Context) {
    private val scriptsDir = File(context.filesDir, "scripts").apply { mkdirs() }

    // ФИКС (аудит): name раньше шёл прямо в File(scriptsDir, "$name.js") без
    // проверки. saveScript()/getScript() вызываются из OtaUpdateUseCase с
    // entry.name — полем из сетевого ответа (api.getScriptManifest()), не
    // константой. "../../../shared_prefs/xxx" в этом поле привёл бы к
    // записи/чтению файла ЗА ПРЕДЕЛАМИ scripts/ (path traversal), а
    // содержимое такого файла затем передаётся в QuickJs.evaluate() —
    // потенциальное выполнение произвольного кода при подмене манифеста.
    // Разрешены только буквы/цифры/подчёркивание/дефис — этого достаточно
    // для всех реальных имён скриптов (например "player_parser").
    private val validNameRegex = Regex("^[a-zA-Z0-9_-]+$")
    private fun scriptFile(name: String): File? {
        if (!validNameRegex.matches(name)) return null
        val file = File(scriptsDir, "$name.js")
        // Доп. проверка (defense in depth): даже если regex когда-нибудь
        // ослабят, canonicalPath должен остаться внутри scriptsDir.
        if (!file.canonicalPath.startsWith(scriptsDir.canonicalPath + File.separator)) return null
        return file
    }

    fun getScript(name: String): String? = scriptFile(name)?.let { if (it.exists()) it.readText() else null }

    fun saveScript(name: String, content: String) {
        scriptFile(name)?.writeText(content)
            ?: throw IllegalArgumentException("Недопустимое имя скрипта: $name")
    }

    fun clearAll() { scriptsDir.listFiles()?.forEach { it.delete() } }

    fun evaluateScript(scriptName: String, functionName: String, vararg args: String): String? {
        val script = getScript(scriptName) ?: return null
        return try {
            QuickJs.create().use { quickjs ->
                quickjs.evaluate(script)
                val q = 34.toChar()
                val bs = 92.toChar()
                val escaped = args.map { a -> q + a.replace(bs.toString(), bs.toString() + bs.toString()).replace(q.toString(), bs.toString() + q.toString()) + q }
                val call = functionName + "(" + escaped.joinToString(",") + ")"
                quickjs.evaluate(call) as? String
            }
        } catch (e: Exception) { null }
    }
}
