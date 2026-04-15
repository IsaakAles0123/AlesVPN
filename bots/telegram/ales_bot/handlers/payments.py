"""Оплата: Telegram Stars (XTR)."""

from __future__ import annotations

import logging

from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import LabeledPrice, Message, PreCheckoutQuery

from ales_bot.config import Settings, is_admin

logger = logging.getLogger(__name__)

router = Router(name="payments")


def _stars_prices(settings: Settings) -> list[LabeledPrice]:
    return [
        LabeledPrice(
            label=settings.product_title[:64],
            amount=settings.price_stars,
        )
    ]


@router.message(Command("buy"))
async def cmd_buy(message: Message, bot: Bot, settings: Settings) -> None:
    await bot.send_invoice(
        chat_id=message.chat.id,
        title=settings.product_title[:32],
        description=settings.product_description[:255],
        payload=settings.invoice_payload,
        currency="XTR",
        prices=_stars_prices(settings),
    )


@router.pre_checkout_query()
async def pre_checkout(query: PreCheckoutQuery, bot: Bot, settings: Settings) -> None:
    if query.invoice_payload != settings.invoice_payload:
        await bot.answer_pre_checkout_query(
            query.id,
            ok=False,
            error_message="Неверный счёт. Запросите /buy снова.",
        )
        return
    await bot.answer_pre_checkout_query(query.id, ok=True)


@router.message(F.successful_payment)
async def successful_payment(message: Message, settings: Settings, bot: Bot) -> None:
    sp = message.successful_payment
    if sp is None:
        return

    uid = message.from_user.id if message.from_user else 0
    uname = message.from_user.username if message.from_user else None

    lines = [
        "Новая оплата (Stars)",
        f"user_id: <code>{uid}</code>",
    ]
    if uname:
        lines.append(f"username: @{uname}")
    lines.extend(
        [
            f"total: {sp.total_amount} {sp.currency}",
            f"payload: <code>{sp.invoice_payload}</code>",
            f"telegram_charge_id: <code>{sp.telegram_payment_charge_id}</code>",
        ]
    )
    admin_text = "\n".join(lines)

    await message.answer(
        "Оплата прошла успешно. Администратор скоро отправит данные для входа в VPN.",
    )

    for admin_id in settings.admin_ids:
        try:
            await bot.send_message(admin_id, admin_text)
        except Exception as e:
            logger.warning("Не удалось уведомить админа %s: %s", admin_id, e)


@router.message(Command("admin_ping"))
async def cmd_admin_ping(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    await message.answer("OK, вы в списке админов.")
