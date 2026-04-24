"""
AlesVPN: YooKassa (веб) → редирект в кассу → return → WireGuard и страница /pay/done?t=.

Uvicorn (1 worker, тот же .env что у бота + YOOKASSA_*):
  set PAY_API_MODE=1
  python -m uvicorn main:app --host 127.0.0.1 --port 8008

Nginx: location /pay/  proxy на этот порт, см. README.
"""

from __future__ import annotations

import html
import logging
import os
import secrets
import sys
import uuid
from contextlib import asynccontextmanager
from html import escape
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlencode

# Каталог pay_api (для import yk_store при запуске: uvicorn pay_api.main:app)
_PA = Path(__file__).resolve().parent
if str(_PA) not in sys.path:
    sys.path.insert(0, str(_PA))
# ../bots/telegram/ales_bot
_T = _PA.parent / "bots" / "telegram"
if str(_T) not in sys.path:
    sys.path.insert(0, str(_T))

import asyncio

from ales_bot.config import load_settings
from ales_bot.db import allocate_next_octet_async, init_db, init_db_async
from ales_bot.wg_provision import WgProvisionError, provision_after_payment
from fastapi import FastAPI, Query, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from yookassa import Configuration, Payment

from yk_store import (
    email_looks_valid,
    first_rub_taken_for_email,
    get_by_token,
    get_by_yk_id,
    init_yk,
    insert_order,
    mark_first_rub_redeemed,
    normalize_email,
    set_first_view,
    set_provision_error,
    set_provision_ok,
)

log = logging.getLogger("pay_api")
logging.basicConfig(level=logging.INFO)

PLANS: dict[str, tuple[str, str]] = {
    "first": ("1.00", "AlesVPN: первый месяц 1 ₽"),
    "monthly": ("99.00", "AlesVPN: 1 мес, 99 ₽"),
    "m6": ("499.00", "AlesVPN: 6 мес, 499 ₽"),
    "m12": ("999.00", "AlesVPN: 12 мес, 999 ₽"),
}

BASE_URL = (os.getenv("PAY_BASE_URL") or "https://alesvpn.ru").rstrip("/")
SHOP_ID = (os.getenv("YOOKASSA_SHOP_ID") or "").strip()
SECRET = (os.getenv("YOOKASSA_SECRET_KEY") or "").strip()

# сериализация выдачи, чтобы не двоить октет
_provision_lock = asyncio.Lock()


def _first_rub_bypass_emails() -> set[str]:
    """
    E-mail, для которых акция «1 ₽» не учитывается (можно снова оформить 1 ₽).
    Базовый список + PAY_FIRST_RUB_BYPASS_EMAILS (через запятую).
    """
    out = {normalize_email("isaakales26@mail.ru")}
    extra = (os.getenv("PAY_FIRST_RUB_BYPASS_EMAILS") or "").strip()
    for part in extra.split(","):
        t = part.strip()
        if t:
            out.add(normalize_email(t))
    return out


def _is_bypass_first_rub_email(norm: str) -> bool:
    return bool(norm) and norm in _first_rub_bypass_emails()


def _maybe_redeem_first_rub_sync(db_path: Path, yk_id: str) -> None:
    """
    После payment.succeeded: «1 ₽ на e-mail» отмечаем в БД навсегда (кроме bypass).
    """
    row = get_by_yk_id(db_path, yk_id)
    if not row or row.plan_code != "first":
        return
    em = normalize_email(row.customer_email or "")
    if not em or not email_looks_valid(em):
        return
    if _is_bypass_first_rub_email(em):
        return
    mark_first_rub_redeemed(db_path, em, yk_id)


