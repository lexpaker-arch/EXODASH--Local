package com.exodash.ia

import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ClimaClient {

    private const val TIMEOUT_MS = 6000

    fun consultar(context: Context): String {
        val (lat, lon) = localizacao(context)
            ?: return "Nao consegui pegar sua localizacao, Senhor. Ative o GPS."

        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&timezone=auto"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body).getJSONObject("current")
            val temp = json.getDouble("temperature_2m")
            val umid = json.getInt("relative_humidity_2m")
            val vento = json.getDouble("wind_speed_10m")
            val code = json.getInt("weather_code")

            String.format(
                Locale("pt", "BR"),
                "Agora: %s, %.0f graus, umidade %d%%, vento %.0f km/h.",
                descrever(code), temp, umid, vento
            )
        } catch (e: Exception) {
            "Falha ao consultar clima: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun localizacao(context: Context): Pair<Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val temPermissao = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!temPermissao) return null
        for (p in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) return Pair(loc.latitude, loc.longitude)
            } catch (_: SecurityException) {} catch (_: Exception) {}
        }
        return null
    }

    private fun descrever(code: Int): String = when (code) {
        0 -> "ceu limpo"
        1, 2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "neblina"
        51, 53, 55 -> "garoa"
        61, 63, 65 -> "chuva"
        66, 67 -> "chuva congelante"
        71, 73, 75 -> "neve"
        77 -> "granizo"
        80, 81, 82 -> "pancadas de chuva"
        85, 86 -> "pancadas de neve"
        95 -> "tempestade"
        96, 99 -> "tempestade com granizo"
        else -> "condicao desconhecida"
    }
}
