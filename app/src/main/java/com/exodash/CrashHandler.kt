package com.exodash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val handlerAnterior: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            salvarCrash(thread, throwable)
        } catch (e: Exception) {}
        handlerAnterior?.uncaughtException(thread, throwable)
    }

    private fun salvarCrash(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()

        val agora = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

        val texto = buildString {
            append("=======================================\n")
            append("EXODASH CRASH - $agora\n")
            append("=======================================\n\n")
            append("Thread: ${thread.name}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Versao do app: ${Updater.versaoLocal(context)}\n\n")
            append("Stack trace:\n")
            append(sw.toString())
            append("\n\n=======================================\n")
        }

        val pasta = context.getExternalFilesDir("crashes")
        if (pasta != null) {
            pasta.mkdirs()
            File(pasta, "crash_$agora.txt").writeText(texto)
            File(pasta, "ultimo_crash.txt").writeText(texto)
        }

        try {
            File(context.filesDir, "crash_$agora.txt").writeText(texto)
            File(context.filesDir, "ultimo_crash.txt").writeText(texto)
        } catch (e: Exception) {}
    }

    companion object {
        fun instalar(context: Context) {
            val anterior = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context, anterior))
        }
    }
}
