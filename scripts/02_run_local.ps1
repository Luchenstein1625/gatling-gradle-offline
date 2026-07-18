[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

docker image inspect gatling-control-api:1.0.0 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    $Tar = Join-Path $Root "offline\docker-images\gatling-control-api-1.0.0.tar"
    if (-not (Test-Path $Tar)) {
        throw "Ejecute primero .\scripts\01_prepare_online.ps1"
    }
    docker load -i $Tar
}

docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar Docker Compose" }

Write-Host ""
Write-Host "Interfaz: http://localhost:8080" -ForegroundColor Green
Write-Host "Swagger:  http://localhost:8080/docs" -ForegroundColor Green
Write-Host "Health:   http://localhost:8080/actuator/health" -ForegroundColor Green
