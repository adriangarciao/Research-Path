<#
.SYNOPSIS
  Live whole-flow browser test (Playwright, desktop + mobile) of the React UI against the REAL backend,
  which calls OpenAlex (a few requests, ~$0.001 each). Start the backend first (.\scripts\backend.ps1).
  The Vite dev server is started or reused automatically. Not part of verify.ps1.
  The suite creates about six paths in a row, more than the default burst of three per connection, so start the
  backend for it with a relaxed burst:  $env:RESEARCHPATH_RATE_LIMIT_CREATE_BURST='20'; .\scripts\backend.ps1
  (rate limiting itself is covered by the backend tests).
.EXAMPLE
  .\scripts\e2e.ps1
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

try {
    Invoke-RestMethod http://localhost:8080/actuator/health -TimeoutSec 5 | Out-Null
} catch {
    throw 'Backend is not reachable on http://localhost:8080; start it with .\scripts\backend.ps1'
}
Push-Location (Join-Path $RepoRoot 'frontend')
try {
    if (-not (Test-Path 'node_modules')) { & npm ci --silent; if ($LASTEXITCODE) { exit $LASTEXITCODE } }
    & npx playwright install chromium
    & npx playwright test
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
