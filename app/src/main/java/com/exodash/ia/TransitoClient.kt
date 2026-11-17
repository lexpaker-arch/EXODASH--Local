package com.exodash.ia

import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object TransitoClient {

    private const val TIMEOUT_MS = 8000

    fun consultar(context: Context, destino: String): String {
        val (lat, lon) = localizacao(context)
            ?: return "Nao consegui pegar sua localizacao, Senhor. Ative o GPS."

        val (destLat, destLon) = geocodificar(destino)
            ?: return "Nao encontrei '$destino', Senhor."

        return try {
            val url = URL(
                "https://router.project-osrm.org/route/v1/driving/" +
                "$lon,$lat;$destLon,$destLat?overview=false"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "EXODASH/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val routes = JSONObject(body).getJSONArray("routes")
            if (routes.length() == 0) return "Sem rota disponivel, Senhor."

            val r = routes.getJSONObject(0)
            val distKm = r.getDouble("distance") / 1000.0
            val durMin = r.getDouble("duration") / 60.0

            String.format(
                Locale("pt", "BR"),
                "Rota ate %s: %.1f km, cerca de %.0f minutos.",
                destino, distKm, durMin
            )
        } catch (e: Exception) {
            "Falha ao calcular rota: ${e.message}"
        }
    }

    private fun geocodificar(endereco: String): Pair<Double, Double>? {
        return try {
            val q = URLEncoder.encode(endereco, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "EXODASH/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            Pair(o.getDouble("lat"), o.getDouble("lon"))
        } catch (_: Exception) { null }
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
}
