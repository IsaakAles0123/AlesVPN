# Generates 5 WireGuard clients: keys + paste for app + peers snippet for wg0.conf
# Requires: WireGuard for Windows (wg.exe)

$ErrorActionPreference = "Stop"
$wg = "C:\Program Files\WireGuard\wg.exe"
if (-not (Test-Path $wg)) {
    Write-Error "wg.exe not found at $wg - install WireGuard from wireguard.com"
}

$root = Join-Path (Split-Path $PSScriptRoot -Parent) "keys\batch-5-clients"
New-Item -ItemType Directory -Force -Path $root | Out-Null

$peerBlocks = New-Object System.Collections.ArrayList

for ($i = 1; $i -le 5; $i++) {
    $num = "{0:D2}" -f $i
    $lastOctet = $i + 1
    $vpnIp = "10.8.0.$lastOctet/32"
    $dir = Join-Path $root "client-$num"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $priv = & $wg genkey
    $pub = ($priv | & $wg pubkey)

    Set-Content -Path (Join-Path $dir "client_private.key") -Value $priv -NoNewline -Encoding ascii
    Set-Content -Path (Join-Path $dir "client_public.key") -Value $pub -NoNewline -Encoding ascii

    $paste = "$priv`n$vpnIp`n"
    Set-Content -Path (Join-Path $dir "paste-for-app.txt") -Value $paste -Encoding utf8

    $block = @"

[Peer]
# client-$num $vpnIp
PublicKey = $pub
AllowedIPs = $vpnIp
PersistentKeepalive = 25
"@
    [void]$peerBlocks.Add($block)
}

$peersPath = Join-Path $root "SERVER-append-these-peers-to-wg0.conf"
$header = @"
# Remove ALL old [Peer] blocks from wg0.conf first. Backup: cp wg0.conf wg0.conf.bak
# Append this file after [Interface] section. Then: wg-quick down wg0 && wg-quick up wg0

"@
Set-Content -Path $peersPath -Value ($header + ($peerBlocks -join "")) -Encoding utf8

Write-Host "OK: $root"
Write-Host "Peers snippet: $peersPath"
