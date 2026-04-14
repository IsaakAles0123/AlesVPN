package com.myvpn.app

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val _showWgKeySetup = MutableStateFlow(false)
    val showWgKeySetup: StateFlow<Boolean> = _showWgKeySetup.asStateFlow()

    fun openWgKeySetup() {
        _showWgKeySetup.value = true
    }

    fun closeWgKeySetup() {
        _showWgKeySetup.value = false
    }

    var tunnelState by mutableStateOf(Tunnel.State.DOWN)
        private set

    var vpnSessionStartMs by mutableStateOf<Long?>(null)
        private set

    fun appendLog(line: String) {
        Log.d(TAG, line)
    }

    fun updateTunnelStateUi(state: Tunnel.State) {
        tunnelState = state
        when (state) {
            Tunnel.State.UP -> if (vpnSessionStartMs == null) vpnSessionStartMs = System.currentTimeMillis()
            Tunnel.State.DOWN -> vpnSessionStartMs = null
            Tunnel.State.TOGGLE -> { /* keep timer */ }
        }
    }

    private companion object {
        const val TAG = "AlesVPN"
    }
}
