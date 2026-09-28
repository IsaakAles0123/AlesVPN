"""Склейка подписок нескольких нод 3x-ui в один URL для Happ.

Порядок SUB_SOURCES = рекомендуемый первым (NL/AMS), запасной вторым (DE/FRA).
"""

from __future__ import annotations

import base64
import binascii
import logging
import os
import ssl
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from fastapi import FastAPI, HTTPException, Response

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("sub_api")

app = FastAPI(title="AlesVPN sub merge", docs_url=None, redoc_url=None)

PROFILE_TITLE = (os.getenv("SUB_PROFILE_TITLE") or "AlesVPN").strip() or "AlesVPN"
UPDATE_INTERVAL = (os.getenv("SUB_UPDATE_INTERVAL") or "12").strip() or "12"

# 3x-ui sub часто на self-signed HTTPS (localhost / IP)
_SSL_CTX = ssl._create_unverified_context()


def _open(req: Request, timeout: float = 8):
    if (req.full_url or "").startswith("https://"):
        return urlopen(req, timeout=timeout, context=_SSL_CTX)
    return urlopen(req, timeout=timeout)


def _sources() -> list[str]:
    raw = (os.getenv("SUB_SOURCES") or "").strip()
    if not raw:
        raise RuntimeError("Задайте SUB_SOURCES=url1,url2 (базы 3x-ui sub без subId)")
    out: list[str] = []
    for part in raw.split(","):
        u = part.strip().rstrip("/") + "/"
        if u.startswith("http://") or u.startswith("https://"):
            out.append(u)
    if not out:
        raise RuntimeError("SUB_SOURCES пуст")
    return out


def _decode_sub_body(raw: bytes) -> list[str]:
    text = raw.decode("utf-8", errors="replace").strip()
    if not text:
        return []
    # 3x-ui чаще отдаёт base64 одним блоком
    try:
        pad = "=" * (-len(text) % 4)
        decoded = base64.b64decode(text + pad, validate=False).decode("utf-8", errors="replace")
        if "://" in decoded or decoded.lstrip().startswith("vless"):
            text = decoded
    except (binascii.Error, ValueError):
        pass
    lines: list[str] = []
    for line in text.replace("\r", "\n").split("\n"):
        line = line.strip()
        if line and not line.startswith("#"):
            lines.append(line)
    return lines


def _fetch_one(base: str, sub_id: str) -> list[str]:
    url = f"{base.rstrip('/')}/{sub_id.strip()}"
    req = Request(url, headers={"User-Agent": "AlesVPN-SubMerge/1.0", "Accept": "*/*"})
    try:
        with _open(req, timeout=8) as resp:
            raw = resp.read()
    except HTTPError as e:
        log.warning("sub fetch HTTP %s %s", e.code, url)
        return []
    except (URLError, OSError, ValueError) as e:
        log.warning("sub fetch fail %s: %s", url, e)
        return []
    except Exception as e:
        # http.client.UnknownProtocol и пр. — не роняем весь /s/
        log.warning("sub fetch error %s: %s", url, e)
        return []
    return _decode_sub_body(raw)


def _prefer_key(line: str) -> tuple[int, str]:
    low = line.lower()
    if "⭐" in line or "nl " in low or "🇳🇱" in line or "ams" in low:
        return (0, line)
    if "de " in low or "🇩🇪" in line or "fra" in low:
        return (2, line)
    return (1, line)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"ok": "1"}


@app.get("/s/{sub_id}")
def merged_sub(sub_id: str) -> Response:
    sid = (sub_id or "").strip()
    if len(sid) < 4 or len(sid) > 128 or not sid.replace("-", "").isalnum():
        raise HTTPException(404, "not found")

    try:
        sources = _sources()
    except RuntimeError as e:
        raise HTTPException(503, str(e)) from e

    lines: list[str] = []
    seen: set[str] = set()
    for base in sources:
        for line in _fetch_one(base, sid):
            if line not in seen:
                seen.add(line)
                lines.append(line)

    if not lines:
        raise HTTPException(404, "empty")

    lines.sort(key=_prefer_key)
    payload = "\n".join(lines) + "\n"
    body = base64.b64encode(payload.encode("utf-8"))
    title_b64 = base64.b64encode(PROFILE_TITLE.encode("utf-8")).decode("ascii")
    headers = {
        "Content-Type": "text/plain; charset=utf-8",
        "Profile-Update-Interval": UPDATE_INTERVAL,
        "Profile-Title": f"base64:{title_b64}",
        "Cache-Control": "no-store",
    }
    return Response(content=body, media_type="text/plain; charset=utf-8", headers=headers)
