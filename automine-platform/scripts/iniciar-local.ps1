# Levanta AUTOMINE en local sin Docker: API (8081) con MySQL local + web en modo desarrollo (5173).
#   powershell -ExecutionPolicy Bypass -File scripts\iniciar-local.ps1 -DbPassword "su-clave" [-Demo]
param(
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$DbUser = "root",
    [switch]$Demo
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-23" }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

if (-not $DbPassword) { $DbPassword = Read-Host "Contrasena de MySQL ($DbUser)" }
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:DEMO_DATA = if ($Demo) { "true" } else { "false" }

Write-Host "Compilando la API..." -ForegroundColor Cyan
Push-Location "$raiz\backend"
mvn -q -DskipTests package
Pop-Location

Write-Host "Iniciando la API en http://localhost:8081 ..." -ForegroundColor Cyan
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "`"$raiz\backend\target\automine-api.jar`"" `
    -RedirectStandardOutput "$raiz\api.log" -RedirectStandardError "$raiz\api-error.log" -WindowStyle Hidden

Write-Host "Iniciando la web en http://localhost:5173 ..." -ForegroundColor Cyan
Push-Location "$raiz\web"
if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
Start-Process -FilePath "npm.cmd" -ArgumentList "run", "dev" -WindowStyle Minimized
Pop-Location

Write-Host ""
Write-Host "Listo. Web: http://localhost:5173  ·  API: http://localhost:8081/AutomineApi/Health" -ForegroundColor Green
Write-Host "Swagger: http://localhost:8081/AutomineApi/swagger-ui.html  ·  Log: $raiz\api.log"
