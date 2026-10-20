<#
.SYNOPSIS
  Pre-launch hosted checks for release decision R5 (run against the Railway API before public traffic).
  A. Complete request time of uncached new paths (POST /api/paths, client-side wall clock, so it includes retrieval,
     gates, optimization and the database write). Every topic is new, so each request retrieves from OpenAlex.
  B. Concurrent requests with one Idempotency-Key: all must return the same path from ONE retrieval (same snapshot
     retrievedAt), with exactly one 201.
  Cost: about (pages per path x topics) + 1 OpenAlex calls (200 candidates = 2 pages).
.DESCRIPTION
  Product paths use researchpath.ingestion.default-candidates. To measure 200 candidates, set
  RESEARCHPATH_INGESTION_DEFAULT_CANDIDATES=200 on the backend for the run, and raise the create limits
  (RESEARCHPATH_RATE_LIMIT_CREATE_BURST=20, RESEARCHPATH_RATE_LIMIT_CREATES_PER_HOUR=40), then REVERT all three.
  Exit code 0 = both checks passed; 1 = a check failed (report the numbers before changing any limit).
.EXAMPLE
  .\scripts\hosted-check.ps1 -ApiBase https://api.example.up.railway.app -ExpectCandidates 200
#>
param(
    [Parameter(Mandatory)][string]$ApiBase,
    [int]$ExpectCandidates = 200,
    [string[]]$Topics = @('coral reef bleaching', 'graph-based recommender systems', 'perovskite solar cell stability',
        'urban heat islands', 'neural machine translation low-resource'),
    [string]$ConcurrentTopic = 'wastewater epidemiology surveillance',
    [int]$Concurrent = 3,
    [int]$FrontendTimeoutSeconds = 45
)
$ErrorActionPreference = 'Stop'
$ApiBase = $ApiBase.TrimEnd('/')
$failed = $false

function New-Key { 'hosted-' + [guid]::NewGuid().ToString('N').Substring(0, 24) }

function Post-Path([string]$Topic, [string]$Key) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-WebRequest -Method Post -Uri "$ApiBase/api/paths" -ContentType 'application/json' `
        -Headers @{ 'Idempotency-Key' = $Key } -Body (@{ topic = $Topic } | ConvertTo-Json) -SkipHttpErrorCheck -TimeoutSec 120
    $sw.Stop()
    $body = $null
    try { $body = $r.Content | ConvertFrom-Json } catch { }
    [pscustomobject]@{ topic = $Topic; status = [int]$r.StatusCode; ms = [int]$sw.Elapsed.TotalMilliseconds
        retrieved = $body.snapshot.candidatesRetrieved; pathId = $body.pathId }
}

Write-Host "A. Uncached new paths ($($Topics.Count) topics, expecting $ExpectCandidates candidates each)"
$rows = foreach ($t in $Topics) { Post-Path $t (New-Key) }
$rows | Format-Table topic, status, ms, retrieved -AutoSize | Out-String | Write-Host
$ok = @($rows | Where-Object { $_.status -eq 201 })
if ($rows | Where-Object { $_.status -eq 429 }) { Write-Host 'A: got 429 - raise the create limits for the run (see help).' }
if ($ok.Count -ne $rows.Count) { Write-Host 'A: FAIL - not every request created a path'; $failed = $true }
if ($ok | Where-Object { $_.retrieved -ne $ExpectCandidates }) {
    Write-Host "A: FAIL - candidatesRetrieved differs from $ExpectCandidates (default-candidates not set for the run?)"; $failed = $true
}
if ($ok.Count) {
    $ms = @($ok.ms | Sort-Object)
    $p50 = $ms[[Math]::Ceiling(0.5 * $ms.Count) - 1]
    Write-Host ("A: complete request time min={0} p50={1} max={2} ms" -f $ms[0], $p50, $ms[-1])
    if ($ms[-1] -gt $FrontendTimeoutSeconds * 1000) {
        Write-Host "A: FAIL - slowest request exceeds the $FrontendTimeoutSeconds s frontend timeout"; $failed = $true
    } elseif ($ms[-1] -gt ($FrontendTimeoutSeconds - 10) * 1000) {
        Write-Host 'A: WARN - less than 10 s headroom below the frontend timeout; report before launch'
    }
}

Write-Host "`nB. $Concurrent concurrent requests with one Idempotency-Key"
$key = New-Key
$jobs = 1..$Concurrent | ForEach-Object -Parallel {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-WebRequest -Method Post -Uri "$($using:ApiBase)/api/paths" -ContentType 'application/json' `
        -Headers @{ 'Idempotency-Key' = $using:key } -Body (@{ topic = $using:ConcurrentTopic } | ConvertTo-Json) `
        -SkipHttpErrorCheck -TimeoutSec 120
    $sw.Stop()
    $b = $null
    try { $b = $r.Content | ConvertFrom-Json } catch { }
    # retrievedAt as the raw string: ConvertFrom-Json would turn it into a DateTime without sub-second precision.
    [pscustomobject]@{ status = [int]$r.StatusCode; ms = [int]$sw.Elapsed.TotalMilliseconds; pathId = $b.pathId
        retrievedAt = [regex]::Match($r.Content, '"retrievedAt"\s*:\s*"([^"]+)"').Groups[1].Value }
} -ThrottleLimit $Concurrent
$jobs | Format-Table -AutoSize | Out-String | Write-Host
$created = @($jobs | Where-Object { $_.status -eq 201 }).Count
$success = @($jobs | Where-Object { $_.status -in 200, 201 })
$paths = @($success.pathId | Sort-Object -Unique).Count
$retrievals = @($success.retrievedAt | Sort-Object -Unique).Count
if ($success.Count -eq $Concurrent -and $created -eq 1 -and $paths -eq 1 -and $retrievals -eq 1) {
    Write-Host 'B: PASS - one path, one retrieval, one 201'
} else {
    Write-Host "B: FAIL - successes=$($success.Count) created=$created distinctPaths=$paths distinctRetrievals=$retrievals"
    $failed = $true
}

if ($failed) { Write-Host "`nRESULT: FAIL (report the measurements before changing limits)"; exit 1 }
Write-Host "`nRESULT: PASS (now revert the temporary variables)"
exit 0
