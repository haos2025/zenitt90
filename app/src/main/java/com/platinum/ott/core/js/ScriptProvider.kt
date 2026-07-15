package com.platinum.ott.core.js

import android.content.Context
import app.cash.quickjs.QuickJs
import java.io.File
import java.security.MessageDigest

class ScriptProvider(private val context: Context) {
    private val scriptsDir = File(context.filesDir, "scripts").apply { mkdirs() }

    fun getScript(name: String): String? = File(scriptsDir, "$name.js").let { if (it.exists()) it.readText() else null }

    fun saveScript(name: String, content: String) {
        File(scriptsDir, "$name.js").writeText(content)
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
