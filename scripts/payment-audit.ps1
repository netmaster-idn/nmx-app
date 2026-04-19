$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$postgresJar = Join-Path $env:USERPROFILE '.m2\repository\org\postgresql\postgresql\42.7.10\postgresql-42.7.10.jar'
$sourceFile = Join-Path $PSScriptRoot 'PaymentAudit.java'
$today = Get-Date -Format 'yyyy-MM-dd'

if (-not (Test-Path $postgresJar)) {
    throw "PostgreSQL JDBC driver tidak ditemukan di: $postgresJar"
}

if ($args.Count -gt 0 -and $args[0]) {
    $outputPath = $args[0]
} else {
    $outputPath = Join-Path $projectRoot "docs\audits\payment-audit-$today.md"
}

Write-Host "Generating payment audit report..."
Write-Host "Output: $outputPath"

Push-Location $projectRoot
try {
    java --class-path $postgresJar $sourceFile $outputPath
} finally {
    Pop-Location
}
