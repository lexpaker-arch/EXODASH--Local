package com.exodash

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Gerencia o arquivo vehicle.json do veiculo atual.
 * Prioridade: filesDir/vehicle.json -> assets/vehicle.json
 */
object VehicleConfig {

    private const val NOME_ARQUIVO = "vehicle.json"

    data class DadoVeiculo(
        val veiculoAlvo: String,
        val dtcs: Map<String, DtcInfo>,
        val falhasOcultas: List<FalhaOculta>
    )

    data class DtcInfo(
        val codigo: String,
        val componente: String,
        val titulo: String,
        val descricao: String,
        val urgencia: String,
        val tagsSintomas: List<String>
    )

    data class FalhaOculta(
        val id: String,
        val tipo: String,
        val componente: String,
        val titulo: String,
        val descricao: String,
        val urgencia: String,
        val tagsSintomas: List<String>
    )

    private var cache: DadoVeiculo? = null

    /** Garante que o arquivo esta em filesDir (copia de assets na 1a vez). */
    fun garantirArquivo(context: Context) {
        try {
            val destino = File(context.filesDir, NOME_ARQUIVO)
            if (destino.exists()) return
            try {
                val input = context.assets.open(NOME_ARQUIVO)
                destino.outputStream().use { out -> input.copyTo(out) }
                Log.d("VehicleConfig", "vehicle.json copiado para filesDir")
            } catch (e: Exception) {
                Log.d("VehicleConfig", "Sem asset vehicle.json - usando so generico")
            }
        } catch (e: Exception) {
            Log.e("VehicleConfig", "Erro ao garantir arquivo", e)
        }
    }

    fun carregar(context: Context): DadoVeiculo? {
        cache?.let { return it }

        val texto = lerArquivo(context) ?: return null

        return try {
            val json = JSONObject(texto)
            val metadados = json.optJSONObject("metadados")
            val veiculo = metadados?.optString("veiculo_alvo", "Veiculo") ?: "Veiculo"

            val dtcMap = mutableMapOf<String, DtcInfo>()
            json.optJSONArray("falhas_registradas")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val codigo = o.optString("codigo", "").uppercase()
                    if (codigo.isEmpty()) continue
                    dtcMap[codigo] = DtcInfo(
                        codigo = codigo,
                        componente = o.optString("componente", ""),
                        titulo = o.optString("titulo", ""),
                        descricao = o.optString("descricao", ""),
                        urgencia = o.optString("urgencia", "media"),
                        tagsSintomas = optStringList(o, "tags_sintomas")
                    )
                }
            }

            val ocultas = mutableListOf<FalhaOculta>()
            json.optJSONArray("falhas_ocultas")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    ocultas.add(
                        FalhaOculta(
                            id = o.optString("id", ""),
                            tipo = o.optString("tipo", ""),
                            componente = o.optString("componente", ""),
                            titulo = o.optString("titulo", ""),
                            descricao = o.optString("descricao", ""),
                            urgencia = o.optString("urgencia", "media"),
                            tagsSintomas = optStringList(o, "tags_sintomas")
                        )
                    )
                }
            }

            DadoVeiculo(
                veiculoAlvo = veiculo,
                dtcs = dtcMap,
                falhasOcultas = ocultas
            ).also { cache = it }
        } catch (e: Exception) {
            Log.e("VehicleConfig", "Erro ao parsear", e)
            null
        }
    }

    private fun lerArquivo(context: Context): String? {
        try {
            val f = File(context.filesDir, NOME_ARQUIVO)
            if (f.exists()) return f.readText()
        } catch (e: Exception) {}
        try {
            return context.assets.open(NOME_ARQUIVO).bufferedReader().use { it.readText() }
        } catch (e: Exception) {}
        return null
    }

    private fun optStringList(o: JSONObject, key: String): List<String> {
        val arr = o.optJSONArray(key) ?: return emptyList()
        val lista = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            lista.add(arr.optString(i, "").lowercase())
        }
        return lista
    }

    fun invalidarCache() {
        cache = null
    }
}
