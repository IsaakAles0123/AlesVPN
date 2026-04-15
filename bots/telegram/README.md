# AlesVPN — Telegram-бот оплаты

Минимальный бот на **Python 3.10+** и **aiogram 3**: счёт в **Telegram Stars (XTR)** — не нужен банк на старте; пользователь платит звёздами внутри Telegram.

## Если ошибка `Cannot connect to host api.telegram.org`

С **России и части сетей** прямой доступ к `api.telegram.org` с ПК **блокируется** — бот не стартует или «молчит».

**Варианты:**

1. **Запуск бота на VPS** за границей (у вас уже есть сервер с VPN — туда же можно положить `bots/telegram`, venv и `systemd`). С ПК блокировка не мешает.
2. **Прокси на этом компьютере** (Clash, v2ray и т.п. с локальным портом). В `.env` добавьте, например:
   ```env
   TELEGRAM_PROXY=socks5://127.0.0.1:7890
   ```
   Порт и тип (`socks5://` / `http://`) возьмите из настроек своего клиента. После изменения: `pip install -r requirements.txt` (нужен пакет `aiohttp-socks`) и снова `python -m ales_bot`.

Проверка без бота: в браузере открывается ли `https://api.telegram.org` (если нет — нужен VPN/прокси или VPS).

## Быстрый старт (Windows / Linux / Mac)

```bash
cd bots/telegram
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # Linux/macOS
pip install -r requirements.txt
copy .env.example .env          # заполните BOT_TOKEN и ADMIN_IDS
python -m ales_bot
```

