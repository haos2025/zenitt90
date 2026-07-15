package com.platinum.ott

import android.app.Application
import android.util.Log
import com.platinum.ott.core.ServiceLocator
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZenithApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        setupCrashHandler()
    }
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logDir = File(filesDir, "crash_logs"); logDir.mkdirs()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val sw = StringWriter(); throwable.printStackTrace(PrintWriter(sw))
                File(logDir, "crash_$ts.txt").writeText("Thread: ${thread.name}\\n$sw")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
