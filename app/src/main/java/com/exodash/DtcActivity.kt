package com.exodash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class DtcActivity : AppCompatActivity() {

    private lateinit var history: DtcHistory
    private lateinit var adapter: DtcAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dtc)

        history = DtcHistory(this)

        findViewById<View>(R.id.btnVoltarDtc).setOnClickListener { finish() }

        val lista = findViewById<ListView>(R.id.listaDtc)
        val vazio = findViewById<TextView>(R.id.textoVazio)

        adapter = DtcAdapter()
        lista.adapter = adapter

        carregar(vazio, lista)

        findViewById<Button>(R.id.btnApagarErros).setOnClickListener {
            confirmarApagar()
        }

        findViewById<Button>(R.id.btnLimparHistorico).setOnClickListener {
            confirmarLimparHistorico()
        }
    }

    private fun carregar(vazio: TextView, lista: ListView) {
        val itens = history.lerTodas().sortedByDescending { it.dataDeteccao }
        if (itens.isEmpty()) {
            vazio.visibility = View.VISIBLE
            lista.visibility = View.GONE
        } else {
            vazio.visibility = View.GONE
            lista.visibility = View.VISIBLE
        }
        adapter.atualizar(itens)
    }

    private fun confirmarApagar() {
        val ativos = history.contarAtivos()
        if (ativos == 0) {
            Toast.makeText(this, "Nenhum erro ativo para apagar", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Apagar erros do carro")
            .setMessage("Isso vai enviar o comando para apagar os $ativos erros ativos na central do carro. Confirma?")
            .setPositiveButton("Sim, apagar") { _, _ ->
                enviarComandoLimpar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarComandoLimpar() {
        // Envia comando pelo ObdService (via MainActivity)
        val ok = ObdBus.limparDTCs()
        if (ok) {
            history.marcarTodosResolvidos()
            Toast.makeText(this, "Comando enviado. Erros serao apagados.", Toast.LENGTH_LONG).show()
            recreate()
        } else {
            Toast.makeText(this, "OBD offline. Nao foi possivel enviar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmarLimparHistorico() {
        AlertDialog.Builder(this)
            .setTitle("Limpar historico")
            .setMessage("Isso apaga o historico local de DTCs (nao mexe no carro). Confirma?")
            .setPositiveButton("Sim") { _, _ ->
                history.limparTudo()
                recreate()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onBackPressed() {
        finish()
    }

    // ============================================
    // ADAPTER
    // ============================================
    inner class DtcAdapter : BaseAdapter() {
        private val lista = mutableListOf<DtcHistory.Entrada>()

        fun atualizar(nova: List<DtcHistory.Entrada>) {
            lista.clear()
            lista.addAll(nova)
            notifyDataSetChanged()
        }

        override fun getCount() = lista.size
        override fun getItem(p: Int) = lista[p]
        override fun getItemId(p: Int) = p.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@DtcActivity)
                .inflate(R.layout.item_dtc, parent, false)

            val e = lista[position]

            view.findViewById<TextView>(R.id.dtcCodigo).text = e.codigo
            view.findViewById<TextView>(R.id.dtcDescricao).text = e.descricao

            val statusView = view.findViewById<TextView>(R.id.dtcStatus)
            if (e.ativo) {
                statusView.text = "ATIVO"
                statusView.setTextColor(0xFFFF3333.toInt())
                statusView.setBackgroundColor(0xFF2A0A0A.toInt())
            } else {
                statusView.text = "RESOLVIDO"
                statusView.setTextColor(0xFF00FFCC.toInt())
                statusView.setBackgroundColor(0xFF0A2A2A.toInt())
            }

            val datas = StringBuilder()
            datas.append("Detectado em ").append(DtcHistory.formatarData(e.dataDeteccao))
            e.dataResolucao?.let {
                datas.append("\nResolvido em ").append(DtcHistory.formatarData(it))
            }
            view.findViewById<TextView>(R.id.dtcDatas).text = datas.toString()

            return view
        }
    }
}
