package com.exodash.ia

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent

object TelefoneController {

    fun ligar(context: Context, numero: String) {
        val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:$numero")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(i) } catch (_: SecurityException) {
            val i2 = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i2)
        }
    }

    fun atender(context: Context) = enviarTecla(context, KeyEvent.KEYCODE_HEADSETHOOK)
    fun desligar(context: Context) = enviarTecla(context, KeyEvent.KEYCODE_ENDCALL)

    private fun enviarTecla(context: Context, keyCode: Int) {
        context.sendOrderedBroadcast(
            Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            ), null
        )
        context.sendOrderedBroadcast(
            Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode)
            ), null
        )
    }
}
