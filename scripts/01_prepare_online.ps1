[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

function Run {
    param([string]$Command, [Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    Write-Host ">> $Command $($Arguments -join ' ')" -ForegroundColor Cyan
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Fallo: $Command (codigo $LASTEXITCODE)" }
}

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$GradleVersion = "8.6"
$JdkVersion = "21.0.11+10"
$BaseImage = "eclipse-temurin:21.0.11_10-jdk-jammy"

$Offline = Join-Path $Root "offline"
$JavaDir = Join-Path $Offline "java"
$JavaHome = Join-Path $JavaDir "jdk-21"
$GradleDir = Join-Path $Offline "gradle"
$GradleHome = Join-Path $GradleDir "gradle-$GradleVersion"
$CacheDir = Join-Path $Offline "gradle-cache"
$ImagesDir = Join-Path $Offline "docker-images"

New-Item -ItemType Directory -Force $JavaDir,$GradleDir,$CacheDir,$ImagesDir | Out-Null

$JavaExe = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $JavaExe)) {
    Write-Host "Descargando JDK 21 portable..." -ForegroundColor Yellow
    $Zip = Join-Path $JavaDir "temurin-jdk-21.zip"
    $Version = [uri]::EscapeDataString($JdkVersion)
    Invoke-WebRequest -UseBasicParsing `
      -Uri "https://api.adoptium.net/v3/binary/version/jdk-$Version/windows/x64/jdk/hotspot/normal/eclipse" `
      -OutFile $Zip

    $Extract = Join-Path $JavaDir "_extract"
    Remove-Item -Recurse -Force $Extract -ErrorAction SilentlyContinue
    Expand-Archive $Zip $Extract -Force
    $Found = Get-ChildItem $Extract -Directory |
      Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
      Select-Object -First 1
    if (-not $Found) { throw "JDK no encontrado" }
    Remove-Item -Recurse -Force $JavaHome -ErrorAction SilentlyContinue
    Move-Item $Found.FullName $JavaHome
    Remove-Item -Recurse -Force $Extract
}

$GradleZip = Join-Path $GradleDir "gradle-$GradleVersion-bin.zip"
$ShaFile = "$GradleZip.sha256"
if (-not (Test-Path $GradleZip)) {
    Write-Host "Descargando Gradle..." -ForegroundColor Yellow
    Invoke-WebRequest -UseBasicParsing `
      -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" `
      -OutFile $GradleZip
}
if (-not (Test-Path $ShaFile)) {
    Invoke-WebRequest -UseBasicParsing `
      -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip.sha256" `
      -OutFile $ShaFile
}
$Expected=(Get-Content $ShaFile -Raw).Trim().Split(" ")[0].ToLower()
$Actual=(Get-FileHash $GradleZip -Algorithm SHA256).Hash.ToLower()
if ($Expected -ne $Actual) { throw "SHA-256 Gradle no coincide" }

if (-not (Test-Path (Join-Path $GradleHome "bin\gradle.bat"))) {
    Expand-Archive $GradleZip $GradleDir -Force
}

$env:JAVA_HOME=$JavaHome
$env:PATH="$JavaHome\bin;$env:PATH"
$env:GRADLE_USER_HOME=$CacheDir
$env:ORG_GRADLE_JAVA_INSTALLATIONS_PATHS=$JavaHome
$env:ORG_GRADLE_JAVA_INSTALLATIONS_AUTO_DETECT="false"
$env:ORG_GRADLE_JAVA_INSTALLATIONS_AUTO_DOWNLOAD="false"

$GradleBat=Join-Path $GradleHome "bin\gradle.bat"

Write-Host "Versiones seleccionadas:" -ForegroundColor Yellow
Write-Host "  Spring Boot: 3.5.16"
Write-Host "  springdoc:    2.8.17"
Write-Host "  Gatling:      3.15.1 / plugin 3.15.1.1"
Write-Host "  OWASP DC:     12.2.2"
Write-Host ""
Write-Host "Validando plugins Gradle publicados..." -ForegroundColor Yellow
Run $GradleBat "--refresh-dependencies" "--no-daemon" "tasks"

Write-Host "Descargando y compilando dependencias..." -ForegroundColor Yellow
Run $GradleBat "--refresh-dependencies" "--no-daemon" "clean" "build" "verifyGatlingNetty" "prepareGatlingRuntime"

Write-Host "Validando compilacion offline..." -ForegroundColor Yellow
Run $GradleBat "--offline" "--no-daemon" "clean" "build" "verifyGatlingNetty" "prepareGatlingRuntime"

Write-Host "Analisis de dependencias omitido en el build principal." -ForegroundColor DarkGray
Write-Host "Para ejecutarlo aparte: .\scripts\04_security_scan.ps1" -ForegroundColor DarkGray

Run "docker" "pull" $BaseImage

Write-Host "Eliminando imagen anterior del proyecto, si existe..." -ForegroundColor DarkGray
$PreviousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& docker image rm -f "gatling-control-api:1.0.0" 2>&1 | Out-Null
$ErrorActionPreference = $PreviousErrorActionPreference
Run "docker" "save" "-o" (Join-Path $ImagesDir "temurin-21.0.11-jdk-jammy.tar") $BaseImage
Run "docker" "build" "--network=none" "--no-cache" "-t" "gatling-control-api:1.0.0" "."
Run "docker" "save" "-o" (Join-Path $ImagesDir "gatling-control-api-1.0.0.tar") "gatling-control-api:1.0.0"

Write-Host "Preparacion completada." -ForegroundColor Green
Write-Host "Ejecute: .\scripts\02_run_local.ps1"
