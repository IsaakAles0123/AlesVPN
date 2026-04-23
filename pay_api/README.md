# Веб-оплата AlesVPN (ЮKassa) и страница с ключом

Сервис `main:app` (FastAPI): создаёт платёж в ЮKassa, после успеха вызывает ту же логику WireGuard, что и Telegram-бот (`ales_bot.wg_provision`), и отдаёт ключ на `/pay/done?t=…`.

## Переменные окружения

Используется **тот же** `.env`, что и у бота (путь к БД, `WG_*`, при необходимости `PAY_API_MODE=1`).

Дополнительно:

| Переменная | Пример | Назначение |
|------------|--------|------------|
| `PAY_API_MODE` | `1` | Позволяет не задавать `BOT_TOKEN` на машине, где крутится только касса (на одном сервере с ботом можно не ставить). |
| `YOOKASSA_SHOP_ID` | из личного кабинета | `shopId` |
| `YOOKASSA_SECRET_KEY` | секретный ключ | `secret key` |
| `PAY_BASE_URL` | `https://alesvpn.ru` | База для `return_url` и ссылок в письмах. Без слеша в конце. |

## Запуск

Из **корня** репозитория (рядом с папками `pay_api` и `bots`):

```bash
export PYTHONPATH=./bots/telegram
export PAY_API_MODE=1
python3 -m uvicorn pay_api.main:app --host 127.0.0.1 --port 8008
```

Рекомендация: **1 worker** (`--workers 1`), чтобы не плодить гонки при выдаче октета.

## Nginx

В `web/nginx-alesvpn-site.conf` есть блок `location /pay/`. **Тот же блок** перенесите в сервер, где настроен HTTPS (после Certbot), и перезагрузите Nginx.

Проверка: `https://alesvpn.ru/pay/` — список кнопок-тарифов.

## Webhook (рекомендуется)

В личном кабинете ЮKassa укажите URL: `https://alesvpn.ru/pay/hook` (POST).  
Тогда, если пользователь **не** вернулся на `return`, выдача ключа догонится событием `payment.succeeded`.

При необходимости настройте проверку IP-адресов ЮKassa по [документации](https://yookassa.ru/developers/using-api/webhooks).

## Systemd (пример)

`WorkingDirectory` — корень клона `MyVPN` на сервере; `EnvironmentFile` — ваш `.env`.

```ini
[Unit]
Description=AlesVPN YooKassa
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/var/www/alesvpn-app
EnvironmentFile=-/opt/alesvpn-telegram/.env
Environment=PYTHONPATH=/var/www/alesvpn-app/bots/telegram
Environment=PAY_API_MODE=1
ExecStart=/var/www/alesvpn-app/venv/bin/python -m uvicorn pay_api.main:app --host 127.0.0.1 --port 8008
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

(Поправьте пути: `WorkingDirectory` — **корень** клона, где лежат `pay_api/` и `bots/`.)

## База

Таблица `yookassa_web` создаётся в том же SQLite, что `payments` (`DB_PATH`).
