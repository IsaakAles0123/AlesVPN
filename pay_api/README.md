# Веб-оплата AlesVPN (ЮKassa) и страница с ключом

Сервис `main:app` (FastAPI): создаёт платёж в ЮKassa, после успеха выдаёт доступ по платформе и отдаёт его на `/pay/done?t=…`.

Платформы на `/pay/`:
- **Android** (`platform=android`) — WireGuard (`ales_bot.wg_provision`)
- **iPhone** (`platform=ios`) — Happ / VLESS (`ales_bot.vless_provision` + 3x-ui)

Ссылка `/pay/done?t=...` одноразовая: после первого успешного открытия становится недействительной.

## Переменные окружения

Используется **тот же** `.env`, что и у бота (путь к БД, `WG_*`, `HAPP_*` / `XUI_*`, при необходимости `PAY_API_MODE=1`).

Дополнительно:

| Переменная | Пример | Назначение |
|------------|--------|------------|
| `PAY_API_MODE` | `1` | Позволяет не задавать `BOT_TOKEN` на машине, где крутится только касса (на одном сервере с ботом можно не ставить). |
| `YOOKASSA_SHOP_ID` | из личного кабинета | `shopId` |
| `YOOKASSA_SECRET_KEY` | секретный ключ | `secret key` |
| `PAY_WEBHOOK_TOKEN` | длинный случайный токен | Необязательно. Если задан — для `POST /pay/hook` нужен заголовок `X-Webhook-Token` (ЮKassa сама заголовки не шлёт; можно пробросить через nginx или оставить переменную пустой и полагаться на проверку платежа через API). |
| `PAY_BASE_URL` | `https://alesvpn.ru` | База для `return_url` и ссылок. Без слеша в конце. |

Тарифы на `/pay/`: **75 ₽** / мес, **350 ₽** / 6 мес, **800 ₽** / 12 мес. Ключ только на `/pay/done` или в боте (без выдачи на e-mail).

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

Проверка: `https://alesvpn.ru/pay/` — два раздела (Android / iPhone) с тарифами.
Якоря: `/pay/#android`, `/pay/#ios`.

Редирект после оплаты: в `return_url` передаётся `?ret=<return_token>`. Касса не гарантирует `paymentId` в query; по `ret` заказ всё равно находится в `yookassa_web`.

## Webhook (рекомендуется)

В личном кабинете ЮKassa укажите URL: `https://alesvpn.ru/pay/hook` (POST).  
Тогда, если пользователь **не** вернулся на `return`, выдача ключа догонится событием `payment.succeeded`.

Если задан `PAY_WEBHOOK_TOKEN`, добавьте на nginx для `/pay/hook` строку  
`proxy_set_header X-Webhook-Token "<значение из .env>";` — иначе ЮKassa не сможет передать заголовок сама.  
Если токен **не** задан, webhook от ЮKassa принимается; выдача ключа всё равно только после проверки платежа в API и совпадения суммы с заказом в БД.

При необходимости дополнительно настройте проверку IP-адресов ЮKassa по [документации](https://yookassa.ru/developers/using-api/webhooks).

## Обновление кода на VPS без git

Из корня репозитория на Windows: `.\scripts\deploy-pay-api-to-vps.ps1`, затем на сервере `sudo systemctl restart alesvpn-pay`. Пошагово — в [`web/SERVER-SETUP.md`](../web/SERVER-SETUP.md) (раздел 8).

## Systemd

Готовый unit: [`deploy/alesvpn-pay.service`](deploy/alesvpn-pay.service) (по умолчанию **`User=root`**, тот же SQLite, что бот, без плясок с правами; при **`www-data`** дайте запись на каталог/файл `DB_PATH`).

```bash
sudo cp pay_api/deploy/alesvpn-pay.service /etc/systemd/system/alesvpn-pay.service
# при необходимости поправьте пути в файле, затем:
sudo systemctl daemon-reload
sudo systemctl enable --now alesvpn-pay
```

`WorkingDirectory` — **корень** проекта, где рядом лежат `pay_api/` и `bots/`; `EnvironmentFile` — путь к `.env` бота (или общий).

## База

Таблица `yookassa_web` создаётся в том же SQLite, что `payments` (`DB_PATH`).
