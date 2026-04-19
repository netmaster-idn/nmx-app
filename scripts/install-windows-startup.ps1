param(
    [string]$TaskName = "NMX Auto Start",
    [switch]$Build
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$startScript = Join-Path $PSScriptRoot "start-nmx.ps1"
$arguments = "-ExecutionPolicy Bypass -File `"$startScript`" -Background"

if ($Build) {
    $arguments += " -Build"
}

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments -WorkingDirectory $repoRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description "Auto start NMX application at Windows logon" -Force | Out-Null

Write-Host "Scheduled task created: $TaskName"
Write-Host "NMX will start automatically at Windows logon."
