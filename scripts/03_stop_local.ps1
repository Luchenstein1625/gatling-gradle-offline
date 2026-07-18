$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
docker compose down --remove-orphans
