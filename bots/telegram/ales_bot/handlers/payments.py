"""Оплата: ЮKassa СБП. Выдача Happ/VLESS; Android — по ANDROID_DELIVERY."""

from __future__ import annotations

import asyncio
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
    Message,
    User,
)

from ales_bot.config import Settings, is_admin, normalize_platform
from ales_bot.db import (
    allocate_next_octet_async,
    get_bot_yk_order_async,
    get_subscription_async,
    insert_bot_yk_order_async,
    insert_payment_async,
    set_bot_yk_order_status_async,
    update_payment_vless_async,
    update_payment_wg_async,
    upsert_subscription_async,
)
from ales_bot.handlers.cabinet import back_keyboard, fmt_expiry
from ales_bot.happ_guide import send_happ_access
from ales_bot.vless_provision import ExistingClient, provision_vless_after_payment
from ales_bot.wg_provision import provision_after_payment
from ales_bot.yk_sbp import (
    create_sbp_payment,
    find_payment,
    payment_canceled,
    payment_succeeded,
)

logger = logging.getLogger(__name__)

router = Router(name="payments")

_PAYLOAD_ANDROID = "alesvpn_android_sbp_v1"
_PAYLOAD_IOS = "alesvpn_ios_sbp_v1"
_ADMIN_FREE_ANDROID = "admin_free_android_v1"
_ADMIN_FREE_IOS = "admin_free_ios_v1"

# не дублировать выдачу при гонке poll + кнопка
_fulfill_locks: dict[str, asyncio.Lock] = {}


def _lock_for(yk_id: str) -> asyncio.Lock:
    lock = _fulfill_locks.get(yk_id)
    if lock is None:
        lock = asyncio.Lock()
        _fulfill_locks[yk_id] = lock
    return lock


def _payload_for_platform(platform: str, *, free_admin: bool) -> str:
    if platform == "ios":
        return _ADMIN_FREE_IOS if free_admin else _PAYLOAD_IOS
    return _ADMIN_FREE_ANDROID if free_admin else _PAYLOAD_ANDROID


def _uses_happ(platform: str, settings: Settings) -> bool:
    return platform == "ios" or settings.android_delivery == "happ"


def _platform_keyboard(settings: Settings) -> InlineKeyboardMarkup:
    android_text = (
        "Android — Happ"
        if settings.android_delivery == "happ"
        else "Android — WireGuard"
    )
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text=android_text,
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


