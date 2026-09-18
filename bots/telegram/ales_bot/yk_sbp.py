"""Создание платежей ЮKassa (СБП) для Telegram-бота."""

from __future__ import annotations

import asyncio
import logging
import uuid
from dataclasses import dataclass
from typing import Any

from yookassa import Configuration, Payment

from ales_bot.config import Settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class YkSbpPayment:
    id: str
    confirmation_url: str
    status: str
    amount_value: str


def _configure(settings: Settings) -> None:
    shop = (settings.yookassa_shop_id or "").strip()
    secret = (settings.yookassa_secret_key or "").strip()
    if not shop or not secret:
        raise RuntimeError("Задайте YOOKASSA_SHOP_ID и YOOKASSA_SECRET_KEY в .env")
    Configuration.account_id = shop
    Configuration.secret_key = secret


def _payment_id(obj: Any) -> str | None:
    return getattr(obj, "id", None) or (obj.get("id") if isinstance(obj, dict) else None)


def _confirmation_url(obj: Any) -> str | None:
    conf = getattr(obj, "confirmation", None)
    if conf is None and isinstance(obj, dict):
        conf = obj.get("confirmation")
    if conf is None:
        return None
    url = getattr(conf, "confirmation_url", None)
    if url:
        return str(url)
    if isinstance(conf, dict):
        return conf.get("confirmation_url")
    return None


def _status(obj: Any) -> str:
    s = getattr(obj, "status", None) or (obj.get("status") if isinstance(obj, dict) else None)
    return str(s or "")


def create_sbp_payment_sync(
    settings: Settings,
    *,
    user_id: int,
    platform: str,
    description: str,
) -> YkSbpPayment:
    _configure(settings)
    amount = settings.price_rub_value
    return_url = settings.yk_return_url
    idem = str(uuid.uuid4())
    payload = {
        "amount": {"value": amount, "currency": "RUB"},
        "capture": True,
        "description": description[:128],
        "payment_method_data": {"type": "sbp"},
        "confirmation": {
            "type": "redirect",
            "return_url": return_url,
        },
        "metadata": {
            "source": "telegram_bot",
            "telegram_user_id": str(user_id),
            "platform": platform,
            "plan": "monthly",
        },
    }
    y_p = Payment.create(payload, idem)
    pid = _payment_id(y_p)
    url = _confirmation_url(y_p)
    if not pid or not url:
        raise RuntimeError("ЮKassa не вернула id или confirmation_url")
    return YkSbpPayment(
        id=pid,
        confirmation_url=url,
        status=_status(y_p) or "pending",
        amount_value=amount,
    )


def find_payment_sync(settings: Settings, payment_id: str) -> Any:
    _configure(settings)
    return Payment.find_one(payment_id)


async def create_sbp_payment(
    settings: Settings,
    *,
    user_id: int,
    platform: str,
    description: str,
) -> YkSbpPayment:
    return await asyncio.to_thread(
        create_sbp_payment_sync,
        settings,
        user_id=user_id,
        platform=platform,
        description=description,
    )


async def find_payment(settings: Settings, payment_id: str) -> Any:
    return await asyncio.to_thread(find_payment_sync, settings, payment_id)


def payment_succeeded(obj: Any) -> bool:
    return _status(obj) == "succeeded"


def payment_canceled(obj: Any) -> bool:
    return _status(obj) in ("canceled", "cancelled")
