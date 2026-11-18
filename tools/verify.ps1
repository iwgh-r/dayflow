param([switch]$PackageWindows)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $projectRoot 'artifacts\verification'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$steps = @(':core:test', ':composeApp:desktopTest', ':composeApp:assembleDebug', ':composeApp:lintDebug')
if ($PackageWindows) { $steps += ':composeApp:createDistributable' }
$results = @()
foreach ($step in $steps) {
    $start = [DateTime]::UtcNow
    $logFile = Join-Path $outputDir ($stamp + '-' + $step.Replace(':', '_') + '.log')
    & (Join-Path $PSScriptRoot 'invoke-build.ps1') $step 2>&1 | Tee-Object -FilePath $logFile
    $code = $LASTEXITCODE
    $results += [ordered]@{ task = $step; startedUtc = $start.ToString('o'); finishedUtc = [DateTime]::UtcNow.ToString('o'); exitCode = $code; log = $logFile }
    $report = [ordered]@{ generatedUtc = [DateTime]::UtcNow.ToString('o'); results = $results; androidRuntime = 'not-run'; referenceComparison = 'pending'; independentReview = 'not-run' }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outputDir 'latest.json') -Encoding utf8
    if ($code -ne 0) { exit $code }
}
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'artifacts\android') | Out-Null
$apk = Join-Path $projectRoot 'composeApp\build\outputs\apk\debug\composeApp-debug.apk'
if (Test-Path -LiteralPath $apk) {
    Copy-Item -LiteralPath $apk -Destination (Join-Path $projectRoot 'artifacts\android\Dayflow-0.1.0-debug.apk') -Force
}
Write-Output 'Build checks passed. Runtime/reference/reviewer checks retain their separate status.'
