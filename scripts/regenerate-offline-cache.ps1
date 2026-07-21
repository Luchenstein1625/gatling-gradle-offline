$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$OutDir = Join-Path $RootDir "offline-deps\gradle"
$StagingDir = Join-Path $RootDir "offline-deps\.gradle-refresh"
$Image = "gatling-gradle-cache-refresh:local"

Set-Location $RootDir
Remove-Item $StagingDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $StagingDir | Out-Null
New-Item -ItemType Directory -Force $OutDir | Out-Null

Write-Host "Construyendo cache limpio con las dependencias actuales..."
docker build --no-cache -f Dockerfile.cache-refresh -t $Image .
if ($LASTEXITCODE -ne 0) { throw "Fallo la construccion del cache conectado." }

Write-Host "Exportando Gradle 8.14.3 y el cache resuelto..."
$GradleTar = Join-Path $StagingDir "gradle-8.14.3.tar.gz"
$CacheTar = Join-Path $StagingDir "gradle-cache.tar.gz"
cmd /c "docker run --rm --entrypoint sh $Image -lc ""tar -C /opt -czf - gradle"" > ""$GradleTar"""
if ($LASTEXITCODE -ne 0) { throw "Fallo la exportacion de Gradle." }
cmd /c "docker run --rm --entrypoint sh $Image -lc ""tar -C /opt -czf - gradle-cache"" > ""$CacheTar"""
if ($LASTEXITCODE -ne 0) { throw "Fallo la exportacion del cache." }

function Split-File([string]$Path, [string]$Prefix, [int64]$ChunkSize) {
    $Input = [System.IO.File]::OpenRead($Path)
    try {
        $Buffer = New-Object byte[] $ChunkSize
        $Index = 0
        while (($Read = $Input.Read($Buffer, 0, $Buffer.Length)) -gt 0) {
            $Part = Join-Path $StagingDir ("{0}.part-{1:D3}" -f $Prefix, $Index)
            $Output = [System.IO.File]::Create($Part)
            try { $Output.Write($Buffer, 0, $Read) } finally { $Output.Dispose() }
            $Index++
        }
    } finally { $Input.Dispose() }
}

Split-File $GradleTar "gradle-8.14.3.tar.gz" 50MB
Split-File $CacheTar "gradle-cache.tar.gz" 50MB

$NewGradleParts = @(Get-ChildItem $StagingDir -Filter "gradle-8.14.3.tar.gz.part-*")
$NewCacheParts = @(Get-ChildItem $StagingDir -Filter "gradle-cache.tar.gz.part-*")
if ($NewGradleParts.Count -eq 0 -or $NewCacheParts.Count -eq 0) {
    throw "No se generaron las partes requeridas. Se conserva el cache anterior."
}

Remove-Item (Join-Path $OutDir "gradle-8.14.3.tar.gz.part-*") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $OutDir "gradle-cache.tar.gz.part-*") -Force -ErrorAction SilentlyContinue
Move-Item (Join-Path $StagingDir "gradle-8.14.3.tar.gz.part-*") $OutDir
Move-Item (Join-Path $StagingDir "gradle-cache.tar.gz.part-*") $OutDir
Remove-Item $StagingDir -Recurse -Force

Write-Host "Validando que el build funciona sin red..."
docker build --network=none --no-cache -t gatling-control-center:offline .
if ($LASTEXITCODE -ne 0) { throw "El cache se genero, pero la validacion offline fallo." }
Write-Host "Cache offline regenerado y validado correctamente."
