package com.exodash

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grava cada passo do app em arquivo.
 * Se crashar, o ultimo "breadcrumb" mostra onde estava.
 */
object Breadcrumbs {

    private var arquivo: File? = null
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun iniciar(context: Context) {
        try {
            val pasta = context.getExternalFilesDir("crashes")
            pasta?.mkdirs()
            arquivo = File(pasta, "rastro.txt")
            // Limpa o arquivo a cada inicio
            arquivo?.writeText("")
            registrar("APP INICIADO")
        } catch (e: Exception) {}
    }

    fun registrar(evento: String) {
        val linha = "${fmt.format(Date())} | $evento\n"
        try {
            arquivo?.appendText(linha)
        } catch (e: Exception) {}
        Log.d("Breadcrumb", evento)
    }

    fun registrarErro(evento: String, t: Throwable) {
        val linha = "${fmt.format(Date())} | ERRO: $evento -> ${t.message}\n"
        try {
            arquivo?.appendText(linha)
        } catch (e: Exception) {}
        Log.e("Breadcrumb", evento, t)
    }
}
