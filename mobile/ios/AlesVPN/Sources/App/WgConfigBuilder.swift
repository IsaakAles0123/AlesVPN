import Foundation

/// Параметры сервера — совпадают с `res/values/strings.xml` на Android; обновляйте вместе.
enum WgVendorConfig {
    static let serverPublicKey = "Rc1neiBkkHNmwFV/2YiWsVJyEc9V0rQm03QnMaWR7Qs="
    static let endpoint = "5.42.122.172:51820"
    static let dns = "1.1.1.1"
    static let allowedIPs = "0.0.0.0/0, ::/0"
    static let persistentKeepalive = "25"
}

enum WgConfigBuilder {
    static func normalizeAddress(_ raw: String) -> String {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty { return s }
        if s.contains("/") { return s }
        let ipv4 = #"^\d{1,3}(\.\d{1,3}){3}$"#
        if s.range(of: ipv4, options: .regularExpression) != nil {
            return "\(s)/32"
        }
        return s
    }

    /// Готовая строка `wg-quick`, или `nil` если не хватает данных.
    static func resolveConfigString(privateKey: String, address: String) -> String? {
        let privateKey = privateKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let address = normalizeAddress(address.trimmingCharacters(in: .whitespacesAndNewlines))
        if privateKey.isEmpty || address.isEmpty { return nil }

        let serverPk = WgVendorConfig.serverPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let endpoint = WgVendorConfig.endpoint.trimmingCharacters(in: .whitespacesAndNewlines)
        if serverPk.isEmpty || endpoint.isEmpty { return nil }

        var lines: [String] = []
        lines.append("[Interface]")
        lines.append("PrivateKey = \(privateKey)")
        lines.append("Address = \(address)")
        let dns = WgVendorConfig.dns.trimmingCharacters(in: .whitespacesAndNewlines)
        if !dns.isEmpty { lines.append("DNS = \(dns)") }
        lines.append("")
        lines.append("[Peer]")
        lines.append("PublicKey = \(serverPk)")
        lines.append("Endpoint = \(endpoint)")
        lines.append("AllowedIPs = \(WgVendorConfig.allowedIPs)")
        let ka = WgVendorConfig.persistentKeepalive.trimmingCharacters(in: .whitespacesAndNewlines)
        if !ka.isEmpty { lines.append("PersistentKeepalive = \(ka)") }
        return lines.joined(separator: "\n")
    }
}
