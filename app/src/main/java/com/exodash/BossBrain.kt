package com.exodash

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

class BossBrain(private val context: Context) {

    private val prefs = Prefs(context)

    private val dtcGenerico: Map<String, DtcGen> by lazy { carregarGenerico() }
    private val veiculo: VehicleConfig.DadoVeiculo? by lazy {
        VehicleConfig.carregar(context)
    }

    data class DtcGen(val desc: String, val urgencia: String)

    data class Resposta(
        val texto: String,
        val acao: Acao = Acao.NENHUMA
    )

    enum class Acao { NENHUMA, ABRIR_GPS, ABRIR_MUSICA, ABRIR_TELEFONE, ABRIR_APPS, LIMPAR_DTC }

    private fun nome(): String = prefs.nomeUsuario.ifEmpty { "Senhor" }

    fun processarOffline(frase: String): Resposta? {
        val t = normalizar(frase)
        val nome = nome()

        // ============================================
        // 1. DTC por codigo
        // ============================================
        val regexDtc = Regex("\\b[PBCU]\\d{4}\\b", RegexOption.IGNORE_CASE)
        regexDtc.find(t)?.let { match ->
            val codigo = match.value.uppercase()
            return responderDTC(codigo, nome)
        }

        // ============================================
        // 2. Busca por sintoma (vehicle.json)
        // ============================================
        val respostaSintoma = buscarPorSintoma(t, nome)
        if (respostaSintoma != null) return respostaSintoma

        // ============================================
        // 3. Saudações (B7)
        // ============================================
        if (Regex("\\b(bom dia|bomdia)\\b").containsMatchIn(t)) {
            return Resposta("Bom dia, $nome. Como posso ajudar?")
        }
        if (Regex("\\b(boa tarde|boatarde)\\b").containsMatchIn(t)) {
            return Resposta("Boa tarde, $nome. Como posso ajudar?")
        }
        if (Regex("\\b(boa noite|boanoite)\\b").containsMatchIn(t)) {
            return Resposta("Boa noite, $nome.")
        }

        // ============================================
        // 4. Hora (B6)
        // ============================================
        if (Regex("(que horas|hora (sao|é)|me diz a hora|horas sao)").containsMatchIn(t)) {
            val hora = obterHora()
            return Resposta("Sao $hora, $nome.")
        }

        // ============================================
        // 5. Status do carro (B4)
        // ============================================
        if (Regex("(ta tudo bem|tudo bem|como (ta|esta) o carro|como (ta|esta) tudo|status|como (ta|esta) o motor)").containsMatchIn(t)) {
            return Resposta("Tudo em ordem, $nome. Nenhum problema detectado.")
        }

        // ============================================
        // 6. Erros (B5)
        // ============================================
        if (Regex("(quantos erros|tem erro|tem algum problema|ta com problema|qual problema|erro ativo|algo errado)").containsMatchIn(t)) {
            val history = DtcHistory(context)
            val qtd = history.contarAtivos()
            return if (qtd == 0) {
                Resposta("Nenhum erro detectado, $nome.")
            } else if (qtd == 1) {
                Resposta("1 erro ativo, $nome. Quer saber qual?")
            } else {
                Resposta("$qtd erros ativos, $nome. Quer saber quais?")
            }
        }

        // ============================================
        // 7. Agradecimento (B8)
        // ============================================
        if (Regex("\\b(obrigado|obrigada|valeu|agradeco|agradeço)\\b").containsMatchIn(t)) {
            return Resposta("De nada, $nome.")
        }

        // ============================================
        // 8. Despedida (B9)
        // ============================================
        if (Regex("\\b(tchau|ate logo|ate mais|falou|adeus)\\b").containsMatchIn(t)) {
            return Resposta("Ate logo, $nome.")
        }

        // ============================================
        // 9. Abrir GPS (B1 — variações)
        // ============================================
        if (Regex("(abrir|abre|ir|navegar|me leva|leva|rota|navegacao|navegação|mapa|mapas|waze|google maps|gps|trajeto|caminho)").containsMatchIn(t)) {
            return Resposta("Abrindo GPS, $nome.", Acao.ABRIR_GPS)
        }

        // ============================================
        // 10. Abrir música (B2 — variações)
        // ============================================
        if (Regex("(tocar|toca|abrir|abre|quero|colocar|coloca|musica|música|player|som|spotify|deezer|youtube music|playlist|toca uma)").containsMatchIn(t)) {
            return Resposta("Abrindo musica, $nome.", Acao.ABRIR_MUSICA)
        }

        // ============================================
        // 11. Telefone (B3 — variações)
        // ============================================
        if (Regex("(abrir|abre|ligar|liga|chamar|chama|discar|disca|telefone|falar com|contatar|whatsapp)").containsMatchIn(t)) {
            return Resposta("Abrindo telefone, $nome.", Acao.ABRIR_TELEFONE)
        }

        // ============================================
        // 12. Apps
        // ============================================
        if (Regex("(abrir|abre|mostrar|mostra|apps|aplicativos|programas|lista de apps)").containsMatchIn(t)) {
            return Resposta("Abrindo lista de apps, $nome.", Acao.ABRIR_APPS)
        }

        // ============================================
        // 13. Limpar DTC
        // ============================================
        if (Regex("(limpar|apagar|apaga|zerar|zera|tirar|remover).*(erro|erros|dtc|codigo|codigos|falha|falhas)").containsMatchIn(t)) {
            return Resposta("Limpando codigos de erro, $nome.", Acao.LIMPAR_DTC)
        }

        // ============================================
        // 14. Ajuda
        // ============================================
        if (Regex("(ajuda|o que voce faz|comandos|o que posso fazer|help)").containsMatchIn(t)) {
            return Resposta("Posso consultar codigos de erro, abrir GPS, musica, telefone e apps. Tambem respondo sobre o tempo, hora e status do carro, $nome.")
        }

        return null
    }

