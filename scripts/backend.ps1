<#
.SYNOPSIS
  Runs the Spring Boot backend with the `local` profile on http://localhost:8080.
  Loads ..\.env into this process (Spring Boot does not read .env by itself).
.EXAMPLE
  .\scripts\backend.ps1
.EXAMPLE
  .\scripts\backend.ps1 -E2E      # adds the test-only e2e profile (relaxed create burst) for scripts\e2e.ps1
#>
param([switch]$E2E)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

Import-DotEnv
Use-Jdk21

Push-Location (Join-Path $RepoRoot 'backend')
try {
    $profiles = if ($E2E) { 'local,e2e' } else { 'local' }
    & .\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.profiles=$profiles"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
