<#
.SYNOPSIS
  Full regression check: backend tests, frontend typecheck/build/lint, and a secret scan.
  Run after every change. Requires Docker running (backend tests use Testcontainers).
  Never prints secret values.
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

$results = [ordered]@{}
function Invoke-Step([string]$name, [scriptblock]$body) {
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    try {
        & $body
        $results[$name] = if ($LASTEXITCODE -eq 0) { 'PASS' } else { "FAIL (exit $LASTEXITCODE)" }
    } catch {
        $results[$name] = "FAIL ($($_.Exception.Message))"
    }
}

Invoke-Step 'docker engine' { docker info --format '{{.ServerVersion}}' | Out-Null }

Use-Jdk21
# `clean` is required: stale classes/reports in target/ (e.g. from another branch) could
# otherwise run or be counted, making a branch "pass" with code it does not contain.
Invoke-Step 'backend tests' {
    Push-Location (Join-Path $RepoRoot 'backend')
    try { & .\mvnw.cmd -B -q clean verify } finally { Pop-Location }
}
$reports = Get-ChildItem (Join-Path $RepoRoot 'backend\target\surefire-reports\*.txt') -ErrorAction SilentlyContinue
$testCount = ($reports | ForEach-Object {
    [int]([regex]::Match((Get-Content $_ -Raw), 'Tests run: (\d+)').Groups[1].Value) } | Measure-Object -Sum).Sum
if ($results['backend tests'] -eq 'PASS') { $results['backend tests'] = "PASS ($testCount tests)" }

Invoke-Step 'frontend build + typecheck' {
    Push-Location (Join-Path $RepoRoot 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) { & npm ci --silent; if ($LASTEXITCODE) { return } }
        & npm run build --silent
    } finally { Pop-Location }
}

Invoke-Step 'frontend lint' {
    Push-Location (Join-Path $RepoRoot 'frontend')
    try { & npm run lint --silent } finally { Pop-Location }
}

Invoke-Step 'secret scan' {
    Push-Location $RepoRoot
    try {
        $envFile = Join-Path $RepoRoot '.env'
        $secrets = @()
        if (Test-Path $envFile) {
            $secrets = Get-Content $envFile |
                Where-Object { $_ -match '^(DB_PASSWORD|OPENALEX_API_KEY)=\S' } |
                ForEach-Object { ($_ -split '=', 2)[1].Trim() } |
                Where-Object { $_.Length -ge 8 }
        }
        & git check-ignore -q .env
        if ($LASTEXITCODE -ne 0) { throw '.env is not gitignored' }
        # Tracked + untracked-but-not-ignored files, plus the frontend bundle.
        $files = @(git ls-files --cached --others --exclude-standard) +
                 @(Get-ChildItem frontend\dist -Recurse -File -ErrorAction SilentlyContinue |
                   ForEach-Object { Resolve-Path -Relative $_.FullName })
        $leaks = foreach ($f in $files) {
            if (-not (Test-Path $f -PathType Leaf)) { continue }
            $c = Get-Content $f -Raw -ErrorAction SilentlyContinue
            if (-not $c) { continue }
            foreach ($s in $secrets) { if ($c.Contains($s)) { $f; break } }
        }
        $secrets = $null
        if ($leaks) { throw "secret value found in: $($leaks -join ', ')" }
        if (git grep -n -I 'VITE_OPENALEX' -- frontend) { throw 'VITE_OPENALEX* used in frontend' }
        $global:LASTEXITCODE = 0
    } finally { Pop-Location }
}

Write-Host "`n=== SUMMARY ===" -ForegroundColor Cyan
$results.GetEnumerator() | ForEach-Object { '{0,-28} {1}' -f $_.Key, $_.Value }
if ($results.Values | Where-Object { $_ -notlike 'PASS*' }) { exit 1 }
