import SwiftUI

struct KeySetupView: View {
    @ObservedObject var store: VPNSettingsStore
    @Environment(\.dismiss) private var dismiss

    @State private var privateKey: String = ""
    @State private var address: String = ""
    @State private var pasteBox: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Приватный ключ", text: $privateKey, axis: .vertical)
                    TextField("IP в VPN (например 10.8.0.2)", text: $address)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Данные клиента")
                }

                Section {
                    TextField("Вставьте две строки (ключ и IP)", text: $pasteBox, axis: .vertical)
                    Button("Разобрать вставку") {
                        if let pair = VPNSettingsStore.parseSellerMessage(pasteBox) {
                            privateKey = pair.0
                            address = pair.1
                        }
                    }
                } header: {
                    Text("Быстрая вставка")
                }

                Section {
                    Button("Сохранить") {
                        store.save(privateKey: privateKey, address: address)
                        dismiss()
                    }
                }
            }
            .navigationTitle("Ключ доступа")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Закрыть") { dismiss() }
                }
            }
            .onAppear {
                privateKey = store.loadPrivateKey()
                address = store.loadAddress()
            }
        }
    }
}
