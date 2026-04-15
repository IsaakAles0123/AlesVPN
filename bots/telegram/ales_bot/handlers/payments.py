"""Оплата: Telegram Stars (XTR)."""

from __future__ import annotations

import html
import logging

from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import (
    BufferedInputFile,
    CallbackQuery,
    LabeledPrice,
    Message,
    PreCheckoutQuery,
)

from ales_bot.config import Settings, is_admin
from ales_bot.db import (
    allocate_next_octet_async,
    insert_payment_async,
    list_recent_payments_async,
    payment_count_async,
    update_payment_wg_async,
)
from ales_bot.wg_provision import provision_after_payment

logger = logging.getLogger(__name__)

router = Router(name="payments")


def _stars_prices(settings: Settings) -> list[LabeledPrice]:
    return [
        LabeledPrice(
            label=settings.product_title[:64],
            amount=settings.price_stars,
        )
    ]


async def _send_invoice(
    chat_id: int,
    bot: Bot,
    settings: Settings,
) -> None:
    await bot.send_invoice(
        chat_id=chat_id,
        title=settings.product_title[:32],
        description=settings.product_description[:255],
        payload=settings.invoice_payload,
        currency="XTR",
        prices=_stars_prices(settings),
    )


@router.message(Command("buy"))
async def cmd_buy(message: Message, bot: Bot, settings: Settings) -> None:
    await _send_invoice(message.chat.id, bot, settings)


@router.callback_query(F.data == "buy")
async def callback_buy(query: CallbackQuery, bot: Bot, settings: Settings) -> None:
    await query.answer()
    if query.message is None:
        return
    await _send_invoice(query.message.chat.id, bot, settings)


@router.pre_checkout_query()
async def pre_checkout(query: PreCheckoutQuery, bot: Bot, settings: Settings) -> None:
    if query.invoice_payload != settings.invoice_payload:
        await bot.answer_pre_checkout_query(
            query.id,
            ok=False,
            error_message="Неверный счёт. Запросите оплату снова (/buy или кнопка).",
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

    inserted = await insert_payment_async(
        settings.db_path,
        telegram_charge_id=sp.telegram_payment_charge_id,
        user_id=uid,
        username=uname,
        amount=sp.total_amount,
        currency=sp.currency,
        invoice_payload=sp.invoice_payload,
    )
    if not inserted:
        logger.warning(
            "Повторное событие оплаты (charge_id уже в базе): %s",
            sp.telegram_payment_charge_id,
        )
        await message.answer(
            "Этот платёж уже был учтён ранее. Если нужен ключ — напишите в поддержку.",
        )
        return

    uid_line = f"\n\nВаш ID: <code>{uid}</code> — сохраните, если поддержка попросит."
    wg_admin_extra = ""

    if settings.wg_auto_provision:
        try:
            octet = await allocate_next_octet_async(
                settings.db_path,
                settings.wg_octet_min,
                settings.wg_octet_max,
            )
            res = await provision_after_payment(settings, octet)
            await update_payment_wg_async(
                settings.db_path,
                sp.telegram_payment_charge_id,
                wg_public_key=res.public_key,
                wg_address=res.address_cidr,
                wg_provision_error=None,
            )
            paste_esc = html.escape(res.paste_two_lines)
            await message.answer(
                "Оплата принята. Данные для приложения <b>AlesVPN</b>:\n\n"
                f"<pre>{paste_esc}</pre>\n\n"
                "В приложении откройте настройку ключа и вставьте <b>две строки</b> "
                "(приватный ключ и адрес). Ниже — полный файл для импорта в WireGuard."
                + uid_line,
            )
            await message.answer_document(
                BufferedInputFile(
                    res.conf_text.encode("utf-8"),
                    filename="alesvpn.conf",
                ),
                caption="Импорт в WireGuard или сохраните как текст конфигурации.",
            )
            wg_admin_extra = (
                f"\nWG: <code>{html.escape(res.address_cidr)}</code> "
                f"pub <code>{html.escape(res.public_key)}</code>"
            )
        except Exception as e:
            logger.exception("Автовыдача WireGuard не удалась")
            err_t = str(e)[:800]
            await update_payment_wg_async(
                settings.db_path,
                sp.telegram_payment_charge_id,
                wg_public_key=None,
                wg_address=None,
                wg_provision_error=err_t,
            )
            await message.answer(
                "Оплата прошла успешно. Автовыдача ключа сейчас недоступна — "
                "администратор отправит доступ вручную."
                + uid_line,
            )
            wg_admin_extra = f"\n<b>Ошибка WG</b>: {html.escape(err_t)}"
    else:
        await message.answer(
            "Оплата прошла успешно. Администратор скоро отправит данные для входа в VPN."
            + uid_line,
        )

    user_link = f'<a href="tg://user?id={uid}">профиль</a>' if uid else "—"
    lines = [
        "Новая оплата (Stars)",
        f"user_id: <code>{uid}</code> ({user_link})",
    ]
    if uname:
        lines.append(f"username: @{html.escape(uname)}")
    lines.extend(
        [
            f"total: {sp.total_amount} {sp.currency}",
            f"payload: <code>{html.escape(sp.invoice_payload)}</code>",
            f"telegram_charge_id: <code>{html.escape(sp.telegram_payment_charge_id)}</code>",
        ]
    )
    if wg_admin_extra:
        lines.append(wg_admin_extra)
    admin_text = "\n".join(lines)

    for admin_id in settings.admin_ids:
        try:
            await bot.send_message(admin_id, admin_text)
        except Exception as e:
            logger.warning("Не удалось уведомить админа %s: %s", admin_id, e)


@router.message(Command("stats"))
async def cmd_stats(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    total = await payment_count_async(settings.db_path)
    rows = await list_recent_payments_async(settings.db_path, limit=15)
    if not rows:
        await message.answer(f"Записей об оплатах пока нет. Всего в базе: {total}.")
        return
    lines = [f"<b>Оплаты</b> (всего: {total})\n"]
    for r in rows:
        uname = f"@{html.escape(r.username)}" if r.username else "—"
        ch = html.escape(r.telegram_charge_id)
        wg = ""
        if r.wg_address:
            wg = f" | WG {html.escape(r.wg_address)}"
        lines.append(
            f"{html.escape(r.created_at)} | <code>{r.user_id}</code> {uname} | "
            f"{r.amount} {html.escape(r.currency)}{wg}\n<code>{ch}</code>"
        )
    text = "\n".join(lines)
    if len(text) > 3900:
        text = text[:3890] + "…"
    await message.answer(text)


@router.message(Command("admin_ping"))
async def cmd_admin_ping(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    await message.answer("OK, вы в списке админов.")
