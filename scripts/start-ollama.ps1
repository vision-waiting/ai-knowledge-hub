[CmdletBinding()]
param(
    [string]$ExecutablePath,
    [string]$ModelsPath
)
$ErrorActionPreference = 'Stop'
function Test-Ollama {
    try { return [bool](Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 3).version }
    catch { return $false }
}
if (Test-Ollama) { Write-Host 'Ollama is already running on 127.0.0.1:11434'; return }
if (-not $ExecutablePath) {
    $command = Get-Command 'ollama.exe' -ErrorAction SilentlyContinue
    if ($command) { $ExecutablePath = $command.Source }
}
if (-not $ExecutablePath) {
    throw 'Ollama executable not found. Install Ollama and reopen the terminal, or pass -ExecutablePath.'
}
$runtime = Join-Path (Split-Path -Parent $PSScriptRoot) '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$settings = @{
    OLLAMA_HOST = '127.0.0.1:11434'
    OLLAMA_NO_CLOUD = '1'
    OLLAMA_NUM_PARALLEL = '1'
    OLLAMA_MAX_LOADED_MODELS = '2'
    OLLAMA_MAX_QUEUE = '2'
}
if ($ModelsPath) { $settings.OLLAMA_MODELS = $ModelsPath }
$previous = @{}
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    $process = Start-Process -FilePath $ExecutablePath -ArgumentList 'serve' -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtime 'ollama.log') -RedirectStandardError (Join-Path $runtime 'ollama-error.log')
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    if (Test-Ollama) { Write-Host 'Ollama ready on 127.0.0.1:11434'; return }
    if ($process.HasExited) { throw 'Ollama exited. See .runtime/ollama-error.log.' }
    Start-Sleep -Seconds 1
}
throw 'Ollama startup timed out. See .runtime/ollama-error.log.'
