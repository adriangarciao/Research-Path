<#
.SYNOPSIS
  Repeatable metadata-quality report for a candidate snapshot (playbook M04), independent of the web UI.
  Requires the backend running with the local profile (.\scripts\backend.ps1).
.EXAMPLE
  .\scripts\snapshot-report.ps1 -Topic "graph neural networks" -MaxCandidates 100   # creates a snapshot (live OpenAlex)
.EXAMPLE
  .\scripts\snapshot-report.ps1 -Id 05248d23-5521-4892-a8a1-680df19f0534            # re-reads an existing one
#>
[CmdletBinding(DefaultParameterSetName = 'Existing')]
param(
    [Parameter(ParameterSetName = 'Create', Mandatory)] [string]$Topic,
    [Parameter(ParameterSetName = 'Create')] [ValidateRange(1, 200)] [int]$MaxCandidates = 100,
    [Parameter(ParameterSetName = 'Existing', Mandatory)] [guid]$Id,
    [string]$BaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'

if ($PSCmdlet.ParameterSetName -eq 'Create') {
    $body = @{ topic = $Topic; maxCandidates = $MaxCandidates } | ConvertTo-Json
    $resp = Invoke-WebRequest "$BaseUrl/api/dev/snapshots" -Method Post -ContentType 'application/json' -Body $body -SkipHttpErrorCheck -TimeoutSec 300
} else {
    $resp = Invoke-WebRequest "$BaseUrl/api/dev/snapshots/$Id" -SkipHttpErrorCheck -TimeoutSec 60
}
$text = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
if ($resp.StatusCode -ge 400) {
    $problem = if ($text) { $text | ConvertFrom-Json } else { $null }
    $reason = if ($resp.StatusCode -eq 404) { 'snapshot not found' }
              elseif ($problem) { "$($problem.title): $($problem.detail)" } else { 'no details' }
    Write-Host "Request failed: HTTP $($resp.StatusCode) - $reason"
    exit 1
}
$json = $text | ConvertFrom-Json

$s = $json; $a = $s.availability; $c = $s.classification; $n = [math]::Max(1, $s.storedCount)
function Row($label, $count) { '| {0} | {1} | {2:P0} |' -f $label, $count, ($count / $n) }

@"
# Snapshot quality report

- Snapshot: ``$($s.id)``  (normalizer v$($s.normalizerVersion), provider $($s.provider))
- Topic: $($s.topic)
- Created: $($s.createdAt)
- Provider matches: $($s.providerTotalCount); requested $($s.requestedCount), received $($s.receivedCount), stored $($s.storedCount), rejected $($s.rejectedCount)

| Metadata | Members | Share |
|---|---:|---:|
$(Row 'Abstract' $a.withAbstract)
$(Row 'Abstract truncated at limit' $a.abstractsTruncated)
$(Row '>= 1 topic' $a.withTopics)
$(Row '>= 1 keyword' $a.withKeywords)
$(Row 'Authors' $a.withAuthors)
$(Row 'Publication year' $a.withYear)
$(Row 'DOI' $a.withDoi)
$(Row 'Safe source link (OA / DOI / landing)' $a.withSourceLink)
$(Row 'Reference list supplied' $a.withReferenceListSupplied)
$(Row 'Non-empty reference list' $a.withReferences)

- Distinct topics: $($c.distinctTopics); distinct keywords: $($c.distinctKeywords) (no confidence threshold)
- Most common primary topic: $($c.mostCommonPrimaryTopic) ($($c.mostCommonPrimaryTopicCount) of $($s.storedCount))
- Citation edges between members: $($s.internalCitationEdges)
- Possible duplicate pairs flagged (evidence): $($s.possibleDuplicatePairs)
- Retrieval notes (rejections / page drift): $(@($s.retrievalNotes).Count)

Counts describe this retrieved pool only; they are not relevance or quality judgments.
"@

''
'## Relevance gate (conservative policy, decision D07)'
''
$rel = $s.relevance
if ($null -eq $rel) {
    'Not evaluated: this snapshot was created before the relevance gate existed.'
} else {
    "- Policy v$($rel.policyVersion): ELIGIBLE $($rel.eligible) · TANGENTIAL $($rel.tangential) · BORDERLINE $($rel.borderline) · INELIGIBLE $($rel.ineligible)"
    "- Optimization candidates (eligible and not a suppressed duplicate): $($rel.optimizationCandidates)"
    foreach ($w in @($rel.warnings)) { "- Warning $($w.code): $($w.message)" }
    # Parenthesized: piped directly, Invoke-RestMethod passes the JSON array as a single object.
    $excluded = @((Invoke-RestMethod "$BaseUrl/api/dev/snapshots/$($s.id)/relevance" -TimeoutSec 60) |
        Where-Object { $_.tier -in 'TANGENTIAL', 'BORDERLINE' })
    if ($excluded.Count -gt 0) {
        ''
        "Excluded for review (tangential / borderline, first 25 of $($excluded.Count)):"
        foreach ($x in ($excluded | Select-Object -First 25)) {
            $why = if ($x.tier -eq 'TANGENTIAL') { "aspect: $($x.evidence.aspectCues -join ', ')" } else { "missing: $($x.evidence.missing -join ', ')" }
            "- #$($x.providerRank) $($x.tier) ($why): $($x.title)"
        }
    }
}

''
'## Concepts (conservative policy, decisions D03/D08)'
''
if ($null -ne $rel) {
    $cm = Invoke-RestMethod "$BaseUrl/api/dev/snapshots/$($s.id)/concepts" -TimeoutSec 60
    $st = $cm.stats
    "- Policy v$($cm.policyVersion), $($cm.representation): $($st.universeSize) concepts over $($cm.candidates) candidates; $($st.candidatesWithConcepts) candidates carry at least one; mean $($st.meanConceptsPerCandidate) per candidate"
    "- Distinct concept signatures: $($st.distinctSignatures); top concept share: $($st.topConceptShare); mean pairwise Jaccard: $($st.meanPairwiseJaccard)"
    "- Excluded: $((($cm.excludedByReason.PSObject.Properties | ForEach-Object { "$($_.Name) $($_.Value)" }) -join ' · '))"
    ''
    '| Concept | Kind | Support | Weight |'
    '|---|---|---:|---:|'
    foreach ($c in ($cm.included | Select-Object -First 15)) { "| $($c.label) | $($c.kind) | $($c.support) | $($c.weight) |" }
} else {
    'Not evaluated: this snapshot predates frozen evaluation inputs.'
}

''
'## Identity resolution (conservative policy, decision D06)'
''
$i = $s.identity
if ($null -eq $i) {
    'Not evaluated: this snapshot was created before identity resolution existed.'
    exit 0
}
"- Policy v$($i.policyVersion): SAME_WORK $($i.sameWorkPairs) · PROBABLE_VERSION $($i.probableVersionPairs) · DISTINCT $($i.distinctPairs) · UNRESOLVED $($i.unresolvedPairs) (work pairs)"
"- Candidates after suppression: $($i.canonicalMembers) of $($s.storedCount); suppressed obvious duplicates: $($i.suppressedMembers) (kept as provenance)"

$d = Invoke-RestMethod "$BaseUrl/api/dev/snapshots/$($s.id)/identity" -TimeoutSec 60
if (@($d.suppressed).Count -gt 0) {
    ''
    '| Suppressed | Canonical (kept) | Title |'
    '|---|---|---|'
    foreach ($x in $d.suppressed) { "| $($x.openalexId) | $($x.canonicalOpenalexId) | $($x.canonicalTitle) |" }
}
$versions = @($d.decisions | Where-Object decision -eq 'PROBABLE_VERSION')
if ($versions.Count -gt 0) {
    ''
    'Probable versions (both kept, flagged for review):'
    foreach ($v in $versions) {
        "- $($v.a.openalexId) ($($v.a.year), $($v.a.workType)) / $($v.b.openalexId) ($($v.b.year), $($v.b.workType)): $($v.a.title)"
    }
}
