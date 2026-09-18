"""
AlesVPN: YooKassa (веб) → редирект в кассу → return → доступ и страница /pay/done?t=.

Платформы:
  • android — WireGuard (WG_AUTO_PROVISION)
  • ios — Happ / VLESS Reality (HAPP_AUTO_PROVISION + 3x-ui)

Uvicorn (1 worker, тот же .env что у бота + YOOKASSA_*):
  set PAY_API_MODE=1
  python -m uvicorn main:app --host 127.0.0.1 --port 8008

Nginx: location /pay/  proxy на этот порт, см. README.
"""

from __future__ import annotations

import html
import hmac
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

from ales_bot.config import load_settings, normalize_platform
from ales_bot.db import allocate_next_octet_async, init_db, init_db_async
from ales_bot.vless_provision import VlessProvisionError, provision_vless_after_payment
from ales_bot.wg_provision import WgProvisionError, provision_after_payment
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from fastapi.staticfiles import StaticFiles
from yookassa import Configuration, Payment

from yk_store import (
    consume_token_once,
    get_by_token,
    get_by_yk_id,
    init_yk,
    insert_order,
    set_provision_error,
    set_provision_ok,
    set_provision_vless_ok,
)

log = logging.getLogger("pay_api")
logging.basicConfig(level=logging.INFO)

PLANS: dict[str, tuple[str, str]] = {
    "monthly": ("75.00", "AlesVPN: 1 мес, 75 ₽"),
    "m6": ("350.00", "AlesVPN: 6 мес, 350 ₽"),
    "m12": ("800.00", "AlesVPN: 12 мес, 800 ₽"),
}

BASE_URL = (os.getenv("PAY_BASE_URL") or "https://alesvpn.ru").rstrip("/")
SHOP_ID = (os.getenv("YOOKASSA_SHOP_ID") or "").strip()
SECRET = (os.getenv("YOOKASSA_SECRET_KEY") or "").strip()
WEBHOOK_TOKEN = (os.getenv("PAY_WEBHOOK_TOKEN") or "").strip()

# сериализация выдачи, чтобы не двоить октет
_provision_lock = asyncio.Lock()


def _html(title: str, body: str) -> str:
    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="dark" />
  <title>{escape(title)}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="/assets/style.css" />
  <style>
    .paybox{{max-width:52rem;margin:0 auto;padding:1.5rem;}}
    .paybox > h1{{
      text-align:center;
      max-width:none;
      width:100%;
      margin-left:auto;
      margin-right:auto;
    }}
    .paybox > .sub,
    .paybox > .pay-foot{{
      text-align:center;
      max-width:none;
      width:100%;
      margin-left:auto;
      margin-right:auto;
    }}
    .pay-cols{{
      display:grid;
      grid-template-columns:1fr 1fr;
      gap:1.5rem 2rem;
      margin:1.5rem 0 1rem;
      align-items:start;
    }}
    .pay-col{{min-width:0;}}
    .pay-col h2{{text-align:center;margin:0 0 0.5rem;font-size:1.15rem;}}
    .pay-col .sub{{
      text-align:center;
      max-width:none;
      width:100%;
      margin-left:auto;
      margin-right:auto;
    }}
    .pay-col .btn{{display:block;width:100%;box-sizing:border-box;text-align:center;margin:0.45rem 0;}}
    @media (max-width:700px){{
      .pay-cols{{grid-template-columns:1fr;}}
    }}
  </style>
