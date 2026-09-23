package com.exodash

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

/**
 * Auto-update simples via GitHub Releases.
 *
 * Configurar em REPO_OWNER/REPO_NAME o repositório onde o APK é publicado.
 * O GitHub Releases deve ter uma release com uma tag (ex: v1.1)
 * e um asset chamado app-debug.apk (ou qualquer .apk).
 */
object Updater {

    // ⚠️ CONFIGURE AQUI O REPOSITÓRIO
    private const val REPO_OWNER = "lexpaker-arch"
    private const val REPO_NAME = "EXODASH--Local"

    data class Resultado(
        val temAtualizacao: Boolean,
        val versaoAtual: String,
        val versaoNova: String,
        val urlApk: String,
        val notas: String
    )

    /**
     * Verifica se há atualização disponível. Chame em uma thread separada.
     */
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
        } catch (e: Exception) {
            "0.0"
        }
    }

    /**
     * Compara duas versões tipo "1.2.3" ou "v1.2.3".
     * Retorna <0 se v1 < v2, 0 se igual, >0 se v1 > v2.
     */
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

    /**
     * Baixa o APK e abre o instalador do Android.
     */
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

                    val pasta = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "")
                    pasta.mkdirs()
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
                    apk
                } catch (e: Exception) {
                    Log.e("Updater", "Erro ao baixar", e)
                    null
                }
            }

            override fun onProgressUpdate(vararg values: Int?) {
                dialog.progress = values[0] ?: 0
            }

            override fun onPostExecute(apk: File?) {
                dialog.dismiss()
                if (apk == null || !apk.exists()) return

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
            }
        }.execute()
    }
}
