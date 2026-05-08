# Синхронизация pay_api (и при желании nginx-шаблона) на VPS без git в /var/www/alesvpn-app.
# Запуск из корня репозитория MyVPN:
#   .\scripts\deploy-pay-api-to-vps.ps1
# С параметрами:
#   .\scripts\deploy-pay-api-to-vps.ps1 -Server "root@alesvpn.ru" -RemotePayApi "/var/www/alesvpn-app/pay_api"

param(
    [string] $Server = "root@alesvpn.ru",
    [string] $RemotePayApi = "/var/www/alesvpn-app/pay_api",
    [switch] $IncludeNginxSample
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root "pay_api\main.py"))) {
    Write-Error "Запустите скрипт из репозитория MyVPN (не найден pay_api\main.py)."
}

$pay = Join-Path $root "pay_api"
$files = @(
    "main.py",
    "yk_store.py",
    "README.md",
    "__init__.py",
    "requirements.txt"
)

foreach ($f in $files) {
    $local = Join-Path $pay $f
    if (Test-Path $local) {
        Write-Host "scp $f -> ${Server}:${RemotePayApi}/"
        scp $local "${Server}:${RemotePayApi}/"
    }
}

if ($IncludeNginxSample) {
    $ngx = Join-Path $root "web\nginx-alesvpn-site.conf"
    if (Test-Path $ngx) {
        Write-Host "scp nginx-alesvpn-site.conf -> ${Server}:/tmp/nginx-alesvpn-site.conf"
        scp $ngx "${Server}:/tmp/nginx-alesvpn-site.conf"
        Write-Host ""
        Write-Host "На сервере затем (путь под свой сайт):"
        Write-Host "  sudo cp /tmp/nginx-alesvpn-site.conf /etc/nginx/sites-available/alesvpn"
        Write-Host "  sudo nginx -t && sudo systemctl reload nginx"
    }
}

Write-Host ""
Write-Host "На сервере перезапуск pay_api:"
Write-Host "  sudo systemctl restart alesvpn-pay"
Write-Host "  curl -s http://127.0.0.1:8008/healthz"
Write-Host ""
