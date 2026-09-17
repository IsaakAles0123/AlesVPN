#!/usr/bin/env bash
# Goal A: сгенерировать Xcode-проект и открыть его.
# Запуск на macOS из каталога mobile/ios: ./scripts/bootstrap-mac.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "Нужен XcodeGen: brew install xcodegen" >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Нужен Xcode (App Store) и CLI: xcode-select --install" >&2
  exit 1
fi

echo "==> xcodegen generate"
xcodegen generate

if [[ ! -d AlesVPN.xcodeproj ]]; then
  echo "AlesVPN.xcodeproj не создан" >&2
  exit 1
fi

echo "==> open AlesVPN.xcodeproj"
open AlesVPN.xcodeproj
echo "Дальше в Xcode: Team на AlesVPN и PacketTunnel → Run на устройство."
