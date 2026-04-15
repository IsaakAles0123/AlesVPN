"""Точка входа: из каталога bots/telegram выполнить: python -m ales_bot"""

from __future__ import annotations

import asyncio
import logging

from aiogram import Bot, Dispatcher
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.fsm.storage.memory import MemoryStorage

from ales_bot.config import load_settings
from ales_bot.handlers import common, payments
from ales_bot.middlewares import SettingsMiddleware

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


async def main() -> None:
    settings = load_settings()
    bot = Bot(
        token=settings.bot_token,
        default=DefaultBotProperties(parse_mode=ParseMode.HTML),
    )
    dp = Dispatcher(storage=MemoryStorage())
    dp.update.middleware(SettingsMiddleware(settings))

    dp.include_router(common.router)
    dp.include_router(payments.router)

    logger.info("Бот запущен. Оплата: Telegram Stars (XTR). Ctrl+C — остановка.")
    await dp.start_polling(bot)
