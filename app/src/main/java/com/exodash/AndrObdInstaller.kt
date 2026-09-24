package com.exodash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Instala o AndrOBd a partir de assets/androbd/AndrOBd.apk
 * O Android sempre mostra a tela de instalacao (nao tem como ser silencioso).
 */
object AndrObdInstaller {

    private const val ASSET_PATH = "androbd/AndrOBd.apk"

    /** Verifica se o AndrOBd esta instalado. */
    fun estaInstalado(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.md7891.androbd", 0)
            true
        } catch (e: Exception) {
            // Tenta outros package names conhecidos
            try {
                context.packageManager.getPackageInfo("com.karl.androbd", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /** Copia o APK dos assets para filesDir e abre o instalador. */
    fun instalar(context: Context) {
        try {
            // 1. Copia o APK dos assets para filesDir
            val destino = File(context.filesDir, "AndrOBd.apk")
            if (!destino.exists()) {
                context.assets.open(ASSET_PATH).use { input ->
                    destino.outputStream().use { input.copyTo(it) }
                }
                Log.d("AndrObdInstaller", "APK extraido para ${destino.absolutePath}")
            }

            // 2. Verifica permissao de instalar APK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // Abre configuracao de "Instalar apps desconhecidos"
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Permissao necessaria")
                        .setMessage("Para instalar o AndrOBd, o EXODASH precisa de permissao para instalar apps. Toque em OK e ative 'Permitir desta fonte'.")
                        .setPositiveButton("OK") { _, _ ->
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                intent.data = Uri.parse("package:${context.packageName}")
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        }
                        .setCancelable(false)
                        .show()
                    return
                }
            }

            // 3. Abre o instalador
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    destino
                )
            } else {
                Uri.fromFile(destino)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e("AndrObdInstaller", "Erro ao instalar", e)
            android.widget.Toast.makeText(context,
                "Erro ao instalar AndrOBd: ${e.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
