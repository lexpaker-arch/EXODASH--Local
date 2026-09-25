package com.exodash

/**
 * Ponte simples entre Activities e o ObdUsbService.
 * O MainActivity registra o service quando estiver ativo.
 */
object ObdBus {

    var servico: ObdUsbService? = null

    fun limparDTCs(): Boolean {
        val s = servico ?: return false
        return try {
            s.limparDTCs()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun estaConectado(): Boolean = servico != null
}
