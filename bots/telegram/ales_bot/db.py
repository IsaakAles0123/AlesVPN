"""SQLite: учёт успешных оплат (Stars) и выдача WireGuard."""

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


def _migrate_payments(conn: sqlite3.Connection) -> None:
    cur = conn.execute("PRAGMA table_info(payments)")
    names = {row[1] for row in cur.fetchall()}
    if "wg_public_key" not in names:
        conn.execute("ALTER TABLE payments ADD COLUMN wg_public_key TEXT")
    if "wg_address" not in names:
        conn.execute("ALTER TABLE payments ADD COLUMN wg_address TEXT")
    if "wg_provision_error" not in names:
        conn.execute("ALTER TABLE payments ADD COLUMN wg_provision_error TEXT")


def init_db(path: Path, *, wg_first_octet: int = 20) -> None:
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
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                wg_public_key TEXT,
                wg_address TEXT,
                wg_provision_error TEXT
            )
            """
        )
        _migrate_payments(conn)
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS wg_meta (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                next_octet INTEGER NOT NULL
            )
            """
        )
        row = conn.execute("SELECT 1 FROM wg_meta WHERE id=1").fetchone()
        if row is None:
            conn.execute(
                "INSERT INTO wg_meta (id, next_octet) VALUES (1, ?)",
                (wg_first_octet,),
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


def update_payment_wg(
    path: Path,
    telegram_charge_id: str,
    *,
    wg_public_key: str | None,
    wg_address: str | None,
    wg_provision_error: str | None,
) -> None:
    conn = _connect(path)
    try:
        conn.execute(
            """
            UPDATE payments
            SET wg_public_key = ?, wg_address = ?, wg_provision_error = ?
            WHERE telegram_charge_id = ?
            """,
            (wg_public_key, wg_address, wg_provision_error, telegram_charge_id),
        )
        conn.commit()
    finally:
        conn.close()


def allocate_next_octet(path: Path, min_o: int, max_o: int) -> int:
    """Атомарно выделяет следующий октет для x.x.x.N/32 (поле next_octet = следующий N)."""
    conn = _connect(path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute("SELECT next_octet FROM wg_meta WHERE id=1").fetchone()
        if row is None:
            cur = min_o
            conn.execute(
                "INSERT INTO wg_meta (id, next_octet) VALUES (1, ?)",
                (cur + 1,),
            )
        else:
            cur = int(row[0])
            if cur > max_o:
                conn.rollback()
                raise RuntimeError(
                    f"Закончились адреса в пуле ({min_o}–{max_o}), проверьте wg_meta",
                )
            conn.execute(
                "UPDATE wg_meta SET next_octet = ? WHERE id=1",
                (cur + 1,),
            )
        conn.commit()
        return cur
    except Exception:
        conn.rollback()
        raise
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
    wg_public_key: str | None
    wg_address: str | None


def list_recent_payments(path: Path, limit: int = 20) -> Sequence[PaymentRow]:
    conn = _connect(path)
    try:
        cur = conn.execute(
            """
            SELECT id, created_at, user_id, username, amount, currency,
                   telegram_charge_id, wg_public_key, wg_address
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
                wg_public_key=r[7],
                wg_address=r[8],
            )
            for r in cur.fetchall()
        )
    finally:
        conn.close()


async def init_db_async(path: Path, *, wg_first_octet: int = 20) -> None:
    await asyncio.to_thread(init_db, path, wg_first_octet=wg_first_octet)


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


async def update_payment_wg_async(
    path: Path,
    telegram_charge_id: str,
    *,
    wg_public_key: str | None,
    wg_address: str | None,
    wg_provision_error: str | None,
) -> None:
    await asyncio.to_thread(
        update_payment_wg,
        path,
        telegram_charge_id,
        wg_public_key=wg_public_key,
        wg_address=wg_address,
        wg_provision_error=wg_provision_error,
    )


async def allocate_next_octet_async(path: Path, min_o: int, max_o: int) -> int:
    return await asyncio.to_thread(allocate_next_octet, path, min_o, max_o)


async def payment_count_async(path: Path) -> int:
    return await asyncio.to_thread(payment_count, path)


async def list_recent_payments_async(path: Path, limit: int = 20) -> Sequence[PaymentRow]:
    return await asyncio.to_thread(list_recent_payments, path, limit)
