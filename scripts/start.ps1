param([ValidateSet('all','users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend')][string]$Service = 'all')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
    if ($Service -eq 'all') {
        docker compose up -d --build --wait --wait-timeout 240
    } else {
        docker compose -f compose.infrastructure.yml up -d --wait --wait-timeout 240
        if ($LASTEXITCODE -ne 0) { throw 'Infrastrukturen kunne ikke startes.' }
        docker compose -f "$Service/docker-compose.yml" up -d --build --wait --wait-timeout 240
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose fejlede.' }
    python scripts/configure_identity.py
    if ($LASTEXITCODE -ne 0) { throw 'Konfiguration af lokal identitet fejlede.' }
    Write-Host 'Frontend: http://localhost:3000'
    Write-Host 'RabbitMQ: http://localhost:15672 (aj / aj-local)'
} finally { Pop-Location }
