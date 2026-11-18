param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tasks)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($projectRoot))).Substring(0, 12).ToLowerInvariant()
$buildPath = Join-Path ([IO.Path]::GetTempPath()) "dayflow-source-$digest"
if (-not (Test-Path -LiteralPath $buildPath)) {
    New-Item -ItemType Junction -Path $buildPath -Target $projectRoot | Out-Null
}
$junction = Get-Item -LiteralPath $buildPath
if ($junction.LinkType -ne 'Junction' -or $junction.Target -ne $projectRoot) {
    throw 'Build link points to an unexpected directory.'
}
$env:JAVA_HOME = Join-Path $buildPath '.tools\jdk'
$env:ANDROID_HOME = Join-Path $buildPath '.tools\android-sdk'
$env:ANDROID_USER_HOME = Join-Path $buildPath '.tools\android-user'
$env:GRADLE_USER_HOME = Join-Path ([IO.Path]::GetTempPath()) 'dayflow-gradle'
if (Test-Path -LiteralPath $env:ANDROID_HOME) {
    $sdkPath = $env:ANDROID_HOME.Replace('\', '/')
    [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkPath`n", [Text.UTF8Encoding]::new($false))
}
$portable = Join-Path $buildPath '.tools\gradle-8.11.1\bin\gradle.bat'
if (Test-Path -LiteralPath $portable) {
    & $portable --project-dir $buildPath @Tasks --console plain
} else {
    & (Join-Path $buildPath 'gradlew.bat') --project-dir $buildPath @Tasks --console plain
}
exit $LASTEXITCODE
