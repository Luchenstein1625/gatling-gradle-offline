[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

function Run {
    param(
        [Parameter(Mandatory=$true)][string]$Command,
        [Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments
    )

    Write-Host ""
    Write-Host ">> $Command $($Arguments -join ' ')" -ForegroundColor Cyan
    & $Command @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Fallo el comando: $Command (codigo $LASTEXITCODE)"
    }
}

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$GradleVersion = "8.6"
$JavaHome = Join-Path $Root "offline\java\jdk-21"
$GradleHome = Join-Path $Root "offline\gradle\gradle-$GradleVersion"
$CacheDir = Join-Path $Root "offline\gradle-cache"
$GradleBat = Join-Path $GradleHome "bin\gradle.bat"

if (-not (Test-Path $GradleBat)) {
    throw "Falta Gradle offline. Ejecute primero .\scripts\01_prepare_online.ps1"
}

if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
    throw "Falta Java offline. Ejecute primero .\scripts\01_prepare_online.ps1"
}

$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"
$env:GRADLE_USER_HOME = $CacheDir
$env:ORG_GRADLE_JAVA_INSTALLATIONS_PATHS = $JavaHome
$env:ORG_GRADLE_JAVA_INSTALLATIONS_AUTO_DETECT = "false"
$env:ORG_GRADLE_JAVA_INSTALLATIONS_AUTO_DOWNLOAD = "false"

if ($env:NVD_API_KEY) {
    Write-Host "Usando NVD_API_KEY configurada." -ForegroundColor Green
    Run $GradleBat "--no-daemon" "-Dnvd.api.key=$env:NVD_API_KEY" "dependencyCheckAnalyze"
} else {
    Write-Warning "NVD_API_KEY no configurada. El primer escaneo puede tardar bastante."
    Run $GradleBat "--no-daemon" "dependencyCheckAnalyze"
}

Write-Host ""
Write-Host "Escaneo completado." -ForegroundColor Green
Write-Host "Reportes: build\reports\dependency-check"
