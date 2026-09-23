package com.exodash

import android.content.Context
import org.json.JSONObject

class BossBrain(private val context: Context) {

    private val dtcMap: Map<String, DtcInfo> by lazy { carregarDTCs() }
    private val prefs = Prefs(context)

    data class DtcInfo(val desc: String, val urgencia: String)

    data class Resposta(
        val texto: String,
        val acao: Acao = Acao.NENHUMA
    )

    enum class Acao { NENHUMA, ABRIR_GPS, ABRIR_MUSICA, ABRIR_TELEFONE, ABRIR_APPS, LIMPAR_DTC }

    /** Retorna o nome atual do usuario (padrao "Senhor"). */
    private fun nome(): String = prefs.nomeUsuario.ifEmpty { "Senhor" }

    fun processarOffline(frase: String): Resposta? {
        val t = normalizar(frase)
        val nome = nome()

        // 1. DTC
        val regexDtc = Regex("\\b[PBCU]\\d{4}\\b", RegexOption.IGNORE_CASE)
        regexDtc.find(t)?.let { match ->
            val codigo = match.value.uppercase()
            val info = dtcMap[codigo]
            return if (info != null) {
                Resposta("$codigo: ${info.desc}.")
            } else {
                Resposta("Nao tenho informacao sobre $codigo, $nome.")
            }
        }

        if (Regex("(abrir|abre|ir|navegar).*(gps|mapa|navega)").containsMatchIn(t)) {
            return Resposta("Abrindo GPS, $nome.", Acao.ABRIR_GPS)
        }

        if (Regex("(tocar|abrir|quero|colocar).*(musica|player|som)").containsMatchIn(t)) {
            return Resposta("Abrindo musica, $nome.", Acao.ABRIR_MUSICA)
        }

        if (Regex("(abrir|abre|ligar|chamar).*(telefone|discar)").containsMatchIn(t)) {
            return Resposta("Abrindo telefone, $nome.", Acao.ABRIR_TELEFONE)
        }

        if (Regex("(abrir|abre|mostrar).*(apps|aplicativos|programas)").containsMatchIn(t)) {
            return Resposta("Abrindo lista de apps, $nome.", Acao.ABRIR_APPS)
        }

        if (Regex("(limpar|apagar|zerar).*(erro|erros|dtc|codigo)").containsMatchIn(t)) {
            return Resposta("Limpando codigos de erro, $nome.", Acao.LIMPAR_DTC)
        }

        if (Regex("(status|como).*(carro|motor|veiculo)").containsMatchIn(t)) {
            return Resposta("OBD offline no momento, $nome.")
        }

        if (Regex("(ajuda|o que voce faz|comandos)").containsMatchIn(t)) {
            return Resposta("Posso consultar codigos de erro, abrir GPS, musica, telefone e apps, $nome.")
        }

        return null
    }

    private fun normalizar(s: String): String {
        var t = s.lowercase()
        t = t.replace("á","a").replace("à","a").replace("â","a").replace("ã","a")
             .replace("é","e").replace("ê","e")
             .replace("í","i")
             .replace("ó","o").replace("ô","o").replace("õ","o")
             .replace("ú","u")
             .replace("ç","c")
        val numeros = mapOf(
            "zero" to "0", "um" to "1", "dois" to "2", "tres" to "3", "três" to "3",
            "quatro" to "4", "cinco" to "5", "seis" to "6", "sete" to "7",
            "oito" to "8", "nove" to "9"
        )
        for ((k, v) in numeros) {
            t = t.replace(Regex("\\b$k\\b"), v)
        }
        return t
    }

    private fun carregarDTCs(): Map<String, DtcInfo> {
        return try {
            val json = context.assets.open("dtc_codes.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val map = mutableMapOf<String, DtcInfo>()
            obj.keys().forEach { codigo ->
                val item = obj.getJSONObject(codigo)
                map[codigo] = DtcInfo(
                    desc = item.optString("desc", "Descricao nao disponivel"),
                    urgencia = item.optString("urgencia", "media")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
