<#
.SYNOPSIS
  Creates the private, gitignored .env from .env.example and generates a strong
  local-only DB password. Never overwrites an existing .env; never prints secrets.
#>
$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$example = Join-Path $root '.env.example'

if (Test-Path $envFile) {
    Write-Host ".env already exists - leaving it unchanged: $envFile"
    exit 0
}

$chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
$bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

$lines = Get-Content $example | ForEach-Object {
    if ($_ -match '^DB_PASSWORD=') { "DB_PASSWORD=$password" } else { $_ }
}
# UTF-8 without BOM so Docker Compose parses the first line correctly.
[System.IO.File]::WriteAllLines($envFile, [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Created $envFile with a generated DB password (not shown)."
Write-Host "Paste your OpenAlex key after OPENALEX_API_KEY= in that file when ready."
