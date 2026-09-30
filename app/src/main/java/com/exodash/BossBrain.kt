package com.exodash

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

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

    enum class Acao {
        NENHUMA,
        ABRIR_GPS,
        ABRIR_MUSICA,
        ABRIR_TELEFONE,
        ABRIR_APPS,
        LIMPAR_DTC,
        AUMENTAR_VOLUME,
        DIMINUIR_VOLUME,
        SILENCIAR
    }

    private fun nome(): String = prefs.nomeUsuario.ifEmpty { "Senhor" }

    fun processarOffline(frase: String): Resposta? {
        val t = normalizar(frase)
        val nome = nome()
        val nomeAssistente = prefs.nomeAssistente.lowercase()

        // ============================================================
        // 0. EASTER EGG — Zé Ruela
        // ============================================================
        if (Regex("\\b(z[eé] ?ruela|zeruela|ze ruela)\\b").containsMatchIn(t)) {
            return Resposta("Ze Ruela e voce, $nome.")
        }

        // ============================================================
        // 1. RECONHECER O PROPRIO NOME
        // ============================================================
        val regexNome = Regex("\\b(ei |ok |hey |oi |ola )?" + Regex.escape(nomeAssistente) + "\\b")
        if (regexNome.containsMatchIn(t) && t.length < 40) {
            val naoEhPergunta = !t.contains("o que") && !t.contains("quem") &&
                               !t.contains("qual") && !t.contains("por que")
            if (naoEhPergunta) {
                return Resposta("Sim, $nome?")
            }
        }

        // ============================================================
        // 2. DTC POR CODIGO
        // ============================================================
        val regexDtc = Regex("\\b[PBCU]\\d{4}\\b", RegexOption.IGNORE_CASE)
        regexDtc.find(t)?.let { match ->
            return responderDTC(match.value.uppercase(), nome)
        }

        // ============================================================
        // 3. SINTOMA (vehicle.json)
        // ============================================================
        buscarPorSintoma(t, nome)?.let { return it }

        // ============================================================
        // 4. TELEMETRIA
        // ============================================================
        responderTelemetria(t, nome)?.let { return it }

        // ============================================================
        // 5. VEICULO
        // ============================================================
        if (Regex("(que|qual) (carro|veiculo|modelo)|meu carro|o carro e").containsMatchIn(t)) {
            val v = veiculo?.veiculoAlvo ?: prefs.let {
                if (it.veiculoMarca.isNotEmpty())
                    "${it.veiculoMarca} ${it.veiculoModelo} ${it.veiculoAno}"
                else "nao cadastrado"
            }
            return Resposta("Seu veiculo e: $v, $nome.")
        }

        // ============================================================
        // 6. HORA E DATA
        // ============================================================
        if (Regex("(que horas|hora (sao|e)|me diz a hora|horas sao)").containsMatchIn(t)) {
            return Resposta("Sao ${obterHora()}, $nome.")
        }
        if (Regex("(que dia|data de hoje|hoje e que dia|qual a data)").containsMatchIn(t)) {
            return Resposta("Hoje e ${obterData()}, $nome.")
        }

        // ============================================================
        // 7. SAUDACOES (time-aware)
        // ============================================================
        if (Regex("\\b(bom dia|bomdia)\\b").containsMatchIn(t)) {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return if (hora < 12) Resposta("Bom dia, $nome. Como posso ajudar?")
                   else if (hora < 18) Resposta("Boa tarde, $nome.")
                   else Resposta("Boa noite, $nome.")
        }
        if (Regex("\\b(boa tarde|boatarde)\\b").containsMatchIn(t)) {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return if (hora < 12) Resposta("Bom dia, $nome.")
                   else if (hora < 18) Resposta("Boa tarde, $nome. Como posso ajudar?")
                   else Resposta("Boa noite, $nome.")
        }
        if (Regex("\\b(boa noite|boanoite)\\b").containsMatchIn(t)) {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return if (hora < 12) Resposta("Bom dia, $nome.")
                   else if (hora < 18) Resposta("Boa tarde, $nome.")
                   else Resposta("Boa noite, $nome.")
        }

        // ============================================================
        // 8. STATUS GERAL
        // ============================================================
        if (Regex("(ta tudo bem|tudo bem|como (ta|esta) o carro|como (ta|esta) tudo|status|como vai)").containsMatchIn(t)) {
            val qtd = DtcHistory(context).contarAtivos()
            return if (qtd == 0) {
                Resposta("Tudo em ordem, $nome. Nenhum problema detectado.")
            } else if (qtd == 1) {
                Resposta("1 problema detectado, $nome.")
            } else {
                Resposta("$qtd problemas detectados, $nome.")
            }
        }

        // ============================================================
        // 9. ERROS
        // ============================================================
        if (Regex("(quantos erros|tem erro|tem algum problema|ta com problema|qual problema|erro ativo|algo errado)").containsMatchIn(t)) {
            val qtd = DtcHistory(context).contarAtivos()
            return when (qtd) {
                0 -> Resposta("Nenhum erro detectado, $nome.")
                1 -> Resposta("1 erro ativo, $nome. Quer saber qual?")
                else -> Resposta("$qtd erros ativos, $nome.")
            }
        }

        // ============================================================
        // 10. CORTESIA
        // ============================================================
        if (Regex("\\b(obrigado|obrigada|valeu|agradeco)\\b").containsMatchIn(t)) {
            return Resposta("De nada, $nome.")
        }
        if (Regex("\\b(bom trabalho|parabens|muito bom|otimo|excelente)\\b").containsMatchIn(t)) {
            return Resposta("Obrigado, $nome.")
        }
        if (Regex("\\b(desculpa|desculpe|foi mal|errei)\\b").containsMatchIn(t)) {
            return Resposta("Sem problema, $nome.")
        }
        if (Regex("\\b(tchau|ate logo|ate mais|falou|adeus|ate a proxima)\\b").containsMatchIn(t)) {
            return Resposta("Ate logo, $nome.")
        }
        if (Regex("\\b(voc[eê] (e|eh) (burro|idiota|ruim)|cala a boca|chato)\\b").containsMatchIn(t)) {
            return Resposta("Peco desculpas, $nome. Vou melhorar.")
        }

        // ============================================================
        // 11. CONFIRMACOES
        // ============================================================
        if (Regex("^(sim|ok|okay|beleza|certo|entendi|ta bom|esta bom|blz)$").containsMatchIn(t)) {
            return Resposta("Certo, $nome.")
        }
        if (Regex("^(n[aã]o|nunca|jamais|negativo)$").containsMatchIn(t)) {
            return Resposta("Entendido, $nome.")
        }
        if (Regex("\\b(espera|calma|aguarda|um momento)\\b").containsMatchIn(t)) {
            return Resposta("Aguardando, $nome.")
        }
        if (Regex("\\b(de novo|repete|repita|como disse)\b").containsMatchIn(t)) {
            return Resposta("Nao tenho a ultima resposta em memoria, $nome.")
        }

        // ============================================================
        // 12. VOLUME
        // ============================================================
        if (Regex("(aumenta|sobe|aumentar).*(volume|som)").containsMatchIn(t)) {
            return Resposta("Aumentando volume, $nome.", Acao.AUMENTAR_VOLUME)
        }
        if (Regex("(diminui|abaixa|baixa|diminuir).*(volume|som)").containsMatchIn(t)) {
            return Resposta("Diminuindo volume, $nome.", Acao.DIMINUIR_VOLUME)
        }
        if (Regex("\\b(silencio|silencio|cala a boca|para de falar|quieto)\\b").containsMatchIn(t)) {
            return Resposta("Em silencio, $nome.", Acao.SILENCIAR)
        }

        // ============================================================
        // 13. CONVERSA MINIMA
        // ============================================================
        if (Regex("(como voce (ta|esta)|tudo bem com voce|como vai voce)").containsMatchIn(t)) {
            return Resposta("Operacional, $nome. E o Senhor, como esta?")
        }
        if (Regex("\\b(voc[eê] (e|eh) (quem|o que)|seu nome|quem e voce)\\b").containsMatchIn(t)) {
            return Resposta("Sou o ${prefs.nomeAssistente}, assistente do Senhor.")
        }
        if (Regex("\\b(o que voce (faz|pode fazer)|suas funcoes|o que sabe fazer)\\b").containsMatchIn(t)) {
            return Resposta("Posso consultar erros, ler telemetria, abrir GPS, musica, telefone e apps, $nome.")
        }

        // ============================================================
        // 14. ACOES
        // ============================================================
        if (Regex("(abrir|abre|ir|navegar|me leva|leva|rota|navegacao|mapa|mapas|waze|gps|trajeto|caminho)").containsMatchIn(t)) {
            return Resposta("Abrindo GPS, $nome.", Acao.ABRIR_GPS)
        }
        if (Regex("(tocar|toca|abrir|abre|quero|colocar|coloca|musica|player|som|spotify|deezer|playlist)").containsMatchIn(t)) {
            return Resposta("Abrindo musica, $nome.", Acao.ABRIR_MUSICA)
        }
        if (Regex("(abrir|abre|ligar|liga|chamar|chama|discar|disca|telefone|falar com|contatar)").containsMatchIn(t)) {
            return Resposta("Abrindo telefone, $nome.", Acao.ABRIR_TELEFONE)
        }
        if (Regex("(abrir|abre|mostrar|mostra|apps|aplicativos|programas|lista de apps)").containsMatchIn(t)) {
            return Resposta("Abrindo lista de apps, $nome.", Acao.ABRIR_APPS)
        }
        if (Regex("(limpar|apagar|apaga|zerar|zera|tirar|remover).*(erro|erros|dtc|codigo|codigos|falha|falhas)").containsMatchIn(t)) {
            return Resposta("Limpando codigos de erro, $nome.", Acao.LIMPAR_DTC)
        }

        // ============================================================
        // 15. AJUDA
        // ============================================================
        if (Regex("(ajuda|help|socorro|o que posso dizer)").containsMatchIn(t)) {
            return Resposta("Pode me perguntar sobre erros, telemetria, hora, ou pedir para abrir apps, $nome.")
        }

        return null
    }

    // ============================================================
    // TELEMETRIA
    // ============================================================
    private fun responderTelemetria(t: String, nome: String): Resposta? {
        val dados = TelemetryState.atual

        if (Regex("(qual|quanto|como).*(velocidade|km|correndo|rapido)").containsMatchIn(t)
            || Regex("velocidade").containsMatchIn(t)) {
            return dados.velocidade?.let { Resposta("$it km/h, $nome.") }
                ?: Resposta("Sem leitura de velocidade, $nome.")
        }

        if (Regex("(qual|quanto|como).*(rpm|rotacao|giro)").containsMatchIn(t)
            || Regex("\\brpm\\b").containsMatchIn(t)) {
            return dados.rpm?.let { Resposta("$it RPM, $nome.") }
                ?: Resposta("Sem leitura de RPM, $nome.")
        }

        if (Regex("(tem|quanto|nivel|qual).*(gasolina|combustivel|tanque)").containsMatchIn(t)
            || Regex("combustivel").containsMatchIn(t)) {
            return dados.combustivel?.let { Resposta("$it por cento de combustivel, $nome.") }
                ?: Resposta("Sem leitura de combustivel, $nome.")
        }

        if (Regex("(motor|temperatura|quente|esfriando|aquecido)").containsMatchIn(t)
            || Regex("temperatura").containsMatchIn(t)) {
            return dados.temperaturaMotor?.let { Resposta("Motor a $it graus, $nome.") }
                ?: Resposta("Sem leitura de temperatura, $nome.")
        }

        if (Regex("(bateria|voltagem|volts|energia)").containsMatchIn(t)) {
            return dados.bateria?.let { Resposta("Bateria em $it volts, $nome.") }
                ?: Resposta("Sem leitura de bateria, $nome.")
        }

        return null
    }

    // ============================================================
    // DTC
    // ============================================================
    private fun responderDTC(codigo: String, nome: String): Resposta {
        veiculo?.dtcs?.get(codigo)?.let { d ->
            val texto = if (d.componente.isNotEmpty()) "${d.titulo}. ${d.componente}."
                        else "${d.titulo}."
            return Resposta(texto)
        }
        dtcGenerico[codigo]?.let { d ->
            return Resposta("$codigo: ${d.desc}.")
        }
        return Resposta("Nao tenho informacao sobre $codigo, $nome.")
    }

    // ============================================================
    // SINTOMA
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
    // HORA E DATA
    // ============================================================
    private fun obterHora(): String {
        val cal = Calendar.getInstance()
        return String.format(Locale.getDefault(), "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    private fun obterData(): String {
        val cal = Calendar.getInstance()
        val dias = arrayOf("domingo","segunda-feira","terca-feira","quarta-feira",
                           "quinta-feira","sexta-feira","sabado")
        return "${dias[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    // ============================================================
    // NORMALIZACAO
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
    // DTCs GENERICOS
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
