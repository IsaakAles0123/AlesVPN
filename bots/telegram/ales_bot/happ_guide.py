"""Инструкция подключения Happ: фото-гайд + ссылка подписки одним нажатием."""

from __future__ import annotations

import html
import logging
from pathlib import Path

from aiogram.types import FSInputFile, InlineKeyboardMarkup, Message

logger = logging.getLogger(__name__)

GUIDE_PHOTO = Path(__file__).resolve().parent / "assets" / "happ_from_clipboard.png"


async def send_happ_access(
    message: Message,
    *,
    link: str,
    title: str,
    uid_line: str = "",
    platform: str = "ios",
    reply_markup: InlineKeyboardMarkup | None = None,
) -> None:
    link_esc = html.escape(link.strip())
    store = "App Store" if platform == "ios" else "Google Play"
    body = (
        f"{title}\n\n"
        "<b>Ссылка подписки</b> — нажмите на неё, чтобы скопировать:\n"
        f"<blockquote><pre>{link_esc}</pre></blockquote>\n\n"
        "<b>Как подключить</b>\n"
        f"1. {store} → установите <b>Happ</b>\n"
        "2. Нажмите на ссылку выше — она скопируется в буфер\n"
        "3. Откройте Happ → кнопка <b>«Из Буфера»</b> (стрелка на фото)\n"
        "4. В подписке будут серверы (⭐ NL — основной, DE — запасной)\n"
        "5. Включите VPN большой круглой кнопкой\n\n"
        "Важно: ссылка только с <code>https://</code> "
        "(не <code>http://</code> и не порт <code>:2096</code>).\n"
        "Ссылка работает и в других клиентах (Hiddify, v2rayNG, Streisand)."
        f"{uid_line}"
    )
    if GUIDE_PHOTO.is_file():
        # caption ≤ 1024; при длинной ссылке — фото + отдельное сообщение
        if len(body) <= 1024 and reply_markup is None:
            await message.answer_photo(FSInputFile(GUIDE_PHOTO), caption=body)
        else:
            await message.answer_photo(
                FSInputFile(GUIDE_PHOTO),
                caption=(
                    "Happ → нажмите <b>«Из Буфера»</b> (стрелка на фото), "
                    "предварительно скопировав ссылку из следующего сообщения."
                ),
            )
            await message.answer(
                body,
                disable_web_page_preview=True,
                reply_markup=reply_markup,
            )
        return
    logger.warning("Нет фото-гайда Happ: %s", GUIDE_PHOTO)
    await message.answer(body, disable_web_page_preview=True, reply_markup=reply_markup)
