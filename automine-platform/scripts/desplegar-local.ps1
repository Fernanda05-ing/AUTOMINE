# Despliegue local de AUTOMINE: compila la web dentro de la API y la deja corriendo en
# http://localhost:8081 (y en la red local para probar desde el celular).
#   powershell -ExecutionPolicy Bypass -File scripts\desplegar-local.ps1 [-SinCompilar]
# Las claves se escriben en la consola: no se muestran ni se guardan en ningun archivo.
param([switch]$SinCompilar)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-23" }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

function Leer-Secreto([string]$texto) {
    $s = Read-Host $texto -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

Write-Host ""
Write-Host "=== Despliegue local de AUTOMINE ===" -ForegroundColor Cyan
# Contrasena de MySQL: se verifica antes de compilar (hasta 3 intentos)
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
for ($intento = 1; $intento -le 3; $intento++) {
    $env:DB_PASSWORD = Leer-Secreto "Contrasena de MySQL local (usuario root)"
    if (-not (Test-Path $mysql)) { break }
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & $mysql -u root -e "SELECT 1" 2>$null | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if ($ok) { Write-Host "Conexion con MySQL correcta." -ForegroundColor Green; break }
    Write-Host "MySQL rechazo la contrasena (intento $intento de 3). Revise que este bien escrita." -ForegroundColor Red
    if ($intento -eq 3) { Write-Host "No se pudo conectar a MySQL. Ejecute el script de nuevo." -ForegroundColor Red; exit 1 }
}
$env:MAIL_USERNAME = Read-Host "Cuenta de Gmail para enviar correos (Enter = sin correo)"
if ($env:MAIL_USERNAME) {
    $env:MAIL_PASSWORD = (Leer-Secreto "Contrasena de aplicacion de Gmail (16 letras)").Replace(" ", "")
}
$env:GOOGLE_CLIENT_ID = Read-Host "ID de cliente de Google para iniciar sesion con Google (Enter = sin Google)"

# Direccion en la red local (para el celular)
$ipLocal = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Select-Object -First 1).IPAddress
$env:PUBLIC_URL = "http://localhost:8081"
$env:CORS_ORIGINS = "http://localhost:8081,http://localhost:5173" + $(if ($ipLocal) { ",http://${ipLocal}:8081" } else { "" })

# Libera el puerto si ya hay una API corriendo
$p = (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue).OwningProcess
if ($p) { Stop-Process -Id $p -Force -Confirm:$false; Start-Sleep -Seconds 2 }

if (-not $SinCompilar) {
    Write-Host "Compilando la plataforma web..." -ForegroundColor Cyan
    Push-Location (Join-Path $raiz "web")
    if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
    npm run build
    Pop-Location
    $static = Join-Path $raiz "backend\src\main\resources\static"
    if (Test-Path $static) { Remove-Item -Recurse -Force $static }
    Copy-Item -Recurse (Join-Path $raiz "web\dist") $static

    Write-Host "Compilando la API..." -ForegroundColor Cyan
    Push-Location (Join-Path $raiz "backend")
    mvn -q -DskipTests package
    Pop-Location
}

Write-Host ""
Write-Host "Iniciando AUTOMINE..." -ForegroundColor Green
Write-Host "  En este computador:  http://localhost:8081" -ForegroundColor Green
if ($ipLocal) { Write-Host "  Desde el celular (misma WiFi):  http://${ipLocal}:8081" -ForegroundColor Green }
Write-Host "  Documentacion de la API:  http://localhost:8081/AutomineApi/swagger-ui.html" -ForegroundColor Green
Write-Host "No cierre esta ventana: si la cierra, AUTOMINE se detiene (Ctrl+C para detenerla)." -ForegroundColor Yellow
Write-Host ""
& "$env:JAVA_HOME\bin\java.exe" -jar (Join-Path $raiz "backend\target\automine-api.jar")
