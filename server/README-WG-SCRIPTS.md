# Скрипты управления peer на WireGuard (wg0)

Файлы: `wg-add-peer.sh`, `wg-remove-peer.sh`

## Копирование на VPS

С Windows (PowerShell), из папки репозитория `server`:

```powershell
scp .\wg-add-peer.sh .\wg-remove-peer.sh root@ВАШ_IP:/usr/local/bin/
```

На сервере:

```bash
sudo chmod +x /usr/local/bin/wg-add-peer.sh /usr/local/bin/wg-remove-peer.sh
```

По умолчанию используется интерфейс `wg0` и файл `/etc/wireguard/wg0.conf`.  
Переопределение:

```bash
export WG_IFACE=wg0
export WG_CONF=/etc/wireguard/wg0.conf
```

## Добавить клиента

Нужен **публичный** ключ клиента и его **AllowedIPs** (обычно один /32).

```bash
sudo wg-add-peer.sh 'ПУБЛИЧНЫЙ_КЛЮЧ_КЛИЕНТА' '10.8.0.7/32' 25
```

Третий аргумент — `PersistentKeepalive` (секунды), по умолчанию `25`.

## Удалить клиента

```bash
sudo wg-remove-peer.sh 'ПУБЛИЧНЫЙ_КЛЮЧ_КЛИЕНТА'
```

Перед изменением конфига создаётся бэкап `wg0.conf.bak.ГГГГММДДЧЧММСС`.

## Зависимости

- `wireguard-tools` (`wg`, `wg-quick`)
- `python3` (только для `wg-remove-peer.sh`, правка конфига)
- Права root

## Если `wg syncconf` недоступен

Скрипты выполняют `wg-quick down` / `wg-quick up` — кратковременный обрыв всех текущих сессий на этом интерфейсе.

## Связка с Telegram-ботом (`bots/telegram`)

После оплаты Stars бот может сам вызывать `wg-add-peer.sh` (см. переменные `WG_*` в `bots/telegram/README.md`). Удобно держать скрипт в `/usr/local/bin/` и запускать **unit systemd бота от root** на том же VPS, где крутится `wg0`.
