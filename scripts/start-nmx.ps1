param(
    [switch]$Build,
    [switch]$Background,
    [string]$Profile = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$targetDir = Join-Path $repoRoot "target"
$logsDir = Join-Path $repoRoot "logs"

function Get-AppJar {
    Get-ChildItem -Path $targetDir -Filter "nmx-*.jar" -File |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if ($Build -or -not (Test-Path $targetDir) -or -not (Get-AppJar)) {
    Write-Host "Building NMX JAR..."
    & $mavenWrapper clean package -DskipTests
}

$jar = Get-AppJar
if (-not $jar) {
    throw "JAR file not found in target/. Run with -Build or check the Maven build."
}

$javaArgs = @()
if ($Profile) {
    $javaArgs += "-Dspring.profiles.active=$Profile"
}
$javaArgs += "-jar"
$javaArgs += $jar.FullName

if ($Background) {
    if (-not (Test-Path $logsDir)) {
        New-Item -ItemType Directory -Path $logsDir | Out-Null
    }

    $stdoutLog = Join-Path $logsDir "nmx-stdout.log"
    $stderrLog = Join-Path $logsDir "nmx-stderr.log"

    $process = Start-Process -FilePath "java" `
        -ArgumentList $javaArgs `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    Write-Host "NMX started in background. PID: $($process.Id)"
    Write-Host "Logs: $stdoutLog and $stderrLog"
    exit 0
}

Write-Host "Starting NMX from $($jar.Name)..."
& java @javaArgs
