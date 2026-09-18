"""Оплата: Telegram Stars (XTR). Android = WireGuard, iOS = Happ/VLESS."""

from __future__ import annotations

import html
import logging
import uuid

from aiogram import Bot, F, Router
from aiogram.filters import Command
from aiogram.types import (
    BufferedInputFile,
    CallbackQuery,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    LabeledPrice,
    Message,
    PreCheckoutQuery,
    User,
)

from ales_bot.config import Settings, is_admin, normalize_platform
from ales_bot.db import (
    allocate_next_octet_async,
    insert_payment_async,
    list_recent_payments_async,
    payment_count_async,
    update_payment_vless_async,
    update_payment_wg_async,
)
from ales_bot.vless_provision import provision_vless_after_payment
from ales_bot.wg_provision import provision_after_payment

logger = logging.getLogger(__name__)

router = Router(name="payments")

_PAYLOAD_ANDROID = "alesvpn_android_v1"
_PAYLOAD_IOS = "alesvpn_ios_v1"
_ADMIN_FREE_ANDROID = "admin_free_android_v1"
_ADMIN_FREE_IOS = "admin_free_ios_v1"


def _platform_from_payload(payload: str) -> str:
    p = (payload or "").strip().lower()
    if "ios" in p or "happ" in p:
        return "ios"
    return "android"


def _payload_for_platform(platform: str, *, free_admin: bool) -> str:
    if platform == "ios":
        return _ADMIN_FREE_IOS if free_admin else _PAYLOAD_IOS
    return _ADMIN_FREE_ANDROID if free_admin else _PAYLOAD_ANDROID


def _stars_prices(settings: Settings, label: str) -> list[LabeledPrice]:
    return [LabeledPrice(label=label[:64], amount=settings.price_stars)]


def _platform_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text="Android — WireGuard",
                    callback_data="buy_android",
                ),
            ],
            [
                InlineKeyboardButton(
                    text="iPhone — Happ",
                    callback_data="buy_ios",
                ),
            ],
        ],
    )


async def _send_invoice(
    chat_id: int,
    bot: Bot,
    settings: Settings,
    platform: str,
) -> None:
    if platform == "ios":
        title = "AlesVPN iPhone"
        desc = "Доступ Happ (VLESS). После оплаты придёт ссылка vless://."
        payload = _PAYLOAD_IOS
    else:
        title = "AlesVPN Android"
        desc = "Доступ WireGuard для приложения AlesVPN."
        payload = _PAYLOAD_ANDROID
    await bot.send_invoice(
        chat_id=chat_id,
        title=title[:32],
        description=desc[:255],
        payload=payload,
        currency="XTR",
        prices=_stars_prices(settings, title),
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
    platform: str,
) -> None:
    uid_line = f"\n\nВаш ID: <code>{uid}</code> — сохраните, если поддержка попросит."
    admin_extra = ""
    platform = normalize_platform(platform) or "android"

    if platform == "ios":
        paid_title = (
            "Выдача Happ для <b>администратора</b>."
            if free_admin
            else "Оплата принята. Ссылка для <b>Happ (iPhone)</b>:"
        )
        if settings.happ_auto_provision:
            try:
                res = await provision_vless_after_payment(
                    settings,
                    plan_code="stars",
                    email_prefix=f"tg{uid}",
                )
                await update_payment_vless_async(
                    settings.db_path,
                    telegram_charge_id,
                    vless_link=res.link,
                    vless_uuid=res.uuid,
                    wg_provision_error=None,
                )
            except Exception as e:
                logger.exception("Автовыдача Happ не удалась")
                err_t = str(e)[:800]
                await update_payment_vless_async(
                    settings.db_path,
                    telegram_charge_id,
                    vless_link=None,
                    vless_uuid=None,
                    wg_provision_error=err_t,
                )
                await message.answer(
                    (
                        "Автовыдача Happ сейчас недоступна — проверьте 3x-ui."
                        if free_admin
                        else "Оплата прошла. Автовыдача Happ недоступна — "
                        "администратор отправит ссылку вручную."
                    )
                    + uid_line,
                )
                admin_extra = f"\n<b>Ошибка Happ</b>: {html.escape(err_t)}"
            else:
                link_esc = html.escape(res.link)
                await message.answer(
                    f"{paid_title}\n\n"
                    f"<code>{link_esc}</code>\n\n"
                    "1) App Store → <b>Happ</b>\n"
                    "2) Скопируйте ссылку → Happ → импорт из буфера\n"
                    "3) Включите VPN"
                    + uid_line,
                    disable_web_page_preview=True,
                )
                admin_extra = (
                    f"\nHapp email=<code>{html.escape(res.email)}</code> "
                    f"uuid=<code>{html.escape(res.uuid)}</code>"
                )
        else:
            await message.answer(
                (
                    "Для админа: Happ выдаётся вручную (HAPP_AUTO_PROVISION выкл)."
                    if free_admin
                    else "Оплата прошла. Администратор отправит ссылку Happ вручную."
                )
                + uid_line,
            )
    else:
        paid_title = (
            "Выдача WireGuard для <b>администратора</b>."
            if free_admin
            else "Оплата принята. Данные для <b>AlesVPN Android</b>:"
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
                logger.exception("Автовыдача WireGuard не удалась")
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
                        "Автовыдача WG сейчас недоступна."
                        if free_admin
                        else "Оплата прошла. Автовыдача ключа недоступна — "
                        "администратор отправит доступ вручную."
                    )
                    + uid_line,
                )
                admin_extra = f"\n<b>Ошибка WG</b>: {html.escape(err_t)}"
            else:
                paste_esc = html.escape(res.paste_two_lines)
                await message.answer(
                    f"{paid_title}\n\n"
                    f"<pre>{paste_esc}</pre>\n\n"
                    "Вставьте <b>две строки</b> в AlesVPN или импортируйте .conf."
                    + uid_line,
                )
                await message.answer_document(
                    BufferedInputFile(
                        res.conf_text.encode("utf-8"),
                        filename="alesvpn.conf",
                    ),
                    caption="Импорт в WireGuard.",
                )
                admin_extra = (
                    f"\nWG: <code>{html.escape(res.address_cidr)}</code> "
                    f"pub <code>{html.escape(res.public_key)}</code>"
                )
        else:
            await message.answer(
                (
                    "WG выдаётся вручную (WG_AUTO_PROVISION выкл)."
                    if free_admin
                    else "Оплата прошла. Администратор отправит ключ WireGuard вручную."
                )
                + uid_line,
            )

    user_link = f'<a href="tg://user?id={uid}">профиль</a>' if uid else "—"
    header = "Бесплатная выдача (админ)" if free_admin else "Новая оплата (Stars)"
    lines = [
        header,
        f"platform: <b>{html.escape(platform)}</b>",
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
    if admin_extra:
        lines.append(admin_extra)
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
    platform: str,
    *,
    actor: User | None = None,
) -> bool:
    user = actor or message.from_user
    uid = user.id if user else 0
    if not uid or not is_admin(uid, settings):
        return False

    plat = normalize_platform(platform) or "android"
    uname = user.username if user else None
    charge_id = f"admin_free_{plat}_{uid}_{uuid.uuid4().hex[:12]}"
    payload = _payload_for_platform(plat, free_admin=True)

    inserted = await insert_payment_async(
        settings.db_path,
        telegram_charge_id=charge_id,
        user_id=uid,
        username=uname,
        amount=0,
        currency="XTR",
        invoice_payload=payload,
        platform=plat,
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
        invoice_payload=payload,
        free_admin=True,
        platform=plat,
    )
    return True


