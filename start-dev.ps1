[CmdletBinding()]
param(
    [switch]$SkipInfrastructure,
    [switch]$DemoAi,
    [switch]$OpenBrowser
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$runtime = Join-Path $root '.runtime'
$originalLocation = Get-Location
$originalEnvironment = @{}
Get-ChildItem Env: | ForEach-Object { $originalEnvironment[$_.Name] = $_.Value }
$started = [Collections.Generic.List[int]]::new()
$lock = $null

function Require-Tool([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required tool not found: $Name. Install it and reopen the launcher."
    }
}
function Test-Backend([int]$Port) {
    try { return (Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3).status -eq 'UP' }
    catch { return $false }
}
function Test-Frontend([int]$Port) {
    try { return (Invoke-WebRequest "http://127.0.0.1:$Port" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 }
    catch { return $false }
}
function Test-Port([int]$Port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}
function Find-Port([int]$Port) {
    for ($candidate = $Port; $candidate -lt [Math]::Min($Port + 100, 65536); $candidate++) {
        if (-not (Test-Port $candidate)) { return $candidate }
    }
    throw "No available port near $Port."
}
function Wait-Service([scriptblock]$Check, [string]$Failure, $Process) {
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        if (& $Check) { return }
        if ($Process -and $Process.HasExited) { throw "$Failure (process exited). See $runtime" }
        Start-Sleep -Seconds 2
    }
    throw "$Failure. See $runtime"
}

try {
    Set-Location -LiteralPath $root
    foreach ($file in @('SpringBoot/pom.xml', 'vue/package.json', 'sql/schema.sql', 'sql/data.sql')) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $file))) { throw "Missing project file: $file" }
    }
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    try {
        $lock = [IO.File]::Open((Join-Path $runtime 'startup.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch {
        throw 'Another launcher is running. Please wait for it to finish.'
    }

    if (-not (Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
    # Read simple KEY=value entries as data, never execute .env content.
    foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
    if ($DemoAi) { $env:LLM_ENABLED = 'false'; $env:EMBEDDING_ENABLED = 'false'; $env:AI_MODE = 'local' }

    $recordPath = Join-Path $runtime 'processes.json'
    $previous = $null
    if (Test-Path -LiteralPath $recordPath) {
        try { $previous = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json }
        catch { Write-Warning 'Ignoring unreadable process record.' }
    }
    $backendHealthy = $false
    $frontendHealthy = $false
    if ($previous) {
        $backendHealthy = [bool](Get-Process -Id $previous.backendPid -ErrorAction SilentlyContinue) -and (Test-Backend $previous.backendPort)
        $frontendHealthy = [bool](Get-Process -Id $previous.frontendPid -ErrorAction SilentlyContinue) -and (Test-Frontend $previous.frontendPort)
    }
    if ($backendHealthy -or $frontendHealthy) {
        $backendPort = [int]$previous.backendPort
        $frontendPort = [int]$previous.frontendPort
        if (-not $backendHealthy -and (Test-Port $backendPort)) { throw "Recovery port $backendPort is occupied. Stop the conflicting process first." }
        if (-not $frontendHealthy -and (Test-Port $frontendPort)) { throw "Recovery port $frontendPort is occupied. Stop the conflicting process first." }
    } else {
        $preferredBackend = 8080
        if ($env:SERVER_PORT) { $preferredBackend = [int]$env:SERVER_PORT }
        $backendPort = Find-Port $preferredBackend
        $frontendPort = Find-Port 5173
        if ($frontendPort -eq $backendPort) { $frontendPort = Find-Port ($frontendPort + 1) }
    }
    $url = "http://127.0.0.1:$frontendPort"
    if ($backendHealthy -and $frontendHealthy) {
        Write-Host "Already running: $url"
        if ($OpenBrowser) { Start-Process $url }
        return
    }

    if (-not $backendHealthy) {
        if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
        Require-Tool 'java'
        Require-Tool 'mvn.cmd'
        $javaVersion = (& java --version | Out-String)
        if ($javaVersion -notmatch '(?m)^(?:java|openjdk)\s+(\d+)' -or [int]$Matches[1] -lt 21) { throw 'Java 21 or newer is required. Set JAVA_HOME to your JDK.' }
    }
    if (-not $frontendHealthy) {
        Require-Tool 'node'
        Require-Tool 'npm.cmd'
        $nodeVersion = & node --version
        if ($nodeVersion -notmatch '^v(\d+)' -or [int]$Matches[1] -lt 20) { throw 'Node.js 20 or newer is required.' }
    }

    if (-not $SkipInfrastructure) {
        Require-Tool 'docker'
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) {
            $desktop = @(
                "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe",
                "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
            ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
            if (-not $desktop) { throw 'Start Docker Desktop, then run this script again.' }
            Write-Host 'Starting Docker Desktop...'
            Start-Process -FilePath $desktop -WindowStyle Hidden
            Wait-Service { & docker info *> $null; $LASTEXITCODE -eq 0 } 'Docker startup timed out' $null
        }
        Write-Host 'Starting MySQL, Redis and Qdrant...'
        # Chinese directory names cannot be used as Compose's inferred project name.
        # Reuse an existing project's name so its database volume is retained.
        $containerIds = @(& docker ps -aq --filter 'name=ai-knowledge-hub-')
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect existing Docker services.' }
        $projects = @($containerIds | ForEach-Object {
            # Read only the project name; unrelated labels may contain Windows paths.
            # Go raw-string quotes also survive Windows PowerShell's native argument handling.
            $projectName = & docker inspect --format '{{ index .Config.Labels `com.docker.compose.project` }}' $_
            if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect container labels.' }
            if ($projectName -and $projectName -ne '<no value>') { $projectName.Trim() }
        } | Where-Object { $_ } | Sort-Object -Unique)
        if ($projects.Count -gt 1) { throw 'Existing knowledge-hub containers belong to different Compose projects. Check Docker Desktop.' }
        $composeProject = if ($projects.Count -eq 1) { $projects[0] } else { 'ai-knowledge-hub' }
        & docker compose -p $composeProject up -d --wait --wait-timeout 180 mysql redis qdrant
        if ($LASTEXITCODE -ne 0) { throw 'Docker services failed to start. Check Docker Desktop and port conflicts.' }
        $env:MYSQL_HOST = '127.0.0.1'
        $env:MYSQL_PORT = if ($env:MYSQL_DOCKER_PORT) { $env:MYSQL_DOCKER_PORT } else { '3307' }
        $env:REDIS_HOST = '127.0.0.1'
        $env:QDRANT_HOST = '127.0.0.1'
        $qdrantPort = if ($env:QDRANT_PORT) { $env:QDRANT_PORT } else { '6333' }
        Wait-Service {
            try { (Invoke-WebRequest "http://127.0.0.1:$qdrantPort/readyz" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 }
            catch { $false }
        } 'Qdrant startup timed out' $null
    }

    $backendPid = if ($backendHealthy) { $previous.backendPid } else { 0 }
    $frontendPid = if ($frontendHealthy) { $previous.frontendPid } else { 0 }
    if (-not $backendHealthy) {
        Write-Host 'Building backend (first build may download dependencies)...'
        & mvn.cmd -f (Join-Path $root 'SpringBoot/pom.xml') -DskipTests package *> (Join-Path $runtime 'backend-build.log')
        if ($LASTEXITCODE -ne 0) { throw "Backend build failed. See $runtime\backend-build.log" }
        $jar = Join-Path $root 'SpringBoot/target/ai-knowledge-hub-server-0.0.1-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $jar)) { throw "Build output missing: $jar" }
        $env:SERVER_PORT = "$backendPort"
        $backend = Start-Process -FilePath 'java' -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend-error.log')
        $backendPid = $backend.Id
        $started.Add($backendPid)
        Wait-Service { Test-Backend $backendPort } 'Backend health check timed out' $backend
    }
    if (-not $frontendHealthy) {
        Push-Location -LiteralPath (Join-Path $root 'vue')
        try {
            if (-not (Test-Path -LiteralPath 'node_modules')) {
                Write-Host 'Installing frontend dependencies...'
                & npm.cmd ci *> (Join-Path $runtime 'frontend-install.log')
                if ($LASTEXITCODE -ne 0) { throw "Frontend install failed. See $runtime\frontend-install.log" }
            }
        } finally { Pop-Location }
        $env:VITE_BACKEND_URL = "http://127.0.0.1:$backendPort"
        $env:VITE_API_BASE_URL = '/api'
        $frontend = Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', "npm.cmd run dev -- --host 127.0.0.1 --port $frontendPort --strictPort") -WorkingDirectory (Join-Path $root 'vue') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend-error.log')
        $frontendPid = $frontend.Id
        $started.Add($frontendPid)
        Wait-Service { Test-Frontend $frontendPort } 'Frontend startup timed out' $frontend
    }
    @{ backendPid = $backendPid; frontendPid = $frontendPid; backendPort = $backendPort; frontendPort = $frontendPort } |
        ConvertTo-Json | Set-Content -LiteralPath $recordPath -Encoding UTF8
    Write-Host "Ready: $url"
    Write-Host "Backend: http://127.0.0.1:$backendPort    Logs: $runtime"
    # Opening a browser is optional and must not roll back healthy services.
    if ($OpenBrowser) {
        try { Start-Process $url } catch { Write-Warning "Open $url in your browser." }
    }
} catch {
    foreach ($processId in $started) { & taskkill.exe /PID $processId /T /F *> $null }
    throw
} finally {
    if ($lock) { $lock.Dispose() }
    foreach ($entry in @(Get-ChildItem Env:)) {
        if (-not $originalEnvironment.ContainsKey($entry.Name)) { [Environment]::SetEnvironmentVariable($entry.Name, $null, 'Process') }
    }
    foreach ($name in $originalEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process') }
    Set-Location -LiteralPath $originalLocation.Path
}
