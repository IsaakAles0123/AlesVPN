"""Генерация ключей WireGuard и вызов wg-add-peer.sh на VPS."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass

from ales_bot.config import Settings


@dataclass(frozen=True)
class WgProvisionResult:
    private_key: str
    public_key: str
    address_cidr: str
    conf_text: str
    paste_two_lines: str


class WgProvisionError(Exception):
    pass


async def _run_wg(
    wg_bin: str,
    *args: str,
    stdin: bytes | None = None,
) -> str:
    proc = await asyncio.create_subprocess_exec(
        wg_bin,
        *args,
        stdin=asyncio.subprocess.PIPE if stdin is not None else None,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    out_b, err_b = await proc.communicate(stdin)
    if proc.returncode != 0:
        err = err_b.decode(errors="replace").strip()
        raise WgProvisionError(f"wg {' '.join(args)}: {err or proc.returncode}")
    return out_b.decode("ascii").strip()


async def generate_keypair(wg_bin: str) -> tuple[str, str]:
    priv = await _run_wg(wg_bin, "genkey")
    pub = await _run_wg(wg_bin, "pubkey", stdin=priv.encode("ascii"))
    if not priv or not pub:
        raise WgProvisionError("Пустой ключ от wg")
    return priv, pub


def build_client_conf(
    settings: Settings,
    private_key: str,
    address_cidr: str,
) -> str:
    lines = [
        "[Interface]",
        f"PrivateKey = {private_key}",
        f"Address = {address_cidr}",
    ]
    if settings.wg_dns.strip():
        lines.append(f"DNS = {settings.wg_dns.strip()}")
    lines.append("")
    lines.append("[Peer]")
    lines.append(f"PublicKey = {settings.wg_server_public_key.strip()}")
    lines.append(f"Endpoint = {settings.wg_endpoint.strip()}")
    lines.append(f"AllowedIPs = {settings.wg_allowed_ips.strip()}")
    ka = settings.wg_keepalive.strip()
    if ka:
        lines.append(f"PersistentKeepalive = {ka}")
    return "\n".join(lines) + "\n"


async def run_add_peer_script(
    settings: Settings,
    public_key: str,
    allowed_ips: str,
) -> None:
    script = settings.wg_add_peer_script
    if not script.is_file():
        raise WgProvisionError(f"Скрипт не найден: {script}")

    proc = await asyncio.create_subprocess_exec(
        str(script),
        public_key,
        allowed_ips,
        settings.wg_keepalive.strip() or "25",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    out_b, err_b = await proc.communicate()
    if proc.returncode != 0:
        out = out_b.decode(errors="replace").strip()
        err = err_b.decode(errors="replace").strip()
        msg = err or out or str(proc.returncode)
        raise WgProvisionError(f"wg-add-peer: {msg}")


async def provision_after_payment(
    settings: Settings,
    octet: int,
) -> WgProvisionResult:
    if not settings.wg_auto_provision:
        raise WgProvisionError("Автовыдача выключена")

    priv, pub = await generate_keypair(settings.wg_binary)
    prefix = settings.wg_subnet_prefix.strip()
    address_cidr = f"{prefix}.{octet}/32"

    await run_add_peer_script(settings, pub, address_cidr)

    conf = build_client_conf(settings, priv, address_cidr)
    paste = f"{priv}\n{address_cidr}"
    return WgProvisionResult(
        private_key=priv,
        public_key=pub,
        address_cidr=address_cidr,
        conf_text=conf,
        paste_two_lines=paste,
    )