def _pay_keyboard(yk_id: str, pay_url: str, price_rub: int) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [
                InlineKeyboardButton(
                    text=f"Оплатить {price_rub} ₽",
                    url=pay_url,
                ),
            ],
            [
                InlineKeyboardButton(
                    text="Проверить оплату",
                    callback_data=f"yk_check:{yk_id}",
                ),
            ],
        ],
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
    extra_key: bool = False,
) -> None:
    uid_line = f"\n\nВаш ID: <code>{uid}</code> — сохраните, если поддержка попросит."
    admin_extra = ""
    platform = normalize_platform(platform) or "android"
    plan_code = "admin_free" if free_admin else "monthly"

    if _uses_happ(platform, settings):
        device = "iPhone" if platform == "ios" else "Android"
        paid_title = (
            "Выдача Happ для <b>администратора</b>."
            if free_admin
            else f"Оплата принята. Ссылка <b>подписки</b> для Happ ({device}):"
        )
        if settings.happ_auto_provision:
            sub = await get_subscription_async(settings.db_path, uid)
            existing = (
                ExistingClient(
                    uuid=sub.client_uuid,
                    email=sub.email,
                    sub_id=sub.sub_id,
                    expiry_ms=sub.expiry_ms,
                )
                if sub is not None and not extra_key
                else None
            )
            try:
                nick = (uname or "").lstrip("@").strip()
                res = await provision_vless_after_payment(
                    settings,
                    plan_code=plan_code,
                    email_prefix=nick or f"tg{uid}",
                    existing=existing,
                    # доп. ключ — суффикс, чтобы не столкнуться с ником основной подписки
                    stable_email=bool(nick) and not extra_key,
                )
                if not extra_key:
                    await upsert_subscription_async(
                        settings.db_path,
                        user_id=uid,
                        username=uname,
                        sub_id=res.sub_id,
                        client_uuid=res.uuid,
                        email=res.email,
                        link=res.link,
                        expiry_ms=res.expiry_ms,
                        plan_code=plan_code,
                        device_limit=settings.xui_device_limit,
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
                if extra_key:
                    await send_happ_access(
                        message,
                        link=res.link,
                        title="🔑 <b>Дополнительный ключ</b> (админ). Кабинет не менялся.",
                        uid_line=uid_line,
                        platform=platform,
                        reply_markup=back_keyboard(),
                    )
                elif existing is not None:
                    await message.answer(
                        "🔄 <b>Подписка продлена</b>\n"
                        f"Действует до: <b>{fmt_expiry(res.expiry_ms)}</b> (МСК)\n\n"
                        "В приложении ничего менять не нужно — ссылка та же."
                        + uid_line,
                        reply_markup=back_keyboard(),
                    )
                else:
                    await send_happ_access(
                        message,
                        link=res.link,
                        title=paid_title,
                        uid_line=uid_line,
                        platform=platform,
                        reply_markup=back_keyboard(),
                    )
                admin_extra = (
                    f"\nHapp email=<code>{html.escape(res.email)}</code> "
                    f"sub=<code>{html.escape(res.sub_id)}</code> "
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
    header = "Бесплатная выдача (админ)" if free_admin else "Новая оплата (СБП)"
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
            f"payment_id: <code>{html.escape(telegram_charge_id)}</code>",
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
    extra_key: bool = False,
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
        currency="RUB",
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
        currency="RUB",
        invoice_payload=payload,
        free_admin=True,
        platform=plat,
        extra_key=extra_key,
    )
    return True


async def _fulfill_yk_payment(
    message: Message,
    bot: Bot,
    settings: Settings,
    *,
    yk_id: str,
    notify_pending: bool = True,
) -> str:
    """pending | canceled | already | ok | error"""
    async with _lock_for(yk_id):
        order = await get_bot_yk_order_async(settings.db_path, yk_id)
        if order is None:
            return "error"
        if order.status == "succeeded":
            return "already"

        try:
            y_p = await find_payment(settings, yk_id)
        except Exception:
            logger.exception("YooKassa find %s", yk_id)
            return "error"

        if payment_canceled(y_p):
            await set_bot_yk_order_status_async(settings.db_path, yk_id, "canceled")
            return "canceled"
        if not payment_succeeded(y_p):
            if notify_pending:
                await message.answer(
                    "Оплата ещё не поступила. Откройте ссылку СБП, оплатите, "
                    "затем нажмите «Проверить оплату» снова.",
                )
            return "pending"

        payload = _payload_for_platform(order.platform, free_admin=False)
        inserted = await insert_payment_async(
            settings.db_path,
            telegram_charge_id=yk_id,
            user_id=order.user_id,
            username=order.username,
            amount=settings.price_rub,
            currency="RUB",
            invoice_payload=payload,
            platform=order.platform,
        )
        if not inserted:
            await set_bot_yk_order_status_async(settings.db_path, yk_id, "succeeded")
            return "already"

        await set_bot_yk_order_status_async(settings.db_path, yk_id, "succeeded")
        await _deliver_purchase(
            message,
            bot,
            settings,
            uid=order.user_id,
            uname=order.username,
            telegram_charge_id=yk_id,
            amount=settings.price_rub,
            currency="RUB",
            invoice_payload=payload,
            free_admin=False,
            platform=order.platform,
        )
        return "ok"


async def _watch_yk_payment(
    bot: Bot,
    settings: Settings,
    *,
    chat_id: int,
    yk_id: str,
) -> None:
    """Фон: до ~10 мин ждём succeeded и выдаём доступ."""
    try:
        for _ in range(60):
            await asyncio.sleep(10)
            order = await get_bot_yk_order_async(settings.db_path, yk_id)
            if order is None or order.status in ("succeeded", "canceled"):
                return
            try:
                y_p = await find_payment(settings, yk_id)
            except Exception:
                continue
            if payment_canceled(y_p):
                await set_bot_yk_order_status_async(settings.db_path, yk_id, "canceled")
                return
            if not payment_succeeded(y_p):
                continue
            class _Msg:
                def __init__(self) -> None:
                    pass

                async def answer(self, text: str, **kwargs: object) -> None:
                    await bot.send_message(chat_id, text, **kwargs)  # type: ignore[arg-type]

                async def answer_document(self, document: object, **kwargs: object) -> None:
                    await bot.send_document(chat_id, document=document, **kwargs)  # type: ignore[arg-type]

                async def answer_photo(self, photo: object, **kwargs: object) -> None:
                    await bot.send_photo(chat_id, photo=photo, **kwargs)  # type: ignore[arg-type]

            await _fulfill_yk_payment(
                _Msg(),  # type: ignore[arg-type]
                bot,
                settings,
                yk_id=yk_id,
                notify_pending=False,
            )
            return
    except Exception:
        logger.exception("watch yk %s", yk_id)


async def _start_sbp_checkout(
    message: Message,
    bot: Bot,
    settings: Settings,
    platform: str,
    *,
    actor: User | None = None,
) -> None:
    user = actor or message.from_user
    uid = user.id if user else 0
    if not uid:
        await message.answer("Не удалось определить пользователя.")
        return
    if not settings.yookassa_shop_id or not settings.yookassa_secret_key:
        await message.answer(
            "Оплата СБП временно недоступна (не заданы ключи ЮKassa). "
            "Напишите в поддержку.",
        )
        return

    plat = normalize_platform(platform) or "android"
    uname = user.username if user else None
    if plat == "ios":
        label = "iPhone (Happ)"
    else:
        label = "Android (Happ)" if _uses_happ(plat, settings) else "Android (WireGuard)"
    desc = f"AlesVPN 1 мес {settings.price_rub} ₽ — {label}"

    try:
        pay = await create_sbp_payment(
            settings,
            user_id=uid,
            platform=plat,
            description=desc,
        )
    except Exception as e:
        logger.exception("create SBP payment")
        await message.answer(
            "Не удалось создать платёж СБП. Попробуйте позже или напишите в поддержку.\n"
            f"<code>{html.escape(str(e)[:200])}</code>",
        )
        return

    await insert_bot_yk_order_async(
        settings.db_path,
        yk_id=pay.id,
        user_id=uid,
        username=uname,
        platform=plat,
        amount_value=pay.amount_value,
    )

    await message.answer(
        f"Оплата <b>{settings.price_rub} ₽</b> за 1 месяц — {label}.\n\n"
        "1) Нажмите «Оплатить» (откроется ЮKassa, лучше СБП)\n"
        "2) После оплаты — «Проверить оплату» "
        "(или подождите, бот проверит сам)",
        reply_markup=_pay_keyboard(pay.id, pay.confirmation_url, settings.price_rub),
    )
    asyncio.create_task(
        _watch_yk_payment(bot, settings, chat_id=message.chat.id, yk_id=pay.id),
    )


@router.message(Command("buy"))
async def cmd_buy(message: Message, settings: Settings) -> None:
    android_line = (
        "• <b>Android</b> — подписка Happ (https)"
        if settings.android_delivery == "happ"
        else "• <b>Android</b> — ключ WireGuard для AlesVPN"
    )
    await message.answer(
        f"Выберите платформу (месяц — <b>{settings.price_rub} ₽</b>, СБП):\n"
        f"{android_line}\n"
        "• <b>iPhone</b> — подписка Happ (https)",
        reply_markup=_platform_keyboard(settings),
    )


def _issue_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[
            [InlineKeyboardButton(text="Android — Happ", callback_data="issue_android")],
            [InlineKeyboardButton(text="iPhone — Happ", callback_data="issue_ios")],
            [InlineKeyboardButton(text="◀️ Админ", callback_data="admin")],
        ],
    )


