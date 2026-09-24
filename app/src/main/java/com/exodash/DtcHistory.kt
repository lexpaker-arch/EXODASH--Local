package com.exodash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gerencia o histórico de codigos de falha (DTCs).
 * Salva em filesDir/dtc_history.json
 * Limite: 200 registros (substitui os mais antigos)
 */
class DtcHistory(private val context: Context) {

    data class Entrada(
        val codigo: String,
        val descricao: String,
        val urgencia: String,
        var dataDeteccao: Long,
        var dataResolucao: Long? = null,
        var ativo: Boolean = true
    )

    private val arquivo: File get() = File(context.filesDir, "dtc_history.json")
    private val limite = 200

    // ============================================
    // LER / SALVAR
    // ============================================
    fun lerTodas(): List<Entrada> {
        return try {
            if (!arquivo.exists()) return emptyList()
            val texto = arquivo.readText()
            val json = JSONObject(texto)
            val arr = json.optJSONArray("entries") ?: return emptyList()
            val lista = mutableListOf<Entrada>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add(
                    Entrada(
                        codigo = o.optString("codigo"),
                        descricao = o.optString("descricao"),
                        urgencia = o.optString("urgencia"),
                        dataDeteccao = o.optLong("dataDeteccao"),
                        dataResolucao = o.optLong("dataResolucao", 0L).takeIf { it > 0 },
                        ativo = o.optBoolean("ativo", true)
                    )
                )
            }
            lista
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun salvar(lista: List<Entrada>) {
        try {
            val arr = JSONArray()
            // Manter no maximo 200 (mais recentes)
            val limitada = if (lista.size > limite) lista.takeLast(limite) else lista
            for (e in limitada) {
                arr.put(JSONObject().apply {
                    put("codigo", e.codigo)
                    put("descricao", e.descricao)
                    put("urgencia", e.urgencia)
                    put("dataDeteccao", e.dataDeteccao)
                    put("dataResolucao", e.dataResolucao ?: 0L)
                    put("ativo", e.ativo)
                })
            }
            val root = JSONObject().apply { put("entries", arr) }
            arquivo.writeText(root.toString())
        } catch (e: Exception) {}
    }

    // ============================================
    // API PUBLICA
    // ============================================

    /** Retorna os DTCs atualmente ativos (nao resolvidos). */
    fun ativos(): List<Entrada> = lerTodas().filter { it.ativo }

    /** Quantidade de DTCs ativos. */
    fun contarAtivos(): Int = ativos().size

    /**
     * Sincroniza uma lista de DTCs ativos vinda do OBD.
     * - DTC novo -> adiciona
     * - DTC que sumiu -> marca como resolvido
     */
    fun sincronizar(dtcsDoObd: List<String>, descricoes: Map<String, String> = emptyMap()) {
        val lista = lerTodas().toMutableList()
        val agora = System.currentTimeMillis()

        // 1. Marca ativos que NAO estao mais no OBD como resolvidos
        for (e in lista) {
            if (e.ativo && e.codigo !in dtcsDoObd) {
                e.ativo = false
                e.dataResolucao = agora
            }
        }

        // 2. Adiciona DTCs novos
        for (codigo in dtcsDoObd) {
            val jaAtivo = lista.any { it.ativo && it.codigo == codigo }
            if (!jaAtivo) {
                // Se ja existia antes (resolvido), reativa
                val existente = lista.firstOrNull { it.codigo == codigo }
                if (existente != null) {
                    existente.ativo = true
                    existente.dataResolucao = null
                    existente.dataDeteccao = agora
                } else {
                    lista.add(
                        Entrada(
                            codigo = codigo,
                            descricao = descricoes[codigo] ?: "Descricao nao disponivel",
                            urgencia = "media",
                            dataDeteccao = agora,
                            ativo = true
                        )
                    )
                }
            }
        }

        salvar(lista)
    }

    /** Marca todos os DTCs como resolvidos (apos comando limpar). */
    fun marcarTodosResolvidos() {
        val lista = lerTodas().toMutableList()
        val agora = System.currentTimeMillis()
        for (e in lista) {
            if (e.ativo) {
                e.ativo = false
                e.dataResolucao = agora
            }
        }
        salvar(lista)
    }

    /** Limpa todo o historico. */
    fun limparTudo() {
        try { arquivo.delete() } catch (e: Exception) {}
    }

    companion object {
        private val fmt = SimpleDateFormat("dd/MM/yyyy 'as' HH:mm", Locale("pt", "BR"))
        fun formatarData(millis: Long): String = fmt.format(Date(millis))
    }
}
