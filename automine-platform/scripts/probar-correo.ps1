# Arranca la API local con el correo de Gmail configurado, para probar el envio.
# Las claves se escriben en la consola (no se muestran ni se guardan en ningun archivo).
#   powershell -ExecutionPolicy Bypass -File scripts\probar-correo.ps1
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-23" }

function Leer-Secreto([string]$texto) {
    $s = Read-Host $texto -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

Write-Host ""
Write-Host "=== Prueba de correo de AUTOMINE ===" -ForegroundColor Cyan
$env:MAIL_USERNAME = Read-Host "Cuenta de Gmail que enviara los correos"
$env:MAIL_PASSWORD = (Leer-Secreto "Contrasena de aplicacion de Gmail (16 letras)").Replace(" ", "")
$env:DB_PASSWORD = Leer-Secreto "Contrasena de MySQL local (usuario root)"
$env:GOOGLE_CLIENT_ID = Read-Host "ID de cliente de Google para iniciar sesion con Google (Enter para omitir)"
$env:PUBLIC_URL = "http://localhost:8081"

# Libera el puerto si ya hay una API corriendo
$p = (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue).OwningProcess
if ($p) { Stop-Process -Id $p -Force -Confirm:$false; Start-Sleep -Seconds 2 }

$jar = Join-Path $raiz "backend\target\automine-api.jar"
if (-not (Test-Path $jar)) {
    Write-Host "Compilando la API..." -ForegroundColor Cyan
    Push-Location (Join-Path $raiz "backend"); mvn -q -DskipTests package; Pop-Location
}

Write-Host ""
Write-Host "Iniciando AUTOMINE en http://localhost:8081 ..." -ForegroundColor Green
Write-Host "Cuando aparezca 'Started AutomineApplication':" -ForegroundColor Green
Write-Host "  1. Abra http://localhost:8081 e ingrese como admin@automine.co" -ForegroundColor Green
Write-Host "  2. Vaya a Administracion > Correo y envie un correo de prueba" -ForegroundColor Green
Write-Host "Para detener la API presione Ctrl+C en esta ventana." -ForegroundColor Green
Write-Host ""
& "$env:JAVA_HOME\bin\java.exe" -jar $jar
