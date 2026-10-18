<#
.SYNOPSIS
  Runs the offline baseline benchmark on frozen datasets (default: every data\benchmarks\*.json) and writes
  baseline-report.md + baseline-runs.json. Needs only JDK 21 and the repository: no database, Docker or network.
.EXAMPLE
  .\scripts\benchmark.ps1                                  # all committed datasets → backend\target\benchmarks
.EXAMPLE
  .\scripts\benchmark.ps1 -Out docs\evaluation\benchmarks -Datasets data\benchmarks\gnn-200.json
#>
param(
    [string]$Out = 'backend\target\benchmarks',
    [string[]]$Datasets
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')
Use-Jdk21

if (-not $Datasets) {
    $Datasets = Get-ChildItem (Join-Path $RepoRoot 'data\benchmarks') -Filter '*.json' | ForEach-Object FullName
}
$paths = $Datasets | ForEach-Object { (Resolve-Path (Join-Path $RepoRoot $_) -ErrorAction SilentlyContinue)?.Path ?? (Resolve-Path $_).Path }
$outPath = [IO.Path]::GetFullPath((Join-Path $RepoRoot $Out))
$argLine = (@('--out', "`"$outPath`"") + ($paths | ForEach-Object { "`"$_`"" })) -join ' '

Push-Location (Join-Path $RepoRoot 'backend')
try {
    & .\mvnw.cmd -B -q compile exec:java "-Dexec.args=$argLine"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
