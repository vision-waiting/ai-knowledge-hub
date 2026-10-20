$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'start-ollama.ps1'
function Test-Path { param($LiteralPath, $Path)
    # Never invoke any legacy launcher outside this repository during a test.
    if ($LiteralPath -like '*start-ai.ps1') { return $false }
    if ($LiteralPath) { return Microsoft.PowerShell.Management\Test-Path -LiteralPath $LiteralPath }
    return Microsoft.PowerShell.Management\Test-Path -Path $Path
}
function Invoke-RestMethod { param($Uri, $TimeoutSec)
    if ($global:ollamaTestReady) { return @{ version = 'test' } }
    throw 'Not running'
}
function Get-Command { param($Name, $ErrorAction)
    if ($global:ollamaTestMissing) { return $null }
    return @{ Source = 'test-ollama.exe' }
}
function Start-Process {
    param($FilePath, $ArgumentList, $WindowStyle, [switch]$PassThru, $RedirectStandardOutput, $RedirectStandardError)
    if ($WindowStyle -ne 'Hidden' -or $ArgumentList -ne 'serve') { throw 'Unexpected process launch' }
    if ($env:OLLAMA_HOST -ne '127.0.0.1:11434') { throw 'Ollama must bind to loopback' }
    $global:ollamaTestStarts++
    $global:ollamaTestReady = $true
    return [pscustomobject]@{ HasExited = $false }
}
$global:ollamaTestStarts = 0
$global:ollamaTestReady = $true
$global:ollamaTestMissing = $true
& $scriptPath
if ($global:ollamaTestStarts -ne 0) { throw 'Healthy service should be reused' }
Write-Host 'PASS reuses running service without requiring a local executable'
$global:ollamaTestReady = $false
$failure = $null
try { & $scriptPath } catch { $failure = $_.Exception.Message }
if ($failure -notlike '*Ollama*not found*') { throw "Expected actionable missing tool error, got: $failure" }
Write-Host 'PASS reports missing Ollama'
$global:ollamaTestMissing = $false
$previousHost = $env:OLLAMA_HOST
& $scriptPath
if ($global:ollamaTestStarts -ne 1) { throw 'Expected exactly one service launch' }
if ($env:OLLAMA_HOST -ne $previousHost) { throw 'Parent environment was changed' }
Write-Host 'PASS launches hidden on loopback and restores environment'
