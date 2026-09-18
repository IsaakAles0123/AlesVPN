"""Создание клиента VLESS Reality в 3x-ui и сборка ссылки для Happ."""

from __future__ import annotations

import json
import logging
import secrets
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urljoin
from urllib.request import Request, build_opener, HTTPCookieProcessor
from http.cookiejar import CookieJar

from ales_bot.config import Settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class VlessProvisionResult:
    uuid: str
    email: str
    link: str
    expiry_ms: int


class VlessProvisionError(Exception):
    pass


def plan_to_expiry_ms(plan_code: str) -> int:
    """expiryTime в миллисекундах Unix (как в 3x-ui). 0 = без срока."""
    days = {
        "first": 31,
        "monthly": 31,
        "m6": 186,
        "m12": 372,
        "stars": 31,
        "admin_free": 31,
    }.get((plan_code or "").strip().lower(), 31)
    if days <= 0:
        return 0
    when = datetime.now(timezone.utc) + timedelta(days=days)
    return int(when.timestamp() * 1000)


def build_vless_link(
    settings: Settings,
    client_uuid: str,
    remark: str,
) -> str:
    host = settings.xui_public_host.strip()
    port = settings.xui_public_port
    pbk = settings.xui_pbk.strip()
    sid = settings.xui_sid.strip()
    sni = settings.xui_sni.strip()
    fp = settings.xui_fp.strip() or "safari"
    flow = settings.xui_flow.strip() or "xtls-rprx-vision"
    if not host or not pbk or not sid or not sni:
        raise VlessProvisionError("Не заданы XUI_PUBLIC_HOST / XUI_PBK / XUI_SID / XUI_SNI")
    q = urlencode(
        {
            "encryption": "none",
            "flow": flow,
            "fp": fp,
            "pbk": pbk,
            "security": "reality",
            "sid": sid,
            "sni": sni,
            "spx": "/",
            "type": "tcp",
        }
    )
    name = quote(remark[:64] or "alesvpn-ios", safe="")
    return f"vless://{client_uuid}@{host}:{port}?{q}#{name}"


