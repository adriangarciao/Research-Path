<#
.SYNOPSIS
  Runs backend tests. They use a disposable Testcontainers PostgreSQL 17,
  not the developer database, so only a running Docker engine is required.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

Use-Jdk21

Push-Location (Join-Path $RepoRoot 'backend')
try {
    & .\mvnw.cmd -B test
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
