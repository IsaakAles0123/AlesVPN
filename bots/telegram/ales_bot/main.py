"""Точка входа: из каталога bots/telegram выполнить: python -m ales_bot"""

from __future__ import annotations

import asyncio
import logging
import os
import socket
from typing import Any

from aiogram import Bot, Dispatcher
from aiogram.client.default import DefaultBotProperties
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.enums import ParseMode
from aiogram.fsm.storage.memory import MemoryStorage

from ales_bot.config import load_settings
from ales_bot.db import init_db_async
from ales_bot.handlers import admin, cabinet, common, payments
from ales_bot.middlewares import SettingsMiddleware

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


def _http_timeout_seconds() -> float:
    """Таймаут запросов к Telegram API (сек). По умолчанию 120 — реже падает на медленном канале."""
    raw = (os.getenv("TELEGRAM_HTTP_TIMEOUT") or "").strip()
    if not raw:
        return 120.0
    try:
        return max(30.0, float(raw))
    except ValueError:
        return 120.0


def _truthy_env(name: str) -> bool:
    v = (os.getenv(name) or "").strip().lower()
    return v in ("1", "true", "yes", "on")


def _ip_family() -> int | None:
    """v4 / v6 / auto: до api.telegram.org у части VPS работает только одна версия."""
    raw = (os.getenv("TELEGRAM_IP_FAMILY") or "").strip().lower()
    if not raw:
        raw = "v6" if _truthy_env("TELEGRAM_PREFER_IPV6") else "v4"
    if raw in ("v4", "4", "ipv4", "inet"):
        return socket.AF_INET
    if raw in ("v6", "6", "ipv6", "inet6"):
        return socket.AF_INET6
    return None


def _family_label() -> str:
    family = _ip_family()
    if family == socket.AF_INET:
        return ", только IPv4"
    if family == socket.AF_INET6:
        return ", только IPv6"
    return ", IPv4+IPv6"


class AiohttpSessionFamily(AiohttpSession):
    """Фиксирует версию IP для запросов к Telegram.

    В aiogram коннектор создаётся из ``_connector_init`` (см. AiohttpSession), не ``connector=`` в __init__.
    """

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        super().__init__(*args, **kwargs)
        family = _ip_family()
        if family is not None:
            self._connector_init["family"] = family
            self._should_reset_connector = True


def _make_bot_session() -> AiohttpSession:
    """TELEGRAM_PROXY — прокси. TELEGRAM_HTTP_TIMEOUT — таймаут. TELEGRAM_IP_FAMILY — v4/v6/auto."""
    timeout = _http_timeout_seconds()
    raw = (os.getenv("TELEGRAM_PROXY") or "").strip()
    if raw:
        return AiohttpSessionFamily(proxy=raw, timeout=timeout)
    return AiohttpSessionFamily(timeout=timeout)


async def main() -> None:
    settings = load_settings()
    await init_db_async(settings.db_path, wg_first_octet=settings.wg_octet_min)
    logger.info("База оплат: %s", settings.db_path.resolve())
    if settings.wg_auto_provision:
        logger.info(
            "Автовыдача WireGuard: endpoint=%s, скрипт=%s",
            settings.wg_endpoint,
            settings.wg_add_peer_script,
        )
    if settings.happ_auto_provision:
        logger.info(
            "Автовыдача Happ: panels=%s format=%s sub_base=%s host=%s:%s",
            ",".join(f"{p.label}:{p.inbound_id}" for p in settings.xui_panels),
            settings.xui_link_format,
            settings.xui_sub_base_url or "—",
            settings.xui_public_host or "—",
            settings.xui_public_port,
        )
    session = _make_bot_session()
    logger.info(
        "HTTP-клиент Telegram: timeout=%ss%s%s",
        _http_timeout_seconds(),
        ", прокси" if (os.getenv("TELEGRAM_PROXY") or "").strip() else "",
        _family_label(),
    )
    bot = Bot(
        token=settings.bot_token,
        session=session,
        default=DefaultBotProperties(parse_mode=ParseMode.HTML),
    )
    dp = Dispatcher(storage=MemoryStorage())
    dp.update.middleware(SettingsMiddleware(settings))

    dp.include_router(cabinet.router)
    dp.include_router(admin.router)
    dp.include_router(common.router)
    dp.include_router(payments.router)

    # Если у бота был включён webhook, long polling не получает апдейты — сбрасываем.
    await bot.delete_webhook(drop_pending_updates=False)
    me = await bot.get_me()
    logger.info("Telegram: авторизован как @%s (id=%s)", me.username, me.id)
    logger.info("Бот запущен. Оплата: ЮKassa СБП (%s ₽/мес). Ctrl+C — остановка.", settings.price_rub)
    await dp.start_polling(bot)
