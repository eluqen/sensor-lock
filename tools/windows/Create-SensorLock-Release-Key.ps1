param()

$ErrorActionPreference = "Stop"

$Alias = "sensorlock-release"
$SigningRoot = Join-Path $env:USERPROFILE "ELUQEN\Signing\SensorLock"
$Keystore = Join-Path $SigningRoot "SensorLock-release.jks"

function Find-Keytool {
    $cmd = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    if (Test-Path $studioJbr) { return $studioJbr }
    throw "keytool.exe was not found. Install Android Studio/JDK first."
}

$keytool = Find-Keytool
New-Item -ItemType Directory -Force -Path $SigningRoot | Out-Null
if (Test-Path $Keystore) {
    throw "Refusing to overwrite the existing release keystore: $Keystore"
}

Write-Host ""
Write-Host "Creating the OWNER-CONTROLLED Sensor Lock release key."
Write-Host "The password is entered directly into keytool and is not stored by this script."
Write-Host ""

& $keytool -genkeypair -v -keystore $Keystore -storetype JKS -alias $Alias -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 36500 -dname "CN=ELUQEN, OU=Software, O=ELUQEN"
if ($LASTEXITCODE -ne 0) { throw "Release-key creation failed." }

Write-Host ""
Write-Host "Release key created:"
Write-Host "  $Keystore"
Write-Host "Alias:"
Write-Host "  $Alias"
Write-Host ""
Write-Host "Certificate details / fingerprints:"
& $keytool -list -v -keystore $Keystore -alias $Alias

Write-Host ""
Write-Host "IMPORTANT: Back up this .jks file and its passwords in at least two secure locations."
Write-Host "Every future Sensor Lock update must be signed with this same key."