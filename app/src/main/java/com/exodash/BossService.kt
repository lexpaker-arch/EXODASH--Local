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

        val modo = prefs.modoIA
        if (modo == "offline_only") {
            responder("Nao tenho essa informacao offline, Senhor.")
            return
        }

        val apiKey = prefs.groqApiKey
        if (apiKey.isEmpty()) {
            responder("Nao tenho essa informacao, Senhor.")
            return
        }

        object : AsyncTask<Void, Void, String?>() {
            override fun doInBackground(vararg p: Void): String? {
                val nome = prefs.nomeUsuario.ifEmpty { "Senhor" }
                return GroqClient.perguntar(apiKey, frase, nome)
            }
            override fun onPostExecute(resposta: String?) {
                if (resposta.isNullOrEmpty()) {
                    responder("Nao consegui consultar agora, Senhor.")
                } else {
                    responder(resposta)
                }
            }
        }.execute()
    }

    fun responder(texto: String) {
        if (prefs.vozAtiva && ttsPronto) {
            if (prefs.bipeAtivo) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    mainHandler.postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "boss_${System.currentTimeMillis()}")
                    }, 200)
                } catch (e: Exception) {
                    tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "boss_${System.currentTimeMillis()}")
                }
            } else {
                tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "boss_${System.currentTimeMillis()}")
            }
        } else {
            Toast.makeText(context, texto, Toast.LENGTH_LONG).show()
        }
    }

    fun destroy() {
        try { mainHandler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        ttsPronto = false
    }
}
