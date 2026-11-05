# Configure the report toolchain without installing a complete TeX distribution.
$ErrorActionPreference = 'Stop'
$reportRepo = Split-Path -Parent $PSScriptRoot
$reportTools = Join-Path $reportRepo 'test-results/tools/report'

function Install-ReportTool {
    param([string]$Name, [string]$Repository, [string]$Tag, [string]$AssetName)
    $existing = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Bruger eksisterende $Name fra $($existing.Source)"
        return
    }
    $packageDirectory = Join-Path $reportTools $Name
    New-Item -ItemType Directory -Force -Path $packageDirectory > $null
    $binary = Get-ChildItem -LiteralPath $packageDirectory -Recurse -Filter "$Name.exe" | Select-Object -First 1
    if (-not $binary) {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repository/releases/tags/$Tag" -Headers @{ 'User-Agent' = 'AJ-report-setup' }
        $asset = $release.assets | Where-Object name -EQ $AssetName | Select-Object -First 1
        if (-not $asset) { throw "Den fastlaaste download findes ikke: $AssetName" }
        $archive = Join-Path $packageDirectory $AssetName
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive
        if ($asset.digest -and $asset.digest.StartsWith('sha256:')) {
            $expected = $asset.digest.Substring(7)
            if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Downloadens checksum matcher ikke.' }
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $packageDirectory -Force
        $binary = Get-ChildItem -LiteralPath $packageDirectory -Recurse -Filter "$Name.exe" | Select-Object -First 1
    }
    if (-not $binary) { throw "$Name.exe mangler efter installation." }
    $env:PATH = "$($binary.DirectoryName);$env:PATH"
}

Push-Location $reportRepo
$reportPreviousPythonPath = $env:PYTHONPATH
try {
    Install-ReportTool pandoc jgm/pandoc 3.6.4 pandoc-3.6.4-windows-x86_64.zip
    Install-ReportTool tectonic tectonic-typesetting/tectonic 'tectonic@0.16.9' tectonic-0.16.9-x86_64-pc-windows-msvc.zip
    python -m pip install --upgrade --target test-results/tools/python-deps -r scripts/requirements-report.txt
    if ($LASTEXITCODE -ne 0) { throw 'Installation af rapportafhaengigheder fejlede.' }
    $reportPythonPath = Join-Path $reportRepo 'test-results/tools/python-deps'
    $env:PYTHONPATH = if ($reportPreviousPythonPath) { "$reportPythonPath;$reportPreviousPythonPath" } else { $reportPythonPath }
    python -m playwright install chromium
    if ($LASTEXITCODE -ne 0) { throw 'Installation af Chromium fejlede.' }
    pandoc --version | Select-Object -First 1
    tectonic --version
    Write-Host 'Rapportvaerktoejer er klar. Koer: python scripts/export_report.py'
} finally {
    $env:PYTHONPATH = $reportPreviousPythonPath
    Pop-Location
}
