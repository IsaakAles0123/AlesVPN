"""Загрузка настроек из переменных окружения (.env)."""

from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


def _parse_admin_ids(raw: str) -> tuple[int, ...]:
    out: list[int] = []
    for part in raw.split(","):
        part = part.strip()
        if part.isdigit():
            out.append(int(part))
    return tuple(out)


@dataclass(frozen=True)
class Settings:
    bot_token: str
    admin_ids: tuple[int, ...]
    price_stars: int
    product_title: str
    product_description: str
    payment_provider_token: str | None
    invoice_payload: str = "alesvpn_sub_v1"


def load_settings() -> Settings:
    token = (os.getenv("BOT_TOKEN") or "").strip()
    if not token:
        raise RuntimeError("Задайте BOT_TOKEN в .env (см. .env.example)")

    price = int(os.getenv("PRICE_STARS") or "50")
    if price < 1:
        raise ValueError("PRICE_STARS должен быть >= 1")

    ppt = os.getenv("PAYMENT_PROVIDER_TOKEN")
    ppt = ppt.strip() if ppt else None
    if ppt == "":
        ppt = None

    return Settings(
        bot_token=token,
        admin_ids=_parse_admin_ids(os.getenv("ADMIN_IDS") or ""),
        price_stars=price,
        product_title=(os.getenv("PRODUCT_TITLE") or "AlesVPN").strip(),
        product_description=(
            os.getenv("PRODUCT_DESCRIPTION") or "Доступ к VPN."
        ).strip(),
        payment_provider_token=ppt,
    )


def is_admin(user_id: int, settings: Settings) -> bool:
    return user_id in settings.admin_ids
