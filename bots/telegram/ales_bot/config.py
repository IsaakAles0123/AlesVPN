"""Загрузка настроек из переменных окружения (.env)."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()


def _truthy(raw: str | None) -> bool:
    if not raw:
        return False
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _parse_admin_ids(raw: str) -> tuple[int, ...]:
    out: list[int] = []
    for part in raw.split(","):
        part = part.strip()
        if part.isdigit():
            out.append(int(part))
    return tuple(out)


@dataclass(frozen=True)
class Settings:
    bot_token: str
    admin_ids: tuple[int, ...]
    price_stars: int
    product_title: str
    product_description: str
    payment_provider_token: str | None
    db_path: Path
    support_username: str | None
    wg_auto_provision: bool
    wg_binary: str
    wg_add_peer_script: Path
    wg_server_public_key: str
    wg_endpoint: str
    wg_dns: str
    wg_allowed_ips: str
    wg_keepalive: str
    wg_subnet_prefix: str
    wg_octet_min: int
    wg_octet_max: int
    happ_auto_provision: bool
    xui_base_url: str
    xui_username: str
    xui_password: str
    xui_api_token: str
    xui_inbound_id: int
    xui_public_host: str
    xui_public_port: int
    xui_pbk: str
    xui_sid: str
    xui_sni: str
    xui_fp: str
    xui_flow: str
    invoice_payload: str = "alesvpn_sub_v1"


def load_settings() -> Settings:
    token = (os.getenv("BOT_TOKEN") or "").strip()
    if not token:
        if _truthy(os.getenv("PAY_API_MODE")):
            token = "__PAY_API_NO_BOT__"
        else:
            raise RuntimeError("Задайте BOT_TOKEN в .env (см. .env.example)")

    price = int(os.getenv("PRICE_STARS") or "50")
    if price < 1:
        raise ValueError("PRICE_STARS должен быть >= 1")

    ppt = os.getenv("PAYMENT_PROVIDER_TOKEN")
    ppt = ppt.strip() if ppt else None
    if ppt == "":
        ppt = None

    db_raw = (os.getenv("DB_PATH") or ".payments.sqlite").strip()
    db_path = Path(db_raw).expanduser()

    sup = (os.getenv("SUPPORT_USERNAME") or "").strip().lstrip("@")
    support_username = sup if sup else None

    wg_auto = _truthy(os.getenv("WG_AUTO_PROVISION"))
    wg_bin = (os.getenv("WG_BINARY") or "wg").strip()
    wg_script = Path(
        (os.getenv("WG_ADD_PEER_SCRIPT") or "/usr/local/bin/wg-add-peer.sh").strip(),
    ).expanduser()
    wg_srv_pk = (os.getenv("WG_SERVER_PUBLIC_KEY") or "").strip()
    wg_ep = (os.getenv("WG_ENDPOINT") or "").strip()
    wg_dns = (os.getenv("WG_DNS") or "1.1.1.1").strip()
    wg_allowed = (os.getenv("WG_ALLOWED_IPS") or "0.0.0.0/0, ::/0").strip()
    wg_ka = (os.getenv("WG_KEEPALIVE") or "25").strip()
    wg_prefix = (os.getenv("WG_SUBNET_PREFIX") or "10.8.0").strip()
    wg_min = int(os.getenv("WG_OCTET_MIN") or "20")
    wg_max = int(os.getenv("WG_OCTET_MAX") or "250")

    if wg_auto and (not wg_srv_pk or not wg_ep):
        raise RuntimeError(
            "WG_AUTO_PROVISION включён: задайте WG_SERVER_PUBLIC_KEY и WG_ENDPOINT в .env",
        )
    if wg_min < 2 or wg_max > 254 or wg_min > wg_max:
        raise ValueError("WG_OCTET_MIN / WG_OCTET_MAX должны быть в диапазоне 2–254 и min ≤ max")

    happ_auto = _truthy(os.getenv("HAPP_AUTO_PROVISION"))
    xui_base = (os.getenv("XUI_BASE_URL") or "").strip()
    xui_user = (os.getenv("XUI_USERNAME") or "").strip()
    xui_pass = os.getenv("XUI_PASSWORD") or ""
    xui_token = (os.getenv("XUI_API_TOKEN") or "").strip()
    xui_inbound = int(os.getenv("XUI_INBOUND_ID") or "0")
    xui_host = (os.getenv("XUI_PUBLIC_HOST") or "").strip()
    xui_port = int(os.getenv("XUI_PUBLIC_PORT") or "8443")
    xui_pbk = (os.getenv("XUI_PBK") or "").strip()
    xui_sid = (os.getenv("XUI_SID") or "").strip()
    xui_sni = (os.getenv("XUI_SNI") or "www.cloudflare.com").strip()
    xui_fp = (os.getenv("XUI_FP") or "safari").strip()
    xui_flow = (os.getenv("XUI_FLOW") or "xtls-rprx-vision").strip()

    if happ_auto:
        if not xui_base:
            raise RuntimeError("HAPP_AUTO_PROVISION: задайте XUI_BASE_URL")
        if not xui_token and (not xui_user or not xui_pass):
            raise RuntimeError(
                "HAPP_AUTO_PROVISION: задайте XUI_API_TOKEN или XUI_USERNAME+XUI_PASSWORD",
            )
        if xui_inbound < 1:
            raise RuntimeError("HAPP_AUTO_PROVISION: задайте XUI_INBOUND_ID")
        if not xui_host or not xui_pbk or not xui_sid:
            raise RuntimeError(
                "HAPP_AUTO_PROVISION: задайте XUI_PUBLIC_HOST, XUI_PBK, XUI_SID",
            )

    return Settings(
        bot_token=token,
        admin_ids=_parse_admin_ids(os.getenv("ADMIN_IDS") or ""),
        price_stars=price,
        product_title=(os.getenv("PRODUCT_TITLE") or "AlesVPN").strip(),
        product_description=(
            os.getenv("PRODUCT_DESCRIPTION") or "Доступ к VPN."
        ).strip(),
        payment_provider_token=ppt,
        db_path=db_path,
        support_username=support_username,
        wg_auto_provision=wg_auto,
        wg_binary=wg_bin,
        wg_add_peer_script=wg_script,
        wg_server_public_key=wg_srv_pk,
        wg_endpoint=wg_ep,
        wg_dns=wg_dns,
        wg_allowed_ips=wg_allowed,
        wg_keepalive=wg_ka,
        wg_subnet_prefix=wg_prefix,
        wg_octet_min=wg_min,
        wg_octet_max=wg_max,
        happ_auto_provision=happ_auto,
        xui_base_url=xui_base,
        xui_username=xui_user,
        xui_password=xui_pass,
        xui_api_token=xui_token,
        xui_inbound_id=xui_inbound,
        xui_public_host=xui_host,
        xui_public_port=xui_port,
        xui_pbk=xui_pbk,
        xui_sid=xui_sid,
        xui_sni=xui_sni,
        xui_fp=xui_fp,
        xui_flow=xui_flow,
    )


def is_admin(user_id: int, settings: Settings) -> bool:
    return user_id in settings.admin_ids


def normalize_platform(raw: str | None) -> str:
    p = (raw or "").strip().lower()
    if p in ("ios", "iphone", "happ", "apple"):
        return "ios"
    if p in ("android", "wg", "wireguard"):
        return "android"
    return ""
