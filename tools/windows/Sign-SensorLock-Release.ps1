param(
    [Parameter(Mandatory = $true)]
    [string]$UnsignedApk,
    [string]$Keystore = (Join-Path $env:USERPROFILE "ELUQEN\Signing\SensorLock\SensorLock-release.jks")
)

$ErrorActionPreference = "Stop"
$Alias = "sensorlock-release"

function Find-AndroidSdk {
    $candidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA "Android\Sdk")) | Where-Object { $_ -and (Test-Path $_) }
    if (-not $candidates) { throw "Android SDK was not found. Install Android Studio/SDK first." }
    return $candidates[0]
}

$UnsignedApk = (Resolve-Path $UnsignedApk).Path
$Keystore = (Resolve-Path $Keystore).Path
$Sdk = Find-AndroidSdk
$BuildToolsRoot = Join-Path $Sdk "build-tools"
$BuildTools = Get-ChildItem $BuildToolsRoot -Directory | Where-Object { $_.Name -match "^\d+(\.\d+){2}$" } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $BuildTools) { throw "No Android SDK Build Tools installation was found." }

$Zipalign = Join-Path $BuildTools.FullName "zipalign.exe"
$Apksigner = Join-Path $BuildTools.FullName "apksigner.bat"
if (-not (Test-Path $Zipalign)) { throw "zipalign.exe was not found." }
if (-not (Test-Path $Apksigner)) { throw "apksigner.bat was not found." }

$OutDir = Split-Path $UnsignedApk -Parent
$Aligned = Join-Path $OutDir "SensorLock-1.0.0-aligned.apk"
$Signed = Join-Path $OutDir "SensorLock-1.0.0-release.apk"
Remove-Item $Aligned, $Signed -Force -ErrorAction SilentlyContinue

Write-Host "Aligning release APK..."
& $Zipalign -p -f 4 $UnsignedApk $Aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed." }

Write-Host ""
Write-Host "Signing with the OWNER-CONTROLLED release key..."
Write-Host "apksigner will ask for the keystore/key password; this script does not store it."
& $Apksigner sign --ks $Keystore --ks-key-alias $Alias --out $Signed $Aligned
if ($LASTEXITCODE -ne 0) { throw "APK signing failed." }

Write-Host ""
Write-Host "Verifying signature..."
& $Apksigner verify --verbose --print-certs $Signed
if ($LASTEXITCODE -ne 0) { throw "Signature verification failed." }

$hash = Get-FileHash -Algorithm SHA256 $Signed
Write-Host ""
Write-Host "FINAL SIGNED APK:"
Write-Host "  $Signed"
Write-Host "SHA-256:"
Write-Host "  $($hash.Hash)"
Remove-Item $Aligned -Force -ErrorAction SilentlyContinue