class _XuiSession:
    def __init__(self, settings: Settings) -> None:
        base = settings.xui_base_url.rstrip("/") + "/"
        self._base = base
        self._settings = settings
        self._jar = CookieJar()
        self._opener = build_opener(HTTPCookieProcessor(self._jar))

    def _url(self, path: str) -> str:
        return urljoin(self._base, path.lstrip("/"))

    def _request(
        self,
        method: str,
        path: str,
        *,
        data: bytes | None = None,
        headers: dict[str, str] | None = None,
        form: dict[str, str] | None = None,
    ) -> tuple[int, str]:
        hdrs = {"User-Agent": "AlesVPN-Provision/1.0", "Accept": "application/json"}
        if headers:
            hdrs.update(headers)
        body = data
        if form is not None:
            body = urlencode(form).encode("utf-8")
            hdrs["Content-Type"] = "application/x-www-form-urlencoded"
        req = Request(self._url(path), data=body, headers=hdrs, method=method.upper())
        try:
            with self._opener.open(req, timeout=30) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                return int(getattr(resp, "status", 200) or 200), raw
        except HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace") if e.fp else ""
            return int(e.code), raw
        except URLError as e:
            raise VlessProvisionError(f"3x-ui недоступен: {e}") from e

    def _csrf_token(self) -> str:
        """Новые 3x-ui требуют CSRF на POST /login (иначе HTTP 403)."""
        for path in ("csrf-token", "panel/csrf-token"):
            code, raw = self._request("GET", path)
            if code >= 400 or not raw.strip():
                continue
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                # иногда отдают сам токен текстом
                tok = raw.strip().strip('"')
                if tok:
                    return tok
                continue
            if isinstance(payload, dict):
                for key in ("csrfToken", "csrf_token", "token", "data"):
                    val = payload.get(key)
                    if isinstance(val, str) and val.strip():
                        return val.strip()
                    if isinstance(val, dict):
                        inner = val.get("csrfToken") or val.get("token")
                        if isinstance(inner, str) and inner.strip():
                            return inner.strip()
        return ""

    def login(self) -> None:
        token = (self._settings.xui_api_token or "").strip()
        if token:
            # Bearer на /panel/api/* — login не нужен
            return
        user = self._settings.xui_username.strip()
        password = self._settings.xui_password
        if not user or not password:
            raise VlessProvisionError("Задайте XUI_API_TOKEN или XUI_USERNAME + XUI_PASSWORD")

        csrf = self._csrf_token()
        body = json.dumps(
            {"username": user, "password": password},
            ensure_ascii=False,
        ).encode("utf-8")
        headers: dict[str, str] = {"Content-Type": "application/json"}
        if csrf:
            headers["X-CSRF-Token"] = csrf

        code, raw = self._request("POST", "login", data=body, headers=headers)
        # fallback: form-urlencoded (старые панели)
        if code >= 400:
            code, raw = self._request(
                "POST",
                "login",
                form={"username": user, "password": password},
                headers={"X-CSRF-Token": csrf} if csrf else None,
            )
        if code >= 400:
            raise VlessProvisionError(
                f"3x-ui login HTTP {code}: {raw[:300]}. "
                "Проще: Settings → Security → API Token → XUI_API_TOKEN в .env"
            )
        try:
            payload = json.loads(raw) if raw.strip() else {}
        except json.JSONDecodeError:
            payload = {}
        if isinstance(payload, dict) and payload.get("success") is False:
            raise VlessProvisionError(f"3x-ui login: {payload.get('msg') or raw[:300]}")

    def _auth_headers(self) -> dict[str, str]:
        token = (self._settings.xui_api_token or "").strip()
        if token:
            return {"Authorization": f"Bearer {token}"}
        return {}

    def add_client(
        self,
        *,
        inbound_id: int,
        client_uuid: str,
        email: str,
        flow: str,
        expiry_ms: int,
        sub_id: str,
    ) -> None:
        client_obj = {
            "id": client_uuid,
            "email": email,
            "flow": flow,
            "limitIp": 0,
            "totalGB": 0,
            "expiryTime": expiry_ms,
            "enable": True,
            "tgId": 0,
            "subId": sub_id,
            "comment": "",
            "reset": 0,
        }
        hdrs = {
            **self._auth_headers(),
            "Content-Type": "application/json",
        }

        # Современная 3x-ui: POST /panel/api/clients/add
        modern = {
            "client": client_obj,
            "inboundIds": [inbound_id],
        }
        code, raw = self._request(
            "POST",
            "panel/api/clients/add",
            data=json.dumps(modern, ensure_ascii=False).encode("utf-8"),
            headers=hdrs,
        )
        # Старые панели: POST /panel/api/inbounds/addClient
        if code == 404:
            legacy = {
                "id": inbound_id,
                "settings": json.dumps({"clients": [client_obj]}, ensure_ascii=False),
            }
            code, raw = self._request(
                "POST",
                "panel/api/inbounds/addClient",
                data=json.dumps(legacy, ensure_ascii=False).encode("utf-8"),
                headers=hdrs,
            )

        if code >= 400:
            raise VlessProvisionError(
                f"addClient HTTP {code}: {raw[:500]}. "
                "Проверьте XUI_INBOUND_ID (id inbound Reality в панели)."
            )
        try:
            payload: Any = json.loads(raw) if raw.strip() else {}
        except json.JSONDecodeError as e:
            raise VlessProvisionError(f"addClient: не JSON: {raw[:300]}") from e
        if isinstance(payload, dict) and payload.get("success") is False:
            raise VlessProvisionError(
                f"addClient: {payload.get('msg') or payload.get('message') or raw[:400]}"
            )


def _make_email_tag(prefix: str) -> str:
    safe = "".join(c if c.isalnum() or c in "-_" else "" for c in prefix)[:24] or "ios"
    return f"{safe}-{secrets.token_hex(3)}"


async def provision_vless_after_payment(
    settings: Settings,
    *,
    plan_code: str,
    email_prefix: str = "ios",
) -> VlessProvisionResult:
    if not settings.happ_auto_provision:
        raise VlessProvisionError("Автовыдача Happ выключена (HAPP_AUTO_PROVISION)")
    if settings.xui_inbound_id < 1:
        raise VlessProvisionError("Задайте XUI_INBOUND_ID")

    client_uuid = str(uuid.uuid4())
    email = _make_email_tag(email_prefix)
    expiry_ms = plan_to_expiry_ms(plan_code)
    flow = settings.xui_flow.strip() or "xtls-rprx-vision"
    sub_id = secrets.token_hex(8)

    def _sync() -> VlessProvisionResult:
        session = _XuiSession(settings)
        session.login()
        session.add_client(
            inbound_id=settings.xui_inbound_id,
            client_uuid=client_uuid,
            email=email,
            flow=flow,
            expiry_ms=expiry_ms,
            sub_id=sub_id,
        )
        link = build_vless_link(settings, client_uuid, f"alesvpn-{email}")
        return VlessProvisionResult(
            uuid=client_uuid,
            email=email,
            link=link,
            expiry_ms=expiry_ms,
        )

    import asyncio

    return await asyncio.to_thread(_sync)
