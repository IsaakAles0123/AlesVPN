# Generates importable WireGuard client .conf (valid PrivateKey + [Peer]).
# Output: keys/generated/ (gitignored). Run: powershell -File tools\generate-alesvpn-windows-conf.ps1

$ErrorActionPreference = "Stop"

$wgCandidates = @(
    "C:\Program Files\WireGuard\wg.exe",
    "${env:ProgramFiles(x86)}\WireGuard\wg.exe"
)
$wg = $wgCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $wg) {
    Write-Error "wg.exe not found. Install WireGuard from https://www.wireguard.com/install/"
}

$repoRoot = Split-Path $PSScriptRoot -Parent

$outDir = Join-Path $repoRoot "keys\generated"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$priv = & $wg genkey
$pub = ($priv | & $wg pubkey)

# Same server as mobile Android wg_sample.conf; edit if your VPS changed.
$serverPublicKey = "Rc1neiBkkHNmwFV/2YiWsVJyEc9V0rQm03QnMaWR7Qs="
$endpoint = "5.42.122.172:51820"
# Pick a free IP from your accounting; server must wg-add-peer this /32.
$clientVpnIp = "10.8.0.10/32"

$confPath = Join-Path $outDir "alesvpn-windows-import.conf"
$stamp = Get-Date -Format "yyyy-MM-dd HH:mm"
$conf = @"
# Generated $stamp - do not commit (keys/ is gitignored).
# Before connecting: add peer on server using client-public.key public key.

[Interface]
PrivateKey = $priv
Address = $clientVpnIp
DNS = 1.1.1.1

[Peer]
PublicKey = $serverPublicKey
Endpoint = $endpoint
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
"@

# WireGuard rejects UTF-8 with BOM (\ufeff) — Windows PowerShell "utf8" adds BOM.
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($confPath, $conf + "`n", $utf8NoBom)
$pubPath = Join-Path $outDir "client-public.key"
[System.IO.File]::WriteAllText($pubPath, $pub, $utf8NoBom)

Write-Host "OK: $confPath"
Write-Host "Client public key (for wg-add-peer.sh): $pub"
Write-Host "WireGuard: Import tunnel from file -> select alesvpn-windows-import.conf"
