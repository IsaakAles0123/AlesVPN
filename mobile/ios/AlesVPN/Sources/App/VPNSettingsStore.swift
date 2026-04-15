import Foundation

/// Хранение пользовательских полей WireGuard (как [VpnSettingsRepository] на Android).
final class VPNSettingsStore: ObservableObject {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let privateKey = "private_key"
        static let address = "address"
    }

    @Published var privateKey: String
    @Published var address: String

    init() {
        privateKey = defaults.string(forKey: Keys.privateKey) ?? ""
        address = defaults.string(forKey: Keys.address) ?? ""
    }

    func loadPrivateKey() -> String { privateKey }
    func loadAddress() -> String { address }

    func save(privateKey: String, address: String) {
        let k = privateKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let a = WgConfigBuilder.normalizeAddress(address.trimmingCharacters(in: .whitespacesAndNewlines))
        self.privateKey = k
        self.address = a
        defaults.set(k, forKey: Keys.privateKey)
        defaults.set(a, forKey: Keys.address)
    }

    func isConfigured() -> Bool {
        !privateKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Две строки: ключ, затем IP (как вставка с ПК).
    static func parseSellerMessage(_ text: String) -> (String, String)? {
        let lines = text
            .split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard let first = lines.first else { return nil }
        let addr = lines.count >= 2 ? WgConfigBuilder.normalizeAddress(String(lines[1])) : ""
        return (first, addr)
    }
}
