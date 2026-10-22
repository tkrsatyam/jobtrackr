<#
.SYNOPSIS
    Builds Maven microservices for JobTrackr Docker environment.
.DESCRIPTION
    Runs 'mvn clean install -DskipTests' across services using their local mvnw wrapper.
.PARAMETER Services
    Specific service directory names to build. If omitted, builds all services configured in docker-compose.
.PARAMETER All
    If specified, builds all services found under ./services.
.PARAMETER RunTests
    If specified, runs tests during the Maven build (omits -DskipTests).
.EXAMPLE
    .\build-services.ps1
.EXAMPLE
    .\build-services.ps1 email-ingestion-service application-service
.EXAMPLE
    .\build-services.ps1 -All
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Services,

    [switch]$All,
    [switch]$RunTests
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (Test-Path (Join-Path $ScriptDir "services")) {
    $RootDir = $ScriptDir
} elseif (Test-Path (Join-Path $ScriptDir "..\services")) {
    $RootDir = (Resolve-Path (Join-Path $ScriptDir "..")).Path
} else {
    $RootDir = (Get-Location).Path
}

$ServicesDir = Join-Path $RootDir "services"

# Default services required for Docker compose
$DockerServices = @(
    "eureka-server",
    "api-gateway",
    "user-service",
    "application-service",
    "email-ingestion-service"
)

if ($All) {
    $TargetServices = Get-ChildItem -Path $ServicesDir -Directory | 
        Where-Object { Test-Path (Join-Path $_.FullName "pom.xml") } | 
        Select-Object -ExpandProperty Name
} elseif ($Services -and $Services.Count -gt 0) {
    $TargetServices = $Services
} else {
    $TargetServices = $DockerServices
}

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " JobTrackr - Microservices Build Script" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host "Target services ($($TargetServices.Count)): $($TargetServices -join ', ')"
Write-Host "Skip tests: $(!$RunTests)"
Write-Host ""

$MavenGoals = @("clean", "install")
if (-not $RunTests) {
    $MavenArgs = @("-DskipTests")
} else {
    $MavenArgs = @()
}

$StopwatchTotal = [System.Diagnostics.Stopwatch]::StartNew()
$Results = @()
$Failed = $false

$Index = 1
foreach ($svc in $TargetServices) {
    $svcPath = Join-Path $ServicesDir $svc
    if (-not (Test-Path $svcPath)) {
        Write-Host "[-] Service directory '$svc' not found at '$svcPath'!" -ForegroundColor Red
        $Results += [PSCustomObject]@{ Service = $svc; Status = "NOT FOUND"; Duration = "0s" }
        $Failed = $true
        break
    }

    $mvnwCmd = Join-Path $svcPath "mvnw.cmd"
    if (Test-Path $mvnwCmd) {
        $mvnExec = $mvnwCmd
    } else {
        $mvnExec = "mvn"
    }

    Write-Host "[$Index/$($TargetServices.Count)] Building $svc..." -ForegroundColor Yellow
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    Push-Location $svcPath
    try {
        & $mvnExec @MavenGoals @MavenArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed with exit code $LASTEXITCODE"
        }
        $sw.Stop()
        $duration = "{0:N1}s" -f $sw.Elapsed.TotalSeconds
        Write-Host "[+] $svc built successfully in $duration" -ForegroundColor Green
        $Results += [PSCustomObject]@{ Service = $svc; Status = "SUCCESS"; Duration = $duration }
    } catch {
        $sw.Stop()
        $duration = "{0:N1}s" -f $sw.Elapsed.TotalSeconds
        Write-Host "[-] Failed to build $svc : $_" -ForegroundColor Red
        $Results += [PSCustomObject]@{ Service = $svc; Status = "FAILED"; Duration = $duration }
        $Failed = $true
        Pop-Location
        break
    }
    Pop-Location
    $Index++
    Write-Host ""
}

$StopwatchTotal.Stop()
$totalMinutes = [Math]::Floor($StopwatchTotal.Elapsed.TotalMinutes)
$totalSeconds = $StopwatchTotal.Elapsed.Seconds
$totalFormatted = "$($totalMinutes)m $($totalSeconds)s"

Write-Host "====================================================" -ForegroundColor Cyan
Write-Host " BUILD SUMMARY" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan
foreach ($r in $Results) {
    if ($r.Status -eq "SUCCESS") {
        Write-Host (" [OK]   {0,-28} {1}" -f $r.Service, $r.Duration) -ForegroundColor Green
    } else {
        Write-Host (" [FAIL] {0,-28} {1}" -f $r.Service, $r.Duration) -ForegroundColor Red
    }
}
Write-Host "----------------------------------------------------"
Write-Host "Total elapsed time: $totalFormatted"

if ($Failed) {
    Write-Host "Build failed. Please check the logs above." -ForegroundColor Red
    exit 1
} else {
    Write-Host "All target JARs updated successfully. Ready for docker compose." -ForegroundColor Green
    exit 0
}
