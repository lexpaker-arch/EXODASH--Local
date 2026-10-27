package com.exodash

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.InputStream
import java.io.OutputStream

/**
 * OBD via USB, protocolo ELM327 direto (sem biblioteca externa).
 * ISO 9141-2 forcado para compatibilidade com Focus 2001.
 */
class ObdUsbService(
    private val context: Context,
    private val onStatus: (Boolean) -> Unit,
    private val onTelemetria: (TelemetryData) -> Unit
) {
    private val TAG = "ObdUsb"
    private val ACTION = "com.exodash.USB_PERMISSION"

    private var usbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var leituraThread: Thread? = null
    @Volatile private var conectado = false
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (ok && device != null) conectarDispositivo(device)
            else emitirStatus(false)
        }
    }

    fun conectar() {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val filtro = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filtro, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filtro)
        }

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager!!)
        if (drivers.isEmpty()) {
            Log.d(TAG, "Nenhum USB serial conectado")
            emitirStatus(false)
            return
        }

        val device = drivers[0].device
        if (usbManager!!.hasPermission(device)) {
            conectarDispositivo(device)
        } else {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager!!.requestPermission(device, pi)
        }
    }

    fun parar() {
        try {
            conectado = false
            leituraThread?.interrupt()
            leituraThread = null
            serialPort?.close()
            serialPort = null
            input = null
            output = null
            emitirStatus(false)
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        } catch (e: Exception) {}
    }

    private fun conectarDispositivo(device: UsbDevice) {
        try {
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device) ?: return
            val connection = usbManager!!.openDevice(device) ?: return
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(38400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            serialPort = port
            input = UsbSerialInputStream(port)
            output = UsbSerialOutputStream(port)

            conectado = true
            emitirStatus(true)
            LogEventos.registrar(context, "OBD USB conectado")

            Thread {
                try {
                    configurarElm327()
                    loopLeitura()
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no loop", e)
                }
            }.also { leituraThread = it }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro conectar", e)
            emitirStatus(false)
        }
    }

    private fun enviarCmd(cmd: String): String {
        return try {
            LogEventos.registrar(context, "OBD >> $cmd")
            output?.write("$cmd\r".toByteArray())
            output?.flush()

            val sb = StringBuilder()
            val buf = ByteArray(256)
            var timeout = 0
            while (timeout < 50) {
                val disp = input?.available() ?: 0
                if (disp > 0) {
                    val n = input?.read(buf) ?: 0
                    if (n > 0) {
                        val s = String(buf, 0, n)
                        sb.append(s)
                        if (s.contains(">")) break
                    }
                } else {
                    Thread.sleep(100)
                    timeout++
                }
            }
            val resposta = sb.toString().trim()
            LogEventos.registrar(context, "OBD << ${resposta.take(100)}")
            resposta
        } catch (e: Exception) {
            Log.e(TAG, "Erro cmd $cmd", e)
            LogEventos.registrar(context, "OBD ERRO $cmd: ${e.message}")
            ""
        }
    }

    private fun configurarElm327() {
        LogEventos.registrar(context, "=== Iniciando ELM327 ===")

        LogEventos.registrar(context, "Passo 1: Reset")
        val reset = enviarCmd("AT Z")
        Thread.sleep(2000)

        LogEventos.registrar(context, "Passo 2: Echo off")
        enviarCmd("AT E0")

        LogEventos.registrar(context, "Passo 3: Linefeed off")
        enviarCmd("AT L0")

        LogEventos.registrar(context, "Passo 4: Spaces off")
        enviarCmd("AT S0")

        LogEventos.registrar(context, "Passo 5: Headers off")
        enviarCmd("AT H0")

        LogEventos.registrar(context, "Passo 6: Forcando ISO 9141-2")
        enviarCmd("AT SP 3")
        Thread.sleep(500)

        // Verificar versao do adaptador
        LogEventos.registrar(context, "Verificando versao")
        val versao = enviarCmd("AT I")
        LogEventos.registrar(context, "Versao ELM327: $versao")

        // Verificar protocolo atual
        val protocolo = enviarCmd("AT DP")
        LogEventos.registrar(context, "Protocolo: $protocolo")

        // Teste de comunicacao com a ECU
        LogEventos.registrar(context, "Testando comunicacao com ECU (PID 00)")
        val pid00 = enviarCmd("01 00")
        LogEventos.registrar(context, "Resposta 01 00: ${pid00.take(80)}")

        LogEventos.registrar(context, "=== ELM327 pronto ===")
    }

    private fun loopLeitura() {
        var ciclos = 0
        var dtcsAtuais: List<String> = emptyList()
        while (conectado) {
            try {
                val velResp = enviarCmd("01 0D")
                val rpmResp = enviarCmd("01 0C")
                val tempResp = enviarCmd("01 05")
                val combResp = enviarCmd("01 2F")
                val batResp = enviarCmd("AT RV")

                val vel = parseSpeed(velResp)
                val rpm = parseRpm(rpmResp)
                val temp = parseTemp(tempResp)
                val comb = parseFuel(combResp)
                val bat = parseBattery(batResp)

                // DTC a cada 10 ciclos (~30s) - ISO 9141-2 e lento, nao da pra ler toda hora
                if (ciclos % 10 == 0) {
                    val dtcResp = enviarCmd("03")
                    val novos = parseDTCs(dtcResp)
                    if (novos.isNotEmpty() || dtcsAtuais.isNotEmpty()) {
                        LogEventos.registrar(context,
                            "DTCs lidos: ${novos.joinToString(",").ifEmpty { "nenhum" }}")
                    }
                    dtcsAtuais = novos
                }

                // Log apenas a cada 5 ciclos (evitar poluir)
                if (ciclos % 5 == 0) {
                    LogEventos.registrar(context,
                        "Dados: vel=$vel rpm=$rpm temp=$temp comb=$comb dtcs=${dtcsAtuais.size}")
                }
                ciclos++

                val dados = TelemetryData(vel, rpm, temp, comb, bat, dtcsAtuais)
                handler.post { onTelemetria(dados) }

                Thread.sleep(1000)
            } catch (e: Exception) {
                Log.e(TAG, "Erro leitura", e)
                LogEventos.registrar(context, "Erro leitura: ${e.message}")
                Thread.sleep(2000)
            }
        }
    }

    /**
     * Le DTCs via Mode 03.
     * Resposta tipica ISO 9141-2: "43 01 33 00 00 00 00"
     * Formato: 43 [byte1 byte2] [byte3 byte4] ...
     * Cada par de bytes = 1 DTC. "00 00" = padding (ignorar).
     * Conversao: byte1/16 -> letra (0=P,1=C,2=B,3=U), byte1%16 e byte2 -> 4 digitos.
     * Ex: "01 33" -> P0133
     */
    private fun parseDTCs(resp: String): List<String> {
        return try {
            // Limpa: remove "43" inicial, espacos e 

            val limpo = resp.uppercase()
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()

            // Extrai todos os pares hex apos o "43"
            val match = Regex("43\\s*(.+)$").find(limpo) ?: return emptyList()
            val payload = match.groupValues[1]
            val bytes = Regex("[0-9A-F]{2}").findAll(payload).map { it.value }.toList()

            if (bytes.size < 2) return emptyList()

            val dtcs = mutableListOf<String>()
            var i = 0
            while (i + 1 < bytes.size) {
                val b1 = bytes[i].toIntOrNull(16) ?: break
                val b2 = bytes[i + 1].toIntOrNull(16) ?: break

                // "00 00" = padding, para de ler
                if (b1 == 0 && b2 == 0) break

                val letra = when (b1 shr 4) {
                    0 -> "P"
                    1 -> "C"
                    2 -> "B"
                    3 -> "U"
                    else -> "?"
                }
                val d1 = b1 and 0x0F
                val codigo = String.format("%s%01d%s", letra, d1, bytes[i + 1].padStart(2, '0'))
                dtcs.add(codigo)
                i += 2
            }
            dtcs
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============= PARSERS =============
    private fun parseSpeed(resp: String): Int? {
        return try {
            val match = Regex("41 ?0D ?([0-9A-F]{2})").find(resp)
            match?.groupValues?.get(1)?.toIntOrNull(16)
        } catch (e: Exception) { null }
    }

    private fun parseRpm(resp: String): Int? {
        return try {
            val match = Regex("41 ?0C ?([0-9A-F]{2}) ?([0-9A-F]{2})").find(resp)
            if (match != null) {
                val a = match.groupValues[1].toIntOrNull(16) ?: return null
                val b = match.groupValues[2].toIntOrNull(16) ?: return null
                ((a * 256) + b) / 4
            } else null
        } catch (e: Exception) { null }
    }

    private fun parseTemp(resp: String): Int? {
        return try {
            val match = Regex("41 ?05 ?([0-9A-F]{2})").find(resp)
            match?.groupValues?.get(1)?.toIntOrNull(16)?.minus(40)
        } catch (e: Exception) { null }
    }

    private fun parseFuel(resp: String): Int? {
        return try {
            val match = Regex("41 ?2F ?([0-9A-F]{2})").find(resp)
            match?.groupValues?.get(1)?.toIntOrNull(16)?.let { (it * 100) / 255 }
        } catch (e: Exception) { null }
    }

    /**
     * Le tensao via "AT RV" (Read Voltage).
     * Retorna algo como "12.6V" ou "12.6" ou "12V".
     * Nao e o PID da ECU - e o proprio ELM327 medindo a linha.
     * Serve como proxy da bateria (fica proximo do real).
     */
    private fun parseBattery(resp: String): Double? {
        return try {
            val match = Regex("(\\d+\\.?\\d*)V?", RegexOption.IGNORE_CASE).find(resp.trim())
            match?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (e: Exception) { null }
    }

    fun limparDTCs() {
        try {
            val resp = enviarCmd("04")
            Log.d(TAG, "Limpar DTC resp: $resp")
            LogEventos.registrar(context, "OBD: comando limpar DTC enviado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro limpar DTC", e)
        }
    }

    private fun emitirStatus(online: Boolean) {
        handler.post { onStatus(online) }
    }
}
