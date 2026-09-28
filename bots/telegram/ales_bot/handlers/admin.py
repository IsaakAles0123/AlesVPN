"""Админ-панель: оплаты и подписки."""

from __future__ import annotations

import html

from aiogram import F, Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command
from aiogram.types import (
    CallbackQuery,
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    Message,
)

from ales_bot.config import Settings, is_admin
from ales_bot.db import (
    PaymentRow,
    list_recent_payments_async,
    payment_stats_async,
    subscription_counts_async,
)
from ales_bot.handlers.cabinet import now_ms

router = Router(name="admin")

_PAGE = 8


def _is_free(row: PaymentRow) -> bool:
    payload = (row.invoice_payload or "").lower()
    return row.amount <= 0 or payload.startswith("admin_free")


def _admin_keyboard(offset: int, total: int) -> InlineKeyboardMarkup:
    rows: list[list[InlineKeyboardButton]] = [
        [InlineKeyboardButton(text="🔑 Выдать новый ключ", callback_data="issue")],
        [InlineKeyboardButton(text="🔄 Обновить", callback_data=f"admin:{offset}")],
    ]
    nav: list[InlineKeyboardButton] = []
    if offset > 0:
        nav.append(
            InlineKeyboardButton(
                text="◀️ Ранее",
                callback_data=f"admin:{max(0, offset - _PAGE)}",
            ),
        )
    if offset + _PAGE < total:
        nav.append(
            InlineKeyboardButton(
                text="Ещё ▶️",
                callback_data=f"admin:{offset + _PAGE}",
            ),
        )
    if nav:
        rows.append(nav)
    rows.append([InlineKeyboardButton(text="◀️ В меню", callback_data="menu")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


async def admin_text(settings: Settings, *, offset: int = 0) -> str:
    stats = await payment_stats_async(settings.db_path)
    active, subs_total = await subscription_counts_async(settings.db_path, now_ms())
    rows = await list_recent_payments_async(
        settings.db_path, limit=_PAGE, offset=offset
    )
    lines = [
        "<b>🛠 Админ — покупки</b>\n",
        f"Оплат в базе: <b>{stats.total}</b>",
        f"Реальных (СБП): <b>{stats.paid_count}</b> на <b>{stats.paid_sum} ₽</b>",
        f"Бесплатных (админ): <b>{stats.free_count}</b>",
        f"Подписок: <b>{active}</b> активных из {subs_total}",
    ]
    if not rows:
        lines.append("\nПокупок пока нет.")
        return "\n".join(lines)

    shown_from = offset + 1
    shown_to = offset + len(rows)
    lines.append(f"\n<b>Последние</b> ({shown_from}–{shown_to} из {stats.total}):\n")
    for r in rows:
        uname = f"@{html.escape(r.username)}" if r.username else "без username"
        plat = html.escape((r.platform or "—").strip() or "—")
        kind = "бесплатно" if _is_free(r) else f"{r.amount} {html.escape(r.currency)}"
        lines.append(
            f"{html.escape(r.created_at)} · {kind} · {plat}\n"
            f"user <code>{r.user_id}</code> {uname}\n"
            f"<code>{html.escape(r.telegram_charge_id)}</code>\n"
        )
    text = "\n".join(lines)
    if len(text) > 3900:
        return text[:3890] + "…"
    return text


async def send_admin_panel(message: Message, settings: Settings, *, offset: int = 0) -> None:
    await message.answer(
        await admin_text(settings, offset=offset),
        reply_markup=_admin_keyboard(offset, (await payment_stats_async(settings.db_path)).total),
        disable_web_page_preview=True,
    )


@router.message(Command("admin", "stats"))
async def cmd_admin(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        await message.answer("Нет доступа.")
        return
    await send_admin_panel(message, settings)


@router.message(F.text.in_({"🛠 Админ", "Админ", "админ"}))
async def text_admin(message: Message, settings: Settings) -> None:
    uid = message.from_user.id if message.from_user else 0
    if not is_admin(uid, settings):
        return
    await send_admin_panel(message, settings)


@router.callback_query(F.data == "admin")
async def callback_admin(query: CallbackQuery, settings: Settings) -> None:
    await query.answer()
    if query.message is None or not query.from_user:
        return
    if not is_admin(query.from_user.id, settings):
        await query.message.answer("Нет доступа.")
        return
    await send_admin_panel(query.message, settings)


@router.callback_query(F.data.startswith("admin:"))
async def callback_admin_page(query: CallbackQuery, settings: Settings) -> None:
    if query.message is None or not query.from_user:
        await query.answer()
        return
    if not is_admin(query.from_user.id, settings):
        await query.answer("Нет доступа.", show_alert=True)
        return
    raw = (query.data or "admin:0").split(":", 1)[-1]
    try:
        offset = max(0, int(raw))
    except ValueError:
        offset = 0
    await query.answer()
    stats = await payment_stats_async(settings.db_path)
    text = await admin_text(settings, offset=offset)
    markup = _admin_keyboard(offset, stats.total)
    try:
        await query.message.edit_text(
            text, reply_markup=markup, disable_web_page_preview=True
        )
    except TelegramBadRequest:
        await query.message.answer(
            text, reply_markup=markup, disable_web_page_preview=True
        )
