<#
.SYNOPSIS
  Runs the Vite dev server on http://localhost:5173 (installs dependencies on first run).
  /api and /actuator are proxied to the backend at http://localhost:8080.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

Push-Location (Join-Path $RepoRoot 'frontend')
try {
    if (-not (Test-Path 'node_modules')) { & npm ci; if ($LASTEXITCODE) { exit $LASTEXITCODE } }
    & npm run dev
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
