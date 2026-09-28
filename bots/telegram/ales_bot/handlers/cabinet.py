"""Личный кабинет: карточка подписки, меню кнопок, подключение устройства."""

from __future__ import annotations

import html
from datetime import datetime, timedelta, timezone
from pathlib import Path

from aiogram import F, Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command, CommandStart
from aiogram.types import (
    CallbackQuery,
    FSInputFile,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    KeyboardButton,
    Message,
    ReplyKeyboardMarkup,
)

from ales_bot.config import Settings, is_admin
from ales_bot.db import SubscriptionRow, get_subscription_async
from ales_bot.happ_guide import send_happ_access

router = Router(name="cabinet")

HEADER_PHOTO = Path(__file__).resolve().parents[1] / "assets" / "cabinet_header.png"
_CAPTION_LIMIT = 1024

MSK = timezone(timedelta(hours=3))

_MONTHS = (
    "января",
    "февраля",
    "марта",
    "апреля",
    "мая",
    "июня",
    "июля",
    "августа",
    "сентября",
    "октября",
    "ноября",
    "декабря",
)

_PLAN_TITLES = {
    "first": "1 месяц",
    "monthly": "1 месяц",
    "m6": "6 месяцев",
    "m12": "12 месяцев",
    "stars": "1 месяц",
    "admin_free": "1 месяц (админ)",
}


def now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def fmt_expiry(expiry_ms: int) -> str:
    if expiry_ms <= 0:
        return "без ограничения"
    when = datetime.fromtimestamp(expiry_ms / 1000, MSK)
    return f"{when.day} {_MONTHS[when.month - 1]} {when.year} года, {when.strftime('%H:%M')}"


