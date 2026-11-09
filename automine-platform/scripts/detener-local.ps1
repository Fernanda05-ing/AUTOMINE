# Detiene la API (puerto 8081) y la web de desarrollo (puerto 5173) iniciadas con iniciar-local.ps1
foreach ($puerto in 8081, 5173) {
    $conn = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Stop-Process -Id $conn.OwningProcess -Force -Confirm:$false
        Write-Host "Detenido el proceso del puerto $puerto"
    }
}
