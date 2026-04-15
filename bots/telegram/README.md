# AlesVPN — Telegram-бот оплаты

Минимальный бот на **Python 3.10+** и **aiogram 3**: счёт в **Telegram Stars (XTR)** — не нужен банк на старте; пользователь платит звёздами внутри Telegram.

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

## Оплата в рублях (ЮKassa и др.)

Нужен **платёжный провайдер**, подключённый к боту через BotFather (получите `provider_token`). Тогда можно добавить второй сценарий (`send_invoice` с `currency="RUB"` и этим токеном). Текущая версия сознательно только **Stars** — проще для первого запуска.

## Что дальше

- Выдача ключа автоматически: связка с VPS API или скриптом `wg-add-peer` (отдельная задача, нужна безопасность).
- Учёт платежей в БД (SQLite) вместо только уведомлений.
- Запуск на VPS: `systemd` unit или Docker.

## Юридически

Продажа цифровых услуг и налоги — на стороне владельца проекта; бот только техническая обвязка.
