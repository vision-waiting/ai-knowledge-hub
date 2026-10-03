$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path $repository ('.runtime/startup-test-' + [Guid]::NewGuid().ToString('N'))
$originalLocation = Get-Location

# Run the real launcher against fake processes/endpoints in an isolated directory.
# No services are started or stopped by this regression test.
function Get-Process {
    param($Id, $ErrorAction)
    if ($Id -eq 1001 -and $global:startupTestScenario.backend) { return [pscustomobject]@{ Id = 1001; ProcessName = 'java'; HasExited = $false } }
    if ($Id -eq 1002 -and $global:startupTestScenario.frontend) { return [pscustomobject]@{ Id = 1002; ProcessName = 'cmd'; HasExited = $false } }
}
function Invoke-RestMethod {
    param($Uri, $TimeoutSec)
    if ($global:startupTestScenario.backend) { return @{ status = 'UP' } }
    throw 'Backend unavailable'
}
function Invoke-WebRequest {
    param($Uri, [switch]$UseBasicParsing, $TimeoutSec)
    if ($Uri -like '*/readyz') { return @{ StatusCode = 200 } }
    if ($global:startupTestScenario.frontend) { return @{ StatusCode = 200 } }
    throw 'Frontend unavailable'
}
function Get-NetTCPConnection {
    param($State, $LocalPort, $ErrorAction)
    if ($LocalPort -in $global:startupTestScenario.occupiedPorts) { return @{ OwningProcess = 9999 } }
}
function Get-Command { param($Name, $ErrorAction) return @{ Source = $Name } }
function java { 'openjdk 21.0.1' }
function node { 'v22.0.0' }
function mvn.cmd { $global:startupTestBuilds++; $global:LASTEXITCODE = 0 }
function docker {
    $global:LASTEXITCODE = 0
    switch ($args[0]) {
        'info' { return }
        'ps' { return @($global:startupTestScenario.projects.Keys) }
        'inspect' {
            if ($global:startupTestScenario.inspectFailure) { $global:LASTEXITCODE = 1; return }
            if ($args[2] -eq '{{json .Config.Labels}}') {
                # Reproduce the reported invalid JSON path without depending on terminal encoding.
                return '{"com.docker.compose.project":"4","com.docker.compose.project.working_dir":"D:\project\data"}'
            }
            Assert-Equal $args[2] '{{ index .Config.Labels `com.docker.compose.project` }}' 'Unexpected label query'
            return $global:startupTestScenario.projects[$args[3]]
        }
        'compose' { $global:startupTestComposeProject = $args[2]; return }
        default { throw "Unexpected Docker command: $args" }
    }
}
function Start-Sleep { param($Seconds) }
function Start-Process {
    param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle, [switch]$PassThru, $RedirectStandardOutput, $RedirectStandardError)
    if ($WindowStyle -ne 'Hidden') { throw 'Service window must be hidden' }
    $global:startupTestStarted.Add($FilePath)
    if ($FilePath -eq 'java') {
        $global:startupTestScenario.backend = -not $global:startupTestScenario.failBackend
        return [pscustomobject]@{ Id = 2001; HasExited = $false }
    }
    if ($FilePath -eq 'cmd.exe') {
        $global:startupTestScenario.frontend = -not $global:startupTestScenario.failFrontend
        return [pscustomobject]@{ Id = 2002; HasExited = $false }
    }
    throw "Unexpected process: $FilePath"
}
function taskkill.exe { $global:startupTestKilled.Add([int]$args[1]); $global:LASTEXITCODE = 0 }
function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ("$Actual" -ne "$Expected") { throw "$Message - expected '$Expected', got '$Actual'" }
}

