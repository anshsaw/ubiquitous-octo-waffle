<#
.SYNOPSIS
    Starts the whole PortfolioPilot AI stack: MongoDB, the Spring Boot API and a
    static server for the frontend.

.DESCRIPTION
    The login page cannot work unless all three are running. Missing any one of
    them produces a different, confusing symptom:

        no MongoDB  -> backend starts but every query fails
        no backend  -> "Cannot reach the server" toast
        file:// page -> silent CORS failure with nothing in the UI

    This script starts all three, waits for each to become healthy, seeds the
    database on first run, and prints the login credentials.

.PARAMETER Reseed
    Wipe and re-seed the database. Use this if data looks wrong or you want the
    demo dates refreshed.

.PARAMETER Stop
    Stop everything this script started.

.EXAMPLE
    .\start-dev.ps1
    .\start-dev.ps1 -Reseed
    .\start-dev.ps1 -Stop
#>
[CmdletBinding()]
param(
    [switch]$Reseed,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'

$Root        = $PSScriptRoot
$MongoPort   = 27017
$ApiPort     = 8080
$WebPort     = 5500
$DataDir     = Join-Path $Root '.devdata\mongo'
$LogDir      = Join-Path $Root '.devdata\logs'

function Write-Step($msg)  { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)    { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn2($msg) { Write-Host "    $msg" -ForegroundColor Yellow }
function Write-Err2($msg)  { Write-Host "    $msg" -ForegroundColor Red }

function Test-Port($port) {
    try {
        $c = New-Object Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $port)
        $c.Close()
        return $true
    } catch {
        return $false
    }
}

function Wait-Port($port, $name, $timeoutSec = 90) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Milliseconds 700
    }
    Write-Err2 "$name did not come up on port $port within ${timeoutSec}s"
    return $false
}

# ---------------------------------------------------------------- stop mode

if ($Stop) {
    Write-Step 'Stopping the stack'
    foreach ($n in @('java', 'mongod')) {
        $p = Get-Process $n -ErrorAction SilentlyContinue
        if ($p) { $p | Stop-Process -Force; Write-Ok "stopped $n" }
    }
    Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'serve|http-server' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; Write-Ok 'stopped static server' }
    Write-Host ''
    return
}

# ------------------------------------------------------------ locate tools

Write-Step 'Locating tools'

# Java: PATH, then JAVA_HOME, then any portable JDK dropped under %TEMP%\opencode.
$JavaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $JavaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) { $JavaExe = $candidate }
}
if (-not $JavaExe) {
    $portable = Get-ChildItem "$env:TEMP\opencode\tools" -Filter 'jdk-*' -Directory -ErrorAction SilentlyContinue |
                Select-Object -First 1
    if ($portable) {
        $candidate = Join-Path $portable.FullName 'bin\java.exe'
        if (Test-Path $candidate) { $JavaExe = $candidate }
    }
}
if (-not $JavaExe) {
    Write-Err2 'No Java found. Install JDK 21: winget install EclipseAdoptium.Temurin.21.JDK'
    return
}
Write-Ok "java   $JavaExe"

# mongod: PATH, then a standard Program Files install, then the portable copy.
$MongodExe = (Get-Command mongod -ErrorAction SilentlyContinue).Source
if (-not $MongodExe) {
    $installed = Get-ChildItem 'C:\Program Files\MongoDB\Server' -Directory -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
    if ($installed) {
        $candidate = Join-Path $installed.FullName 'bin\mongod.exe'
        if (Test-Path $candidate) { $MongodExe = $candidate }
    }
}
if (-not $MongodExe) {
    $candidate = "$env:TEMP\opencode\tools\mongod.exe"
    if (Test-Path $candidate) { $MongodExe = $candidate }
}
if (-not $MongodExe) {
    Write-Err2 'No mongod found. Install MongoDB Community Server, or run: docker run -d -p 27017:27017 mongo:8'
    return
}
Write-Ok "mongod $MongodExe"

$NodeExe = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $NodeExe) {
    Write-Err2 'Node.js is required to seed the database and serve the frontend.'
    return
}
Write-Ok "node   $NodeExe"

New-Item -ItemType Directory -Force -Path $DataDir, $LogDir | Out-Null

# ------------------------------------------------------------ 1. MongoDB