</head>
<body>
  <div id="bg" aria-hidden="true">
    <div class="stars-css"></div>
    <div class="stars-css-dense"></div>
    <div class="stars-tile"></div>
    <div class="stars-tile stars-tile-b"></div>
    <canvas id="star-canvas"></canvas>
    <div class="comets">
      <span class="comet" style="--comet-dur: 13s; --comet-delay: 0s; --comet-rot: atan2(70vh, 85vw); --comet-sx: 5vw; --comet-sy: 8vh; --comet-ex: 90vw; --comet-ey: 78vh; --comet-scale: 0.9"></span>
      <span class="comet" style="--comet-dur: 17s; --comet-delay: 3.2s; --comet-rot: atan2(48vh, 75vw); --comet-sx: 20vw; --comet-sy: 2vh; --comet-ex: 95vw; --comet-ey: 50vh; --comet-scale: 0.75"></span>
      <span class="comet" style="--comet-dur: 11s; --comet-delay: 6.5s; --comet-rot: atan2(55vh, 82vw); --comet-sx: -2vw; --comet-sy: 35vh; --comet-ex: 80vw; --comet-ey: 90vh; --comet-scale: 0.65"></span>
      <span class="comet" style="--comet-dur: 20s; --comet-delay: 1.8s; --comet-rot: atan2(86vh, 69vw); --comet-sx: 3vw; --comet-sy: 2vh; --comet-ex: 72vw; --comet-ey: 88vh; --comet-scale: 0.7"></span>
      <span class="comet" style="--comet-dur: 15s; --comet-delay: 8.2s; --comet-rot: atan2(38vh, 52vw); --comet-sx: 48vw; --comet-sy: 0vh; --comet-ex: 100vw; --comet-ey: 38vh; --comet-scale: 0.55"></span>
    </div>
    <div class="glow-tr"></div>
    <div class="glow-bl"></div>
  </div>
  <div class="wrap paybox" id="content">{body}</div>
  <script src="/assets/stars.js" defer></script>
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


@app.get("/", include_in_schema=False)
async def root() -> RedirectResponse:
    """Сайт-лендинг в nginx на /; pay_api локально смотрят /pay/ — с корня редиректим."""
    return RedirectResponse(url="/pay/", status_code=302)


def _q_pid(request: Request) -> str | None:
    q = request.query_params
    for k in ("paymentId", "payment_id", "id"):
        v = (q.get(k) or "").strip()
        if v:
            return v
    return None


def _order_has_access(row) -> bool:
    if not row:
        return False
    if (row.vless_link or "").strip():
        return True
    if (row.paste_two_lines or "").strip() and (row.conf_text or "").strip():
        return True
    return False


def _redir_to_key(row) -> RedirectResponse:
    return RedirectResponse(f"{BASE_URL}/pay/done?t={row.return_token}", 302)


async def _yoo_get(pid: str) -> Any:
    if not (SHOP_ID and SECRET):
        return None
    return await asyncio.to_thread(Payment.find_one, pid)


def _yoo_amount_value(pay: Any) -> str:
    amount = getattr(pay, "amount", None)
    if isinstance(amount, dict):
        return str(amount.get("value") or "").strip()
    return str(getattr(amount, "value", "") or "").strip()


def _yoo_currency(pay: Any) -> str:
    amount = getattr(pay, "amount", None)
    if isinstance(amount, dict):
        return str(amount.get("currency") or "").strip().upper()
    return str(getattr(amount, "currency", "") or "").strip().upper()


