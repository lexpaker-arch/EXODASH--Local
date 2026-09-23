package com.exodash

/**
 * Dados de telemetria do veiculo.
 * Todos os campos sao nulaveis — null significa "sem dado".
 */
data class TelemetryData(
    val velocidade: Int? = null,      // km/h
    val rpm: Int? = null,             // rotacoes por minuto
    val temperaturaMotor: Int? = null,// Celsius
    val combustivel: Int? = null,     // %
    val bateria: Double? = null,      // Volts
    val dtcs: List<String> = emptyList()
) {
    companion object {
        val VAZIO = TelemetryData()
    }
}
