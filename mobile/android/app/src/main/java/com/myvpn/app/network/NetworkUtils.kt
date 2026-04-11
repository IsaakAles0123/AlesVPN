package com.myvpn.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

fun findNetworkBypassingVpn(context: Context): Network? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Suppress("DEPRECATION")
    val networks = cm.allNetworks
    var fallback: Network? = null
    for (network in networks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
        val wifiOrEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (wifiOrEth) return network
        if (fallback == null) fallback = network
    }
    return fallback
}
