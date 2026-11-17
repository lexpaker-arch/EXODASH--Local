package com.exodash

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Servico que escuta as sessoes de midia ativas (Spotify, YouTube Music, etc).
 * Requer permissao "Acesso a notificacoes" (o usuario autoriza uma vez).
 *
 * Modulo de midia puro - sem dependencia do Vigilante/BossService.
 */
class MediaListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaListener"
        private const val TICK_MS = 500L

        /** Callback legado (compat com UI atual). */
        var callback: ((MusicaInfo?) -> Unit)? = null

        private val _estado = MutableStateFlow<MusicaInfo?>(null)
        /** Estado observavel (novo). */
        val estado: StateFlow<MusicaInfo?> = _estado.asStateFlow()

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

        private fun emitir(info: MusicaInfo?) {
            _estado.value = info
            callback?.invoke(info)
        }
    }

    data class MusicaInfo(
        val titulo: String,
        val artista: String,
        val posicao: Long,
        val duracao: Long,
        val tocando: Boolean,
        val capa: Bitmap? = null,
        val pacote: String? = null,
        val album: String? = null
    )

    private var controller: MediaController? = null
    private var msm: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        try {
            msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, MediaListenerService::class.java)
            sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { atualizarSessao() }
            msm?.addOnActiveSessionsChangedListener(sessionsListener!!, cn)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar listener de sessoes", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Conectado")
        atualizarSessao()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try { controller?.unregisterCallback(callbackSessao) } catch (_: Exception) {}
        controller = null
        pararTicker()
        emitir(null)
    }

    override fun onDestroy() {
        try { sessionsListener?.let { msm?.removeOnActiveSessionsChangedListener(it) } } catch (_: Exception) {}
        try { controller?.unregisterCallback(callbackSessao) } catch (_: Exception) {}
        pararTicker()
        scope.cancel()
        super.onDestroy()
    }

    private fun atualizarSessao() {
        try {
            val m = msm ?: return
            val cn = ComponentName(this, MediaListenerService::class.java)
            val sessoes = m.getActiveSessions(cn)

            if (sessoes.isEmpty()) {
                try { controller?.unregisterCallback(callbackSessao) } catch (_: Exception) {}
                controller = null
                pararTicker()
                emitir(null)
                return
            }

            val sessao = sessoes.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessoes.first()

            if (controller?.sessionToken != sessao.sessionToken) {
                try { controller?.unregisterCallback(callbackSessao) } catch (_: Exception) {}
                controller = sessao
                sessao.registerCallback(callbackSessao)
            }
            emitirInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar sessao", e)
        }
    }

    private val callbackSessao = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { emitirInfo() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { emitirInfo() }
        override fun onSessionDestroyed() {
            controller = null
            pararTicker()
            emitir(null)
        }
    }

    private fun emitirInfo() {
        try {
            val c = controller
            if (c == null) { emitir(null); return }

            val meta = c.metadata
            val titulo = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artista = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val duracao = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val estado = c.playbackState
            val posicao = estado?.position ?: 0L
            val tocando = estado?.state == PlaybackState.STATE_PLAYING
            val pacote = c.packageName
            val capa = extrairCapa(meta)

            if (titulo.isEmpty() && artista.isEmpty()) {
                emitir(null)
                return
            }

            emitir(MusicaInfo(titulo, artista, posicao, duracao, tocando, capa, pacote, album))

            if (tocando) iniciarTicker() else pararTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao emitir info", e)
        }
    }

    private fun extrairCapa(meta: MediaMetadata?): Bitmap? {
        if (meta == null) return null
        try {
            meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
            meta.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
            val uriStr = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (!uriStr.isNullOrEmpty()) {
                contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                    return BitmapFactory.decodeStream(input)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao extrair capa", e)
        }
        return null
    }

    private fun iniciarTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                val c = controller ?: break
                val estado = c.playbackState ?: break
                if (estado.state != PlaybackState.STATE_PLAYING) break
                val meta = c.metadata
                val titulo = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artista = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                if (titulo.isEmpty() && artista.isEmpty()) break
                val novaPos = estado.position
                val atual = _estado.value
                if (atual != null) {
                    emitir(atual.copy(posicao = novaPos, tocando = true))
                }
            }
        }
    }

    private fun pararTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ---- Controles publicos (delegam para a sessao ativa) ----

    fun play() { controller?.transportControls?.play() }
    fun pause() { controller?.transportControls?.pause() }
    fun toggle() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
}