    // ============================================================
    // DTC
    // ============================================================
    private fun responderDTC(codigo: String, nome: String): Resposta {
        veiculo?.dtcs?.get(codigo)?.let { d ->
            val texto = if (d.componente.isNotEmpty()) {
                "${d.titulo}. ${d.componente}."
            } else {
                "${d.titulo}."
            }
            return Resposta(texto)
        }
        dtcGenerico[codigo]?.let { d ->
            return Resposta("$codigo: ${d.desc}.")
        }
        return Resposta("Nao tenho informacao sobre $codigo, $nome.")
    }

    // ============================================================
    // Sintoma
    // ============================================================
    private fun buscarPorSintoma(texto: String, nome: String): Resposta? {
        val v = veiculo ?: return null

        v.dtcs.values.firstOrNull { dtc ->
            dtc.tagsSintomas.any { tag -> texto.contains(tag) }
        }?.let {
            return Resposta("${it.titulo}. ${it.componente}.")
        }

        v.falhasOcultas.firstOrNull { f ->
            f.tagsSintomas.any { tag -> texto.contains(tag) }
        }?.let {
            return Resposta("Pode ser ${it.titulo.lowercase()}, $nome.")
        }

        return null
    }

    // ============================================================
    // Hora
    // ============================================================
    private fun obterHora(): String {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return String.format("%02d:%02d", h, m)
    }

    // ============================================================
    // Normalizacao
    // ============================================================
    private fun normalizar(s: String): String {
        var t = s.lowercase().trim()
        t = t.replace("á","a").replace("à","a").replace("â","a").replace("ã","a")
             .replace("é","e").replace("ê","e")
             .replace("í","i")
             .replace("ó","o").replace("ô","o").replace("õ","o")
             .replace("ú","u").replace("ü","u")
             .replace("ç","c")
        val numeros = mapOf(
            "zero" to "0", "um" to "1", "dois" to "2", "tres" to "3",
            "quatro" to "4", "cinco" to "5", "seis" to "6", "sete" to "7",
            "oito" to "8", "nove" to "9"
        )
        for ((k, v) in numeros) {
            t = t.replace(Regex("\\b$k\\b"), v)
        }
        return t
    }

    // ============================================================
    // DTCs genericos
    // ============================================================
    private fun carregarGenerico(): Map<String, DtcGen> {
        return try {
            val json = context.assets.open("dtc_codes.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val map = mutableMapOf<String, DtcGen>()
            obj.keys().forEach { codigo ->
                val item = obj.getJSONObject(codigo)
                map[codigo] = DtcGen(
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
