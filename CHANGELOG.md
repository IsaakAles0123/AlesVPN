# Changelog

Формат: кратко по версиям приложения **AlesVPN (Android)** и заметным изменениям репозитория.

## [Unreleased]

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
