"""SQLite: учёт успешных оплат (Stars)."""

from __future__ import annotations

import asyncio
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


def _connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path))
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db(path: Path) -> None:
    conn = _connect(path)
    try:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                telegram_charge_id TEXT NOT NULL UNIQUE,
                user_id INTEGER NOT NULL,
                username TEXT,
                amount INTEGER NOT NULL,
                currency TEXT NOT NULL,
                invoice_payload TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        conn.commit()
    finally:
        conn.close()


def insert_payment(
    path: Path,
    *,
    telegram_charge_id: str,
    user_id: int,
    username: str | None,
    amount: int,
    currency: str,
    invoice_payload: str,
) -> bool:
    """True если строка добавлена, False при повторном charge_id."""
    conn = _connect(path)
    try:
        try:
            conn.execute(
                """
                INSERT INTO payments (
                    telegram_charge_id, user_id, username,
                    amount, currency, invoice_payload
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    telegram_charge_id,
                    user_id,
                    username,
                    amount,
                    currency,
                    invoice_payload,
                ),
            )
            conn.commit()
            return True
        except sqlite3.IntegrityError:
            return False
    finally:
        conn.close()


def payment_count(path: Path) -> int:
    conn = _connect(path)
    try:
        row = conn.execute("SELECT COUNT(*) FROM payments").fetchone()
        return int(row[0]) if row else 0
    finally:
        conn.close()


@dataclass(frozen=True)
class PaymentRow:
    id: int
    created_at: str
    user_id: int
    username: str | None
    amount: int
    currency: str
    telegram_charge_id: str


def list_recent_payments(path: Path, limit: int = 20) -> Sequence[PaymentRow]:
    conn = _connect(path)
    try:
        cur = conn.execute(
            """
            SELECT id, created_at, user_id, username, amount, currency, telegram_charge_id
            FROM payments
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        )
        return tuple(
            PaymentRow(
                id=r[0],
                created_at=r[1],
                user_id=r[2],
                username=r[3],
                amount=r[4],
                currency=r[5],
                telegram_charge_id=r[6],
            )
            for r in cur.fetchall()
        )
    finally:
        conn.close()


async def init_db_async(path: Path) -> None:
    await asyncio.to_thread(init_db, path)


async def insert_payment_async(
    path: Path,
    *,
    telegram_charge_id: str,
    user_id: int,
    username: str | None,
    amount: int,
    currency: str,
    invoice_payload: str,
) -> bool:
    return await asyncio.to_thread(
        insert_payment,
        path,
        telegram_charge_id=telegram_charge_id,
        user_id=user_id,
        username=username,
        amount=amount,
        currency=currency,
        invoice_payload=invoice_payload,
    )


async def payment_count_async(path: Path) -> int:
    return await asyncio.to_thread(payment_count, path)


async def list_recent_payments_async(path: Path, limit: int = 20) -> Sequence[PaymentRow]:
    return await asyncio.to_thread(list_recent_payments, path, limit)
