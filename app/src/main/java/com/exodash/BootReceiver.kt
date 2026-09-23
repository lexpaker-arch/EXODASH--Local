package com.exodash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Recebe o evento BOOT_COMPLETED e inicia o EXODASH automaticamente.
 * Tambem trata QUICKBOOT_POWERON (HTC) e LOCKED_BOOT_COMPLETED (direct boot).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val acao = intent?.action ?: return
        Log.d("BootReceiver", "Recebido: $acao")

        when (acao) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {

                try {
                    val i = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(i)
                    Log.d("BootReceiver", "EXODASH iniciado no boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Erro ao iniciar", e)
                }
            }
        }
    }
}
