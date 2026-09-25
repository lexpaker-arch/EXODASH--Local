package com.exodash

/**
 * Estado compartilhado da telemetria atual.
 * MainActivity atualiza; BossBrain consulta.
 */
object TelemetryState {
    @Volatile var atual: TelemetryData = TelemetryData.VAZIO
    @Volatile var veiculoNome: String = ""
}
