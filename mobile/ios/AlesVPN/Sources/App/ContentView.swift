import SwiftUI
import NetworkExtension

struct ContentView: View {
    @StateObject private var settings = VPNSettingsStore()
    @StateObject private var vpn = VPNManager()
    @State private var showKeySetup = false
    @State private var busy = false
    @State private var banner: String?

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.07, green: 0.06, blue: 0.12)
                    .ignoresSafeArea()

                VStack(spacing: 24) {
                    statusCard

                    Button {
                        toggleVpn()
                    } label: {
                        Text(mainButtonTitle)
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(mainButtonColor)
                            .foregroundStyle(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    .disabled(busy || (needsConfig && !isConnected))

                    if let banner {
                        Text(banner)
                            .font(.footnote)
                            .foregroundStyle(.orange)
                            .multilineTextAlignment(.center)
                    }

                    Spacer()
                }
                .padding(24)
            }
            .navigationTitle("AlesVPN")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showKeySetup = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                    }
                    .accessibilityLabel("Ключ доступа")
                }
            }
            .sheet(isPresented: $showKeySetup) {
                KeySetupView(store: settings)
            }
            .onReceive(vpn.$status) { _ in
                banner = nil
            }
        }
    }

    private var needsConfig: Bool { !settings.isConfigured() }

    private var isConnected: Bool {
        vpn.status == .connected || vpn.status == .connecting
    }

    private var mainButtonTitle: String {
        if needsConfig { return "Сначала укажите ключ" }
        switch vpn.status {
        case .connected, .connecting, .reasserting:
            return "Отключить"
        default:
            return "Подключить"
        }
    }

    private var mainButtonColor: Color {
        if needsConfig { return Color.gray }
        switch vpn.status {
        case .connected, .connecting, .reasserting:
            return Color.red.opacity(0.85)
        default:
            return Color.purple.opacity(0.9)
        }
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Статус")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(statusText)
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var statusText: String {
        switch vpn.status {
        case .invalid: return "Не настроено"
        case .disconnected: return "Отключено"
        case .connecting: return "Подключение…"
        case .connected: return "Подключено"
        case .reasserting: return "Переподключение…"
        case .disconnecting: return "Отключение…"
        @unknown default: return "—"
        }
    }

    private func toggleVpn() {
        if vpn.status == .connected || vpn.status == .connecting || vpn.status == .reasserting {
            busy = true
            vpn.disconnect { err in
                busy = false
                if let err { banner = err.localizedDescription }
            }
            return
        }

        guard let cfg = WgConfigBuilder.resolveConfigString(
            privateKey: settings.loadPrivateKey(),
            address: settings.loadAddress()
        ) else {
            banner = "Откройте настройки и сохраните приватный ключ и IP."
            return
        }

        busy = true
        vpn.connect(wgQuickConfig: cfg) { err in
            busy = false
            if let err {
                banner = err.localizedDescription
            }
        }
    }
}

#Preview {
    ContentView()
}
