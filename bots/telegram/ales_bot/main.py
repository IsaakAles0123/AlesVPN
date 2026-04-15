"""Точка входа: из каталога bots/telegram выполнить: python -m ales_bot"""

from __future__ import annotations

import asyncio
import logging
import os

from aiogram import Bot, Dispatcher
from aiogram.client.default import DefaultBotProperties
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.enums import ParseMode
from aiogram.fsm.storage.memory import MemoryStorage

from ales_bot.config import load_settings
from ales_bot.db import init_db_async
from ales_bot.handlers import common, payments
from ales_bot.middlewares import SettingsMiddleware

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


def _make_bot_session() -> AiohttpSession | None:
    """Если с ПК не открывается api.telegram.org (блокировка), задайте TELEGRAM_PROXY в .env."""
    raw = (os.getenv("TELEGRAM_PROXY") or "").strip()
    if not raw:
        return None
    return AiohttpSession(proxy=raw)


async def main() -> None:
    settings = load_settings()
    await init_db_async(settings.db_path)
    logger.info("База оплат: %s", settings.db_path.resolve())
    session = _make_bot_session()
    if session is not None:
        logger.info("Используется прокси для Telegram API (TELEGRAM_PROXY).")
    bot = Bot(
        token=settings.bot_token,
        session=session,
        default=DefaultBotProperties(parse_mode=ParseMode.HTML),
    )
    dp = Dispatcher(storage=MemoryStorage())
    dp.update.middleware(SettingsMiddleware(settings))

    dp.include_router(common.router)
    dp.include_router(payments.router)

    # Если у бота был включён webhook, long polling не получает апдейты — сбрасываем.
    await bot.delete_webhook(drop_pending_updates=False)
    me = await bot.get_me()
    logger.info("Telegram: авторизован как @%s (id=%s)", me.username, me.id)
    logger.info("Бот запущен. Оплата: Telegram Stars (XTR). Ctrl+C — остановка.")
    await dp.start_polling(bot)
