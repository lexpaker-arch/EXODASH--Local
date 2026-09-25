package com.exodash

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class BossService(
    private val context: Context,
    private val prefs: Prefs,
    private val onAcao: (BossBrain.Acao) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private val brain = BossBrain(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Listener de foco de audio (para abaixar o Waze/Spotify enquanto fala)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("pt", "BR")
            ttsPronto = true
        }
    }

    fun processar(frase: String) {
        if (frase.isBlank()) return

        val offline = brain.processarOffline(frase)
        if (offline != null) {
            responder(offline.texto)
            if (offline.acao != BossBrain.Acao.NENHUMA) {
                mainHandler.postDelayed({ onAcao(offline.acao) }, 1200)
            }
            return
        }

        val nome = prefs.nomeUsuario.ifEmpty { "Senhor" }
        val modo = prefs.modoIA
        if (modo == "offline_only") {
            responder("Nao entendi, $nome. Posso consultar codigos de erro, abrir GPS, musica, telefone ou apps.")
            return
        }

        val apiKey = prefs.groqApiKey
        if (apiKey.isEmpty()) {
            responder("Nao entendi, $nome. Posso consultar codigos de erro, abrir GPS, musica, telefone ou apps.")
            return
        }

        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg p: Void): String? {
                val nome = prefs.nomeUsuario.ifEmpty { "Senhor" }
                return GroqClient.perguntar(apiKey, frase, nome)
            }
            override fun onPostExecute(resposta: String?) {
                val nome = prefs.nomeUsuario.ifEmpty { "Senhor" }
                if (resposta.isNullOrEmpty()) {
                    responder("Sem conexao, $nome. Posso consultar codigos de erro, abrir GPS, musica, telefone ou apps.")
                } else {
                    responder(resposta)
                }
            }
        }.execute()
    }

    fun responder(texto: String) {
        if (prefs.vozAtiva && ttsPronto) {
            // Pede foco de audio (abaixa Waze/Spotify enquanto fala)
            val foco = try {
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            } catch (e: Exception) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }

            val liberarFoco = Runnable {
                try { audioManager.abandonAudioFocus(focusListener) } catch (_: Exception) {}
            }

            if (prefs.bipeAtivo) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    mainHandler.postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                        falar(texto, liberarFoco)
                    }, 200)
                } catch (e: Exception) {
                    falar(texto, liberarFoco)
                }
            } else {
                falar(texto, liberarFoco)
            }
        } else {
            Toast.makeText(context, texto, Toast.LENGTH_LONG).show()
        }
    }

    private fun falar(texto: String, liberarFoco: Runnable) {
        try { LogEventos.registrar(context, "BOSS: ${texto.take(80)}") } catch (_: Exception) {}
        val utteranceId = "boss_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post(liberarFoco)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post(liberarFoco)
            }
        })
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun pararFala() {
        try {
            tts?.stop()
            mainHandler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {}
    }

    fun destroy() {
        try { mainHandler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        try { audioManager.abandonAudioFocus(focusListener) } catch (_: Exception) {}
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ttsPronto = false
    }
}
