param(
    [switch]$Debug
)

$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$env:JAVA_HOME = 'C:\dev\jdk21'
$gradle = 'C:\dev\gradle-8.9\bin\gradle.bat'

if (-not (Test-Path $gradle)) {
    throw "Gradle nicht gefunden: $gradle"
}
if (-not (Test-Path $env:JAVA_HOME)) {
    throw "JDK nicht gefunden: $env:JAVA_HOME"
}

$task = if ($Debug) { ':app:assembleDebug' } else { ':app:assembleRelease' }
Write-Host "Baue LS TruckNav ($task) ..."
& $gradle -p $project $task

if ($LASTEXITCODE -ne 0) {
    throw "Build fehlgeschlagen (Exitcode $LASTEXITCODE)."
}

$variant = if ($Debug) { 'debug' } else { 'release' }
$apk = Join-Path $project "app\build\outputs\apk\$variant\app-$variant.apk"
if (-not (Test-Path $apk)) {
    throw "APK nicht gefunden: $apk"
}

$target = Join-Path $project 'LS-TruckNav-0.4.0.apk'
Copy-Item $apk $target -Force

Write-Host ""
Write-Host "Fertig: $target"
Write-Host "Installieren: adb install -r `"$target`""