Токен бота: [@BotFather](https://t.me/BotFather) → `/newbot`.

**ADMIN_IDS** — ваш числовой `user id` (например [@userinfobot](https://t.me/userinfobot)). После оплаты бот шлёт уведомление каждому админу.

## Переменные `.env`

| Переменная | Обязательно | Описание |
|------------|-------------|----------|
| `BOT_TOKEN` | да | Токен от BotFather |
| `ADMIN_IDS` | нет | Через запятую: кого уведомлять о платеже |
| `PRICE_STARS` | нет | Цена в Stars (по умолчанию 50) |
| `PRODUCT_TITLE` | нет | Заголовок счёта |
| `PRODUCT_DESCRIPTION` | нет | Описание в счёте |
| `TELEGRAM_PROXY` | нет | Прокси до `api.telegram.org`, если с сервера/ПК API недоступен |
| `DB_PATH` | нет | Путь к SQLite с учётом оплат (по умолчанию `.payments.sqlite` в рабочей папке) |
| `SUPPORT_USERNAME` | нет | Логин поддержки в Telegram без `@` — показывается в `/help` |
| `WG_AUTO_PROVISION` | нет | `true` / `1` — после Stars генерировать ключ, вызывать `wg-add-peer.sh`, прислать конфиг пользователю |
| `WG_SERVER_PUBLIC_KEY` | при автовыдаче | Публичный ключ **сервера** WireGuard (как `wg_vendor_server_public_key` в Android) |
| `WG_ENDPOINT` | при автовыдаче | `IP:порт` сервера (как в приложении) |
| `WG_ADD_PEER_SCRIPT` | нет | Путь к `wg-add-peer.sh` (по умолчанию `/usr/local/bin/wg-add-peer.sh`) |
| `WG_SUBNET_PREFIX` | нет | Первые три октета, например `10.8.0` |
| `WG_OCTET_MIN` / `WG_OCTET_MAX` | нет | Диапазон последнего октета клиента (по умолчанию 20–250) |

После оплаты запись пишется в базу; админы могут смотреть последние оплаты командой **`/stats`**.

### Автовыдача WireGuard

Работает только на **Linux VPS**, где уже поднят `wg0` и установлены **`wireguard-tools`** (`wg`, `wg-quick`). На том же хосте, что и бот (или с тем же доступом к `wg0.conf`), должен лежать скрипт [`server/wg-add-peer.sh`](../../server/wg-add-peer.sh) — скопируйте в `/usr/local/bin/`, `chmod +x`, запускайте unit бота **от root** (как в примере `deploy/ales-bot.service`), иначе `wg-add-peer` не сможет править `/etc/wireguard/`.

Бот: генерирует пару ключей (`wg genkey` / `wg pubkey`), выделяет следующий IP из пула `10.8.0.N` (учёт в SQLite), вызывает `wg-add-peer.sh <pub> N/32`, отправляет пользователю **две строки** для вставки в AlesVPN и файл `alesvpn.conf`.

Параметры `WG_SERVER_PUBLIC_KEY`, `WG_ENDPOINT`, DNS и AllowedIPs должны **совпадать** с тем, что зашито в приложении (`mobile/android/.../values/strings.xml`), иначе туннель не поднимется.

## Запуск на VPS 24/7 (без ПК)

Идея: каталог бота лежит на сервере (например `/opt/alesvpn-telegram`), процесс поднимает **systemd** — после перезагрузки VPS бот стартует сам.

### 1. Скопировать проект на сервер

**Не копируйте папку `.venv`** с ПК на Linux: она огромная, а venv с **Windows на Ubuntu не подходит** — на сервере всегда делайте `python3 -m venv .venv` и `pip install -r requirements.txt` заново.

С **Windows** (PowerShell), только исходники:

```powershell
scp -r C:\MyVPN\bots\telegram\ales_bot C:\MyVPN\bots\telegram\requirements.txt C:\MyVPN\bots\telegram\README.md C:\MyVPN\bots\telegram\deploy root@ВАШ_IP:/opt/alesvpn-telegram/
```

Если уже скопировали всё включая `.venv` — на сервере выполните `rm -rf /opt/alesvpn-telegram/.venv` и создайте venv заново (см. ниже).

Старый вариант «всё подряд» (долго и с лишним venv):

```powershell
scp -r C:\MyVPN\bots\telegram root@ВАШ_IP:/opt/alesvpn-telegram
```

На сервере (SSH):

```bash
cd /opt/alesvpn-telegram
apt update && apt install -y python3 python3-venv python3-pip
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
nano .env
```

В **`.env`** на сервере — те же `BOT_TOKEN`, `ADMIN_IDS`, при необходимости **`TELEGRAM_PROXY` убрать** (с VPS Telegram API обычно доступен без прокси). Права:

```bash
chmod 600 .env
```

### 2. Systemd

Пример unit-файла: [`deploy/ales-bot.service`](deploy/ales-bot.service). Пути уже под `/opt/alesvpn-telegram`.

```bash
sudo cp /opt/alesvpn-telegram/deploy/ales-bot.service /etc/systemd/system/ales-bot.service
sudo systemctl daemon-reload
sudo systemctl enable --now ales-bot
sudo systemctl status ales-bot
```

Логи:

```bash
journalctl -u ales-bot -f
```

Правки кода на сервере — снова `scp` или `git pull`, затем `sudo systemctl restart ales-bot`.

### 3. ПК

Можно **остановить** локальный `python -m ales_bot` (Ctrl+C), чтобы не было двух копий с одним токеном — иначе оба дерутся за `getUpdates`. На VPS должен работать **один** процесс.

## Оплата в рублях (ЮKassa и др.)

Нужен **платёжный провайдер**, подключённый к боту через BotFather (получите `provider_token`). Тогда можно добавить второй сценарий (`send_invoice` с `currency="RUB"` и этим токеном). Текущая версия сознательно только **Stars** — проще для первого запуска.

## Что дальше

- Экспорт оплат в CSV, уведомления в канал, отзыв ключа по команде — по необходимости.
- Учёт оплат и пул IP — в SQLite (`DB_PATH` + таблица `wg_meta`); делайте бэкап на VPS.
- Docker-обёртка (по желанию).

## Юридически

Продажа цифровых услуг и налоги — на стороне владельца проекта; бот только техническая обвязка.
