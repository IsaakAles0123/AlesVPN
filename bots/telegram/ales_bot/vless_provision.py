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

from ales_bot.config import Settings, XuiPanel

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class VlessProvisionResult:
    uuid: str
    email: str
    link: str
    sub_id: str
    expiry_ms: int


@dataclass(frozen=True)
class ExistingClient:
    """Уже выданная подписка: продлеваем её, а не плодим новые ключи."""

    uuid: str
    email: str
    sub_id: str
    expiry_ms: int


class VlessProvisionError(Exception):
    pass


def plan_days(plan_code: str) -> int:
    return {
        "first": 31,
        "monthly": 31,
        "m6": 186,
        "m12": 372,
        "stars": 31,
        "admin_free": 31,
    }.get((plan_code or "").strip().lower(), 31)


def plan_to_expiry_ms(plan_code: str) -> int:
    """expiryTime в миллисекундах Unix (как в 3x-ui). 0 = без срока."""
    days = plan_days(plan_code)
    if days <= 0:
        return 0
    when = datetime.now(timezone.utc) + timedelta(days=days)
    return int(when.timestamp() * 1000)


def extend_expiry_ms(current_ms: int, plan_code: str) -> int:
    """Продление: от текущего срока, если он ещё не истёк, иначе от сегодня."""
    days = plan_days(plan_code)
    if days <= 0:
        return 0
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    start = current_ms if current_ms and current_ms > now_ms else now_ms
    return start + days * 86_400_000