@router.message(Command("buy"))
async def cmd_buy(message: Message, settings: Settings) -> None:
    await message.answer(
        "Выберите платформу:\n"
        "• <b>Android</b> — ключ WireGuard для AlesVPN\n"
        "• <b>iPhone</b> — ссылка Happ (vless://)",
        reply_markup=_platform_keyboard(),
    )


@router.callback_query(F.data.in_({"buy", "buy_android", "buy_ios"}))
async def callback_buy(query: CallbackQuery, bot: Bot, settings: Settings) -> None:
    await query.answer()
    if query.message is None:
        return
    data = query.data or "buy_android"
    if data == "buy":
        await query.message.answer(
            "Выберите платформу:",
            reply_markup=_platform_keyboard(),
        )
        return
    platform = "ios" if data == "buy_ios" else "android"
    if query.from_user and is_admin(query.from_user.id, settings):
        if await _try_admin_free_buy(
            query.message,
            bot,
            settings,
            platform,
            actor=query.from_user,
        ):
            return
    await _send_invoice(query.message.chat.id, bot, settings, platform)


@router.pre_checkout_query()
async def pre_checkout(query: PreCheckoutQuery, bot: Bot, settings: Settings) -> None:
    ok_payloads = {_PAYLOAD_ANDROID, _PAYLOAD_IOS, settings.invoice_payload}
    if query.invoice_payload not in ok_payloads:
        await bot.answer_pre_checkout_query(
            query.id,
            ok=False,
            error_message="Неверный счёт. Запросите оплату снова (/buy).",
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
    platform = _platform_from_payload(sp.invoice_payload)

    inserted = await insert_payment_async(
        settings.db_path,
        telegram_charge_id=sp.telegram_payment_charge_id,
        user_id=uid,
        username=uname,
        amount=sp.total_amount,
        currency=sp.currency,
        invoice_payload=sp.invoice_payload,
        platform=platform,
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
        platform=platform,
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
        wg = f" | WG {html.escape(r.wg_address)}" if r.wg_address else ""
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
