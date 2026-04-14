#!/usr/bin/env bash
#
# Добавляет peer в работающий wg0 и в /etc/wireguard/wg0.conf (устойчиво к перезагрузке).
#
# Использование:
#   sudo ./wg-add-peer.sh <public_key> <allowed_ips> [persistent_keepalive]
#
# Пример:
#   sudo ./wg-add-peer.sh 'nZs3C99LKqlg6dA3tNdn3eUXgG0EnTmNjeDU+T+CZSI=' '10.8.0.7/32' 25
#
set -euo pipefail

IFACE="${WG_IFACE:-wg0}"
CONF="${WG_CONF:-/etc/wireguard/${IFACE}.conf}"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Запускайте от root: sudo $0 $*" >&2
  exit 1
fi

if [[ $# -lt 2 ]]; then
  echo "Использование: $0 <public_key> <allowed_ips> [persistent_keepalive]" >&2
  exit 1
fi

PUBKEY="$1"
ALLOWED="$2"
KEEPALIVE="${3:-25}"

if ! command -v wg >/dev/null 2>&1; then
  echo "Не найден wg (установите wireguard-tools)" >&2
  exit 1
fi

if [[ ! -f "$CONF" ]]; then
  echo "Файл не найден: $CONF" >&2
  exit 1
fi

if grep -qF "PublicKey = ${PUBKEY}" "$CONF" 2>/dev/null; then
  echo "Этот PublicKey уже есть в $CONF" >&2
  exit 1
fi

BACKUP="${CONF}.bak.$(date +%Y%m%d%H%M%S)"
cp -a "$CONF" "$BACKUP"
echo "Бэкап: $BACKUP"

{
  echo ""
  echo "[Peer]"
  echo "# added $(date -Iseconds) by wg-add-peer.sh"
  echo "PublicKey = ${PUBKEY}"
  echo "AllowedIPs = ${ALLOWED}"
  echo "PersistentKeepalive = ${KEEPALIVE}"
} >>"$CONF"

if ! command -v wg-quick >/dev/null 2>&1; then
  echo "wg-quick не найден — примените конфиг вручную: wg-quick down $IFACE && wg-quick up $IFACE" >&2
  exit 1
fi

# Применить без полного рестарта (если поддерживается)
if wg syncconf "$IFACE" <(wg-quick strip "$IFACE") 2>/dev/null; then
  echo "OK: peer добавлен, конфиг применён (wg syncconf)."
else
  echo "wg syncconf не сработал — перезапуск интерфейса..."
  wg-quick down "$IFACE" 2>/dev/null || true
  wg-quick up "$IFACE"
  echo "OK: peer добавлен, интерфейс перезапущен."
fi

echo "Проверка: wg show $IFACE"
