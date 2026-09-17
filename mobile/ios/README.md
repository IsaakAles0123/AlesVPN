# AlesVPN (iOS)

Нативная оболочка под **iPhone**: SwiftUI + **Network Extension (Packet Tunnel)** + **WireGuardKit** из официального репозитория [wireguard-apple](https://github.com/WireGuard/wireguard-apple).

Сборка и подпись возможны **только на macOS с Xcode** (на Windows репозиторий можно хранить, но не скомпилировать).

**Goal A (туннель на одном устройстве):** см. пошаговый чеклист **[GOAL_A.md](GOAL_A.md)**.

## Структура

| Путь | Назначение |
|------|------------|
| `AlesVPN/Sources/App/` | Приложение: UI, `UserDefaults`, сборка `wg-quick`, `NETunnelProviderManager` |
| `AlesVPN/Sources/Extension/` | Расширение `PacketTunnelProvider` + WireGuard |
| `AlesVPN/Resources/` | Entitlements, `Info.plist` расширения |
| `project.yml` | Спецификация [XcodeGen](https://github.com/yonaskolb/XcodeGen) |
| `scripts/` | `bootstrap-mac.sh`, `check-mac-ready.sh` |

Параметры сервера (публичный ключ, endpoint, DNS…) заданы в `WgConfigBuilder.swift` — **синхронизируйте с** `mobile/android/app/src/main/res/values/strings.xml`.

## Быстрый старт (Mac)

```bash
cd mobile/ios
./scripts/check-mac-ready.sh
./scripts/bootstrap-mac.sh
```

1. В Xcode выберите **Team** для подписи (оба target'а: приложение и **PacketTunnel**).
2. Capabilities: **Network Extensions → Packet Tunnel**, **App Groups** → `group.com.myvpn.app.alesvpn`.
3. Запуск на **физический iPhone** (симулятор для туннеля ненадёжен).
4. Первый запуск VPN: система запросит разрешение на добавление VPN-конфигурации.

## Зависимость WireGuard

`project.yml` подключает SPM-пакет `wireguard-apple` (ветка `master`). При смене версии/ветки проверяйте сборку.

## Идентификаторы

- Приложение: `com.myvpn.app.alesvpn`
- Расширение: `com.myvpn.app.alesvpn.PacketTunnel`
- App Group: `group.com.myvpn.app.alesvpn`

Строка `VPNManager.tunnelProviderBundleId` должна совпадать с bundle id расширения.

## Учётная запись разработчика

Для Network Extension на устройстве нужен **платный Apple Developer Program**. Подробности и оплата из РФ — в [GOAL_A.md](GOAL_A.md).

## Синхронизация параметров сервера с Android

```powershell
.\tools\verify-wg-vendor-sync.ps1
```

Код 0 — значения в `WgConfigBuilder.swift` и XML совпадают.

## Что дальше (после Goal A)

- TestFlight / App Store
- UI паритет с Android
- Иконка: `Assets.xcassets` / App Icon в Xcode
