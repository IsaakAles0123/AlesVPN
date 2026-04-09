package com.myvpn.app.tunnel

import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.net.InetAddress

class MyVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closeTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel != null) {
            return START_STICKY
        }
        val builder = Builder()
            .setSession("AlesVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .allowBypass()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.excludeRoute(IpPrefix(InetAddress.getByName("10.0.2.0"), 24))
        }
        tunnel = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }

    private fun closeTunnel() {
        tunnel?.close()
        tunnel = null
    }

    companion object {
        const val ACTION_STOP = "com.myvpn.app.action.STOP_VPN"
    }
}
