#!/usr/bin/env bash
#
# Удаляет peer из wg0 и из /etc/wireguard/wg0.conf.
#
# Использование:
#   sudo ./wg-remove-peer.sh <public_key>
#
# Пример:
#   sudo ./wg-remove-peer.sh 'nZs3C99LKqlg6dA3tNdn3eUXgG0EnTmNjeDU+T+CZSI='
#
set -euo pipefail

IFACE="${WG_IFACE:-wg0}"
CONF="${WG_CONF:-/etc/wireguard/${IFACE}.conf}"

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Запускайте от root: sudo $0 $*" >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "Использование: $0 <public_key>" >&2
  exit 1
fi

PUBKEY="$1"

if ! command -v wg >/dev/null 2>&1; then
  echo "Не найден wg (установите wireguard-tools)" >&2
  exit 1
fi

if [[ ! -f "$CONF" ]]; then
  echo "Файл не найден: $CONF" >&2
  exit 1
fi

# Убрать из работающего интерфейса (если peer не был — wg вернёт ошибку, это нормально)
if wg set "$IFACE" peer "$PUBKEY" remove 2>/dev/null; then
  echo "Peer убран из работающего $IFACE."
else
  echo "Примечание: peer не был активен на $IFACE (или уже снят)."
fi

BACKUP="${CONF}.bak.$(date +%Y%m%d%H%M%S)"
cp -a "$CONF" "$BACKUP"
echo "Бэкап: $BACKUP"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Нужен python3 для правки $CONF" >&2
  exit 1
fi

python3 - "$CONF" "$PUBKEY" <<'PY'
import re
import sys

path, pubkey = sys.argv[1], sys.argv[2].strip()
with open(path, encoding="utf-8") as f:
    content = f.read()

# Удаляем сегменты, начинающиеся с [Peer], где встречается этот PublicKey
parts = re.split(r"(?=\n\[Peer\])", content, flags=re.MULTILINE)
if len(parts) < 2:
    print("В конфиге нет блоков [Peer].", file=sys.stderr)
    sys.exit(1)

out = [parts[0]]
removed = False
for seg in parts[1:]:
    m = re.search(r"PublicKey\s*=\s*(\S+)", seg)
    if m and m.group(1).strip() == pubkey:
        removed = True
        continue
    out.append(seg)

if not removed:
    print(f"PublicKey не найден в {path}: {pubkey[:20]}...", file=sys.stderr)
    sys.exit(1)

new_content = "".join(out)
with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(new_content)
PY

if ! command -v wg-quick >/dev/null 2>&1; then
  echo "wg-quick не найден — перезапустите интерфейс вручную." >&2
  exit 0
fi

if wg syncconf "$IFACE" <(wg-quick strip "$IFACE") 2>/dev/null; then
  echo "OK: peer удалён из конфига, применено (wg syncconf)."
else
  echo "wg syncconf не сработал — перезапуск интерфейса..."
  wg-quick down "$IFACE" 2>/dev/null || true
  wg-quick up "$IFACE"
  echo "OK: peer удалён, интерфейс перезапущен."
fi

echo "Проверка: wg show $IFACE"
