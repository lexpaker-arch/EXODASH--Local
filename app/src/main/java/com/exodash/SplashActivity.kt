package com.exodash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var fallback: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var jaSaiu = false

    private val runnableIrParaMain = Runnable { irParaMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.splashVideo)
        fallback = findViewById(R.id.splashFallback)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.splash}")

        try {
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
                // Esconde o fallback e toca o video
                fallback.visibility = View.GONE
                videoView.start()
            }
            videoView.setOnCompletionListener { irParaMain() }
            videoView.setOnErrorListener { _, _, _ ->
                // Video falhou: mostra fallback e aguarda 3s
                fallback.visibility = View.VISIBLE
                handler.postDelayed(runnableIrParaMain, 3000)
                true
            }
            // Timeout de seguranca
            handler.postDelayed(runnableIrParaMain, 15000)
        } catch (e: Exception) {
            fallback.visibility = View.VISIBLE
            handler.postDelayed(runnableIrParaMain, 3000)
        }
    }

    private fun irParaMain() {
        if (jaSaiu) return
        jaSaiu = true
        try {
            startActivity(Intent(this, MainActivity::class.java))
        } catch (e: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { videoView.stopPlayback() } catch (e: Exception) {}
    }
}
