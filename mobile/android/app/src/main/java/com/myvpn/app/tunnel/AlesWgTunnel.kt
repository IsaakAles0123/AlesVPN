package com.myvpn.app.tunnel

import com.wireguard.android.backend.Tunnel

class AlesWgTunnel(
    private val tunnelName: String,
    private val onState: (Tunnel.State) -> Unit,
) : Tunnel {

    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        onState(newState)
    }
}
