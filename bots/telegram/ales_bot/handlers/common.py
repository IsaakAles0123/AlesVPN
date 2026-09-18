import html

from aiogram import Router
from aiogram.filters import Command, CommandStart
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup, Message

from ales_bot.config import Settings

router = Router(name="common")


def _support_html(settings: Settings) -> str:
    if not settings.support_username:
        return ""
    u = html.escape(settings.support_username)
    return f"\n\nПоддержка: <a href=\"https://t.me/{u}\">@{u}</a>"


def _platform_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text="Android — WireGuard",
                    callback_data="buy_android",
                ),
            ],
            [
                InlineKeyboardButton(
                    text="iPhone — Happ",
                    callback_data="buy_ios",
                ),
            ],
        ],
    )


@router.message(CommandStart())
async def cmd_start(message: Message, settings: Settings) -> None:
    await message.answer(
        "Привет! Это бот оплаты <b>AlesVPN</b>.\n\n"
        f"Месяц — <b>{settings.price_rub} ₽</b>, оплата через <b>СБП</b>.\n"
        "Выберите платформу:\n"
        "• <b>Android</b> — ключ WireGuard (приложение AlesVPN)\n"
        "• <b>iPhone</b> — ссылка для Happ (vless://)\n\n"
        "Команды: /buy — оплата, /help — справка",
        reply_markup=_platform_keyboard(),
    )


@router.message(Command("help"))
async def cmd_help(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await message.answer(
        f"Ваш Telegram ID: <code>{uid}</code> — сообщите его в поддержке, "
        "если нужно найти ваш платёж.\n\n"
        f"Оплата: <b>{settings.price_rub} ₽</b> / месяц через СБП (ЮKassa).\n"
        "После оплаты:\n"
        "• Android — ключ WireGuard в чат\n"
        "• iPhone — ссылка vless:// для Happ\n"
        "Если что-то пошло не так — укажите ID платежа ЮKassa."
        + _support_html(settings),
    )
