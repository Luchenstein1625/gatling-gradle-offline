[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$BaseUrl = "http://localhost:8080"
$Temp = Join-Path $env:TEMP "gatling-control-api-smoke"

Remove-Item -Recurse -Force $Temp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $Temp | Out-Null

function Check-Get {
    param([string]$Name, [string]$Url)

    Write-Host "Probando $Name..." -ForegroundColor Cyan
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Url
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Name respondió HTTP $($response.StatusCode)"
    }
    Write-Host "OK $Name" -ForegroundColor Green
}

Check-Get "Health" "$BaseUrl/actuator/health"
Check-Get "OpenAPI" "$BaseUrl/openapi"
Check-Get "Recursos" "$BaseUrl/api/system/resources"
Check-Get "Plantilla PEAK Login" "$BaseUrl/api/templates/peak-login"
Check-Get "Plantilla TPS Login" "$BaseUrl/api/templates/tps-login"
Check-Get "Plantilla PEAK Token" "$BaseUrl/api/templates/peak-external-token"
Check-Get "Plantilla TPS Token" "$BaseUrl/api/templates/tps-external-token"
Check-Get "Plantilla Mock JSONPlaceholder" "$BaseUrl/api/templates/mock-jsonplaceholder"
Check-Get "Plantilla Mock HTTPBin" "$BaseUrl/api/templates/mock-httpbin"
Check-Get "Plantilla CSV" "$BaseUrl/api/templates/users-csv"

$MockConfig = Join-Path $Temp "mock-jsonplaceholder.yaml"
Invoke-WebRequest -UseBasicParsing `
  -Uri "$BaseUrl/api/templates/mock-jsonplaceholder" `
  -OutFile $MockConfig

Write-Host "Subiendo configuración mock..." -ForegroundColor Cyan
$UploadJson = & curl.exe -sS -X POST `
  "$BaseUrl/api/configurations" `
  -F "configuration=@$MockConfig"

if ($LASTEXITCODE -ne 0) { throw "Falló upload mock" }

$Upload = $UploadJson | ConvertFrom-Json
$ConfigurationId = $Upload.configurationId
if (-not $ConfigurationId) { throw "No se recibió configurationId" }
Write-Host "configurationId=$ConfigurationId" -ForegroundColor Green

Write-Host "Ejecutando smoke test público..." -ForegroundColor Cyan
$ExecutionJson = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/executions/$ConfigurationId"

$ExecutionId = $ExecutionJson.executionId
if (-not $ExecutionId) { throw "No se recibió executionId" }
Write-Host "executionId=$ExecutionId" -ForegroundColor Green

do {
    Start-Sleep -Seconds 2
    $Status = Invoke-RestMethod -Uri "$BaseUrl/api/executions/$ExecutionId"
    Write-Host "Estado: $($Status.status)"
} while ($Status.status -in @("QUEUED", "RUNNING", "GENERATING_REPORT"))

if ($Status.status -ne "SUCCESS") {
    Write-Warning "La plataforma respondió, pero la prueba terminó en estado $($Status.status)"
    Write-Warning "Esto puede ocurrir si el mock público está temporalmente inaccesible."
}

$LogFile = Join-Path $Temp "execution.log"
Invoke-WebRequest -UseBasicParsing `
  -Uri "$BaseUrl/api/executions/$ExecutionId/logs" `
  -OutFile $LogFile

if ($Status.status -eq "SUCCESS") {
    $ReportFile = Join-Path $Temp "report.zip"
    Invoke-WebRequest -UseBasicParsing `
      -Uri "$BaseUrl/api/executions/$ExecutionId/report" `
      -OutFile $ReportFile
    Write-Host "Reporte descargado: $ReportFile" -ForegroundColor Green
}

Write-Host ""
Write-Host "Pruebas de endpoints completadas." -ForegroundColor Green
Write-Host "Archivos temporales: $Temp"
Write-Host ""
Write-Host "Nota: /api/kubernetes/pod/logs solo se prueba dentro de Kubernetes con RBAC."
