$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

# aapt2 fails on Windows if the path contains non-ASCII characters (e.g. Chinese directory names).
# If non-ASCII characters are present, temporarily mount a virtual drive letter via subst.
if ($root -match "[^\x00-\x7F]" -and -not $env:DAFEIYU_SUBST_WRAPPED) {
    $parentRepo = Split-Path -Parent (Split-Path -Parent $root)
    $availLetters = [char[]]([int][char]'G'..[int][char]'Z') | Where-Object { -not (Test-Path "$($_):") }
    if ($availLetters.Count -gt 0) {
        $driveLetter = "$($availLetters[-1]):"
        & subst $driveLetter $parentRepo | Out-Null
        try {
            $env:DAFEIYU_SUBST_WRAPPED = "1"
            & powershell -ExecutionPolicy Bypass -File "$driveLetter\app\DafeiyuEye\build.ps1"
            return
        } finally {
            $env:DAFEIYU_SUBST_WRAPPED = $null
            & subst $driveLetter /D 2>$null | Out-Null
        }
    }
}
$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$bt = Join-Path $sdk "build-tools\34.0.0"
$androidJar = Join-Path $sdk "platforms\android-34\android.jar"
$aapt2 = Join-Path $bt "aapt2.exe"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$d8 = Join-Path $bt "d8.bat"
$jdk = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
    $env:JAVA_HOME.TrimEnd('\')
} else {
    Join-Path $env:ProgramFiles "Java\jdk-17"
}
$javac = Join-Path $jdk "bin\javac.exe"
$jarExe = Join-Path $jdk "bin\jar.exe"
$keytool = Join-Path $jdk "bin\keytool.exe"
$env:JAVA_HOME = $jdk
$env:Path = "$(Join-Path $jdk 'bin');$env:Path"
$build = Join-Path $root "build"
$dist = Join-Path $root "dist"
$sherpaJar = Join-Path $root "libs\sherpa-onnx-classes.jar"
$kotlinJar = Join-Path $root "libs\kotlin-stdlib.jar"

if (-not (Test-Path $aapt2)) { throw "aapt2 not found: $aapt2" }
if (-not (Test-Path $androidJar)) { throw "android.jar not found: $androidJar" }

if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $build, (Join-Path $build "classes"), (Join-Path $build "dex"), (Join-Path $build "gen"), $dist | Out-Null

Write-Host "[1/7] compile resources"
& $aapt2 compile --dir (Join-Path $root "res") -o (Join-Path $build "res.zip")

Write-Host "[2/7] link resources + assets"
& $aapt2 link `
    -o (Join-Path $build "app-unsigned.apk") `
    -I $androidJar `
    --manifest (Join-Path $root "AndroidManifest.xml") `
    -R (Join-Path $build "res.zip") `
    -A (Join-Path $root "assets") `
    -0 onnx `
    -0 txt `
    --auto-add-overlay `
    --min-sdk-version 23 `
    --target-sdk-version 34 `
    --java (Join-Path $build "gen")

Write-Host "[3/7] compile java"
$sources = @(Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += @(Get-ChildItem (Join-Path $build "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$androidJar;$bt\core-lambda-stubs.jar" -classpath "$sherpaJar;$kotlinJar" -d (Join-Path $build "classes") $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] d8"
& $jarExe cf (Join-Path $build "classes.jar") -C (Join-Path $build "classes") .
& $d8 --lib $androidJar --min-api 23 --output (Join-Path $build "dex") (Join-Path $build "classes.jar") $sherpaJar $kotlinJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] add classes.dex"
& $jarExe uf (Join-Path $build "app-unsigned.apk") -C (Join-Path $build "dex") classes.dex

Write-Host "[5b/7] add native ASR libs"
$libStage = Join-Path $build "nativelib"
foreach ($abi in @("arm64-v8a", "armeabi-v7a")) {
    $abiSrc = Join-Path $root "libs\$abi"
    if (-not (Test-Path $abiSrc)) { continue }
    New-Item -ItemType Directory -Force -Path (Join-Path $libStage "lib\$abi") | Out-Null
    Copy-Item (Join-Path $abiSrc "*.so") (Join-Path $libStage "lib\$abi") -Force
}
& $jarExe uf (Join-Path $build "app-unsigned.apk") -C $libStage lib

Write-Host "[6/7] zipalign"
& $zipalign -f -p 4 (Join-Path $build "app-unsigned.apk") (Join-Path $build "app-aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[7/7] sign"
$ks = Join-Path $root "debug.keystore"
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -alias androiddebugkey -storepass android -keypass android `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" | Out-Null
}
$out = Join-Path $dist "DafeiyuEye.apk"
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $out (Join-Path $build "app-aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
& $apksigner verify --verbose $out

Get-Item $out | Select-Object FullName, Length, LastWriteTime