async def _do_provision(yk_id: str) -> str | None:
    """
    Выдать доступ при успехе. Вернуть None при успехе, иначе текст ошибки для HTML.
    platform=ios → Happ/VLESS; android → WireGuard.
    """
    s = load_settings()
    p = s.db_path
    async with _provision_lock:
        r0 = await asyncio.to_thread(get_by_yk_id, p, yk_id)
        if r0 and _order_has_access(r0):
            return None
        if r0 and (r0.provision_error or "").strip():
            return (r0.provision_error or "")[:800]

        platform = normalize_platform(getattr(r0, "platform", None) if r0 else None) or "android"

        try:
            if platform == "ios":
                if not s.happ_auto_provision:
                    e = "Автовыдача Happ выключена (HAPP_AUTO_PROVISION)."
                    await asyncio.to_thread(set_provision_error, p, yk_id, e)
                    return e
                prefix = "web"
                if r0 and r0.customer_email:
                    prefix = (r0.customer_email.split("@")[0] or "web")[:16]
                res = await provision_vless_after_payment(
                    s,
                    plan_code=(r0.plan_code if r0 else "monthly"),
                    email_prefix=prefix,
                )
                await asyncio.to_thread(set_provision_vless_ok, p, yk_id, res.link)
            else:
                if not s.wg_auto_provision:
                    e = "Автовыдача WireGuard выключена (WG_AUTO_PROVISION)."
                    await asyncio.to_thread(set_provision_error, p, yk_id, e)
                    return e
                octet = await allocate_next_octet_async(
                    p, s.wg_octet_min, s.wg_octet_max
                )
                res_wg = await provision_after_payment(s, octet)
                await asyncio.to_thread(
                    set_provision_ok,
                    p,
                    yk_id,
                    res_wg.paste_two_lines,
                    res_wg.conf_text,
                )
        except (WgProvisionError, VlessProvisionError, OSError, RuntimeError) as e:
            msg = f"Платёж принят, но сервер ключа: {e!r}"[:1000]
            log.exception("Provision (%s): %s", platform, e)
            await asyncio.to_thread(set_provision_error, p, yk_id, str(e)[:800])
            return msg
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
    if _order_has_access(row):
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
    err = await _do_provision(yk_id)
    row2 = get_by_yk_id(s.db_path, yk_id)
    if row2 and _order_has_access(row2):
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
    row = await asyncio.to_thread(consume_token_once, s.db_path, t)
    if not row:
        return HTMLResponse(
            _html("Ссылка", "<h1>Ссылка недействительна или уже использована</h1>"),
            410,
        )
    if (row.provision_error or "").strip() and not _order_has_access(row):
        return HTMLResponse(
            _html(
                "Ключ",
                f"<h1>Выдача</h1><p class='sub'>{escape((row.provision_error or '')[:2000])}</p>",
            ),
        )
    platform = normalize_platform(row.platform) or "android"
    if platform == "ios" and (row.vless_link or "").strip():
        link = html.escape(row.vless_link or "")
        inner = f"""
<h1>Доступ AlesVPN — iPhone (Happ)</h1>
<p class="sub">Сохраните ссылку. Страница одноразовая.</p>
<p class="sub">1) Установите приложение <b>Happ</b> из App Store.<br>
2) Скопируйте ссылку ниже → Happ → импорт из буфера.<br>
3) Включите VPN. Отпечаток в конфиге — safari.</p>
<h2>Ссылка vless://</h2>
<pre class="security" style="text-align:left;user-select:all;white-space:pre-wrap;word-break:break-all">{link}</pre>
<p class="sub"><a href='{BASE_URL}/'>на главную</a></p>
"""
        return HTMLResponse(_html("Ключ iOS", inner), headers={"Cache-Control": "no-store"})
    if not row.paste_two_lines or not row.conf_text:
        return HTMLResponse(
            _html(
                "Ждите",
                f"<h1>Ключ готовится</h1><p><a href='{BASE_URL}/pay/return?ret={escape(row.return_token)}'>"
                f"Статус оплаты</a></p>",
            ),
            202,
        )
    pe = html.escape(row.paste_two_lines)
    conf = html.escape(row.conf_text or "")
    inner = f"""
<h1>Доступ AlesVPN — Android (WireGuard)</h1>
<p class="sub">Сохраните данные. Ссылка одноразовая и больше не откроется после этой страницы.</p>
<p class="sub">В приложении AlesVPN вставьте <b>две строки</b> (ключ и адрес) или импортируйте .conf в WireGuard.</p>
<h2>Две строки (скопируй&nbsp;их!!!)</h2>
<pre class="security" style="text-align:left;user-select:all;white-space:pre-wrap;word-break:break-all">{pe}</pre>
<h2>Конфиг WireGuard (.conf)</h2>
<pre class="security" style="text-align:left;user-select:all;white-space:pre-wrap;word-break:break-all">{conf}</pre>
<p class="sub"><a href='{BASE_URL}/'>на главную</a></p>
"""
    return HTMLResponse(_html("Ключ Android", inner), headers={"Cache-Control": "no-store"})


@app.get("/pay/download-conf")
async def pay_download_conf(t: str | None = None) -> Any:
    """Отключено: конфиг доступен только на одноразовой странице /pay/done."""
    return HTMLResponse(
        _html("Ссылка", "<h1>Скачивание отключено</h1><p>Используйте одноразовую страницу ключа.</p>"),
        410,
    )


