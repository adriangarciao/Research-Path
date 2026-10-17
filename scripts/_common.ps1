# Shared helpers, dot-sourced by the other scripts. Never prints secret values.

$script:RepoRoot = Split-Path -Parent $PSScriptRoot

function Import-DotEnv {
    param([string]$Path = (Join-Path $script:RepoRoot '.env'))
    if (-not (Test-Path $Path)) {
        throw ".env not found at $Path. Run scripts\setup-env.ps1 first."
    }
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*(#|$)') { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and $value[0] -eq $value[-1] -and $value[0] -in '"', "'") {
                $value = $value.Substring(1, $value.Length - 2)
            }
            # Process scope only: nothing is written to user/machine environment.
            [Environment]::SetEnvironmentVariable($Matches[1], $value, 'Process')
        }
    }
}

function Get-JavaMajor([string]$javaHome) {
    $release = Join-Path $javaHome 'release'
    if (-not (Test-Path $release)) { return $null }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    return $null
}

# Makes this PowerShell process use a JDK 21 (PATH on this machine lists JDK 17 first).
function Use-Jdk21 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' `
        -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | ForEach-Object FullName
    foreach ($jdk in $candidates) {
        if ((Get-JavaMajor $jdk) -eq 21) {
            $env:JAVA_HOME = $jdk
            $env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
            return
        }
    }
    throw 'JDK 21 not found. Install Temurin 21 (https://adoptium.net) and set JAVA_HOME.'
}
