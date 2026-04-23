"""SQLite: заказы YooKassa (веб) и единовременный показ ключа (тот же DB_PATH, что у бота)."""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class YkOrderRow:
    yk_id: str
    plan_code: str
    amount_value: str
    return_token: str
    status: str | None
    paste_two_lines: str | None
    conf_text: str | None
    provision_error: str | None
    first_view_at: str | None


def _connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(path), timeout=30.0, isolation_level=None)
    return conn


def init_yk(db_path: Path) -> None:
    conn = _connect(db_path)
    try:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=OFF")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS yookassa_web (
                yk_id TEXT PRIMARY KEY,
                plan_code TEXT NOT NULL,
                amount_value TEXT NOT NULL,
                return_token TEXT NOT NULL UNIQUE,
                status TEXT,
                paste_two_lines TEXT,
                conf_text TEXT,
                provision_error TEXT,
                first_view_at TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        cur = conn.execute("PRAGMA table_info(yookassa_web)")
        have = {row[1] for row in cur.fetchall()}
        if "first_view_at" not in have:
            try:
                conn.execute("ALTER TABLE yookassa_web ADD COLUMN first_view_at TEXT")
            except sqlite3.OperationalError:
                pass
        conn.execute("CREATE INDEX IF NOT EXISTS idx_yk_token ON yookassa_web (return_token)")
    finally:
        conn.close()


def insert_order(
    path: Path,
    *,
    yk_id: str,
    plan_code: str,
    amount_value: str,
    return_token: str,
    status: str = "created",
) -> None:
    conn = _connect(path)
    try:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute(
            """
            INSERT INTO yookassa_web
            (yk_id, plan_code, amount_value, return_token, status)
            VALUES (?, ?, ?, ?, ?)
            """,
            (yk_id, plan_code, amount_value, return_token, status),
        )
        conn.commit()
    except sqlite3.IntegrityError as e:
        raise RuntimeError(f"Заказ {yk_id!r} уже есть: {e}") from e
    finally:
        conn.close()


def get_by_yk_id(path: Path, yk_id: str) -> YkOrderRow | None:
    conn = _connect(path)
    try:
        row = conn.execute(
            """
            SELECT yk_id, plan_code, amount_value, return_token, status,
                   paste_two_lines, conf_text, provision_error, first_view_at
            FROM yookassa_web
            WHERE yk_id = ?
            """,
            (yk_id,),
        ).fetchone()
        if not row:
            return None
        return YkOrderRow(*row)
    finally:
        conn.close()


def get_by_token(path: Path, token: str) -> YkOrderRow | None:
    conn = _connect(path)
    try:
        row = conn.execute(
            """
            SELECT yk_id, plan_code, amount_value, return_token, status,
                   paste_two_lines, conf_text, provision_error, first_view_at
            FROM yookassa_web
            WHERE return_token = ?
            """,
            (token,),
        ).fetchone()
        if not row:
            return None
        return YkOrderRow(*row)
    finally:
        conn.close()


def set_provision_ok(
    path: Path,
    yk_id: str,
    paste: str,
    conf: str,
) -> bool:
    """True, если эта вставка выиграла гонку (rowcount=1)."""
    conn = _connect(path)
    try:
        cur = conn.execute(
            """
            UPDATE yookassa_web
            SET paste_two_lines = ?, conf_text = ?, provision_error = NULL
            WHERE yk_id = ? AND (paste_two_lines IS NULL OR TRIM(paste_two_lines) = '')
            """,
            (paste, conf, yk_id),
        )
        n = cur.rowcount if cur else 0
        conn.commit()
        return n > 0
    finally:
        conn.close()


def set_provision_error(
    path: Path,
    yk_id: str,
    err: str,
) -> bool:
    conn = _connect(path)
    try:
        cur = conn.execute(
            """
            UPDATE yookassa_web
            SET provision_error = ?
            WHERE yk_id = ? AND (paste_two_lines IS NULL OR TRIM(paste_two_lines) = '')
            """,
            (err, yk_id),
        )
        n = cur.rowcount if cur else 0
        conn.commit()
        return n > 0
    finally:
        conn.close()


def set_first_view(path: Path, return_token: str) -> None:
    conn = _connect(path)
    try:
        conn.execute(
            """
            UPDATE yookassa_web
            SET first_view_at = COALESCE(
                first_view_at,
                (datetime('now'))
            )
            WHERE return_token = ? AND (first_view_at IS NULL OR first_view_at = '')
            """,
            (return_token,),
        )
        conn.commit()
    finally:
        conn.close()
