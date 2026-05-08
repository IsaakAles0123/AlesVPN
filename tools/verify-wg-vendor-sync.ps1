# Сверяет wg_vendor_* из Android strings.xml с WgVendorConfig в iOS WgConfigBuilder.swift.
# Запуск из корня репозитория: .\tools\verify-wg-vendor-sync.ps1
# Код 0 — совпадают; код 1 — расхождение (перед коммитом правьте оба файла).

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$strings = Join-Path $root "mobile\android\app\src\main\res\values\strings.xml"
$swift = Join-Path $root "mobile\ios\AlesVPN\Sources\App\WgConfigBuilder.swift"

function Get-XmlValue([string]$content, [string]$name) {
    if ($content -match "<string name=`"$name`"[^>]*>([^<]+)</string>") {
        return $Matches[1].Trim()
    }
    return $null
}

function Get-SwiftStatic([string]$content, [string]$prop) {
    if ($content -match "static let $prop = `"([^`"]*)`"") {
        return $Matches[1].Trim()
    }
    return $null
}

$s = Get-Content -Raw $strings
$w = Get-Content -Raw $swift

$map = @{
    "serverPublicKey" = "wg_vendor_server_public_key"
    "endpoint"        = "wg_vendor_endpoint"
    "dns"             = "wg_vendor_dns"
    "allowedIPs"      = "wg_vendor_allowed_ips"
    "persistentKeepalive" = "wg_vendor_persistent_keepalive"
}

$ok = $true
foreach ($entry in $map.GetEnumerator()) {
    $swiftVal = Get-SwiftStatic $w $entry.Key
    $xmlVal = Get-XmlValue $s $entry.Value
    if ($swiftVal -ne $xmlVal) {
        Write-Host "MISMATCH $($entry.Key): Swift='$swiftVal' XML='$xmlVal'" -ForegroundColor Red
        $ok = $false
    }
}

if ($ok) {
    Write-Host "OK: WgVendorConfig matches strings.xml (wg_vendor_*)." -ForegroundColor Green
    exit 0
}
exit 1
