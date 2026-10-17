<#
.SYNOPSIS
  Minimal server-side OpenAlex check using OPENALEX_API_KEY from ..\.env.
  Prints only HTTP status, result count and one shortened title. Never prints the key.
  Not part of the application; real ingestion is a later milestone.
.OUTPUTS
  LIVE API VERIFIED | API KEY NOT PROVIDED | API KEY PRESENT BUT CHECK FAILED
#>
param([string]$Query = 'graph neural networks')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_common.ps1')

Import-DotEnv
$key = $env:OPENALEX_API_KEY
if ([string]::IsNullOrWhiteSpace($key)) {
    Write-Host 'API KEY NOT PROVIDED - paste it after OPENALEX_API_KEY= in .env and re-run.'
    exit 2
}

$uri = 'https://api.openalex.org/works?search=' + [uri]::EscapeDataString($Query) +
       '&per_page=1&select=id,display_name,publication_year'
try {
    # Key travels in the Authorization header, not the URL, so it cannot leak via logged URLs.
    $resp = Invoke-WebRequest -Uri $uri -Headers @{ Authorization = "Bearer $key" } -TimeoutSec 30 -SkipHttpErrorCheck
} catch {
    Write-Host "API KEY PRESENT BUT CHECK FAILED - network error: $($_.Exception.GetType().Name)"
    exit 1
} finally {
    $key = $null
}

if ($resp.StatusCode -ne 200) {
    $hint = if ($resp.StatusCode -in 401, 403) { ' (key rejected - check it was pasted correctly)' }
            elseif ($resp.StatusCode -eq 429) { ' (rate limit or daily budget exhausted)' } else { '' }
    Write-Host "API KEY PRESENT BUT CHECK FAILED - HTTP $($resp.StatusCode)$hint"
    exit 1
}

$body  = $resp.Content | ConvertFrom-Json
$first = $body.results | Select-Object -First 1
$title = if ($first) { $first.display_name } else { '(no results)' }
if ($title.Length -gt 80) { $title = $title.Substring(0, 77) + '...' }
Write-Host "HTTP 200 | works matching '$Query': $($body.meta.count) | first: $title ($($first.publication_year))"
Write-Host 'LIVE API VERIFIED'
