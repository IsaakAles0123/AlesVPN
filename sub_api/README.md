# Две ноды в одной подписке Happ (NL рекомендуемый + DE запасной)

## Идея
- Бот создаёт **одного** клиента (один UUID + один subId) на AMS и на FRA.
- `sub_api` на AMS склеивает ответы двух 3x-ui sub в один HTTPS URL.
- В Happ один ключ → два сервера; первым идёт ⭐ NL.

## 1. AMS — Reality не на 443 (нужен nginx)
В 3x-ui AMS: inbound NL → порт **8443** (вместо 443) → сохранить.
Firewall: TCP 8443, 80, 443, 2096, 29292, 22.

Клиентам, кто уже на 443, обновить подписку после смены.

## 2. Подписка в обеих панелях
3x-ui → Settings → Subscription:
- включить Sub
- запомнить URI path (например `/abc123`)
- порт обычно 2096

Inbound remark:
- AMS: `⭐ NL AlesVPN`
- FRA: `DE AlesVPN`

## 3. DNS
`nl.alesvpn.ru` → A → `129.101.122.146`

## 4. sub_api на AMS
```bash
mkdir -p /var/www/alesvpn-app/sub_api
# залить sub_api/* и venv (или общий venv)
pip install -r /var/www/alesvpn-app/sub_api/requirements.txt
cp /var/www/alesvpn-app/sub_api/.env.example /var/www/alesvpn-app/sub_api/.env
nano /var/www/alesvpn-app/sub_api/.env
```

`.env`:
```env
SUB_SOURCES=https://129.101.122.146:2096/ПУТЬ_AMS/,http://186.246.24.7:2096/ПУТЬ_FRA/
SUB_PROFILE_TITLE=AlesVPN
```

Схема (`http`/`https`) — как у панели (`x-ui settings` → «Panel is secure with SSL»).
Локальную AMS-панель указывать по публичному IP: 3x-ui берёт хост для `vless://`
из запроса, поэтому с `127.0.0.1` в ссылке окажется нерабочий `localhost`.
Пути sub: `sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings;" | grep -i sub`.

```bash
cp deploy/alesvpn-sub.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now alesvpn-sub
curl -s http://127.0.0.1:8091/healthz
```

## 5. nginx + certbot на AMS
```bash
# конфиг из web/nginx-alesvpn-ams-sub.conf
certbot --nginx -d nl.alesvpn.ru
nginx -t && systemctl reload nginx
```

Проверка:
```bash
curl -sI "https://nl.alesvpn.ru/s/ТЕСТОВЫЙ_SUBID"
```

## 6. Бот на FRA — .env
```env
HAPP_AUTO_PROVISION=true
XUI_LINK_FORMAT=subscription
XUI_SUB_BASE_URL=https://nl.alesvpn.ru/s

XUI_BASE_URL=https://129.101.122.146:29292/PATH_AMS
XUI_API_TOKEN=токен_AMS
XUI_INBOUND_ID=1
XUI_LABEL=ams-nl

XUI2_BASE_URL=http://127.0.0.1:13478/PATH_FRA
XUI2_API_TOKEN=токен_FRA
XUI2_INBOUND_ID=1
XUI2_LABEL=fra-de
```

Залить обновлённые `config.py`, `vless_provision.py`, `main.py`, `payments.py` → `systemctl restart ales-bot`.

`PATH_AMS` — актуальный `webBasePath` из `x-ui settings` на AMS (при обновлении панели
он меняется, и бот начинает получать 404 на добавление клиента).

## 7. Проверка
`/buy` → iPhone → ссылка `https://nl.alesvpn.ru/s/....` → Happ → два сервера, ⭐ NL сверху.

Склейку удобно проверять на AMS:
```bash
curl -sS "http://127.0.0.1:8091/s/SUBID" | base64 -d
```
Ожидаются две строки с публичными IP нод; `localhost` или одна строка — см. п.4.

Ключи, выданные при неверном `XUI_BASE_URL`, есть только на FRA — их нужно перевыдать.
