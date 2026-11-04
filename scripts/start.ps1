param([ValidateSet('all','users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend')][string]$Service = 'all')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
    if ($Service -eq 'all') {
        docker compose up -d --build --wait --wait-timeout 240
    } else {
        docker compose -f compose.infrastructure.yml up -d
        if ($LASTEXITCODE -ne 0) { throw 'Infrastrukturen kunne ikke startes.' }
        docker compose -f "$Service/docker-compose.yml" up -d --build
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose fejlede.' }
    if ($Service -eq 'all') { python scripts/configure_identity.py }
    Write-Host 'Frontend: http://localhost:3000'
    Write-Host 'RabbitMQ: http://localhost:15672 (aj / aj-local)'
} finally { Pop-Location }
