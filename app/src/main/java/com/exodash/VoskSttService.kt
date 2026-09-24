package com.exodash

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * STT offline via Vosk. Le o modelo de assets/model-pt.
 */
class VoskSttService(
    private val context: Context,
    private val onResultado: (String) -> Unit,
    private val onParcial: (String) -> Unit = {},
    private val onErro: (String) -> Unit = {}
) : RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var pronto = false

    fun inicializar() {
        try {
            val pasta = File(context.filesDir, "model-pt")
            if (!pasta.exists() || pasta.listFiles()?.isEmpty() != false) {
                copiarModeloDosAssets(pasta)
            }
            model = Model(pasta.absolutePath)
            pronto = true
            Log.d("VoskStt", "Modelo carregado")
        } catch (e: Exception) {
            Log.e("VoskStt", "Erro ao carregar modelo", e)
            onErro("Erro ao carregar modelo Vosk: ${e.message}")
        }
    }

    private fun copiarModeloDosAssets(destino: File) {
        destino.mkdirs()
        val assets = context.assets.list("model-pt") ?: return
        for (nome in assets) {
            copiarRecursivo("model-pt/$nome", File(destino, nome))
        }
    }

    private fun copiarRecursivo(caminhoAsset: String, destino: File) {
        val filhos = context.assets.list(caminhoAsset) ?: emptyArray()
        if (filhos.isEmpty()) {
            // É arquivo
            destino.parentFile?.mkdirs()
            context.assets.open(caminhoAsset).use { input ->
                destino.outputStream().use { input.copyTo(it) }
            }
        } else {
            destino.mkdirs()
            for (f in filhos) copiarRecursivo("$caminhoAsset/$f", File(destino, f))
        }
    }

    fun iniciarEscuta() {
        if (!pronto || model == null) {
            onErro("Modelo Vosk nao carregado")
            return
        }
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            onErro("Erro ao iniciar escuta: ${e.message}")
        }
    }

    fun pararEscuta() {
        try {
            speechService?.stop()
        } catch (e: Exception) {}
    }

    fun destroy() {
        try { speechService?.shutdown() } catch (e: Exception) {}
        try { model?.close() } catch (e: Exception) {}
        speechService = null
        model = null
        pronto = false
    }

    // RecognitionListener
    override fun onPartialResult(hypothesis: String?) {
        val texto = extrair(hypothesis)
        if (texto.isNotEmpty()) onParcial(texto)
    }

    override fun onResult(hypothesis: String?) {
        val texto = extrair(hypothesis)
        if (texto.isNotEmpty()) onResultado(texto)
    }

    override fun onFinalResult(hypothesis: String?) {
        val texto = extrair(hypothesis)
        if (texto.isNotEmpty()) onResultado(texto)
    }

    override fun onError(exception: Exception?) {
        onErro("Erro Vosk: ${exception?.message}")
    }

    override fun onTimeout() {
        onErro("Timeout na escuta")
    }

    private fun extrair(json: String?): String {
        if (json.isNullOrEmpty()) return ""
        return try {
            val obj = org.json.JSONObject(json)
            obj.optString("text", "").trim()
        } catch (e: Exception) { "" }
    }
}