@router.message(Command("issue"))
async def cmd_issue(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    await message.answer(
        "🔑 <b>Дополнительный ключ</b>\n"
        "Новая ссылка в панели. Кабинет и основная подписка не меняются.",
        reply_markup=_issue_keyboard(),
    )


@router.callback_query(F.data.in_({"issue", "issue_android", "issue_ios"}))
async def callback_issue(query: CallbackQuery, bot: Bot, settings: Settings) -> None:
    await query.answer()
    if query.message is None or not query.from_user:
        return
    if not is_admin(query.from_user.id, settings):
        await query.message.answer("Нет доступа.")
        return
    data = query.data or "issue"
    if data == "issue":
        await query.message.answer(
            "🔑 <b>Дополнительный ключ</b>\nВыберите платформу:",
            reply_markup=_issue_keyboard(),
        )
        return
    platform = "ios" if data == "issue_ios" else "android"
    await _try_admin_free_buy(
        query.message,
        bot,
        settings,
        platform,
        actor=query.from_user,
        extra_key=True,
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
            reply_markup=_platform_keyboard(settings),
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
    await _start_sbp_checkout(
        query.message,
        bot,
        settings,
        platform,
        actor=query.from_user,
    )


@router.callback_query(F.data.startswith("yk_check:"))
async def callback_yk_check(query: CallbackQuery, bot: Bot, settings: Settings) -> None:
    await query.answer()
    if query.message is None or not query.from_user:
        return
    yk_id = (query.data or "").split(":", 1)[-1].strip()
    if not yk_id:
        return
    order = await get_bot_yk_order_async(settings.db_path, yk_id)
    if order is None:
        await query.message.answer("Платёж не найден. Создайте новый через /buy.")
        return
    if order.user_id != query.from_user.id and not is_admin(query.from_user.id, settings):
        await query.message.answer("Это оплата другого пользователя.")
        return

    status = await _fulfill_yk_payment(
        query.message,
        bot,
        settings,
        yk_id=yk_id,
        notify_pending=True,
    )
    if status == "already":
        await query.message.answer("Этот платёж уже обработан. Если ключ не пришёл — /help.")
    elif status == "canceled":
        await query.message.answer("Платёж отменён. Создайте новый через /buy.")
    elif status == "error":
        await query.message.answer("Не удалось проверить оплату. Попробуйте позже.")


@router.message(Command("admin_ping"))
async def admin_ping(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    await message.answer("pong (админ)")
