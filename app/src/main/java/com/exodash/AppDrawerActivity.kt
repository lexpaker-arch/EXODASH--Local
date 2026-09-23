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
import androidx.appcompat.app.AppCompatActivity

class AppDrawerActivity : AppCompatActivity() {

    data class AppInfo(val nome: String, val pacote: String, val icone: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        findViewById<android.view.View>(R.id.btnVoltarApps).setOnClickListener {
            finish()
        }

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppInfo(
                    nome = it.loadLabel(pm).toString(),
                    pacote = it.activityInfo.packageName,
                    icone = it.loadIcon(pm)
                )
            }
            .sortedBy { it.nome.lowercase() }

        val grid = findViewById<GridView>(R.id.appGrid)
        grid.adapter = AppAdapter(apps)

        grid.setOnItemClickListener { _, _, pos, _ ->
            val launch = pm.getLaunchIntentForPackage(apps[pos].pacote)
            if (launch != null) startActivity(launch)
        }
    }

    inner class AppAdapter(private val lista: List<AppInfo>) : BaseAdapter() {
        override fun getCount() = lista.size
        override fun getItem(position: Int) = lista[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@AppDrawerActivity)
                .inflate(R.layout.item_app, parent, false)

            val app = lista[position]
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(app.icone)
            view.findViewById<TextView>(R.id.appName).text = app.nome
            return view
        }
    }
}
