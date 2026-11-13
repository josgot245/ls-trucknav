$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\dev\jdk21'
$sdk = 'C:\dev\android-sdk'
$sdkmgr = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"

# Lizenzen akzeptieren (mehrfach y fuer alle Teil-Lizenzen)
$yes = ("y`r`n" * 40)
$yes | & $sdkmgr --sdk_root=$sdk --licenses | Out-Null

& $sdkmgr --sdk_root=$sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager fehlgeschlagen: $LASTEXITCODE" }
'SDK_DONE' | Out-File C:\dev\sdk-done.txt -Encoding ascii
