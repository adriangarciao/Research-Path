<#
.SYNOPSIS
  Runs the Spring Boot backend with the `local` profile on http://localhost:8080.
  Loads ..\.env into this process (Spring Boot does not read .env by itself).
.EXAMPLE
  .\scripts\backend.ps1
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

Import-DotEnv
Use-Jdk21

Push-Location (Join-Path $RepoRoot 'backend')
try {
    & .\mvnw.cmd -B spring-boot:run "-Dspring-boot.run.profiles=local"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
