[CmdletBinding()]
param([switch]$OpenBrowser)
$ErrorActionPreference = 'Stop'
$envPath = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Create .env from .env.example and configure local AI first. See docs/local-ai.md.'
}
$configuration = @{}
foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $configuration[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
if ($configuration.LLM_BASE_URL -notin @('http://127.0.0.1:11434/v1', 'http://localhost:11434/v1') `
    -or $configuration.LLM_ENABLED -ne 'true' -or $configuration.LOCAL_LLM_ENABLED -ne 'true' -or -not $configuration.LLM_MODEL) {
    throw 'The project .env is not configured for local AI. See docs/local-ai.md.'
}
& (Join-Path $PSScriptRoot 'scripts/start-ollama.ps1') -ExecutablePath $configuration.OLLAMA_EXECUTABLE -ModelsPath $configuration.OLLAMA_MODELS
$available = (Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5).models.name
$required = @($configuration.LLM_MODEL)
if ($configuration.EMBEDDING_ENABLED -eq 'true' -and $configuration.EMBEDDING_BASE_URL -in @('http://127.0.0.1:11434/v1', 'http://localhost:11434/v1')) {
    $required += $configuration.EMBEDDING_MODEL
}
foreach ($model in $required) {
    if ($model -notin $available) { throw "Model missing: $model. Run ollama pull $model first." }
}
& (Join-Path $PSScriptRoot 'start-dev.ps1') -OpenBrowser:$OpenBrowser
