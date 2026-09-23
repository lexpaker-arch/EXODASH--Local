package com.exodash

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var boss: BossService
    private lateinit var network: NetworkMonitor
    private var obd: ObdService? = null

    // Header
    private lateinit var statusExo: TextView
    private lateinit var statusObd: TextView
    private lateinit var statusUser: TextView

    // Relogio
    private lateinit var clockTime: TextView
    private lateinit var clockSeconds: TextView
    private lateinit var clockDate: TextView
    private lateinit var clockDay: TextView

    // Telemetria
    private lateinit var telSpeed: TextView
    private lateinit var telRpm: TextView
    private lateinit var telTemp: TextView
    private lateinit var telFuel: TextView
    private lateinit var telBattery: TextView

    // Musica
    private lateinit var musicProgress: View
    private lateinit var musicFrame: LinearLayout
    private lateinit var musicTrack: TextView
    private lateinit var musicArtist: TextView

    // BOSS
    private lateinit var bossButton: Button
    private lateinit var errorTriangle: View
    private var speechRecognizer: SpeechRecognizer? = null
    private var ouvindo = false
    private var obdConectado = false
    private var online = false

    private val handler = Handler(Looper.getMainLooper())
    private val locale: Locale = Locale.getDefault()

    private val runnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val REQ_AUDIO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.instalar(this)
        Breadcrumbs.iniciar(this)
        Breadcrumbs.registrar("MainActivity: setContentView")
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Breadcrumbs.registrar("MainActivity: prefs criadas")

        // Modo imersivo: esconde status bar e navigation bar
        esconderBarrasSistema()

        // Header
        statusExo = findViewById(R.id.statusExo)
        statusObd = findViewById(R.id.statusObd)
        statusUser = findViewById(R.id.statusUser)

        // Relogio
        clockTime = findViewById(R.id.clockTime)
        clockSeconds = findViewById(R.id.clockSeconds)
        clockDate = findViewById(R.id.clockDate)
        clockDay = findViewById(R.id.clockDay)
        handler.post(runnable)

        // Telemetria
        telSpeed = findViewById(R.id.telSpeed)
        telRpm = findViewById(R.id.telRpm)
        telTemp = findViewById(R.id.telTemp)
        telFuel = findViewById(R.id.telFuel)
        telBattery = findViewById(R.id.telBattery)

        // Musica
        musicFrame = findViewById(R.id.musicFrame)
        musicTrack = findViewById(R.id.musicTrack)
        musicArtist = findViewById(R.id.musicArtist)
        musicProgress = findViewById(R.id.musicProgress)
        musicFrame.visibility = View.GONE

        // Listener de midia (guard para nao crashar se activity morrer)
        MediaListenerService.callback = { info ->
            if (!isFinishing && !isDestroyed) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) atualizarMusica(info)
                }
            }
        }

        // Triangulo de erro
        errorTriangle = findViewById(R.id.errorTriangle)
        errorTriangle.visibility = View.GONE

        // BOSS
        bossButton = findViewById(R.id.bossButton)
        boss = BossService(this, prefs) { acao -> executarAcao(acao) }
        bossButton.setOnClickListener { iniciarEscuta() }
        bossButton.setOnLongClickListener {
            abrirDialogoPergunta()
            true
        }

        // Rodape
        findViewById<View>(R.id.btnTelefone).setOnClickListener { abrirTelefone() }
        findViewById<View>(R.id.btnMidia).setOnClickListener { abrirMidia() }
        findViewById<View>(R.id.btnGps).setOnClickListener { abrirGps() }
        findViewById<View>(R.id.btnApps).setOnClickListener { abrirApps() }
        findViewById<View>(R.id.btnConfig).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Atualizar nome do usuario
        atualizarUsuario()

        // Iniciar monitor de rede (REAL)
        network = NetworkMonitor(this) { conectado ->
            online = conectado
            atualizarHeader()
        }
        network.iniciar()

        // Iniciar OBD (tenta conectar, mas nao trava se nao tiver)
        iniciarObd()

        // Inicializa STT
        inicializarSpeech()

        // Pede permissao de midia (se ainda nao tiver)
        handler.postDelayed({ pedirPermissaoMidia() }, 2000)
    }

    override fun onResume() {
        super.onResume()
        atualizarUsuario()
    }

    private fun atualizarUsuario() {
        statusUser.text = "USUÁRIO: ${prefs.nomeUsuario.uppercase()}"
    }

    // =============================================================
    // HEADER — atualiza em tempo real
    // =============================================================

    private fun atualizarHeader() {
        if (online) {
            statusExo.text = "EXODASH: ONLINE"
            statusExo.setTextColor(0xFF00FFCC.toInt())
        } else {
            statusExo.text = "EXODASH: OFFLINE"
            statusExo.setTextColor(0xFFFFAA00.toInt())
        }

        if (obdConectado) {
            statusObd.text = "OBD: ONLINE"
            statusObd.setTextColor(0xFF00FFCC.toInt())
        } else {
            statusObd.text = "OBD: OFFLINE"
            statusObd.setTextColor(0xFFFF3333.toInt())
        }
    }

    // =============================================================
    // OBD
    // =============================================================

    private fun iniciarObd() {
        obd = ObdService(
            host = prefs.obdHost,
            porta = prefs.obdPorta,
            onStatus = { conectado ->
                obdConectado = conectado
                atualizarHeader()
                if (!conectado) {
                    // OBD caiu: zera a telemetria
                    telSpeed.text = "--"
                    telRpm.text = "--"
                    telTemp.text = "--"
                    telFuel.text = "--"
                    telBattery.text = "--"
                }
            },
            onTelemetria = { dados ->
                atualizarTelemetria(dados)
            }
        )
        obd?.conectar()
    }

    private fun atualizarTelemetria(dados: TelemetryData) {
        telSpeed.text = dados.velocidade?.let { "$it km/h" } ?: "--"
        telRpm.text = dados.rpm?.toString() ?: "--"
        telTemp.text = dados.temperaturaMotor?.let { "$it °C" } ?: "--"
        telFuel.text = dados.combustivel?.let { "$it %" } ?: "--"
        telBattery.text = dados.bateria?.let { String.format("%.1f V", it) } ?: "--"

        // Triangulo de erro
        if (dados.dtcs.isNotEmpty()) {
            errorTriangle.visibility = View.VISIBLE
        } else {
            errorTriangle.visibility = View.GONE
        }
    }

    // =============================================================
    // SPEECH RECOGNIZER
    // =============================================================

    private fun inicializarSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Reconhecimento de voz indisponivel")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    handler.postDelayed({
                        if (ouvindo) pararEscuta()
                    }, 2000)
                }
                override fun onError(error: Int) {
                    pararEscuta()
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nao entendi, Senhor."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nao ouvi nada, Senhor."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sem conexao para reconhecer voz."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissao de microfone negada."
                        else -> "Erro no reconhecimento."
                    }
                    toast(msg)
                }
                override fun onResults(results: Bundle?) {
                    pararEscuta()
                    val textos = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val frase = textos?.firstOrNull()?.trim().orEmpty()
                    if (frase.isNotEmpty()) boss.processar(frase)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun iniciarEscuta() {
        if (ouvindo) { pararEscuta(); return }
        if (!temPermissaoAudio()) { pedirPermissaoAudio(); return }
        if (speechRecognizer == null) {
            inicializarSpeech()
            if (speechRecognizer == null) { abrirDialogoPergunta(); return }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        ouvindo = true
        bossButton.setBackgroundResource(R.drawable.boss_button_listening)
        bossButton.text = "OUVINDO"
        speechRecognizer?.startListening(intent)

        // Timeout: se em 10s nao recebeu resultado, destrava
        handler.postDelayed({
            if (ouvindo) {
                pararEscuta()
                toast("Nao consegui ouvir. Tente de novo.")
            }
        }, 10000)
    }

    private fun pararEscuta() {
        ouvindo = false
        bossButton.setBackgroundResource(R.drawable.boss_button_bg)
        bossButton.text = "BOSS"
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }

    private fun temPermissaoAudio(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun pedirPermissaoAudio() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarEscuta()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Permissao necessaria")
                    .setMessage("O BOSS precisa do microfone. Voce pode digitar segurando o botao.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // =============================================================
    // DIALOGO MANUAL
    // =============================================================


    // =============================================================
    // MUSICA
    // =============================================================

    private fun atualizarMusica(info: MediaListenerService.MusicaInfo?) {
        if (info == null) {
            musicFrame.visibility = View.GONE
            return
        }

        musicFrame.visibility = View.VISIBLE
        musicTrack.text = info.titulo.ifEmpty { "---" }
        musicArtist.text = info.artista.ifEmpty { "---" }

        // Barra de progresso
        if (info.duracao > 0) {
            val frac = (info.posicao.toFloat() / info.duracao.toFloat()).coerceIn(0f, 1f)
            val params = musicProgress.layoutParams
            // A barra tem largura 0dp; usamos weight do FrameLayout pai
            val parent = musicProgress.parent as? android.widget.FrameLayout
            val larguraPai = parent?.width ?: 0
            if (larguraPai > 0) {
                params.width = (larguraPai * frac).toInt()
                musicProgress.layoutParams = params
            }
        }
    }

    // =============================================================
    // PERMISSAO DE MIDIA
    // =============================================================

    private fun pedirPermissaoMidia() {
        if (isFinishing || isDestroyed) return
        if (!MediaListenerService.temPermissao(this)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Acesso a midia")
                .setMessage("Para mostrar a musica tocando, o EXODASH precisa de acesso as notificacoes. Autorize nas proximas telas.")
                .setPositiveButton("Abrir configuracoes") { _, _ ->
                    MediaListenerService.abrirConfiguracoes(this)
                }
                .setNegativeButton("Depois", null)
                .show()
        }
    }

    private fun abrirDialogoPergunta() {
        val input = EditText(this).apply {
            hint = "Ex: o que e P0301?"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Perguntar ao BOSS")
            .setView(input)
            .setPositiveButton("Enviar") { _, _ ->
                val pergunta = input.text.toString().trim()
                if (pergunta.isNotEmpty()) boss.processar(pergunta)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =============================================================
    // ACOES
    // =============================================================

    private fun executarAcao(acao: BossBrain.Acao) {
        when (acao) {
            BossBrain.Acao.ABRIR_GPS -> abrirGps()
            BossBrain.Acao.ABRIR_MUSICA -> abrirMidia()
            BossBrain.Acao.ABRIR_TELEFONE -> abrirTelefone()
            BossBrain.Acao.ABRIR_APPS -> abrirApps()
            BossBrain.Acao.LIMPAR_DTC -> {
                if (obdConectado) {
                    obd?.limparDTCs()
                    toast("Comando enviado ao OBD")
                } else {
                    toast("OBD offline")
                }
            }
            BossBrain.Acao.NENHUMA -> {}
        }
    }

    private fun abrirTelefone() {
        Breadcrumbs.registrar("abrirTelefone: chamado")
        val escolhido = prefs.appTelefone
        if (escolhido.isNotEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(escolhido)
            if (intent != null) { startActivity(intent); return }
        }
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory("android.intent.category.APP_DIAL")
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else toast("Nenhum app de telefone encontrado")
    }

    private fun abrirMidia() {
        Breadcrumbs.registrar("abrirMidia: chamado")
        val escolhido = prefs.appMusica
        if (escolhido.isNotEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(escolhido)
            if (intent != null) { startActivity(intent); return }
        }
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun abrirGps() {
        Breadcrumbs.registrar("abrirGps: chamado")
        val escolhido = prefs.appGps
        if (escolhido.isNotEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(escolhido)
            if (intent != null) { startActivity(intent); return }
        }
        // Fallback: procura qualquer app que responda a geo:
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else toast("Nenhum app de GPS encontrado")
    }

    private fun abrirApps() {
        Breadcrumbs.registrar("abrirApps: chamado")
        try {
            startActivity(Intent(this, AppDrawerActivity::class.java))
            Breadcrumbs.registrar("abrirApps: activity iniciada")
        } catch (e: Exception) {
            Breadcrumbs.registrarErro("abrirApps", e)
        }
    }

    // =============================================================
    // RELOGIO
    // =============================================================

    private fun updateClock() {
        if (isFinishing || isDestroyed) return
        val now = Date()
        clockTime.text = SimpleDateFormat("HH:mm", locale).format(now)
        clockSeconds.text = ": " + SimpleDateFormat("ss", locale).format(now)
        clockDate.text = SimpleDateFormat("dd 'de' MMMM", locale).format(now).uppercase(locale)
        clockDay.text = SimpleDateFormat("EEEE", locale).format(now).uppercase(locale)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }


    // =============================================================
    // MODO IMERSIVO
    // =============================================================

    @Suppress("DEPRECATION")
    private fun esconderBarrasSistema() {
        if (isFinishing || isDestroyed) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+
                window.insetsController?.let {
                    it.hide(android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // Android 10 e abaixo
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        } catch (e: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) esconderBarrasSistema()
    }

    override fun onBackPressed() {}

    override fun onDestroy() {
        Breadcrumbs.registrar("MainActivity: onDestroy")
        super.onDestroy()
        MediaListenerService.callback = null
        handler.removeCallbacksAndMessages(null)
        network.parar()
        obd?.parar()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (::boss.isInitialized) boss.destroy()
    }
}
