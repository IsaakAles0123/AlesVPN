package com.myvpn.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import com.myvpn.app.R

/**
 * Параметры сервера — в [strings.xml] (wg_vendor_*), один сервер для всех клиентов.
 * У клиента хранятся только [PrivateKey] и [Address] (выдаются вам при создании пира).
 */
class VpnSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPrivateKey(): String = prefs.getString(KEY_PRIVATE_KEY, "") ?: ""

    fun loadAddress(): String = prefs.getString(KEY_ADDRESS, "") ?: ""

    fun save(privateKey: String, address: String) {
        prefs.edit {
            putString(KEY_PRIVATE_KEY, privateKey.trim())
            putString(KEY_ADDRESS, normalizeAddress(address.trim()))
        }
    }

    fun isConfigured(): Boolean {
        val k = loadPrivateKey()
        val a = loadAddress()
        return k.isNotBlank() && a.isNotBlank()
    }

    /**
     * Готовая строка для [com.wireguard.config.Config.parse], или null если не хватает данных.
     */
    fun resolveConfigString(res: Resources): String? {
        val privateKey = loadPrivateKey()
        val address = loadAddress()
        if (privateKey.isBlank() || address.isBlank()) return null

        val serverPk = res.getString(R.string.wg_vendor_server_public_key).trim()
        val endpoint = res.getString(R.string.wg_vendor_endpoint).trim()
        val dns = res.getString(R.string.wg_vendor_dns).trim()
        val allowed = res.getString(R.string.wg_vendor_allowed_ips).trim()
        val keepalive = res.getString(R.string.wg_vendor_persistent_keepalive).trim()
        if (serverPk.isBlank() || endpoint.isBlank()) return null

        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = $address")
            if (dns.isNotBlank()) appendLine("DNS = $dns")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $serverPk")
            appendLine("Endpoint = $endpoint")
            appendLine("AllowedIPs = $allowed")
            if (keepalive.isNotBlank()) {
                appendLine("PersistentKeepalive = $keepalive")
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "ales_vpn_wg_user"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_ADDRESS = "address"

        /**
         * Вторая строка может быть `10.8.0.5` — добавим /32.
         */
        fun normalizeAddress(raw: String): String {
            val s = raw.trim()
            if (s.isEmpty()) return s
            if (s.contains("/")) return s
            val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
            return if (ipv4.matches(s)) "$s/32" else s
        }

        /**
         * Разбор вставки: строка 1 — приватный ключ, строка 2 — IP в VPN.
         * Возвращает null, если нет хотя бы одной непустой строки.
         */
        fun parseSellerMessage(text: String): Pair<String, String>? {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val key = lines[0]
            val addr = if (lines.size >= 2) normalizeAddress(lines[1]) else ""
            return key to addr
        }
    }
}
