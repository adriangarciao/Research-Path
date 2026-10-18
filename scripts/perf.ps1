<#
.SYNOPSIS
  Small, reproducible latency measurement of the path API (playbook M13 slice 5) against a running local backend.
  Cold = new paths (retrieval from OpenAlex, ~USD 0.001 each); warm = regenerations from the frozen snapshot;
  read = GET of a stored revision. Prints p50/p95/max per operation and the backend's memory.
  Start the backend with RESEARCHPATH_RATE_LIMIT_ENABLED=false for this measurement, otherwise 429s are counted.
.EXAMPLE
  .\scripts\perf.ps1 -ColdTopics 5 -Reads 200
#>
param(
    [int]$ColdTopics = 5,
    [int]$Reads = 200,
    [string]$Base = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'

$topics = @('graph neural networks', 'reinforcement learning in robotics', 'AI-assisted medical diagnosis',
    'federated learning privacy', 'protein structure prediction', 'quantum error correction',
    'causal inference in economics', 'speech recognition transformers') | Select-Object -First $ColdTopics

function Measure-Call([scriptblock]$Call) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $status = & $Call
    $sw.Stop()
    [pscustomobject]@{ ms = $sw.Elapsed.TotalMilliseconds; status = $status }
}

function Invoke-Json([string]$Method, [string]$Url, $Body) {
    try {
        $r = Invoke-WebRequest -Method $Method -Uri $Url -ContentType 'application/json' -SkipHttpErrorCheck `
            -Body ($Body | ConvertTo-Json -Compress)
        return $r
    } catch {
        return [pscustomobject]@{ StatusCode = 0; Content = '' }
    }
}

function Summary([string]$Name, $Samples) {
    $ok = @($Samples | Where-Object { $_.status -ge 200 -and $_.status -lt 300 })
    $sorted = @($ok | ForEach-Object ms | Sort-Object)
    if ($sorted.Count -eq 0) { return [pscustomobject]@{ operation = $Name; n = 0; failed = $Samples.Count } }
    $pct = { param($p) $sorted[[Math]::Min($sorted.Count - 1, [Math]::Ceiling($p * $sorted.Count) - 1)] }
    [pscustomobject]@{
        operation = $Name; n = $sorted.Count; failed = $Samples.Count - $ok.Count
        p50_ms = [Math]::Round((& $pct 0.50)); p95_ms = [Math]::Round((& $pct 0.95)); max_ms = [Math]::Round($sorted[-1])
    }
}

Invoke-RestMethod "$Base/actuator/health" | Out-Null
$results = @()

# Cold: one new path per topic (sequential, distinct topics).
$paths = @()
$cold = foreach ($t in $topics) {
    $resp = $null
    $m = Measure-Call { $script:resp = Invoke-Json POST "$Base/api/paths" @{ topic = $t; budget = 10 }; $script:resp.StatusCode }
    if ($resp.StatusCode -eq 201) { $paths += ($resp.Content | ConvertFrom-Json).pathId }
    $m
}
$results += Summary 'create (cold, provider)' $cold
if ($paths.Count -eq 0) { throw 'no path was created; cannot measure warm operations' }

# Warm: every experience x focus x budget combination on the first path (new revisions, no provider).
$warm = foreach ($e in 'beginner', 'intermediate', 'advanced') {
    foreach ($f in 'balanced', 'foundational', 'recent') {
        foreach ($b in 5, 10, 15, 20) {
            if ($e -eq 'intermediate' -and $f -eq 'balanced' -and $b -eq 10) { continue }
            Measure-Call { (Invoke-Json POST "$Base/api/paths/$($paths[0])/revisions" @{ experience = $e; focus = $f; budget = $b }).StatusCode }
        }
    }
}
$results += Summary 'regenerate (warm, new revision)' $warm

# Repeats of a stored option set (double clicks): answered without generation.
$repeat = 1..20 | ForEach-Object {
    Measure-Call { (Invoke-Json POST "$Base/api/paths/$($paths[0])/revisions" @{ experience = 'advanced'; focus = 'recent'; budget = 20 }).StatusCode }
}
$results += Summary 'regenerate (identical repeat)' $repeat

$read = 1..$Reads | ForEach-Object {
    $id = $paths[$_ % $paths.Count]
    Measure-Call { (Invoke-WebRequest "$Base/api/paths/$id" -SkipHttpErrorCheck).StatusCode }
}
$results += Summary 'get (stored revision)' $read

$results | Format-Table -AutoSize | Out-String | Write-Output
$backendPid = (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
if ($backendPid) {
    $proc = Get-Process -Id $backendPid
    Write-Output ("backend process: working set {0:N0} MB, CPU time {1:N1} s" -f ($proc.WorkingSet64 / 1MB), $proc.CPU)
}
Write-Output "paths created: $($paths -join ', ')"
