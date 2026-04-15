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


def _buy_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text="💫 Оплатить (Stars)",
                    callback_data="buy",
                ),
            ],
        ],
    )


@router.message(CommandStart())
async def cmd_start(message: Message, settings: Settings) -> None:
    await message.answer(
        "Привет! Это бот оплаты <b>AlesVPN</b>.\n\n"
        "Команды:\n"
        "/buy — счёт на оплату (Telegram Stars)\n"
        "/help — справка и ваш ID для поддержки",
        reply_markup=_buy_keyboard(),
    )


@router.message(Command("help"))
async def cmd_help(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await message.answer(
        f"Ваш Telegram ID: <code>{uid}</code> — сообщите его в поддержке, "
        "если нужно найти ваш платёж.\n\n"
        "После оплаты через Stars администратор выдаст ключ для приложения.\n"
        "Если что-то пошло не так — укажите ID платежа из чека Telegram."
        + _support_html(settings),
    )
