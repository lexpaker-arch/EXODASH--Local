package com.exodash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de eventos do app. Limite: 200 registros (substitui os mais antigos).
 * Salvo em filesDir/logs.json
 */
object LogEventos {

    private const val LIMITE = 200
    private const val ARQUIVO = "logs.json"
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))

    data class Evento(val timestamp: Long, val texto: String)

    private fun arquivo(context: Context) = File(context.filesDir, ARQUIVO)

    fun registrar(context: Context, evento: String) {
        try {
            val lista = ler(context).toMutableList()
            lista.add(Evento(System.currentTimeMillis(), evento))
            // Limitar tamanho
            val limitada = if (lista.size > LIMITE) lista.takeLast(LIMITE) else lista
            salvar(context, limitada)
        } catch (e: Exception) {}
    }

    fun ler(context: Context): List<Evento> {
        return try {
            val f = arquivo(context)
            if (!f.exists()) return emptyList()
            val json = JSONObject(f.readText())
            val arr = json.optJSONArray("eventos") ?: return emptyList()
            val lista = mutableListOf<Evento>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(Evento(o.optLong("ts"), o.optString("txt")))
            }
            lista
        } catch (e: Exception) { emptyList() }
    }

    private fun salvar(context: Context, lista: List<Evento>) {
        try {
            val arr = JSONArray()
            for (e in lista) {
                arr.put(JSONObject().apply {
                    put("ts", e.timestamp)
                    put("txt", e.texto)
                })
            }
            val root = JSONObject().apply { put("eventos", arr) }
            arquivo(context).writeText(root.toString())
        } catch (e: Exception) {}
    }

    fun limpar(context: Context) {
        try { arquivo(context).delete() } catch (e: Exception) {}
    }

    fun formatarHora(ts: Long): String = fmt.format(Date(ts))
}
