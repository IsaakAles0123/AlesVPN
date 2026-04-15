import Foundation
import NetworkExtension
import Combine

/// Управление системным VPN-профилем (Packet Tunnel) и передача `wg-quick` в расширение.
@MainActor
final class VPNManager: ObservableObject {
    /// Должен совпадать с bundle id target'а PacketTunnel в Xcode / project.yml.
    static let tunnelProviderBundleId = "com.myvpn.app.alesvpn.PacketTunnel"

    @Published private(set) var status: NEVPNStatus = .invalid
    @Published private(set) var lastError: String?

    private var statusObserver: NSObjectProtocol?

    init() {
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.refreshStatus()
            }
        }
        refreshStatus()
    }

    deinit {
        if let statusObserver {
            NotificationCenter.default.removeObserver(statusObserver)
        }
    }

    func refreshStatus() {
        NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, _ in
            guard let self else { return }
            let m = managers?.first
            Task { @MainActor in
                self.status = m?.connection.status ?? .invalid
            }
        }
    }

    /// Сохраняет профиль и поднимает туннель с конфигом `wg-quick`.
    func connect(wgQuickConfig: String, completion: @escaping (Error?) -> Void) {
        lastError = nil
        NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, error in
            if let error {
                Task { @MainActor in
                    self?.lastError = error.localizedDescription
                    completion(error)
                }
                return
            }
            let manager = managers?.first ?? NETunnelProviderManager()
            let proto = NETunnelProviderProtocol()
            proto.providerBundleIdentifier = Self.tunnelProviderBundleId
            proto.serverAddress = "AlesVPN"
            proto.providerConfiguration = [
                "WgQuickConfig": wgQuickConfig,
            ]
            manager.protocolConfiguration = proto
            manager.localizedDescription = "AlesVPN"
            manager.isEnabled = true

            manager.saveToPreferences { error in
                if let error {
                    Task { @MainActor in
                        self?.lastError = error.localizedDescription
                        completion(error)
                    }
                    return
                }
                manager.loadFromPreferences { error in
                    if let error {
                        Task { @MainActor in
                            self?.lastError = error.localizedDescription
                            completion(error)
                        }
                        return
                    }
                    do {
                        try manager.connection.startVPNTunnel()
                        Task { @MainActor in
                            self?.refreshStatus()
                            completion(nil)
                        }
                    } catch {
                        Task { @MainActor in
                            self?.lastError = error.localizedDescription
                            completion(error)
                        }
                    }
                }
            }
        }
    }

    func disconnect(completion: ((Error?) -> Void)? = nil) {
        NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, _ in
            managers?.first?.connection.stopVPNTunnel()
            Task { @MainActor in
                self?.refreshStatus()
                completion?(nil)
            }
        }
    }
}