Write-Step "Starting MongoDB on :$MongoPort"
if (Test-Port $MongoPort) {
    Write-Ok 'already running'
} else {
    Start-Process -FilePath $MongodExe `
        -ArgumentList '--dbpath', $DataDir, '--port', $MongoPort, '--bind_ip', '127.0.0.1', `
                      '--logpath', (Join-Path $LogDir 'mongod.log') `
        -WindowStyle Hidden | Out-Null
    if (-not (Wait-Port $MongoPort 'MongoDB' 45)) { return }
    Write-Ok 'started'
}

# --------------------------------------------------------------- 2. Seed

$env:MONGODB_URI = "mongodb://127.0.0.1:$MongoPort"
$env:MONGODB_DB  = 'portfoliopilot'

$mongoDir = Join-Path $Root 'mongodb'
if (-not (Test-Path (Join-Path $mongoDir 'node_modules'))) {
    Write-Step 'Installing database tooling dependencies'
    Push-Location $mongoDir
    & npm install --no-audit --no-fund 2>&1 | Out-Null
    Pop-Location
}

$marker = Join-Path $Root '.devdata\seeded.marker'
if ($Reseed -or -not (Test-Path $marker)) {
    Write-Step 'Seeding the database'
    Push-Location $mongoDir
    if ($Reseed) {
        $env:ALLOW_DESTRUCTIVE = 'true'
        & node scripts/reset.js 2>&1 | Out-Null
    }
    & node seed/seed.js 2>&1 | Select-String -Pattern 'seeded:|users |ALL CHECKS|FAILED' | ForEach-Object { Write-Ok $_.Line.Trim() }
    Pop-Location
    Set-Content -Path $marker -Value (Get-Date -Format o)
} else {
    Write-Ok 'already seeded (use -Reseed to refresh)'
}

# ------------------------------------------------------------ 3. Backend

Write-Step "Starting the API on :$ApiPort"
$jar = Join-Path $Root 'backend\target\portfoliopilot-api-1.0.0.jar'
if (Test-Port $ApiPort) {
    Write-Ok 'already running'
} elseif (-not (Test-Path $jar)) {
    Write-Err2 "Jar not found. Build it first:  cd backend; mvn package -DskipTests"
    return
} else {
    # Both loopback spellings are allowed; the backend adds the twin automatically.
    $env:FRONTEND_URL = "http://localhost:$WebPort"
    $env:JWT_SECRET   = 'local-development-only-secret-change-me-0123456789abcdef'

    Start-Process -FilePath $JavaExe -ArgumentList '-jar', $jar `
        -WorkingDirectory (Join-Path $Root 'backend') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogDir 'api.log') `
        -RedirectStandardError  (Join-Path $LogDir 'api.err') | Out-Null

    if (-not (Wait-Port $ApiPort 'API' 120)) {
        Write-Err2 "Check $LogDir\api.log"
        return
    }
    Write-Ok 'started'
}

# ----------------------------------------------------------- 4. Frontend

Write-Step "Serving the frontend on :$WebPort"
if (Test-Port $WebPort) {
    Write-Ok 'already running'
} else {
    Start-Process -FilePath $NodeExe `
        -ArgumentList (Join-Path $env:APPDATA 'npm\node_modules\serve\build\main.js'), '-l', $WebPort, '--no-clipboard' `
        -WorkingDirectory (Join-Path $Root 'portfoliopilot-ai') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogDir 'web.log') `
        -RedirectStandardError  (Join-Path $LogDir 'web.err') -ErrorAction SilentlyContinue | Out-Null

    if (-not (Test-Port $WebPort)) {
        Start-Process -FilePath 'npx.cmd' -ArgumentList '--yes', 'serve', '-l', $WebPort, '--no-clipboard' `
            -WorkingDirectory (Join-Path $Root 'portfoliopilot-ai') -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $LogDir 'web.log') `
            -RedirectStandardError  (Join-Path $LogDir 'web.err') | Out-Null
    }
    if (-not (Wait-Port $WebPort 'static server' 60)) {
        Write-Warn2 "Serve it yourself:  cd portfoliopilot-ai; npx serve -l $WebPort"
    } else {
        Write-Ok 'started'
    }
}

# -------------------------------------------------------------- summary

Write-Host ''
Write-Host '======================================================================' -ForegroundColor Green
Write-Host ' PortfolioPilot AI is running' -ForegroundColor Green
Write-Host '======================================================================' -ForegroundColor Green
Write-Host ''
Write-Host "  Login page   http://localhost:$WebPort/login.html"
Write-Host "  Admin panel  http://localhost:$WebPort/admin/login.html"
Write-Host "  Swagger UI   http://localhost:$ApiPort/swagger-ui.html"
Write-Host ''
Write-Host '  Password for every account below:  DemoPass123!' -ForegroundColor Yellow
Write-Host ''
Write-Host '    admin@portfoliopilot.local     ADMIN'
Write-Host '    demo@portfoliopilot.local      full demo data'
Write-Host '    aarav@portfoliopilot.local'
Write-Host '    priya@portfoliopilot.local'
Write-Host '    neha@portfoliopilot.local'
Write-Host '    arjun@portfoliopilot.local'
Write-Host ''
Write-Host '  rohan@portfoliopilot.local is SUSPENDED on purpose and will be rejected.'
Write-Host ''
Write-Host "  Logs  $LogDir"
Write-Host '  Stop  .\start-dev.ps1 -Stop'
Write-Host ''
