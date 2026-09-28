import html

from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message

from ales_bot.config import Settings

router = Router(name="common")


def _support_html(settings: Settings) -> str:
    if not settings.support_username:
        return ""
    u = html.escape(settings.support_username)
    return f"\n\nПоддержка: <a href=\"https://t.me/{u}\">@{u}</a>"


@router.message(Command("help"))
async def cmd_help(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await message.answer(
        f"Ваш Telegram ID: <code>{uid}</code> — сообщите его в поддержке, "
        "если нужно найти ваш платёж.\n\n"
        f"Оплата: <b>{settings.price_rub} ₽</b> / месяц через СБП (ЮKassa).\n"
        "После оплаты:\n"
        + (
            "• Android — ссылка подписки https для Happ\n"
            if settings.android_delivery == "happ"
            else "• Android — ключ WireGuard в чат\n"
        )
        + "• iPhone — ссылка подписки https для Happ\n\n"
        "Команды: /start — кабинет, /buy — оплата. "
        "Внизу экрана кнопка «Главное меню».\n"
        "Если что-то пошло не так — укажите ID платежа ЮKassa."
        + _support_html(settings),
    )