@app.get("/pay/buy", response_class=HTMLResponse)
async def pay_buy(
    plan: str = "monthly",
    platform: str = "android",
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
    plat = normalize_platform(platform) or "android"
    amount, desc = PLANS[pl]
    plat_label = "iPhone (Happ)" if plat == "ios" else "Android (WireGuard)"
    desc = f"{desc} [{plat_label}]"
    idem = str(uuid.uuid4())
    return_token = secrets.token_urlsafe(32)
    r_url = f"{BASE_URL}/pay/return?{urlencode({'ret': return_token})}"
    meta: dict[str, str] = {
        "plan": pl,
        "ret": return_token,
        "platform": plat,
    }
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
            customer_email=None,
            platform=plat,
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


def _plan_buttons(b: str, plat: str) -> str:
    return f"""
<p><a class="btn btn-main" href="{b}/pay/buy?plan=monthly&platform={plat}">75&nbsp;₽ — месяц</a></p>
<p><a class="btn btn-main" href="{b}/pay/buy?plan=m6&platform={plat}">350&nbsp;₽ — 6 месяцев</a></p>
<p><a class="btn btn-main" href="{b}/pay/buy?plan=m12&platform={plat}">800&nbsp;₽ — 12 месяцев</a></p>
"""


def _pay_index_body() -> str:
    b = BASE_URL
    return f"""
<h1>Оплата AlesVPN</h1>
<p class="sub">Выберите платформу — выдаётся разный доступ.</p>

<div class="pay-cols">
  <section class="pay-col" id="android">
    <h2>Android — WireGuard</h2>
    <p class="sub">Ключ для приложения AlesVPN</p>
    {_plan_buttons(b, "android")}
  </section>
  <section class="pay-col" id="ios">
    <h2>iPhone — Happ</h2>
    <p class="sub">Ссылка <code>vless://</code> для Happ</p>
    {_plan_buttons(b, "ios")}
  </section>
</div>

<p class="sub pay-foot"><a href="{b}/">на главную</a></p>
"""


@app.get("/pay", response_class=HTMLResponse, include_in_schema=False)
@app.get("/pay/", response_class=HTMLResponse, include_in_schema=False)
async def pay_index() -> Any:
    return HTMLResponse(_html("Оплата AlesVPN", _pay_index_body()))


@app.post("/pay/hook", include_in_schema=False)
async def pay_hook(request: Request) -> Any:
    """YooKassa: payment.succeeded — догнать выдачу, если return не сработал.

    Если задан PAY_WEBHOOK_TOKEN — требуется заголовок X-Webhook-Token (можно
    пробросить через nginx). Если токен не задан — типичный POST от ЮKassa:
    защита только проверкой платежа через API и совпадением суммы с заказом в БД.
    """
    if not (SHOP_ID and SECRET):
        return JSONResponse({"ok": False}, 503)
    if WEBHOOK_TOKEN:
        got_token = (request.headers.get("X-Webhook-Token") or "").strip()
        if not hmac.compare_digest(got_token, WEBHOOK_TOKEN):
            return JSONResponse({"ok": False}, 403)
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
            existing = await asyncio.to_thread(get_by_yk_id, s.db_path, pid)
            if not existing:
                return JSONResponse({"ok": True})
            pay = await _yoo_get(pid)
            if not pay or _pay_state(pay) != "succeeded":
                return JSONResponse({"ok": True})
            if _yoo_currency(pay) != "RUB":
                log.warning("hook bad currency: %s", _yoo_currency(pay))
                return JSONResponse({"ok": True})
            if _yoo_amount_value(pay) != (existing.amount_value or "").strip():
                log.warning(
                    "hook amount mismatch: payment=%s expected=%s",
                    _yoo_amount_value(pay),
                    existing.amount_value,
                )
                return JSONResponse({"ok": True})
            err = await _do_provision(pid)
            if err:
                log.warning("hook provision: %s", err[:200])
    except Exception as e:  # pragma: no cover
        log.exception("webhook: %s", e)
    return JSONResponse({"ok": True})


@app.get("/healthz", include_in_schema=False)
async def healthz() -> Any:
    s = load_settings()
    db_ok = s.db_path.exists() or s.db_path.parent.exists()
    return JSONResponse(
        {
            "ok": True,
            "db_path": str(s.db_path),
            "db_ready": db_ok,
            "yookassa_configured": bool(SHOP_ID and SECRET),
            "webhook_token_set": bool(WEBHOOK_TOKEN),
        }
    )


# Локально (Docker / Uvicorn): тот же /assets, что в nginx на проде (web/assets).
_web_assets = _PA.parent / "web" / "assets"
if _web_assets.is_dir():
    app.mount(
        "/assets",
        StaticFiles(directory=str(_web_assets)),
        name="assets",
    )
