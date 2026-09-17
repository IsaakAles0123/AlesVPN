# Goal A — туннель WireGuard на одном iPhone (Development)

Полный чеклист из плана. Шаги **1–3** делает владелец аккаунта/железа; **4–5** — на Mac после них. Этот репозиторий уже подготовлен под пункты 4–5.

## Критерий готово

На реальном iPhone: приложение ставится, VPN-профиль создаётся, туннель UP, трафик через тот же WG-сервер, что на Android.

---

## 1. Apple Developer Program ($99/год)

Без платной программы **Network Extension (Packet Tunnel)** на устройстве не работает.

1. Зайди на [developer.apple.com/programs](https://developer.apple.com/programs/).
2. Enroll с Apple ID (лучше отдельный «рабочий»).
3. Оплата из РФ (часто не проходит карта РФ):
   - иностранная карта (Bybit / банк соседней страны), или
   - Apple Gift Card региона аккаунта.
4. Дождись активации Membership (иногда до суток).
5. В [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list):
   - App ID: `com.myvpn.app.alesvpn` — capability **Network Extensions** → Packet Tunnel.
   - App ID: `com.myvpn.app.alesvpn.PacketTunnel` — то же.
   - App Group: `group.com.myvpn.app.alesvpn` (привязать к обоим App ID).

Статус в репо: **runbook готов**; оплату выполняет только владелец Apple ID.

---

## 2. Mac с Xcode

Сборка iOS VPN **только на macOS**.

Варианты:

| Вариант | Когда |
|---------|--------|
| Свой Mac | Лучший |
| Mac знакомого на вечер | Разовая установка на твой iPhone |
| Облачный Mac (MacStadium, etc.) | Если нет железа |

На Mac:

```bash
xcode-select --install   # если нужно
brew install xcodegen
# Xcode из App Store, один раз открыть и принять license
sudo xcodebuild -license accept
```

Затем из репозитория:

```bash
cd mobile/ios
./scripts/bootstrap-mac.sh
```

Статус в репо: **скрипт bootstrap готов**; Mac должен предоставить ты.

---

## 3. UDID тестового iPhone

1. Подключи iPhone кабелем к Mac **или** найди UDID: Настройки → Основные → Об этом устройстве → идентификатор (на новых iOS: касание номера → UDID).
2. Developer portal → **Devices** → зарегистрируй iPhone (имя + UDID).
3. Profiles: Development profile для обоих App ID, включающий это устройство  
   (при Automatic Signing в Xcode Xcode часто делает это сам, если Team выбран и устройство подключено).

Статус в репо: **инструкция готова**; UDID регистрирует владелец устройства.

---

## 4. Сборка, подпись, установка

На Mac, в каталоге `mobile/ios`:

```bash
./scripts/bootstrap-mac.sh
open AlesVPN.xcodeproj
```

В Xcode:

1. Target **AlesVPN** и **PacketTunnel** → Signing & Capabilities → Team (твой Developer Team).
2. Убедись, что есть **Network Extensions** → Packet Tunnel и **App Groups** → `group.com.myvpn.app.alesvpn`.
3. Выбери физический iPhone (не симулятор).
4. Product → Run (⌘R).
5. На iPhone: доверие разработчику при необходимости (Настройки → Основные → VPN и управление устройством).

Автоматическая проверка окружения Mac:

```bash
./scripts/check-mac-ready.sh
```

---

## 5. Проверка туннеля (= Android)

Перед тестом на Windows (или любом ПК с PowerShell) из корня репо:

```powershell
.\tools\verify-wg-vendor-sync.ps1
```

Ожидается: `OK: WgVendorConfig matches strings.xml`.

На iPhone:

1. Шестерёнка → вставь **тот же** приватный ключ и IP, что на Android (две строки от продавца).
2. Подключить → статус «Подключено».
3. Сравни: IP в VPN, доступ в интернет, при желании `whatismyip` / свой внутренний `10.8.0.x`.

Если ошибка профиля VPN / extension — проверь bundle id расширения:  
`com.myvpn.app.alesvpn.PacketTunnel` (= `VPNManager.tunnelProviderBundleId`).

---

## Явно не входит в Goal A

- TestFlight / App Store  
- Sideloadly / free Apple ID  
- Полный UI как на Android (космос)  
- Подключение через Happ (это другой стек: Xray, не этот план)

---

## Файлы в репозитории

| Файл | Роль |
|------|------|
| [project.yml](project.yml) | XcodeGen: app + PacketTunnel + entitlements |
| [scripts/bootstrap-mac.sh](scripts/bootstrap-mac.sh) | `xcodegen generate` + открытие проекта |
| [scripts/check-mac-ready.sh](scripts/check-mac-ready.sh) | Проверка Xcode / xcodegen / подписи |
| [../../tools/verify-wg-vendor-sync.ps1](../../tools/verify-wg-vendor-sync.ps1) | Синхрон vendor-параметров с Android |
