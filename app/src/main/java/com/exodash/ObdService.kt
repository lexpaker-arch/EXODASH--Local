package com.exodash

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket para o AndrOBD (plugin WebSocket Server).
 * Padrao: ws://127.0.0.1:35000
 *
 * Reconecta com backoff (5s, 10s, 20s, 30s...max 60s).
 * Loga formato bruto para diagnostico.
 */
class ObdService(
    private val context: Context,
    private val host: String,
    private val porta: Int,
    private val onStatus: (conectado: Boolean) -> Unit,
    private val onTelemetria: (TelemetryData) -> Unit
) {

    private var webSocket: WebSocket? = null
    private var conectado = false
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var parado = false
    private var tentativas = 0
    private var ultimoFormatoLogado = false

    private val reconectar = object : Runnable {
        override fun run() {
            if (!parado && !conectado) conectar()
        }
    }

    fun conectar() {
        parado = false
        val url = "ws://$host:$porta"
        val request = Request.Builder().url(url).build()

        try {
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    conectado = true
                    tentativas = 0
                    ultimoFormatoLogado = false
                    handler.post {
                        onStatus(true)
                        LogEventos.registrar(context, "OBD: conectado em $url")
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    // Loga o PRIMEIRO formato bruto recebido (para diagnostico)
                    if (!ultimoFormatoLogado) {
                        ultimoFormatoLogado = true
                        val preview = text.take(500)
                        LogEventos.registrar(context, "OBD formato: $preview")
                        Log.d("ObdService", "Formato bruto: $text")
                    }

                    val dados = parsear(text)
                    if (dados != null) {
                        handler.post { onTelemetria(dados) }
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    marcarOffline("closed")
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.d("ObdService", "Falha: ${t.message}")
                    marcarOffline("failure: ${t.message}")
                }
            })
        } catch (e: Exception) {
            marcarOffline("exception: ${e.message}")
        }
    }

    private fun marcarOffline(motivo: String) {
        val estavaConectado = conectado
        conectado = false
        if (estavaConectado) {
            handler.post {
                onStatus(false)
                LogEventos.registrar(context, "OBD: desconectado ($motivo)")
            }
        }
        if (!parado) {
            tentativas++
            val espera = when {
                tentativas <= 1 -> 5000L
                tentativas <= 3 -> 10000L
                tentativas <= 6 -> 20000L
                else -> 60000L
            }
            handler.removeCallbacks(reconectar)
            handler.postDelayed(reconectar, espera)
        }
    }

    fun parar() {
        parado = true
        handler.removeCallbacks(reconectar)
        try {
            webSocket?.close(1000, "fechando")
            webSocket = null
        } catch (e: Exception) {}
        conectado = false
    }

    fun limparDTCs() {
        try {
            // Formato 1: comando direto
            val cmd1 = JSONObject().apply { put("cmd", "resetTroubleCodes") }
            webSocket?.send(cmd1.toString())
            // Formato 2: comando ELM327
            val cmd2 = JSONObject().apply { put("cmd", "04") }
            webSocket?.send(cmd2.toString())
            // Formato 3: comando via texto
            webSocket?.send("resetTroubleCodes")
            LogEventos.registrar(context, "OBD: comando limpar DTC enviado")
        } catch (e: Exception) {
            LogEventos.registrar(context, "OBD: erro ao limpar DTC: ${e.message}")
        }
    }

    // ============================================================
    // PARSER — tenta 8 formatos comuns do AndrOBD / ELM327
    // ============================================================
    private fun parsear(texto: String): TelemetryData? {
        val t = texto.trim()
        if (t.isEmpty()) return null

        // Formato 1: array de objetos [{"pid":"0C","value":1234}, ...]
        if (t.startsWith("[")) return parsearArray(t)

        // Formato 2: objeto {"0C":1234,"0D":87,...}
        if (t.startsWith("{")) return parsearObjeto(t)

        // Formato 3: resposta ELM327 pura "41 0C 1A F8" (hex)
        if (t.matches(Regex("[0-9A-Fa-f ]+"))) return parsearHex(t)

        // Formato 4: "PID:0C VAL:1234" (linha unica)
        if (t.contains(":") && t.contains("VAL")) return parsearChaveValor(t)

        // Formato 5: linha unica de PIDs separados por virgula
        if (t.contains(",") && !t.contains("{")) return parsearCSV(t)

        return null
    }

    private fun parsearArray(t: String): TelemetryData? {
        return try {
            val arr = JSONArray(t)
            var velocidade: Int? = null
            var rpm: Int? = null
            var temp: Int? = null
            var comb: Int? = null
            var bat: Double? = null

            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val pid = item.optString("pid", "").uppercase().removePrefix("0X")
                val valor = item.opt("value") ?: item.opt("val")
                aplicarPid(pid, valor, { velocidade = it }, { rpm = it }, { temp = it }, { comb = it }, { bat = it })
            }
            if (velocidade == null && rpm == null && temp == null && comb == null && bat == null) null
            else TelemetryData(velocidade, rpm, temp, comb, bat)
        } catch (e: Exception) { null }
    }

    private fun parsearObjeto(t: String): TelemetryData? {
        return try {
            val obj = JSONObject(t)
            var velocidade: Int? = null
            var rpm: Int? = null
            var temp: Int? = null
            var comb: Int? = null
            var bat: Double? = null

            obj.keys().forEach { key ->
                val pid = key.uppercase().removePrefix("0X")
                val valor = obj.opt(key)
                aplicarPid(pid, valor, { velocidade = it }, { rpm = it }, { temp = it }, { comb = it }, { bat = it })
            }
            if (velocidade == null && rpm == null && temp == null && comb == null && bat == null) null
            else TelemetryData(velocidade, rpm, temp, comb, bat)
        } catch (e: Exception) { null }
    }

    private fun parsearHex(t: String): TelemetryData? {
        return try {
            // "41 0C 1A F8" = resposta do PID 0C (RPM)
            val bytes = t.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16) }
            if (bytes.size < 3) return null
            val modo = bytes[0]
            if (modo != 0x41 && modo != 0x42) return null
            val pid = bytes[1].toString(16).uppercase().padStart(2, '0')
            val valor: Int = when {
                bytes.size >= 4 -> (bytes[2] shl 8) or bytes[3]
                else -> bytes[2]
            }
            when (pid) {
                "0C" -> return TelemetryData(rpm = valor / 4)
                "0D" -> return TelemetryData(velocidade = valor)
                "05" -> return TelemetryData(temperaturaMotor = valor - 40)
                "2F" -> return TelemetryData(combustivel = valor * 100 / 255)
                "42" -> return TelemetryData(bateria = valor / 1000.0)
            }
            null
        } catch (e: Exception) { null }
    }

    private fun parsearChaveValor(t: String): TelemetryData? {
        return try {
            // "PID:0C VAL:1234"
            val regex = Regex("PID:([0-9A-F]+)\\s+VAL:(\\d+)", RegexOption.IGNORE_CASE)
            val match = regex.find(t) ?: return null
            val pid = match.groupValues[1].uppercase()
            val valor = match.groupValues[2].toIntOrNull() ?: return null
            val dados = when (pid) {
                "0C" -> TelemetryData(rpm = valor)
                "0D" -> TelemetryData(velocidade = valor)
                "05" -> TelemetryData(temperaturaMotor = valor - 40)
                "2F" -> TelemetryData(combustivel = valor)
                else -> return null
            }
            dados
        } catch (e: Exception) { null }
    }

    private fun parsearCSV(t: String): TelemetryData? {
        return try {
            // "0C,1234" ou "0C=1234"
            val partes = t.split(",", "=").map { it.trim() }
            if (partes.size < 2) return null
            val pid = partes[0].uppercase().removePrefix("0X")
            val valor = partes[1].toIntOrNull() ?: return null
            when (pid) {
                "0C" -> TelemetryData(rpm = valor)
                "0D" -> TelemetryData(velocidade = valor)
                "05" -> TelemetryData(temperaturaMotor = valor - 40)
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private inline fun aplicarPid(
        pid: String,
        valor: Any?,
        setVel: (Int) -> Unit,
        setRpm: (Int) -> Unit,
        setTemp: (Int) -> Unit,
        setComb: (Int) -> Unit,
        setBat: (Double) -> Unit
    ) {
        val num = (valor as? Number)?.toDouble() ?: return
        when (pid) {
            "0D" -> setVel(num.toInt())
            "0C" -> setRpm((num / 4).toInt())
            "05" -> setTemp(num.toInt() - 40)
            "2F" -> setComb(num.toInt())
            "42" -> setBat(num / 1000.0)
        }
    }
}
