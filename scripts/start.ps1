param(
    [ValidateSet('all','users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend')][string]$Service = 'all',
    [ValidateSet('auto','compose','kubernetes')][string]$Runtime = 'compose'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Push-Location $repo
try {
    if ($Runtime -eq 'auto') {
        $clusters = docker ps --filter name=aj-exam-control-plane --format '{{.Names}}'
        if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop kunne ikke kontaktes.' }
        $Runtime = if ($clusters -contains 'aj-exam-control-plane') { 'kubernetes' } else { 'compose' }
    }
    if ($Runtime -eq 'kubernetes') {
        if ($Service -ne 'all') { throw 'Brug hele systemet ved Kubernetes-start: ./scripts/start.ps1 -Runtime kubernetes' }
        python scripts/kubernetes.py start
        if ($LASTEXITCODE -ne 0) { throw 'Kubernetes kunne ikke startes.' }
        return
    }
    if ($Service -eq 'all') {
        python scripts/start_local.py
        if ($LASTEXITCODE -ne 0) { throw 'AJ kunne ikke startes.' }
        return
    } else {
        docker compose -f compose.infrastructure.yml up -d --wait --wait-timeout 240
        if ($LASTEXITCODE -ne 0) { throw 'Infrastrukturen kunne ikke startes.' }
        docker compose -f "$Service/docker-compose.yml" up -d --build --wait --wait-timeout 240
    }
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose fejlede.' }
    python scripts/configure_identity.py
    if ($LASTEXITCODE -ne 0) { throw 'Konfiguration af lokal identitet fejlede.' }
    Write-Host 'Frontend: http://localhost:3000'
    Write-Host 'RabbitMQ: http://localhost:15672 (admin / din AJ_ADMIN_PASSWORD)'
} finally { Pop-Location }
