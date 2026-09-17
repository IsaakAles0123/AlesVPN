#!/usr/bin/env bash
# Goal A: быстрая проверка, что Mac готов к сборке AlesVPN.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0

check() {
  local name=$1
  shift
  if "$@"; then
    echo "OK  $name"
  else
    echo "FAIL $name"
    fail=1
  fi
}

check "xcodebuild in PATH" command -v xcodebuild
check "xcodegen in PATH" command -v xcodegen
check "project.yml exists" test -f project.yml
check "App entitlements" test -f AlesVPN/Resources/App/AlesVPN.entitlements
check "Extension entitlements" test -f AlesVPN/Resources/Extension/PacketTunnel.entitlements
check "PacketTunnelProvider.swift" test -f AlesVPN/Sources/Extension/PacketTunnelProvider.swift
check "WgConfigBuilder.swift" test -f AlesVPN/Sources/App/WgConfigBuilder.swift

if command -v xcodebuild >/dev/null 2>&1; then
  if xcodebuild -checkFirstLaunchStatus >/dev/null 2>&1; then
    echo "OK  Xcode first-launch / license"
  else
    echo "WARN Xcode may need: open Xcode once, or sudo xcodebuild -license accept"
  fi
fi

echo ""
echo "Bundle IDs (must match Developer portal + VPNManager):"
echo "  App:       com.myvpn.app.alesvpn"
echo "  Extension: com.myvpn.app.alesvpn.PacketTunnel"
echo "  App Group: group.com.myvpn.app.alesvpn"
echo ""
echo "Vendor sync (run on any machine with PowerShell from repo root):"
echo "  .\\tools\\verify-wg-vendor-sync.ps1"

exit "$fail"
