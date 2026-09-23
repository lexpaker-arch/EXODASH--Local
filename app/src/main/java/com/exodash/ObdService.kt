package com.exodash

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
 * Cliente WebSocket para o AndrOBd (plugin WebSocket Server).
 * Padrao: ws://127.0.0.1:35000
 *
 * Reconecta automaticamente se cair.
 * Notifica mudancas de estado e novos dados de telemetria.
 */
class ObdService(
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
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var parado = false

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
                    handler.post { onStatus(true) }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    val dados = parsear(text)
                    if (dados != null) {
                        handler.post { onTelemetria(dados) }
                    }
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(1000, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    marcarOffline()
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.d("ObdService", "Falha: ${t.message}")
                    marcarOffline()
                }
            })
        } catch (e: Exception) {
            marcarOffline()
        }
    }

    private fun marcarOffline() {
        if (conectado) {
            conectado = false
            handler.post { onStatus(false) }
        }
        if (!parado) {
            handler.removeCallbacks(reconectar)
            handler.postDelayed(reconectar, 5000)
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
            val cmd = JSONObject().apply {
                put("cmd", "resetTroubleCodes")
            }
            webSocket?.send(cmd.toString())
        } catch (e: Exception) {}
    }

    // ============================================================
    // PARSER — tenta varios formatos possiveis do AndrOBd
    // ============================================================
    private fun parsear(texto: String): TelemetryData? {
        return try {
            // Formato 1: array de objetos {"pid":"0C","value":1234}
            if (texto.trim().startsWith("[")) {
                val arr = JSONArray(texto)
                var velocidade: Int? = null
                var rpm: Int? = null
                var temp: Int? = null
                var comb: Int? = null
                var bat: Double? = null

                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pid = item.optString("pid", "").uppercase()
                    val valor = item.opt("value")

                    when (pid) {
                        "0D" -> velocidade = (valor as? Number)?.toInt()       // Speed
                        "0C" -> rpm = (valor as? Number)?.toInt()              // RPM
                        "05" -> temp = (valor as? Number)?.toInt()             // Coolant temp
                        "2F" -> comb = (valor as? Number)?.toInt()             // Fuel level
                        "42" -> bat = (valor as? Number)?.toDouble()?.div(1000.0)  // Battery mV
                        "0100", "0120" -> {} // PIDs suportados
                    }
                }

                if (velocidade == null && rpm == null && temp == null &&
                    comb == null && bat == null) {
                    return null
                }

                return TelemetryData(velocidade, rpm, temp, comb, bat)
            }

            // Formato 2: objeto {"0C":1234,"0D":87,...}
            if (texto.trim().startsWith("{")) {
                val obj = JSONObject(texto)
                val velocidade = obj.optInt("0D", -1).takeIf { it >= 0 }
                val rpm = obj.optInt("0C", -1).takeIf { it >= 0 }
                val temp = obj.optInt("05", -1).takeIf { it >= 0 }
                val comb = obj.optInt("2F", -1).takeIf { it >= 0 }
                val bat = obj.optDouble("42", -1.0).takeIf { it >= 0 }?.div(1000.0)

                if (velocidade == null && rpm == null && temp == null &&
                    comb == null && bat == null) {
                    return null
                }
                return TelemetryData(velocidade, rpm, temp, comb, bat)
            }

            null
        } catch (e: Exception) {
            Log.e("ObdService", "Erro ao parsear: $texto", e)
            null
        }
    }
}
