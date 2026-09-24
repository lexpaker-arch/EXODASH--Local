package com.exodash

/**
 * Ponte simples entre Activities e o ObdService.
 * O MainActivity registra o service quando estiver ativo.
 */
object ObdBus {

    var servico: ObdService? = null

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
