package com.exodash

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Monitora o estado real da conexao (Wi-Fi, 4G, etc).
 * Dispara callback quando muda.
 */
class NetworkMonitor(
    context: Context,
    private val onChange: (online: Boolean) -> Unit
) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { emitir() }
        override fun onLost(network: Network) { emitir() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { emitir() }
    }

    fun iniciar() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {}
        emitir()
    }

    fun parar() {
        try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) {}
    }

    fun estaOnline(): Boolean {
        val rede = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(rede) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun emitir() {
        onChange(estaOnline())
    }
}
