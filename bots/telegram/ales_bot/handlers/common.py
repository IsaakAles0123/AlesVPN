from aiogram import Router
from aiogram.filters import Command, CommandStart
from aiogram.types import Message

router = Router(name="common")


@router.message(CommandStart())
async def cmd_start(message: Message) -> None:
    await message.answer(
        "Привет! Это бот оплаты <b>AlesVPN</b>.\n\n"
        "Команды:\n"
        "/buy — оплатить доступ (Telegram Stars)\n"
        "/help — справка",
    )


@router.message(Command("help"))
async def cmd_help(message: Message) -> None:
    await message.answer(
        "После оплаты через Stars администратор свяжется и выдаст ключ для приложения.\n"
        "Если что-то пошло не так — напишите в поддержку, указав ID платежа из чека."
    )