def _html(title: str, body: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="dark" />
  <title>{escape(title)}</title>
  <link rel="stylesheet" href="/assets/style.css" />
  <style>.paybox{{max-width:40rem;margin:0 auto;padding:1.5rem;}}</style>
</head>
<body>
  <div class="wrap paybox" id="content">{body}</div>
</body>
</html>"""


def _pay_state(p: Any) -> str:
    st = getattr(p, "status", None)
    if st is None:
        return ""
    s = st.value if hasattr(st, "value") else st
    return (str(s) or "").lower()


@asynccontextmanager
async def lifespan(_: FastAPI):
    s = load_settings()
    p = s.db_path
    init_db(p, wg_first_octet=s.wg_octet_min)
    init_yk(p)
    await init_db_async(p, wg_first_octet=s.wg_octet_min)
    if SHOP_ID and SECRET:
        Configuration.account_id = SHOP_ID
        Configuration.secret_key = SECRET
    else:
        log.warning("YOO_KASSA не настроена (YOOKASSA_SHOP_ID / YOOKASSA_SECRET_KEY).")
    yield
    return


app = FastAPI(lifespan=lifespan, title="AlesVPN pay")


def _q_pid(request: Request) -> str | None:
    q = request.query_params
    for k in ("paymentId", "payment_id", "id"):
        v = (q.get(k) or "").strip()
        if v:
            return v
    return None


def _redir_to_key(row) -> RedirectResponse:
    return RedirectResponse(f"{BASE_URL}/pay/done?t={row.return_token}", 302)


async def _yoo_get(pid: str) -> Any:
    if not (SHOP_ID and SECRET):
        return None
    return await asyncio.to_thread(Payment.find_one, pid)


async def _do_provision(yk_id: str) -> str | None:
    """
    Выдать ключ при успехе. Вернуть None при успехе, иначе текст ошибки для HTML.
    """
    s = load_settings()
    p = s.db_path
    if not s.wg_auto_provision:
        e = "Автовыдача отключена (WG_AUTO_PROVISION)."
        await asyncio.to_thread(set_provision_error, p, yk_id, e)
        return e

    async with _provision_lock:
        r0 = await asyncio.to_thread(get_by_yk_id, p, yk_id)
        if r0 and r0.paste_two_lines and r0.conf_text:
            return None
        if r0 and (r0.provision_error or "").strip():
            return (r0.provision_error or "")[:800]
        try:
            octet = await allocate_next_octet_async(
                p, s.wg_octet_min, s.wg_octet_max
            )
            res = await provision_after_payment(s, octet)
        except (WgProvisionError, OSError, RuntimeError) as e:
            msg = f"Платёж принят, но сервер ключа: {e!r}"[:1000]
            log.exception("WireGuard: %s", e)
            await asyncio.to_thread(set_provision_error, p, yk_id, str(e)[:800])
            return msg
        won = await asyncio.to_thread(
            set_provision_ok, p, yk_id, res.paste_two_lines, res.conf_text
        )
        if not won:
            return None
    return None


async def _html_after_paid(yk_id: str) -> RedirectResponse | HTMLResponse:
    row = get_by_yk_id(load_settings().db_path, yk_id)
    if not row:
        return HTMLResponse(
            _html(
                "Заказ",
                f"<h1>Заказ</h1><p>ID {escape(yk_id[:12])}… нет в базе. "
                f"<a href='{BASE_URL}/'>На главную</a></p>",
            ),
            404,
        )
    if row.paste_two_lines and row.conf_text:
        return _redir_to_key(row)

    pay = await _yoo_get(yk_id)
    if not pay:
        return HTMLResponse(
            _html("Касса", "<h1>Касса недоступна</h1>"),
            502,
        )
    st = _pay_state(pay)
    if st in ("canceled", "cancelled"):
        return HTMLResponse(
            _html("Отмена", f"<h1>Оплата отменена</h1><p><a href='{BASE_URL}/#услуги'>Тарифы</a></p>")
        )
    if st != "succeeded":
        return HTMLResponse(
            _html(
                "Оплата",
                f"<h1>Ожидаем оплату</h1><p>Статус: {escape(st)}. "
                f"Обновите страницу через минуту.</p>"
                f"<script>setTimeout(function(){{location.reload();}}, 5000);</script>",
            )
        )

    s = load_settings()
    await asyncio.to_thread(_maybe_redeem_first_rub_sync, s.db_path, yk_id)
    err = await _do_provision(yk_id)
    row2 = get_by_yk_id(s.db_path, yk_id)
    if row2 and row2.paste_two_lines and row2.conf_text:
        return _redir_to_key(row2)
    if err:
        return HTMLResponse(
            _html(
                "Ключ",
                f"<h1>Ошибка выдачи</h1><p>{escape(err)}</p><p>ID: <code>{escape(yk_id)}</code></p>",
            )
        )
    return HTMLResponse(
        _html("Ключ", f"<h1>Данных ещё нет</h1><p>Обновите. ID: {escape(yk_id)}</p>"),
        202,
    )


@app.get("/pay/return", response_class=HTMLResponse)
async def pay_return(request: Request) -> Any:
    pid = _q_pid(request)
    if not pid:
        ret = (request.query_params.get("ret") or "").strip()
        if len(ret) >= 8:
            s = load_settings()
            row = await asyncio.to_thread(get_by_token, s.db_path, ret)
            if row:
                pid = row.yk_id
    if not pid:
        return HTMLResponse(
            _html(
                "Платёж",
                f"<h1>Нет номера оплаты</h1><p><a href='{BASE_URL}/'>На главную</a></p>",
            ),
            400,
        )
    return await _html_after_paid(pid)


@app.get("/pay/done", response_class=HTMLResponse)
async def pay_done(t: str | None = None) -> Any:
    if not t or len(t) < 8:
        return HTMLResponse(
            _html("Нет доступа", f"<h1>Нет параметра t</h1>"),
            400,
        )
    s = load_settings()
    row = await asyncio.to_thread(get_by_token, s.db_path, t)
    if not row:
        return HTMLResponse(
            _html("Ссылка", f"<h1>Нет данных</h1>"),
            404,
        )
    if (row.provision_error or "").strip() and not (
        row.paste_two_lines
    ):
        return HTMLResponse(
            _html(
                "Ключ",
                f"<h1>Выдача</h1><p class='sub'>{escape((row.provision_error or '')[:2000])}</p>",
            ),
        )
    if not row.paste_two_lines or not row.conf_text:
        return HTMLResponse(
            _html(
                "Ждите",
                f"<h1>Ключ готовится</h1><p><a href='{BASE_URL}/pay/return?ret={escape(row.return_token)}'>"
                f"Статус оплаты</a></p>",
            ),
            202,
        )
    await asyncio.to_thread(set_first_view, s.db_path, t)
    pe = html.escape(row.paste_two_lines)
    t_q = quote(t, safe="")
    dl = f"{BASE_URL}/pay/download-conf?t={t_q}"
    inner = f"""
<h1>Ключ AlesVPN</h1>
<p class="sub">Сохраните. Для <strong>AlesVPN</strong> — две строки ниже. Для <strong>WireGuard</strong> — скачайте готовый файл, не вставляйте его в AlesVPN.</p>
<h2>Две строки(скопируй&nbsp;их!!!)</h2>
<pre class="security" style="text-align:left;user-select:all;white-space:pre-wrap;word-break:break-all">{pe}</pre>
<h2>Конфиг WireGuard</h2>
<p><a class="btn btn-main" href="{dl}">Скачать alesvpn.conf</a> — в приложении WireGuard: «Импорт из файла» / «Create from file».</p>
<p class="sub"><a href='{BASE_URL}/'>на главную</a></p>
"""
    return HTMLResponse(_html("Ключ", inner), headers={"Cache-Control": "no-store"})


@app.get("/pay/download-conf")
async def pay_download_conf(t: str | None = None) -> Any:
    """Скачивание .conf по тому же одноразовому t, что и /pay/done (без повторного set_first_view)."""
    if not t or len(t) < 8:
        return HTMLResponse(
            _html("Нет доступа", f"<h1>Нет параметра t</h1>"),
            400,
        )
    s = load_settings()
    row = await asyncio.to_thread(get_by_token, s.db_path, t)
    if not row or not (row.conf_text or "").strip():
        return HTMLResponse(
            _html("Ссылка", f"<h1>Нет данных</h1>"),
            404,
        )
    body = (row.conf_text or "").encode("utf-8")
    return Response(
        content=body,
        media_type="text/plain; charset=utf-8",
        headers={
            "Content-Disposition": 'attachment; filename="alesvpn.conf"',
            "Cache-Control": "no-store",
        },
    )


@app.get("/pay/buy", response_class=HTMLResponse)
async def pay_buy(
    plan: str = "monthly",
    email: str | None = Query(None, description="E-mail для учёта «1 ₽ — первый месяц»"),
) -> Any:
    if not (SHOP_ID and SECRET):
        return HTMLResponse(
            _html("Касса", f"<h1>Касса</h1><p>Задайте YOOKASSA_* в .env</p>"),
            503,
        )
    s = load_settings()
    p = s.db_path
    pl = (plan or "monthly").lower().strip()
    if pl not in PLANS:
        return HTMLResponse(
            _html("Тариф", "<h1>Нет такого плана</h1>"),
            400,
        )
    customer_email: str | None = None
    if pl == "first":
        en = normalize_email(email or "")
        if not email_looks_valid(en):
            return HTMLResponse(
                _html(
                    "E-mail",
                    f"<h1>Первый месяц за 1&nbsp;₽</h1>"
                    f"<p class='sub'>Укажите корректный e-mail на <a href='{BASE_URL}/pay/'>странице оплаты</a> — "
                    f"по нему отмечаем, что акция «1&nbsp;₽» ещё не использовалась.</p>"
                    f"<p class='sub'><a href='{BASE_URL}/pay/'>← к тарифам</a></p>",
                ),
                400,
            )
        if not _is_bypass_first_rub_email(
            en
        ) and first_rub_taken_for_email(p, en):
            return HTMLResponse(
                _html(
                    "Акция",
                    f"<h1>Акция 1&nbsp;₽ уже использована</h1>"
                    f"<p class='sub'>Для <code>{escape(en)}</code> первый месяц за 1&nbsp;₽ уже оформляли. "
                    f"Продлите за 99&nbsp;₽.</p>"
                    f"<p><a class='btn btn-main' href='{BASE_URL}/pay/buy?plan=monthly'>"
                    f"99&nbsp;₽ — месяц</a></p>"
                    f"<p class='sub'><a href='{BASE_URL}/pay/'>все тарифы</a></p>",
                ),
                403,
            )
        customer_email = en
    amount, desc = PLANS[pl]
    idem = str(uuid.uuid4())
    return_token = secrets.token_urlsafe(32)
    # ЮKassa не всегда дописывает paymentId к return; свой ret = return_token — однозначный поиск в БД.
    r_url = f"{BASE_URL}/pay/return?{urlencode({'ret': return_token})}"
    meta: dict[str, str] = {
        "plan": pl,
        "ret": return_token,
    }
    if pl == "first" and customer_email:
        meta["email"] = customer_email
    try:
        y_p = await asyncio.to_thread(
            Payment.create,
            {
                "amount": {"value": amount, "currency": "RUB"},
                "capture": True,
                "description": desc[:128],
                "metadata": meta,
                "confirmation": {
                    "type": "redirect",
                    "return_url": r_url,
                },
            },
            idem,
        )
    except Exception as e:
        log.exception("YooKassa create")
        return HTMLResponse(
            _html("Касса", f"<h1>Ошибка</h1><pre>{escape(str(e)[:2000])}</pre>"),
            502,
        )
    yk_id = (
        getattr(y_p, "id", None) or (y_p.get("id") if isinstance(y_p, dict) else None)
    )
    if not yk_id:
        return HTMLResponse(
            _html("Касса", "<h1>Нет id</h1>"),
            500,
        )
    try:
        await asyncio.to_thread(
            insert_order,
            p,
            yk_id=yk_id,
            plan_code=pl,
            amount_value=amount,
            return_token=return_token,
            status="created",
            customer_email=customer_email,
        )
    except RuntimeError as e:
        log.error("order insert: %s", e)
        return HTMLResponse(
            _html(
                "Ошибка БД",
                f"<h1>Не удалось зафиксировать заказ</h1><p>ID в ЮKassa: <code>{escape(yk_id)}</code> — "
                f"сохраните, свяжитесь с поддержкой. <pre>{escape(str(e)[:1000])}</pre></p>",
            ),
            500,
        )
    url = y_p.confirmation
    c_url = getattr(url, "confirmation_url", None) if url else None
    if not c_url and url and isinstance(url, dict):
        c_url = url.get("confirmation_url")
    if not c_url and hasattr(y_p, "confirmation") and isinstance(
        y_p.confirmation, dict
    ):
        c_url = y_p.confirmation.get("confirmation_url")
    if not c_url:
        return HTMLResponse(
            _html("Касса", f"<h1>Нет ссылки</h1>"),
            500,
        )
    return RedirectResponse(c_url, 302)


def _pay_index_body() -> str:
    b = BASE_URL
    return f"""
<h1>Оплата AlesVPN</h1>
<p class="sub">Оплата в ЮKassa, затем — страница с ключом.</p>
<p class="sub">Первый месяц за 1&nbsp;₽ — один раз на e-mail. Укажите тот же адрес, что в чеке, если касса попросит.</p>
<form class="sub" method="get" action="{b}/pay/buy" style="max-width:22rem;margin:1rem 0">
  <input type="hidden" name="plan" value="first" />
  <label for="pemail">E-mail</label>
  <input type="email" name="email" id="pemail" required placeholder="name@mail.ru" autocomplete="email" style="width:100%;margin:0.4rem 0" />
  <p><button type="submit" class="btn btn-main" style="width:100%;border:none;cursor:pointer">1&nbsp;₽ — первый месяц</button></p>
</form>
<p><a class="btn btn-main" href="{b}/pay/buy?plan=monthly">99&nbsp;₽ — месяц</a></p>
<p><a class="btn btn-main" href="{b}/pay/buy?plan=m6">499&nbsp;₽ — 6 месяцев</a></p>
<p><a class="btn btn-main" href="{b}/pay/buy?plan=m12">999&nbsp;₽ — 12 месяцев</a></p>
<p class="sub"><a href="{b}/">на главную</a></p>
"""


@app.get("/pay", response_class=HTMLResponse, include_in_schema=False)
@app.get("/pay/", response_class=HTMLResponse, include_in_schema=False)
async def pay_index() -> Any:
    return HTMLResponse(_html("Оплата AlesVPN", _pay_index_body()))


@app.post("/pay/hook", include_in_schema=False)
async def pay_hook(request: Request) -> Any:
    """YooKassa: payment.succeeded — догнать выдачу, если return не сработал."""
    if not (SHOP_ID and SECRET):
        return JSONResponse({"ok": False}, 503)
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"ok": True})
    try:
        ev = (body or {}).get("event") or ""
        obj = (body or {}).get("object")
        if isinstance(obj, str):
            pid = obj
        else:
            obj = obj or {}
            pid = (obj or {}).get("id") or ""
        if ev == "payment.succeeded" and isinstance(
            pid, str
        ) and len(pid) > 4:
            s = load_settings()
            await asyncio.to_thread(
                _maybe_redeem_first_rub_sync, s.db_path, pid
            )
            err = await _do_provision(pid)
            if err:
                log.warning("hook provision: %s", err[:200])
    except Exception as e:  # pragma: no cover
        log.exception("webhook: %s", e)
    return JSONResponse({"ok": True})
