import Foundation
import NetworkExtension
import WireGuardKit

/// Расширение Packet Tunnel: получает `wg-quick` строку из `NETunnelProviderProtocol.providerConfiguration`.
final class PacketTunnelProvider: NEPacketTunnelProvider {

    private lazy var adapter: WireGuardAdapter = {
        WireGuardAdapter(with: self) { _, message in
            NSLog("%@", message)
        }
    }()

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        guard
            let tunnelProtocol = protocolConfiguration as? NETunnelProviderProtocol,
            let provider = tunnelProtocol.providerConfiguration,
            let cfgStr = provider["WgQuickConfig"] as? String
        else {
            completionHandler(
                NSError(
                    domain: "AlesVPN",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Нет WgQuickConfig в профиле VPN."]
                )
            )
            return
        }

        do {
            let tunnelConfiguration = try TunnelConfiguration(fromWgQuickConfig: cfgStr)
            adapter.start(tunnelConfiguration: tunnelConfiguration) { adapterError in
                if let adapterError {
                    NSLog("WireGuardAdapter error: %@", String(describing: adapterError))
                }
                completionHandler(adapterError)
            }
        } catch {
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        adapter.stop { _ in
            completionHandler()
        }
    }
}
