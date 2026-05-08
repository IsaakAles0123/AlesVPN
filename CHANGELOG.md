# Changelog

Формат: кратко по версиям приложения **AlesVPN (Android)** и заметным изменениям репозитория.

## [Unreleased]

- Деплой pay_api на VPS без git: [`scripts/deploy-pay-api-to-vps.ps1`](scripts/deploy-pay-api-to-vps.ps1), раздел в [`web/SERVER-SETUP.md`](web/SERVER-SETUP.md).
- Android: release-подпись при наличии `mobile/android/keystore.properties` ([`keystore.properties.example`](mobile/android/keystore.properties.example)); чеклист Play — [`docs/play-console-checklist.txt`](docs/play-console-checklist.txt).
- iOS/Android: сверка `wg_vendor_*` — [`tools/verify-wg-vendor-sync.ps1`](tools/verify-wg-vendor-sync.ps1); инструкция в [`mobile/ios/README.md`](mobile/ios/README.md).
- Очистка истории git от секретов: [`docs/git-secrets-history.txt`](docs/git-secrets-history.txt).
- Сайт: обновлён [`web/assets/alesvpn.apk`](web/assets/alesvpn.apk) (0.1.3 / versionCode 4, debug для лендинга).
- Репозиторий: из индекса git убраны локальная БД `.payments.sqlite`, кеш Python и личные CV; дополнен `.gitignore`; учётный CSV — только шаблон без секретов.
- `pay_api`: webhook ЮKassa без обязательного `PAY_WEBHOOK_TOKEN` (при пустом токене защита через проверку платежа в API); опциональный токен + подсказка в nginx.
- iOS: каркас проекта в `mobile/ios/` (SwiftUI + Packet Tunnel + WireGuardKit, сборка на macOS с Xcode).
- `bots/telegram/`: бот оплаты через **Telegram Stars** (aiogram 3), уведомления админам.
- Дальнейшие правки — по мере работы.

## [0.1.1] — 2026-04

### Android (`mobile/android`)

- WireGuard через `GoBackend`, туннель `aleswg`, настройка ключа и IP в UI (шестерёнка).
- Параметры сервера в `strings.xml` (`wg_vendor_*`); сборка `wg-quick` из ключа клиента и адреса.
- Главный экран: неоновый фон, глобус (без декоративных «сердец» на сфере), карусель серверов, таймер сессии, верхняя панель.
- Репозиторий настроек: `VpnSettingsRepository` (SharedPreferences).

### Сервер (`server/`)

- Скрипты: `wg-add-peer.sh`, `wg-remove-peer.sh`, `wg-backup-wg0.sh` (LF в репозитории, `.gitattributes` для `*.sh`).
- Документация: `README-WG-SCRIPTS.md` (копирование на VPS, cron-бэкап).

### Инструменты

- Скрипты и утилиты под ключи/учёт — в `tools/` и `docs/` (по необходимости).

---

Ранние версии не фиксировались отдельно; при смене `versionName` в `app/build.gradle.kts` добавляйте сюда секцию.
