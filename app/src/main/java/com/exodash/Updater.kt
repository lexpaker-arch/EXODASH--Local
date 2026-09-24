package com.exodash

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Updater {

    private const val REPO_OWNER = "lexpaker-arch"
    private const val REPO_NAME = "EXODASH--Local"
    private const val PREFS = "exodash_updater"
    private const val KEY_PENDENTE_APK = "pendente_apk"
    private const val KEY_PENDENTE_URL = "pendente_url"

    data class Resultado(
        val temAtualizacao: Boolean,
        val versaoAtual: String,
        val versaoNova: String,
        val urlApk: String,
        val notas: String
    )

    // ============================================================
    // VERIFICAR
    // ============================================================
    fun verificar(context: Context): Resultado? {
        return try {
            val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) return null

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val bodyNotas = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: return null

            var urlApk = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val nome = a.optString("name", "")
                if (nome.endsWith(".apk", ignoreCase = true)) {
                    urlApk = a.optString("browser_download_url", "")
                    break
                }
            }
            if (urlApk.isEmpty()) return null

            val versaoAtual = versaoLocal(context)
            val tem = compararVersoes(versaoAtual, tag) < 0

            LogEventos.registrar(context, "Update: versao atual $versaoAtual, ultima $tag")

            Resultado(tem, versaoAtual, tag, urlApk, bodyNotas)
        } catch (e: Exception) {
            Log.e("Updater", "Erro ao verificar", e)
            null
        }
    }

    fun versaoLocal(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0.0"
        } catch (e: Exception) { "0.0" }
    }

    private fun compararVersoes(v1: String, v2: String): Int {
        val a = v1.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val b = v2.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    // ============================================================
    // PERMISSAO DE INSTALAR APK
    // ============================================================
    fun temPermissaoInstalar(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun abrirConfigPermissao(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {}
            }
        }
    }

    // ============================================================
    // FLUXO PRINCIPAL — verifica permissao ANTES de baixar
    // ============================================================
    fun iniciarAtualizacao(context: Context, r: Resultado) {
        // 1. Tem permissao?
        if (!temPermissaoInstalar(context)) {
            // Salva a URL para retomar depois
            salvarPendente(context, r.urlApk)
            AlertDialog.Builder(context)
                .setTitle("Permissao necessaria")
                .setMessage(
                    "Para instalar a atualizacao, o EXODASH precisa de permissao para instalar apps.\n\n" +
                    "Toque em OK e ative a opcao 'Permitir desta fonte' nas configuracoes.\n\n" +
                    "Depois volte ao EXODASH que a atualizacao continua sozinha."
                )
                .setPositiveButton("OK") { _, _ ->
                    abrirConfigPermissao(context)
                }
                .setCancelable(false)
                .show()
            return
        }
        // 2. Ja tem permissao — baixa e instala
        baixarEInstalar(context, r.urlApk)
    }

    // ============================================================
    // RETOMAR — chamar em onResume()
    // ============================================================
    fun retomarSePendente(context: Context) {
        val url = getPendente(context) ?: return
        if (temPermissaoInstalar(context)) {
            limparPendente(context)
            baixarEInstalar(context, url)
        }
    }

    private fun salvarPendente(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDENTE_URL, url).apply()
    }

    private fun getPendente(context: Context): String? {
        val url = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PENDENTE_URL, "") ?: ""
        return url.ifEmpty { null }
    }

    private fun limparPendente(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PENDENTE_URL).apply()
    }

    // ============================================================
    // BAIXAR E INSTALAR
    // ============================================================
    fun baixarEInstalar(context: Context, urlApk: String) {
        val dialog = ProgressDialog(context).apply {
            setTitle("Atualizando EXODASH")
            setMessage("Baixando...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }

        object : AsyncTask<Void, Int, File?>() {
            override fun doInBackground(vararg p: Void): File? {
                return try {
                    val url = URL(urlApk)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connect()
                    val total = conn.contentLength
                    val input = conn.inputStream

                    val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    pasta?.mkdirs()
                    val apk = File(pasta, "exodash_update.apk")

                    FileOutputStream(apk).use { out ->
                        val buf = ByteArray(8192)
                        var lido: Int
                        var soma = 0L
                        while (input.read(buf).also { lido = it } > 0) {
                            out.write(buf, 0, lido)
                            soma += lido
                            if (total > 0) publishProgress(((soma * 100) / total).toInt())
                        }
                    }
                    LogEventos.registrar(context, "Update: APK baixado")
                    apk
                } catch (e: Exception) {
                    Log.e("Updater", "Erro ao baixar", e)
                    LogEventos.registrar(context, "Update: falha no download")
                    null
                }
            }

            override fun onProgressUpdate(vararg values: Int?) {
                dialog.progress = values[0] ?: 0
            }

            override fun onPostExecute(apk: File?) {
                dialog.dismiss()
                if (apk == null || !apk.exists()) {
                    android.widget.Toast.makeText(context,
                        "Falha no download", android.widget.Toast.LENGTH_LONG).show()
                    return
                }

                // Se perdeu permissao no meio do caminho (raro), salva e para
                if (!temPermissaoInstalar(context)) {
                    salvarPendente(context, urlApk)
                    return
                }

                abrirInstalador(context, apk)
            }
        }.execute()
    }

    private fun abrirInstalador(context: Context, apk: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apk
                )
            } else {
                Uri.fromFile(apk)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("Updater", "Erro ao abrir instalador", e)
            android.widget.Toast.makeText(context,
                "Erro: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
