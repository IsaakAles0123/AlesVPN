"""Оплата: Telegram Stars (XTR)."""

from __future__ import annotations

import html
import logging
import uuid

from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import (
    BufferedInputFile,
    CallbackQuery,
    LabeledPrice,
    Message,
    PreCheckoutQuery,
    User,
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

_ADMIN_FREE_PAYLOAD = "admin_free_v1"


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


async def _deliver_purchase(
    message: Message,
    bot: Bot,
    settings: Settings,
    *,
    uid: int,
    uname: str | None,
    telegram_charge_id: str,
    amount: int,
    currency: str,
    invoice_payload: str,
    free_admin: bool,
) -> None:
    uid_line = f"\n\nВаш ID: <code>{uid}</code> — сохраните, если поддержка попросит."
    wg_admin_extra = ""

    paid_title = (
        "Выдача для <b>администратора</b> (без списания Stars)."
        if free_admin
        else "Оплата принята. Данные для приложения <b>AlesVPN</b>:"
    )

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
                telegram_charge_id,
                wg_public_key=res.public_key,
                wg_address=res.address_cidr,
                wg_provision_error=None,
            )
        except Exception as e:
            logger.exception("Автовыдача WireGuard не удалась (ключ или БД)")
            err_t = str(e)[:800]
            await update_payment_wg_async(
                settings.db_path,
                telegram_charge_id,
                wg_public_key=None,
                wg_address=None,
                wg_provision_error=err_t,
            )
            await message.answer(
                (
                    "Автовыдача ключа сейчас недоступна — проверьте логи и wg на сервере."
                    if free_admin
                    else "Оплата прошла успешно. Автовыдача ключа сейчас недоступна — "
                    "администратор отправит доступ вручную."
                )
                + uid_line,
            )
            wg_admin_extra = f"\n<b>Ошибка выдачи / БД</b>: {html.escape(err_t)}"
        else:
            paste_esc = html.escape(res.paste_two_lines)
            try:
                await message.answer(
                    f"{paid_title}\n\n"
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
            except Exception as e:
                err_t = str(e)[:800]
                logger.exception("Ключ в БД, но отправка в Telegram не удалась (таймаут/сеть)")
                wg_admin_extra = (
                    f"\nWG: <code>{html.escape(res.address_cidr)}</code> "
                    f"pub <code>{html.escape(res.public_key)}</code>\n"
                    f"<b>Ошибка Telegram API</b>: {html.escape(err_t)}"
                )
                try:
                    await message.answer(
                        "Ключ уже создан, но доставка в чат сорвалась (сеть или Telegram). "
                        "Повторите /start — напишите в поддержку, пришлём вручную."
                        + uid_line,
                    )
                except Exception:
                    logger.warning("Не удалось ни ключ с файлом, ни короткое уведомление")
            else:
                wg_admin_extra = (
                    f"\nWG: <code>{html.escape(res.address_cidr)}</code> "
                    f"pub <code>{html.escape(res.public_key)}</code>"
                )
    else:
        await message.answer(
            (
                "Для администратора счёт не требуется. Ключ выдаётся вручную."
                if free_admin
                else "Оплата прошла успешно. Администратор скоро отправит данные для входа в VPN."
            )
            + uid_line,
        )

    user_link = f'<a href="tg://user?id={uid}">профиль</a>' if uid else "—"
    header = (
        "Бесплатная выдача (админ)"
        if free_admin
        else "Новая оплата (Stars)"
    )
    lines = [
        header,
        f"user_id: <code>{uid}</code> ({user_link})",
    ]
    if uname:
        lines.append(f"username: @{html.escape(uname)}")
    lines.extend(
        [
            f"total: {amount} {currency}",
            f"payload: <code>{html.escape(invoice_payload)}</code>",
            f"telegram_charge_id: <code>{html.escape(telegram_charge_id)}</code>",
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


async def _try_admin_free_buy(
    message: Message,
    bot: Bot,
    settings: Settings,
    *,
    actor: User | None = None,
) -> bool:
    """Если пользователь — админ, выдаём доступ за 0 Stars. Возвращает True, если обработано."""
    user = actor or message.from_user
    uid = user.id if user else 0
    if not uid or not is_admin(uid, settings):
        return False

    uname = user.username if user else None
    charge_id = f"admin_free_{uid}_{uuid.uuid4().hex[:16]}"

    inserted = await insert_payment_async(
        settings.db_path,
        telegram_charge_id=charge_id,
        user_id=uid,
        username=uname,
        amount=0,
        currency="XTR",
        invoice_payload=_ADMIN_FREE_PAYLOAD,
    )
    if not inserted:
        await message.answer("Не удалось записать выдачу. Попробуйте /buy ещё раз.")
        return True

    await _deliver_purchase(
        message,
        bot,
        settings,
        uid=uid,
        uname=uname,
        telegram_charge_id=charge_id,
        amount=0,
        currency="XTR",
        invoice_payload=_ADMIN_FREE_PAYLOAD,
        free_admin=True,
    )
    return True


@router.message(Command("buy"))
async def cmd_buy(message: Message, bot: Bot, settings: Settings) -> None:
    if await _try_admin_free_buy(message, bot, settings):
        return
    await _send_invoice(message.chat.id, bot, settings)


@router.callback_query(F.data == "buy")
async def callback_buy(query: CallbackQuery, bot: Bot, settings: Settings) -> None:
    await query.answer()
    if query.message is None:
        return
    if query.from_user and is_admin(query.from_user.id, settings):
        if await _try_admin_free_buy(
            query.message,
            bot,
            settings,
            actor=query.from_user,
        ):
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

    await _deliver_purchase(
        message,
        bot,
        settings,
        uid=uid,
        uname=uname,
        telegram_charge_id=sp.telegram_payment_charge_id,
        amount=sp.total_amount,
        currency=sp.currency,
        invoice_payload=sp.invoice_payload,
        free_admin=False,
    )


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
