# Веб-оплата AlesVPN (ЮKassa) и страница с ключом

Сервис `main:app` (FastAPI): создаёт платёж в ЮKassa, после успеха вызывает ту же логику WireGuard, что и Telegram-бот (`ales_bot.wg_provision`), и отдаёт ключ на `/pay/done?t=…`.
Ссылка `/pay/done?t=...` одноразовая: после первого успешного открытия становится недействительной.

## Переменные окружения

Используется **тот же** `.env`, что и у бота (путь к БД, `WG_*`, при необходимости `PAY_API_MODE=1`).

Дополнительно:

| Переменная | Пример | Назначение |
|------------|--------|------------|
| `PAY_API_MODE` | `1` | Позволяет не задавать `BOT_TOKEN` на машине, где крутится только касса (на одном сервере с ботом можно не ставить). |
| `YOOKASSA_SHOP_ID` | из личного кабинета | `shopId` |
| `YOOKASSA_SECRET_KEY` | секретный ключ | `secret key` |
| `PAY_WEBHOOK_TOKEN` | длинный случайный токен | Обязательный токен для `POST /pay/hook`, передаётся в заголовке `X-Webhook-Token`. Без него webhook отклоняется. |
| `PAY_BASE_URL` | `https://alesvpn.ru` | База для `return_url` и ссылок в письмах. Без слеша в конце. |
| `PAY_FIRST_RUB_BYPASS_EMAILS` | `a@b.ru, c@d.ru` | Список e-mail (через запятую), для которых **не** действует лимит «1 ₽ — один раз». По умолчанию список пустой. |

## Акция «1 ₽ — первый месяц»

- На `/pay/` для тарифа **1 ₽** нужно ввести **e-mail** (GET-форма ведёт на `/pay/buy?plan=first&email=…`).
- После успешной оплаты (`payment.succeeded`) e-mail (кроме bypass) **один раз** записывается в таблицу `pay_first_rub_redeemed` в той же SQLite, что `yookassa_web`; повторно оформить 1 ₽ с тем же адресом нельзя.
- Повторные покупки — тариф **99 ₽** и остальные. Учёт **только по введённому e-mail** (тот же способ, что и защита «один раз»; при необходимости позже можно связать с Telegram-ботом отдельно).

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

Редирект после оплаты: в `return_url` передаётся `?ret=<return_token>`. Касса не гарантирует `paymentId` в query; по `ret` заказ всё равно находится в `yookassa_web`.

## Webhook (рекомендуется)

В личном кабинете ЮKassa укажите URL: `https://alesvpn.ru/pay/hook` (POST).  
Тогда, если пользователь **не** вернулся на `return`, выдача ключа догонится событием `payment.succeeded`.

Отправляйте в webhook заголовок `X-Webhook-Token: <PAY_WEBHOOK_TOKEN>`.
При необходимости дополнительно настройте проверку IP-адресов ЮKassa по [документации](https://yookassa.ru/developers/using-api/webhooks).

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
