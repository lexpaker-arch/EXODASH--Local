package com.exodash

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Servico que escuta as sessoes de midia ativas (Spotify, YouTube Music, etc).
 * Requer permissao "Acesso a notificacoes" (o usuario autoriza uma vez).
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        var callback: ((MusicaInfo?) -> Unit)? = null

        fun temPermissao(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName)
        }

        fun abrirConfiguracoes(context: Context) {
            try {
                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {}
            }
        }
    }

    data class MusicaInfo(
        val titulo: String,
        val artista: String,
        val posicao: Long,
        val duracao: Long,
        val tocando: Boolean
    )

    private var controller: MediaController? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaListener", "Conectado")
        atualizarSessao()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        callback?.invoke(null)
    }

    private fun atualizarSessao() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, MediaListenerService::class.java)
            val sessoes = msm.getActiveSessions(cn)

            if (sessoes.isEmpty()) {
                controller = null
                callback?.invoke(null)
                return
            }

            // Pega a sessao com playback ativo, ou a primeira
            val sessao = sessoes.firstOrNull { it.playbackState?.state ==
                    android.media.session.PlaybackState.STATE_PLAYING } ?: sessoes.first()

            if (controller?.sessionToken != sessao.sessionToken) {
                controller = sessao
                sessao.registerCallback(callbackSessao)
            }
            emitirInfo()
        } catch (e: Exception) {
            Log.e("MediaListener", "Erro", e)
        }
    }

    private val callbackSessao = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { emitirInfo() }
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { emitirInfo() }
        override fun onSessionDestroyed() {
            controller = null
            callback?.invoke(null)
        }
    }

    private fun emitirInfo() {
        val c = controller
        if (c == null) { callback?.invoke(null); return }

        val meta = c.metadata
        val titulo = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artista = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val duracao = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val estado = c.playbackState
        val posicao = estado?.position ?: 0L
        val tocando = estado?.state == android.media.session.PlaybackState.STATE_PLAYING

        if (titulo.isEmpty() && artista.isEmpty()) {
            callback?.invoke(null)
            return
        }

        callback?.invoke(MusicaInfo(titulo, artista, posicao, duracao, tocando))
    }
}