def days_left(expiry_ms: int) -> int:
    if expiry_ms <= 0:
        return 0
    return max(0, (expiry_ms - now_ms()) // 86_400_000)


def is_active(sub: SubscriptionRow) -> bool:
    return sub.expiry_ms <= 0 or sub.expiry_ms > now_ms()


def plan_title(plan_code: str) -> str:
    return _PLAN_TITLES.get((plan_code or "").strip().lower(), "1 месяц")


def _devices_line(sub: SubscriptionRow) -> str:
    if sub.device_limit <= 0:
        return "• Устройства: <b>без лимита</b>"
    return f"• Устройства: <b>1 из {sub.device_limit}</b>"


def menu_text(settings: Settings, sub: SubscriptionRow | None) -> str:
    head = "💰 Баланс: <b>0 ₽</b>\n\n"
    if sub is None:
        limit = settings.xui_device_limit
        if limit <= 0:
            devices = "• Устройства: без лимита"
        else:
            devices = f"• Устройства: <b>0 из {limit}</b>"
        return (
            f"{head}"
            "❌ <b>Подписки нет</b>\n"
            f"{devices}\n"
            f"• План: не оформлен\n\n"
            f"Месяц — <b>{settings.price_rub} ₽</b>, оплата через СБП.\n"
            "Нажмите «Оформить подписку» ниже."
        )

    if not is_active(sub):
        return (
            f"{head}"
            "⌛️ <b>Подписка истекла</b>\n"
            f"• Закончилась: <b>{fmt_expiry(sub.expiry_ms)}</b> (МСК)\n"
            f"{_devices_line(sub)}\n"
            f"• План: <b>{plan_title(sub.plan_code)}</b>\n\n"
            "Продлите — ссылка и настройки в Happ останутся те же."
        )
    left = days_left(sub.expiry_ms)
    left_txt = f" — осталось {left} дн." if sub.expiry_ms > 0 else ""
    return (
        f"{head}"
        "✅ <b>Подписка активна</b>\n"
        f"• Действует до: <b>{fmt_expiry(sub.expiry_ms)}</b> (МСК){left_txt}\n"
        f"{_devices_line(sub)}\n"
        f"• План: <b>{plan_title(sub.plan_code)}</b>\n\n"
        "🔗 <b>Ссылка для подключения</b>\n"
        f"<blockquote><pre>{html.escape(sub.link.strip())}</pre></blockquote>"
    )


def root_reply_keyboard(*, admin: bool) -> ReplyKeyboardMarkup:
    rows = [[KeyboardButton(text="👤 Главное меню")]]
    if admin:
        rows.append([KeyboardButton(text="🛠 Админ")])
    return ReplyKeyboardMarkup(
        keyboard=rows,
        resize_keyboard=True,
        is_persistent=True,
        input_field_placeholder="Главное меню внизу экрана",
    )


def menu_keyboard(
    settings: Settings,
    sub: SubscriptionRow | None,
    *,
    admin: bool = False,
) -> InlineKeyboardMarkup:
    rows: list[list[InlineKeyboardButton]] = []
    if sub is None:
        rows.append(
            [
                InlineKeyboardButton(
                    text=f"💳 Оформить подписку — {settings.price_rub} ₽",
                    callback_data="buy",
                ),
            ],
        )
    else:
        if is_active(sub):
            rows.append(
                [InlineKeyboardButton(text="📲 Подключить устройство", callback_data="connect")],
            )
        rows.append(
            [
                InlineKeyboardButton(
                    text=f"🔄 Продлить подписку — {settings.price_rub} ₽",
                    callback_data="buy",
                ),
            ],
        )
    tail = [InlineKeyboardButton(text="ℹ️ О сервисе", callback_data="about")]
    if settings.support_username:
        tail.append(
            InlineKeyboardButton(
                text="💬 Поддержка",
                url=f"https://t.me/{settings.support_username}",
            ),
        )
    rows.append(tail)
    if admin:
        rows.append([InlineKeyboardButton(text="🛠 Админ", callback_data="admin")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def back_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[[InlineKeyboardButton(text="◀️ В меню", callback_data="menu")]],
    )


async def _send_menu_card(
    message: Message,
    settings: Settings,
    user_id: int,
    *,
    pin_reply: bool = False,
) -> None:
    admin = is_admin(user_id, settings)
    if pin_reply:
        await message.answer(
            "👤 Главное меню",
            reply_markup=root_reply_keyboard(admin=admin),
        )
    sub = await get_subscription_async(settings.db_path, user_id)
    text = menu_text(settings, sub)
    markup = menu_keyboard(settings, sub, admin=admin)
    if HEADER_PHOTO.is_file() and len(text) <= _CAPTION_LIMIT:
        await message.answer_photo(
            FSInputFile(HEADER_PHOTO),
            caption=text,
            reply_markup=markup,
        )
        return
    await message.answer(text, reply_markup=markup, disable_web_page_preview=True)


async def show_menu(
    message: Message,
    settings: Settings,
    user_id: int,
    *,
    pin_reply: bool = False,
) -> None:
    await _send_menu_card(message, settings, user_id, pin_reply=pin_reply)


@router.message(CommandStart())
async def cmd_start(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await show_menu(message, settings, uid, pin_reply=True)


@router.message(Command("menu"))
async def cmd_menu(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await show_menu(message, settings, uid, pin_reply=True)


@router.message(F.text.in_({"👤 Главное меню", "Главное меню", "главное меню"}))
async def text_main_menu(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    await show_menu(message, settings, uid)


@router.callback_query(F.data == "menu")
async def callback_menu(query: CallbackQuery, settings: Settings) -> None:
    await query.answer()
    if query.message is None or not query.from_user:
        return
    admin = is_admin(query.from_user.id, settings)
    sub = await get_subscription_async(settings.db_path, query.from_user.id)
    text = menu_text(settings, sub)
    markup = menu_keyboard(settings, sub, admin=admin)
    if query.message.photo and len(text) <= _CAPTION_LIMIT:
        try:
            await query.message.edit_caption(caption=text, reply_markup=markup)
            return
        except TelegramBadRequest:
            pass
    try:
        await query.message.edit_text(
            text,
            reply_markup=markup,
            disable_web_page_preview=True,
        )
    except TelegramBadRequest:
        await _send_menu_card(query.message, settings, query.from_user.id)


@router.callback_query(F.data == "connect")
async def callback_connect(query: CallbackQuery, settings: Settings) -> None:
    await query.answer()
    if query.message is None or not query.from_user:
        return
    sub = await get_subscription_async(settings.db_path, query.from_user.id)
    if sub is None:
        await query.message.answer(
            "Подписки пока нет — оформите её в меню.",
            reply_markup=menu_keyboard(settings, None),
        )
        return
    if not is_active(sub):
        await query.message.answer(
            "Подписка истекла — продлите, и ссылка снова заработает.",
            reply_markup=menu_keyboard(settings, sub),
        )
        return
    await send_happ_access(
        query.message,
        link=sub.link,
        title="📲 <b>Подключение устройства</b>",
        platform="ios",
        reply_markup=back_keyboard(),
    )


@router.callback_query(F.data == "about")
async def callback_about(query: CallbackQuery, settings: Settings) -> None:
    await query.answer()
    if query.message is None:
        return
    uid = query.from_user.id if query.from_user else 0
    await query.message.answer(
        "<b>О сервисе AlesVPN</b>\n\n"
        "• Протокол VLESS Reality — трафик выглядит как обычный HTTPS\n"
        "• Серверы: ⭐ Нидерланды (основной) и Германия (запасной)\n"
        "• Одна ссылка подписки работает в Happ, Hiddify, v2rayNG, Streisand\n"
        f"• Цена: <b>{settings.price_rub} ₽</b> за 30 дней, оплата СБП\n"
        "• Логи действий не ведём\n\n"
        f"Ваш ID: <code>{uid}</code> — назовите его в поддержке.",
        reply_markup=back_keyboard(),
    )
