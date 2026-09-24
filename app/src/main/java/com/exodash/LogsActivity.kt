package com.exodash

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
