<#
.SYNOPSIS
  Exports a snapshot's frozen optimization candidates as a benchmark dataset (data/benchmarks/*.json)
  and prints its SHA-256. Requires the backend running with the local profile (.\scripts\backend.ps1).
.EXAMPLE
  .\scripts\export-benchmark.ps1 -Id 7e49ed44-2332-4220-bc97-f6f4ebc1e66a -Name rl-robotics-200
#>
param(
    [Parameter(Mandatory)] [guid]$Id,
    [Parameter(Mandatory)] [ValidatePattern('^[a-z0-9][a-z0-9-]{1,60}$')] [string]$Name,
    [string]$BaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$resp = Invoke-WebRequest "$BaseUrl/api/dev/snapshots/$Id/export" -SkipHttpErrorCheck -TimeoutSec 120
if ($resp.StatusCode -ne 200) {
    Write-Host "Export failed: HTTP $($resp.StatusCode)"
    exit 1
}
$json = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()).Replace("`r`n", "`n")
$out = Join-Path $RepoRoot "data\benchmarks\$Name.json"
New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
[IO.File]::WriteAllText($out, $json, [Text.UTF8Encoding]::new($false))

$bytes = [Text.Encoding]::UTF8.GetBytes($json)
$hash = -join ([Security.Cryptography.SHA256]::HashData($bytes) | ForEach-Object { $_.ToString('x2') })
$d = $json | ConvertFrom-Json
Write-Host "Wrote $out"
Write-Host "  topic: $($d.topic); candidates: $(@($d.candidates).Count); snapshot: $($d.snapshotId)"
Write-Host "  sha256: $hash"