def build_subscription_link(settings: Settings, sub_id: str) -> str:
    base = (settings.xui_sub_base_url or "").strip().rstrip("/")
    sid = (sub_id or "").strip()
    if not base or not sid:
        raise VlessProvisionError("Не заданы XUI_SUB_BASE_URL или subId")
    return f"{base}/{sid}"


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
    def __init__(self, panel: XuiPanel) -> None:
        base = panel.base_url.rstrip("/") + "/"
        self._base = base
        self._panel = panel
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
        token = (self._panel.api_token or "").strip()
        if token:
            # Bearer на /panel/api/* — login не нужен
            return
        user = self._panel.username.strip()
        password = self._panel.password
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
        token = (self._panel.api_token or "").strip()
        if token:
            return {"Authorization": f"Bearer {token}"}
        return {}

    @staticmethod
    def _payload_ok(code: int, raw: str) -> bool:
        if code >= 400 or not raw.strip():
            return False
        try:
            payload: Any = json.loads(raw)
        except json.JSONDecodeError:
            return False
        if isinstance(payload, dict) and payload.get("success") is False:
            return False
        return True

    @staticmethod
    def _client_obj(
        *,
        client_uuid: str,
        email: str,
        flow: str,
        expiry_ms: int,
        sub_id: str,
        limit_ip: int,
    ) -> dict[str, Any]:
        return {
            "id": client_uuid,
            "email": email,
            "flow": flow,
            "limitIp": max(0, int(limit_ip)),
            "totalGB": 0,
            "expiryTime": expiry_ms,
            "enable": True,
            "tgId": 0,
            "subId": sub_id,
            "comment": "",
            "reset": 0,
        }

    def add_client(
        self,
        *,
        inbound_id: int,
        client_uuid: str,
        email: str,
        flow: str,
        expiry_ms: int,
        sub_id: str,
        limit_ip: int = 0,
    ) -> None:
        client_obj = self._client_obj(
            client_uuid=client_uuid,
            email=email,
            flow=flow,
            expiry_ms=expiry_ms,
            sub_id=sub_id,
            limit_ip=limit_ip,
        )
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

        if code == 401:
            raise VlessProvisionError(
                "addClient HTTP 401 (нет доступа к панели). "
                "Проверьте XUI_BASE_URL (path), логин/пароль AMS или "
                "создайте новый API Token в 3x-ui → Настройки → Безопасность "
                "и пропишите XUI_API_TOKEN (уберите старый токен от FRA)."
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

    def _clients_list(self) -> list[dict[str, Any]]:
        hdrs = {**self._auth_headers(), "Accept": "application/json"}
        code, raw = self._request("GET", "panel/api/clients/list", headers=hdrs)
        if not self._payload_ok(code, raw):
            return []
        try:
            payload: Any = json.loads(raw)
        except json.JSONDecodeError:
            return []
        rows = payload.get("obj") if isinstance(payload, dict) else None
        if isinstance(rows, list):
            return [r for r in rows if isinstance(r, dict)]
        return []

    def lookup_client_email(
        self,
        *,
        sub_id: str | None,
        client_uuid: str | None,
        email: str | None,
    ) -> str | None:
        for row in self._clients_list():
            if sub_id and str(row.get("subId") or "") == sub_id:
                found = str(row.get("email") or "").strip()
                if found:
                    return found
            if client_uuid and str(row.get("id") or "") == client_uuid:
                found = str(row.get("email") or "").strip()
                if found:
                    return found
            if email and str(row.get("email") or "") == email:
                return email
        return email or None

    def update_client(
        self,
        *,
        inbound_id: int,
        client_uuid: str,
        email: str,
        flow: str,
        expiry_ms: int,
        sub_id: str,
        limit_ip: int = 0,
        old_email: str | None = None,
    ) -> None:
        client_obj = self._client_obj(
            client_uuid=client_uuid,
            email=email,
            flow=flow,
            expiry_ms=expiry_ms,
            sub_id=sub_id,
            limit_ip=limit_ip,
        )
        hdrs = {
            **self._auth_headers(),
            "Content-Type": "application/json",
        }
        current = self.lookup_client_email(
            sub_id=sub_id,
            client_uuid=client_uuid,
            email=old_email or email,
        ) or old_email or email
        # Актуальный 3x-ui: POST /panel/api/clients/update/:email — тело = сам клиент
        path = f"panel/api/clients/update/{quote(current, safe='')}"
        code, raw = self._request(
            "POST",
            path,
            data=json.dumps(client_obj, ensure_ascii=False).encode("utf-8"),
            headers=hdrs,
        )
        if self._payload_ok(code, raw):
            return
        # Старые панели
        legacy = {
            "id": inbound_id,
            "settings": json.dumps({"clients": [client_obj]}, ensure_ascii=False),
        }
        for ident in (current, old_email, client_uuid):
            if not ident:
                continue
            legacy_path = f"panel/api/inbounds/updateClient/{quote(ident, safe='')}"
            code2, raw2 = self._request(
                "POST",
                legacy_path,
                data=json.dumps(legacy, ensure_ascii=False).encode("utf-8"),
                headers=hdrs,
            )
            if self._payload_ok(code2, raw2):
                return
            raw = raw2
            path = legacy_path
            code = code2
        raise VlessProvisionError(f"updateClient {path} HTTP {code}: {raw[:400]}")


def sanitize_client_name(raw: str) -> str:
    return "".join(c if c.isalnum() or c in "-_" else "" for c in (raw or ""))[:32]


def resolve_client_email(
    prefix: str,
    *,
    existing_email: str | None = None,
    stable: bool = False,
) -> str:
    """Имя клиента в 3x-ui: Telegram-ник, иначе tg{id}-xxxxxx."""
    safe = sanitize_client_name(prefix) or "user"
    if stable:
        return safe
    if existing_email:
        return existing_email
    return f"{safe}-{secrets.token_hex(3)}"


async def provision_vless_after_payment(
    settings: Settings,
    *,
    plan_code: str,
    email_prefix: str = "ios",
    existing: ExistingClient | None = None,
    stable_email: bool = False,
) -> VlessProvisionResult:
    if not settings.happ_auto_provision:
        raise VlessProvisionError("Автовыдача Happ выключена (HAPP_AUTO_PROVISION)")
    panels = settings.xui_panels or ()
    if not panels:
        raise VlessProvisionError("Не заданы панели XUI (XUI_BASE_URL)")
    if panels[0].inbound_id < 1:
        raise VlessProvisionError("Задайте XUI_INBOUND_ID")

    flow = settings.xui_flow.strip() or "xtls-rprx-vision"
    limit_ip = settings.xui_device_limit
    email = resolve_client_email(
        email_prefix,
        existing_email=existing.email if existing is not None else None,
        stable=stable_email,
    )
    if existing is not None:
        client_uuid = existing.uuid
        sub_id = existing.sub_id
        expiry_ms = extend_expiry_ms(existing.expiry_ms, plan_code)
    else:
        client_uuid = str(uuid.uuid4())
        sub_id = secrets.token_hex(8)
        expiry_ms = plan_to_expiry_ms(plan_code)

    def _sync() -> VlessProvisionResult:
        primary_ok = False
        errors: list[str] = []
        for i, panel in enumerate(panels):
            label = panel.label or f"node{i+1}"
            try:
                session = _XuiSession(panel)
                session.login()
                kwargs = {
                    "inbound_id": panel.inbound_id,
                    "client_uuid": client_uuid,
                    "email": email,
                    "flow": flow,
                    "expiry_ms": expiry_ms,
                    "sub_id": sub_id,
                    "limit_ip": limit_ip,
                }
                if existing is None:
                    session.add_client(**kwargs)
                else:
                    try:
                        session.update_client(
                            **kwargs,
                            old_email=existing.email,
                        )
                    except VlessProvisionError as upd_err:
                        # клиента стёрли в панели — создаём заново с тем же subId
                        try:
                            session.add_client(**kwargs)
                        except VlessProvisionError as add_err:
                            if "already in use" in str(add_err).lower():
                                raise VlessProvisionError(
                                    f"Клиент в панели есть, но обновить не вышло: {upd_err}"
                                ) from add_err
                            raise
                primary_ok = primary_ok or i == 0
                log.info(
                    "Happ client %s on %s inbound=%s email=%s",
                    "renewed" if existing is not None else "ok",
                    label,
                    panel.inbound_id,
                    email,
                )
            except Exception as e:
                err = f"{label}: {e}"
                errors.append(err)
                log.exception("Happ provision failed on %s", label)
                if i == 0:
                    raise VlessProvisionError(err) from e

        if settings.xui_link_format == "vless":
            link = build_vless_link(
                settings,
                client_uuid,
                remark="⭐ NL AlesVPN",
            )
        else:
            link = build_subscription_link(settings, sub_id)
        if errors:
            log.warning("Happ partial provision (primary ok): %s", "; ".join(errors))
        return VlessProvisionResult(
            uuid=client_uuid,
            email=email,
            link=link,
            sub_id=sub_id,
            expiry_ms=expiry_ms,
        )

    import asyncio

    return await asyncio.to_thread(_sync)
