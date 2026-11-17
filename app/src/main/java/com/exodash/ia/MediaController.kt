package com.exodash.ia

import android.content.Context
import android.content.Intent
import android.view.KeyEvent

object MediaController {

    fun playPause(context: Context) = enviar(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(context: Context) = enviar(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev(context: Context) = enviar(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun stop(context: Context) = enviar(context, KeyEvent.KEYCODE_MEDIA_STOP)

    private fun enviar(context: Context, keyCode: Int) {
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
