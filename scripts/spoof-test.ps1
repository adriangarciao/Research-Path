<#
.SYNOPSIS
  Spoof-resistance test for rate-limit identity (release gate, decision R3). Sends rebuild requests to an existing
  path, each claiming a different X-Real-IP (and X-Forwarded-For). If the edge overwrites these client-supplied values,
  all requests share one identity and the limiter answers 429 within the burst (default 10 rebuilds).
  PASS = a 429 arrived; FAIL = no 429 (client-chosen identities were accepted) -> DEPLOYMENT BLOCKER.
  Rebuilds make no provider calls; the test creates up to -Requests revisions on the given path.
.EXAMPLE
  .\scripts\spoof-test.ps1 -ApiBase https://researchpath-api.up.railway.app -PathId AbCdEfGhIjKlMnOpQrStUv
#>
param(
    [Parameter(Mandatory)] [string]$ApiBase,
    [Parameter(Mandatory)] [ValidatePattern('^[A-Za-z0-9_-]{22}$')] [string]$PathId,
    [int]$Requests = 12
)
$ErrorActionPreference = 'Stop'
$uri = "$($ApiBase.TrimEnd('/'))/api/paths/$PathId/revisions"
$budgets = 5, 10, 15, 20
$codes = for ($i = 1; $i -le $Requests; $i++) {
    $fake = "198.51.100.$((Get-Random -Minimum 1 -Maximum 254))"
    $body = @{ budget = $budgets[$i % 4]; experience = @('beginner', 'intermediate', 'advanced')[$i % 3] } | ConvertTo-Json -Compress
    $r = Invoke-WebRequest -Method Post -Uri $uri -ContentType 'application/json' -Body $body -SkipHttpErrorCheck `
        -Headers @{ 'X-Real-IP' = $fake; 'X-Forwarded-For' = $fake; 'Forwarded' = "for=$fake" }
    [int]$r.StatusCode
}
"status codes: $($codes -join ' ')"
if ($codes -contains 429) {
    'PASS: forged client addresses did not create new rate-limit identities.'
    exit 0
}
if ($codes -contains 400) {
    'FAIL: requests were refused as unattributable (400): the trusted header is configured but the edge did not set it.'
    exit 1
}
'FAIL: no 429 - forged X-Real-IP values were accepted as separate clients. DEPLOYMENT BLOCKER.'
exit 1
