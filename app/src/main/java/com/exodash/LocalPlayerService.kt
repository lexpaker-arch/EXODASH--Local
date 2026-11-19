package com.exodash

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
import java.io.File

/**
 * Player local do EXODASH.
 *
 * Toca arquivos de audio da pasta EXODASH/Music (ou fallback: toda a biblioteca
 * de audio do dispositivo via MediaStore).
 *
 * Expoe o mesmo contrato do MediaListenerService (callback + StateFlow<MusicaInfo>)
 * para que a UI do MainActivity funcione sem saber de onde vem o audio.
 */
class LocalPlayerService : MediaSessionService() {

    companion object {
        private const val TAG = "LocalPlayer"
        private const val TICK_MS = 500L

        /** Callback compativel com MediaListenerService.callback. */
        var callback: ((MediaListenerService.MusicaInfo?) -> Unit)? = null

        private val _estado = MutableStateFlow<MediaListenerService.MusicaInfo?>(null)
        val estado: StateFlow<MediaListenerService.MusicaInfo?> = _estado.asStateFlow()

        /** Instancia viva (para comandos diretos da UI). */
        @Volatile
        var instancia: LocalPlayerService? = null
            private set

        private fun emitir(info: MediaListenerService.MusicaInfo?) {
            _estado.value = info
            callback?.invoke(info)
        }
    }

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var fila: List<MediaItem> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instancia = this

        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    emitirAtual()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    emitirAtual()
                    if (isPlaying) iniciarTicker() else pararTicker()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    emitirAtual()
                }
            })
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        // Carrega a fila em background (nao trava o onCreate)
        scope.launch(Dispatchers.IO) {
            val itens = carregarFila()
            launch(Dispatchers.Main) {
                fila = itens
                player.setMediaItems(itens)
                player.prepare()
                Log.d(TAG, "Fila carregada: ${itens.size} itens")
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        instancia = null
        pararTicker()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        emitir(null)
        super.onDestroy()
    }

    // =============================================================
    // CARREGAMENTO DA FILA
    // =============================================================

    private fun carregarFila(): List<MediaItem> {
        val itens = mutableListOf<MediaItem>()

        // 1) Tenta pasta EXODASH/Music primeiro
        val pastaExo = File(android.os.Environment.getExternalStorageDirectory(), "EXODASH/Music")
        if (pastaExo.exists() && pastaExo.isDirectory) {
            pastaExo.listFiles { f -> f.isFile && ehAudio(f.name) }?.sortedBy { it.name }?.forEach { f ->
                itens.add(montarItem(f))
            }
        }

        // 2) Fallback: MediaStore (toda a biblioteca)
        if (itens.isEmpty()) {
            try {
                val proj = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.ALBUM,
                    android.provider.MediaStore.Audio.Media.DURATION
                )
                val sel = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
                contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj, sel, null,
                    "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val albCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                    val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val meta = MediaMetadata.Builder()
                            .setTitle(c.getString(titCol) ?: "---")
                            .setArtist(c.getString(artCol) ?: "---")
                            .setAlbumTitle(c.getString(albCol))
                            .build()
                        itens.add(MediaItem.Builder().setUri(uri).setMediaMetadata(meta).build())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao ler MediaStore", e)
            }
        }

        return itens
    }

    private fun montarItem(f: File): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(f.nameWithoutExtension)
            .setArtist("EXODASH")
            .build()
        return MediaItem.Builder()
            .setUri(Uri.fromFile(f))
            .setMediaMetadata(meta)
            .build()
    }

    private fun ehAudio(nome: String): Boolean {
        val ext = nome.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
    }

    // =============================================================
    // EMISSAO DE ESTADO
    // =============================================================

    private fun emitirAtual() {
        try {
            val item = player.currentMediaItem ?: run { emitir(null); return }
            val meta = item.mediaMetadata
            val titulo = meta.title?.toString() ?: ""
            val artista = meta.artist?.toString() ?: ""
            val album = meta.albumTitle?.toString()
            val duracao = if (player.duration > 0) player.duration else 0L
            val posicao = player.currentPosition.coerceAtLeast(0L)
            val tocando = player.isPlaying

            if (titulo.isEmpty() && artista.isEmpty()) { emitir(null); return }

            emitir(
                MediaListenerService.MusicaInfo(
                    titulo = titulo,
                    artista = artista,
                    posicao = posicao,
                    duracao = duracao,
                    tocando = tocando,
                    capa = null,
                    pacote = packageName,
                    album = album
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao emitir estado", e)
        }
    }

    private fun iniciarTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                if (!player.isPlaying) break
                emitirAtual()
            }
        }
    }

    private fun pararTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // =============================================================
    // CONTROLES PUBLICOS (chamados pela UI / MediaController)
    // =============================================================

    fun play() { player.play() }
    fun pause() { player.pause() }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun next() { player.seekToNextMediaItem() }
    fun previous() { player.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { player.seekTo(ms) }
    fun temFila(): Boolean = fila.isNotEmpty()
}