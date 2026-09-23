package com.exodash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private val handler = Handler(Looper.getMainLooper())
    private var jaSaiu = false

    private val runnableIrParaMain = Runnable { irParaMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.splashVideo)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.splash}")

        try {
            videoView.setVideoURI(uri)
            videoView.setOnPreparedListener { mp ->
                mp.isLooping = false
                videoView.start()
            }
            videoView.setOnCompletionListener { irParaMain() }
            videoView.setOnErrorListener { _, _, _ ->
                // Video falhou: vai direto pra launcher
                irParaMain()
                true
            }
            // Timeout de seguranca (15s)
            handler.postDelayed(runnableIrParaMain, 15000)
        } catch (e: Exception) {
            irParaMain()
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
