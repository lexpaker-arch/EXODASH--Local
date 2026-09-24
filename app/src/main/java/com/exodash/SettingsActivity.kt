package com.exodash

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private var contadorToquesVersao = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = Prefs(this)

        findViewById<android.view.View>(R.id.btnVoltar).setOnClickListener { finish() }

        // Perfis
        findViewById<Button>(R.id.btnNomeUsuario).setOnClickListener { editarNome() }

        // Atalhos
        findViewById<Button>(R.id.btnEscolherTelefone).setOnClickListener {
            escolherApp("Telefone", Intent(Intent.ACTION_DIAL)) { prefs.appTelefone = it }
        }
        findViewById<Button>(R.id.btnEscolherMidia).setOnClickListener {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory("android.intent.category.APP_MUSIC")
            escolherApp("Mídia", i) { prefs.appMusica = it }
        }
        findViewById<Button>(R.id.btnEscolherGps).setOnClickListener {
            // Waze nao responde a CATEGORY_APP_MAPS.
            // Usa geo: URI — o Waze, Google Maps, etc respondem a isso.
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test"))
            escolherApp("GPS", i) { prefs.appGps = it }
        }

        // Voz
        val swVoz = findViewById<Switch>(R.id.swVozAtiva)
        val swBipe = findViewById<Switch>(R.id.swBipeAtivo)
        swVoz.isChecked = prefs.vozAtiva
        swBipe.isChecked = prefs.bipeAtivo
        swVoz.setOnCheckedChangeListener { _, v -> prefs.vozAtiva = v }
        swBipe.setOnCheckedChangeListener { _, v -> prefs.bipeAtivo = v }

        // OBD
        findViewById<Button>(R.id.btnEditarObd).setOnClickListener { editarObd() }

        // Veículo
        findViewById<Button>(R.id.btnEditarVeiculo).setOnClickListener { editarVeiculo() }

        // IA
        findViewById<Button>(R.id.btnEditarGroq).setOnClickListener { editarGroq() }
        findViewById<TextView>(R.id.txtModoIA).setOnClickListener {
            alternarModoIA()
        }

        // Atualização
        val swAutoUpdate = findViewById<Switch>(R.id.swAutoUpdate)
        swAutoUpdate.isChecked = prefs.autoUpdateAtivo
        swAutoUpdate.setOnCheckedChangeListener { _, v -> prefs.autoUpdateAtivo = v }

        findViewById<Button>(R.id.btnVerificarUpdate).setOnClickListener { verificarUpdate() }

        // Sobre
        findViewById<android.view.View>(R.id.btnLimparCache).setOnClickListener { limparCache() }
        findViewById<android.view.View>(R.id.btnSobre).setOnClickListener { mostrarSobre() }

        // Click oculto: 7 toques na versao desbloqueia modo avancado
        findViewById<TextView>(R.id.txtVersaoAtual).setOnClickListener {
            contadorToquesVersao++
            if (contadorToquesVersao >= 7) {
                prefs.modoAvancado = true
                contadorToquesVersao = 0
                Toast.makeText(this, "Modo avancado ativado", Toast.LENGTH_SHORT).show()
                recreate()
            } else if (contadorToquesVersao >= 4) {
                Toast.makeText(this,
                    "Faltam ${7 - contadorToquesVersao} toques",
                    Toast.LENGTH_SHORT).show()
            }
        }

        // Botao de escolher nome do assistente (so visivel no modo avancado)
        findViewById<android.view.View>(R.id.btnNomeAssistente).setOnClickListener {
            escolherNomeAssistente()
        }

        atualizarLabels()
        atualizarSecaoAvancada()
    }

    private fun atualizarSecaoAvancada() {
        try {
            val secao = findViewById<TextView>(R.id.secaoAvancadaNome)
            val botao = findViewById<android.view.View>(R.id.btnNomeAssistente)
            val txt = findViewById<TextView>(R.id.txtNomeAssistenteAtual)

            if (prefs.modoAvancado) {
                secao.visibility = android.view.View.VISIBLE
                botao.visibility = android.view.View.VISIBLE
                txt.text = prefs.nomeAssistente
            } else {
                secao.visibility = android.view.View.GONE
                botao.visibility = android.view.View.GONE
            }
        } catch (e: Exception) {}
    }

    private fun escolherNomeAssistente() {
        val opcoes = arrayOf("EXO (padrao)", "BOSS (particular)")
        val atual = if (prefs.nomeAssistente == "BOSS") 1 else 0
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nome do assistente")
            .setSingleChoiceItems(opcoes, atual) { dialog, which ->
                prefs.nomeAssistente = if (which == 0) "EXO" else "BOSS"
                atualizarSecaoAvancada()
                Toast.makeText(this,
                    "Nome alterado para ${prefs.nomeAssistente}",
                    Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =============================================
    // EDITORES
    // =============================================

    private fun editarNome() {
        val input = EditText(this).apply {
            setText(prefs.nomeUsuario)
            inputType = InputType.TYPE_CLASS_TEXT
            setHint("Ex: Senhor, Gleici, João")
        }
        AlertDialog.Builder(this)
            .setTitle("Nome do usuário")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val nome = input.text.toString().trim()
                if (nome.isNotEmpty()) {
                    prefs.nomeUsuario = nome
                    atualizarLabels()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editarObd() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val inputHost = EditText(this).apply {
            setText(prefs.obdHost)
            hint = "Host (ex: 127.0.0.1)"
        }
        val inputPorta = EditText(this).apply {
            setText(prefs.obdPorta.toString())
            hint = "Porta (ex: 35000)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(TextView(this).apply { text = "Host:" })
        layout.addView(inputHost)
        layout.addView(TextView(this).apply { text = "Porta:"; setPadding(0, 20, 0, 0) })
        layout.addView(inputPorta)

        AlertDialog.Builder(this)
            .setTitle("Conexão OBD")
            .setView(layout)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.obdHost = inputHost.text.toString().trim()
                prefs.obdPorta = inputPorta.text.toString().toIntOrNull() ?: 35000
                atualizarLabels()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editarVeiculo() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val inputMarca = EditText(this).apply {
            setText(prefs.veiculoMarca); hint = "Marca"
        }
        val inputModelo = EditText(this).apply {
            setText(prefs.veiculoModelo); hint = "Modelo"
        }
        val inputAno = EditText(this).apply {
            setText(if (prefs.veiculoAno > 0) prefs.veiculoAno.toString() else "")
            hint = "Ano"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(TextView(this).apply { text = "Marca:" })
        layout.addView(inputMarca)
        layout.addView(TextView(this).apply { text = "Modelo:"; setPadding(0, 20, 0, 0) })
        layout.addView(inputModelo)
        layout.addView(TextView(this).apply { text = "Ano:"; setPadding(0, 20, 0, 0) })
        layout.addView(inputAno)

        AlertDialog.Builder(this)
            .setTitle("Dados do veículo")
            .setView(layout)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.veiculoMarca = inputMarca.text.toString().trim()
                prefs.veiculoModelo = inputModelo.text.toString().trim()
                prefs.veiculoAno = inputAno.text.toString().toIntOrNull() ?: 0
                atualizarLabels()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editarGroq() {
        val input = EditText(this).apply {
            setText(prefs.groqApiKey)
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("API Groq")
            .setMessage("Cole sua chave do Groq (console.groq.com/keys)")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.groqApiKey = input.text.toString().trim()
                atualizarLabels()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun alternarModoIA() {
        val modos = arrayOf("Online → Offline", "Só offline", "Só online")
        val chaves = arrayOf("online_primeiro", "offline_only", "online_only")
        val atual = chaves.indexOf(prefs.modoIA).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Modo da IA")
            .setSingleChoiceItems(modos, atual) { dialog, which ->
                prefs.modoIA = chaves[which]
                atualizarLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =============================================
    // ATUALIZAÇÃO
    // =============================================

    private fun verificarUpdate() {
        Toast.makeText(this, "Verificando...", Toast.LENGTH_SHORT).show()

        object : AsyncTask<Void, Void, Updater.Resultado?>() {
            override fun doInBackground(vararg p: Void): Updater.Resultado? {
                return Updater.verificar(this@SettingsActivity)
            }

            override fun onPostExecute(r: Updater.Resultado?) {
                if (r == null) {
                    Toast.makeText(this@SettingsActivity,
                        "Não foi possível verificar. Verifique a internet.",
                        Toast.LENGTH_LONG).show()
                    return
                }
                if (!r.temAtualizacao) {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Tudo em ordem")
                        .setMessage("Você está na versão mais recente (${r.versaoAtual}).")
                        .setPositiveButton("OK", null)
                        .show()
                    return
                }
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Atualização disponível")
                    .setMessage("Versão atual: ${r.versaoAtual}\nNova versão: ${r.versaoNova}\n\n${r.notas}")
                    .setPositiveButton("Baixar e instalar") { _, _ ->
                        Updater.iniciarAtualizacao(this@SettingsActivity, r)
                    }
                    .setNegativeButton("Depois", null)
                    .show()
            }
        }.execute()
    }

    private fun limparCache() {
        try {
            val cacheDir = cacheDir
            var total = 0L
            cacheDir.listFiles()?.forEach { f ->
                total += f.length()
                f.delete()
            }
            Toast.makeText(this, "Cache limpo (${total / 1024} KB)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao limpar cache", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarSobre() {
        val versao = Updater.versaoLocal(this)
        AlertDialog.Builder(this)
            .setTitle("EXODASH")
            .setMessage(
                "Versão: $versao\n\n" +
                "Launcher para multimídia automotiva\n" +
                "Assistente de voz BOSS\n\n" +
                "BY ALEX. P\n\n" +
                "github.com/lexpaker-arch/EXODASH--Local"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // =============================================
    // ESCOLHER APPS
    // =============================================

    private fun escolherApp(titulo: String, intent: Intent, salvar: (String) -> Unit) {
        val apps = packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString() }

        if (apps.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nenhum app encontrado")
                .setMessage("Nenhum app de $titulo foi encontrado.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val nomes = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val pacotes = apps.map { it.activityInfo.packageName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha o app de $titulo")
            .setItems(nomes) { _, which ->
                salvar(pacotes[which])
                atualizarLabels()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =============================================
    // LABELS
    // =============================================

    private fun atualizarLabels() {
        findViewById<TextView>(R.id.txtNomeUsuario).text = prefs.nomeUsuario

        findViewById<TextView>(R.id.txtEscolhaTelefone).text =
            nomeAmigavel(prefs.appTelefone, "Padrão do sistema")
        findViewById<TextView>(R.id.txtEscolhaMidia).text =
            nomeAmigavel(prefs.appMusica, "Não escolhido")
        findViewById<TextView>(R.id.txtEscolhaGps).text =
            nomeAmigavel(prefs.appGps, "Padrão do sistema")

        findViewById<TextView>(R.id.txtObdHost).text =
            "${prefs.obdHost}:${prefs.obdPorta}"

        val veic = if (prefs.veiculoMarca.isEmpty()) "Não informado"
                   else "${prefs.veiculoMarca} ${prefs.veiculoModelo} ${prefs.veiculoAno}"
        findViewById<TextView>(R.id.txtVeiculo).text = veic

        val groq = findViewById<TextView>(R.id.txtGroqStatus)
        when {
            prefs.groqApiKeyEmbutida -> {
                groq.text = "Configurada (embutida no APK) ✓"
                groq.setTextColor(0xFF00FFCC.toInt())
            }
            prefs.groqApiKeyConfigurada -> {
                groq.text = "Configurada manualmente ✓"
                groq.setTextColor(0xFF00FFCC.toInt())
            }
            else -> {
                groq.text = "Não configurada"
                groq.setTextColor(0xFFFF3333.toInt())
            }
        }

        val modo = when (prefs.modoIA) {
            "online_primeiro" -> "Online → Offline"
            "offline_only" -> "Só offline"
            "online_only" -> "Só online"
            else -> "Online → Offline"
        }
        findViewById<TextView>(R.id.txtModoIA).text = modo

        findViewById<TextView>(R.id.txtVersaoAtual).text = Updater.versaoLocal(this)
    }

    private fun nomeAmigavel(pkg: String, padrao: String): String {
        if (pkg.isEmpty()) return padrao
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            padrao
        }
    }

    override fun onResume() {
        super.onResume()
        // Se ficou um update pendente (usuario foi autorizar permissao), retoma
        Updater.retomarSePendente(this)
    }

    private fun atualizarResumoLogs() {
        try {
            val total = LogEventos.ler(this).size
            val txt = findViewById<TextView>(R.id.txtLogsResumo)
            txt.text = if (total == 0) "Nenhum registro"
                       else "$total evento${if (total > 1) "s" else ""} registrado${if (total > 1) "s" else ""}"
        } catch (e: Exception) {}
    }

    private fun atualizarResumoDtc() {
        try {
            val history = DtcHistory(this)
            val ativos = history.contarAtivos()
            val total = history.lerTodas().size
            val txt = findViewById<TextView>(R.id.txtDtcResumo)
            txt.text = when {
                total == 0 -> "Nenhum registro"
                ativos == 0 -> "$total no historico, nenhum ativo"
                ativos == 1 -> "1 ativo, $total no historico"
                else -> "$ativos ativos, $total no historico"
            }
        } catch (e: Exception) {}
    }

    private fun simularDtc() {
        try {
            val history = DtcHistory(this)
            history.sincronizar(listOf("P0301"), mapOf("P0301" to "Falha de ignicao no cilindro 1"))
            Toast.makeText(this, "DTC P0301 simulado", Toast.LENGTH_SHORT).show()
            atualizarResumoDtc()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        finish()
    }
}