try {
    foreach ($directory in @('SpringBoot/target', 'vue/node_modules', 'sql', '.runtime')) {
        New-Item -ItemType Directory -Path (Join-Path $fixtureRoot $directory) -Force | Out-Null
    }
    foreach ($file in @('SpringBoot/pom.xml', 'SpringBoot/target/ai-knowledge-hub-server-0.0.1-SNAPSHOT.jar', 'vue/package.json', 'sql/schema.sql', 'sql/data.sql', '.env')) {
        Set-Content -LiteralPath (Join-Path $fixtureRoot $file) -Value ''
    }
    if (-not (Test-Path -LiteralPath (Join-Path $repository 'start-dev.ps1'))) { throw 'Launcher is missing: start-dev.ps1' }
    Copy-Item -LiteralPath (Join-Path $repository 'start-dev.ps1') -Destination (Join-Path $fixtureRoot 'start-dev.ps1')
    $cases = @(
        @{ name = 'recover missing frontend'; backend = $true; frontend = $false; starts = 'cmd.exe'; builds = 0; backendPid = 1001; frontendPid = 2002 },
        @{ name = 'recover missing backend'; backend = $false; frontend = $true; starts = 'java'; builds = 1; backendPid = 2001; frontendPid = 1002 },
        @{ name = 'repeat healthy startup'; backend = $true; frontend = $true; starts = ''; builds = 0; backendPid = 1001; frontendPid = 1002 },
        @{ name = 'start both services'; backend = $false; frontend = $false; starts = 'java,cmd.exe'; builds = 1; backendPid = 2001; frontendPid = 2002 },
        @{ name = 'frontend recovery failure preserves backend'; backend = $true; frontend = $false; failFrontend = $true; error = 'Frontend startup timed out'; killed = '2002' },
        @{ name = 'backend recovery failure preserves frontend'; backend = $false; frontend = $true; failBackend = $true; error = 'Backend health check timed out'; killed = '2001' },
        @{ name = 'occupied backend port preserves frontend'; backend = $false; frontend = $true; occupiedPorts = @(8180); error = '8180'; killed = '' },
        @{ name = 'occupied frontend port preserves backend'; backend = $true; frontend = $false; occupiedPorts = @(5273); error = '5273'; killed = '' },
        @{ name = 'reuse project without parsing Windows path labels'; infrastructure = $true; projects = @{ redis = '4'; mysql = '4' }; project = '4'; backend = $false; frontend = $false; starts = 'java,cmd.exe'; builds = 1; backendPid = 2001; frontendPid = 2002 },
        @{ name = 'default project for fresh installation'; infrastructure = $true; projects = @{}; project = 'ai-knowledge-hub'; backend = $false; frontend = $false; starts = 'java,cmd.exe'; builds = 1; backendPid = 2001; frontendPid = 2002 },
        @{ name = 'conflicting projects stop before compose'; infrastructure = $true; projects = @{ redis = '4'; mysql = 'other' }; error = 'different Compose projects'; killed = '' },
        @{ name = 'failed inspect stops before compose'; infrastructure = $true; projects = @{ redis = '4' }; inspectFailure = $true; error = 'Unable to inspect container labels'; killed = '' }
    )
    foreach ($case in $cases) {
        $global:startupTestScenario = $case.Clone()
        $global:startupTestStarted = [Collections.Generic.List[string]]::new()
        $global:startupTestKilled = [Collections.Generic.List[int]]::new()
        $global:startupTestBuilds = 0
        $global:startupTestComposeProject = $null
        $record = Join-Path $fixtureRoot '.runtime/processes.json'
        @{ backendPid = 1001; frontendPid = 1002; backendPort = 8180; frontendPort = 5273 } | ConvertTo-Json | Set-Content -LiteralPath $record
        $failure = $null
        try { & (Join-Path $fixtureRoot 'start-dev.ps1') -SkipInfrastructure:(!$case.infrastructure) }
        catch { $failure = $_.Exception.Message }
        if ($case.error) {
            if ($failure -notlike "*$($case.error)*") { throw "$($case.name): unexpected failure '$failure'" }
            Assert-Equal ($global:startupTestKilled -join ',') $case.killed 'Failure cleanup killed the wrong processes'
            if ($case.occupiedPorts) { Assert-Equal $global:startupTestStarted.Count 0 'Started a service on an occupied port' }
            if ($case.infrastructure) { Assert-Equal $global:startupTestComposeProject $null 'Compose ran after an inspection error' }
        } else {
            if ($failure) { throw "$($case.name): $failure" }
            Assert-Equal ($global:startupTestStarted -join ',') $case.starts 'Wrong services started'
            Assert-Equal $global:startupTestBuilds $case.builds 'Wrong backend build count'
            Assert-Equal $global:startupTestKilled.Count 0 'Healthy process was stopped'
            if ($case.infrastructure) { Assert-Equal $global:startupTestComposeProject $case.project 'Wrong Compose project would change database volume' }
            $saved = Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
            Assert-Equal $saved.backendPid $case.backendPid 'Wrong saved backend PID'
            Assert-Equal $saved.frontendPid $case.frontendPid 'Wrong saved frontend PID'
            if ($case.backend -or $case.frontend) {
                Assert-Equal $saved.backendPort 8180 'Existing backend port changed'
                Assert-Equal $saved.frontendPort 5273 'Existing frontend port changed'
            }
        }
        Write-Host "PASS: $($case.name)"
    }
    Write-Host "All $($cases.Count) startup regression tests passed."
} finally {
    Set-Location -LiteralPath $originalLocation.Path
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $allowedRoot = [IO.Path]::GetFullPath((Join-Path $repository '.runtime')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedFixture.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test cleanup path' }
    if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
    Remove-Variable startupTestScenario, startupTestStarted, startupTestKilled, startupTestBuilds, startupTestComposeProject -Scope Global -ErrorAction SilentlyContinue
}
