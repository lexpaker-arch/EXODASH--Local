package com.exodash

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.content.Intent
import android.util.Log

/**
 * Servico que o Android reconhece como assistente de voz.
 * Isso faz o EXODASH aparecer em:
 *   Configuracoes > Apps > Apps padrao > Assistente
 */
class MainVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.d("VoiceService", "Pronto para ser assistente")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        abrirLauncher()
    }

    private fun abrirLauncher() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("VoiceService", "Erro ao abrir", e)
        }
    }
}

/**
 * SessionService — obrigatorio para o VoiceInteractionService funcionar.
 * Ele cria a "sessao" quando o usuario aciona o assistente.
 */
class MainVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return MainVoiceInteractionSession(this)
    }
}

class MainVoiceInteractionSession(service: VoiceInteractionSessionService) :
    VoiceInteractionSession(service) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
        hide()
    }
}
