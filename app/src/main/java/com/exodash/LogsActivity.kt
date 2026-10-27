package com.exodash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LogsActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        findViewById<View>(R.id.btnVoltarLogs).setOnClickListener { finish() }

        val lista = findViewById<ListView>(R.id.listaLogs)
        val vazio = findViewById<TextView>(R.id.textoVazioLogs)

        adapter = LogAdapter()
        lista.adapter = adapter

        carregar(vazio, lista)

        findViewById<Button>(R.id.btnLimparLogs).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar logs")
                .setMessage("Apaga todos os eventos registrados. Confirma?")
                .setPositiveButton("Sim") { _, _ ->
                    LogEventos.limpar(this)
                    recreate()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        findViewById<Button>(R.id.btnExportarLogs).setOnClickListener {
            exportarLog()
        }
    }

    private fun exportarLog() {
        try {
            // 1. Filtra so linhas de OBD (>> comando e << resposta)
            val eventos = LogEventos.ler(this).sortedBy { it.timestamp }
            val linhas = eventos
                .filter { it.texto.contains("OBD >>") || it.texto.contains("OBD <<") || it.texto.contains("OBD ERRO") }
                .map { "${LogEventos.formatarHora(it.timestamp)}  ${it.texto}" }

            if (linhas.isEmpty()) {
                android.widget.Toast.makeText(this,
                    "Sem comandos OBD no log ainda. Conecte o adaptador primeiro.",
                    android.widget.Toast.LENGTH_LONG).show()
                return
            }

            // 2. Salva em cacheDir (FileProvider ja configurado pra isso)
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val arquivo = java.io.File(cacheDir, "obd_log_$ts.txt")
            arquivo.writeText(linhas.joinToString("\n"))

            // 3. Abre o compartilhamento do Android
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                arquivo
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "OBD log EXODASH - $ts")
                putExtra(Intent.EXTRA_TEXT, "Log bruto ELM327 - ${linhas.size} linhas")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Exportar log OBD"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this,
                "Erro ao exportar: ${e.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun carregar(vazio: TextView, lista: ListView) {
        val itens = LogEventos.ler(this).sortedByDescending { it.timestamp }
        if (itens.isEmpty()) {
            vazio.visibility = View.VISIBLE
            lista.visibility = View.GONE
        } else {
            vazio.visibility = View.GONE
            lista.visibility = View.VISIBLE
        }
        adapter.atualizar(itens)
    }

    override fun onBackPressed() { finish() }

    inner class LogAdapter : BaseAdapter() {
        private val lista = mutableListOf<LogEventos.Evento>()

        fun atualizar(nova: List<LogEventos.Evento>) {
            lista.clear()
            lista.addAll(nova)
            notifyDataSetChanged()
        }

        override fun getCount() = lista.size
        override fun getItem(p: Int) = lista[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@LogsActivity)
                .inflate(R.layout.item_log, parent, false)

            val e = lista[position]
            view.findViewById<TextView>(R.id.logHora).text = LogEventos.formatarHora(e.timestamp)
            view.findViewById<TextView>(R.id.logEvento).text = e.texto
            return view
        }
    }
}
