# AlesVPN (iOS)

Нативная оболочка под **iPhone**: SwiftUI + **Network Extension (Packet Tunnel)** + **WireGuardKit** из официального репозитория [wireguard-apple](https://github.com/WireGuard/wireguard-apple).

Сборка и подпись возможны **только на macOS с Xcode** (на Windows репозиторий можно хранить, но не скомпилировать).

## Структура

| Путь | Назначение |
|------|------------|
| `AlesVPN/Sources/App/` | Приложение: UI, `UserDefaults`, сборка `wg-quick`, `NETunnelProviderManager` |
| `AlesVPN/Sources/Extension/` | Расширение `PacketTunnelProvider` + WireGuard |
| `AlesVPN/Resources/` | Entitlements, `Info.plist` расширения |
| `project.yml` | Спецификация [XcodeGen](https://github.com/yonaskolb/XcodeGen) |

Параметры сервера (публичный ключ, endpoint, DNS…) заданы в `WgConfigBuilder.swift` — **синхронизируйте с** `mobile/android/app/src/main/res/values/strings.xml`.

## Быстрый старт (Mac)

1. Установите Xcode (рекомендуется актуальная стабильная версия).
2. Установите XcodeGen: `brew install xcodegen`
3. В каталоге `mobile/ios` выполните:
   ```bash
   xcodegen generate
   open AlesVPN.xcodeproj
   ```
4. В Xcode выберите **Team** для подписи (оба target'а: приложение и **PacketTunnel**).
5. Включите capability **Network Extensions** → **Packet Tunnel** для обоих targets (если Xcode не подтянул из entitlements автоматически).
6. Первый запуск VPN: система запросит разрешение на добавление VPN-конфигурации.

## Зависимость WireGuard

`project.yml` подключает SPM-пакет `wireguard-apple` (ветка `master`). При смене версии/ветки проверяйте сборку.

Замечание: часть окружений **симулятора** с WireGuard может вести себя иначе; для проверки туннеля предпочтителен **реальный iPhone**.

## Идентификаторы

- Приложение: `com.myvpn.app.alesvpn`
- Расширение: `com.myvpn.app.alesvpn.PacketTunnel`

Строка `VPNManager.tunnelProviderBundleId` в коде должна совпадать с bundle id target'а расширения.

## Учётная запись разработчика

Для Network Extension на устройстве нужен **платный Apple Developer Program** (или корпоративная команда с соответствующими профилями).

## Что дальше

- Выровнять UI с Android (глобус, карусель серверов) — сейчас минимальный экран.
- Иконка приложения: добавьте `Assets.xcassets` / App Icon в Xcode.
- Локализация строк при необходимости.
