package com.exodash

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppDrawerActivity : AppCompatActivity() {

    data class AppInfo(
        val nome: String,
        val pacote: String,
        val icone: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Breadcrumbs.registrar("AppDrawer: onCreate")

        try {
            setContentView(R.layout.activity_app_drawer)
            Breadcrumbs.registrar("AppDrawer: setContentView OK")
        } catch (e: Exception) {
            Breadcrumbs.registrarErro("AppDrawer setContentView", e)
            finish()
            return
        }

        try {
            findViewById<View>(R.id.btnVoltarApps)?.setOnClickListener {
                Breadcrumbs.registrar("AppDrawer: voltar")
                finish()
            }
        } catch (e: Exception) {
            Breadcrumbs.registrarErro("AppDrawer botao voltar", e)
        }

        try {
            Breadcrumbs.registrar("AppDrawer: listando apps")
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val lista = pm.queryIntentActivities(mainIntent, 0)
            Breadcrumbs.registrar("AppDrawer: ${lista.size} apps encontrados")

            val apps = lista
                .filter { it.activityInfo.packageName != packageName }
                .mapNotNull {
                    try {
                        AppInfo(
                            nome = it.loadLabel(pm).toString(),
                            pacote = it.activityInfo.packageName,
                            icone = null  // NÃO carrega icone agora
                        )
                    } catch (e: Exception) { null }
                }
                .sortedBy { it.nome.lowercase() }

            Breadcrumbs.registrar("AppDrawer: ${apps.size} apps filtrados")

            val grid = findViewById<GridView>(R.id.appGrid)
            grid.adapter = AppAdapter(apps)

            grid.setOnItemClickListener { _, _, pos, _ ->
                try {
                    Breadcrumbs.registrar("AppDrawer: abrindo ${apps[pos].pacote}")
                    val launch = pm.getLaunchIntentForPackage(apps[pos].pacote)
                    if (launch != null) startActivity(launch)
                    else Toast.makeText(this, "Nao foi possivel abrir", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Breadcrumbs.registrarErro("AppDrawer abrir app", e)
                }
            }
            Breadcrumbs.registrar("AppDrawer: adapter configurado")
        } catch (e: Exception) {
            Breadcrumbs.registrarErro("AppDrawer carregar apps", e)
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    inner class AppAdapter(private val lista: List<AppInfo>) : BaseAdapter() {
        override fun getCount() = lista.size
        override fun getItem(position: Int) = lista[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AppDrawerActivity)
                .inflate(R.layout.item_app, parent, false)

            try {
                val app = lista[position]
                view.findViewById<ImageView>(R.id.appIcon)?.setImageResource(android.R.drawable.sym_def_app_icon)
                view.findViewById<TextView>(R.id.appName)?.text = app.nome
            } catch (e: Exception) {}
            return view
        }
    }
}
