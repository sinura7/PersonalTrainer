# Repository-owned Android virtual devices. Never targets a physical phone.
[CmdletBinding()]
param(
    [ValidateSet('Inspect', 'Install', 'Start', 'Stop')][string]$Action = 'Inspect',
    [ValidateSet(26, 29, 36)][int]$Api = 29
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $repoRoot 'build\android-runtime'
$sdkRoot = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
    Where-Object { $_ -and (Test-Path (Join-Path $_ 'emulator\emulator.exe')) } |
    Select-Object -First 1
if (-not $sdkRoot) { throw 'Android SDK with the emulator package was not found.' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$avdName = "temper-tests-api$Api"
$port = switch ($Api) { 29 { 5554 }; 36 { 5556 }; 26 { 5558 } }
$serial = "emulator-$port"
$image = "system-images;android-$Api;default;x86_64"
$imageRevision = switch ($Api) { 29 { '8' }; 36 { '2' }; 26 { '1' } }
$imageProperties = Join-Path $sdkRoot "system-images\android-$Api\default\x86_64\source.properties"
$env:ANDROID_AVD_HOME = Join-Path $runtimeRoot 'avd'
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}

function Assert-OwnedDevice {
    $name = & $adb -s $serial emu avd name 2>$null
    if ($LASTEXITCODE -ne 0 -or $name -notcontains $avdName) {
        throw "Refusing to act on ${serial}: it is not $avdName."
    }
}

function Assert-ImageRevision {
    if (-not (Test-Path -LiteralPath $imageProperties) -or
        -not (Select-String -LiteralPath $imageProperties -Pattern "^Pkg.Revision=$([regex]::Escape($imageRevision))$" -Quiet)) {
        throw "Image revision differs from the approved profile ($image revision $imageRevision). Review before recording baselines."
    }
}

if ($Action -eq 'Inspect') {
    Write-Host "SDK: $sdkRoot"
    Write-Host "Repository AVD directory: $env:ANDROID_AVD_HOME"
    Invoke-Checked $emulator @('-accel-check')
    Invoke-Checked $adb @('devices', '-l')
    Invoke-Checked $emulator @('-list-avds')
    return
}

New-Item -ItemType Directory -Force -Path $runtimeRoot, $env:ANDROID_AVD_HOME | Out-Null
if ($Action -eq 'Stop') {
    Assert-OwnedDevice
    Invoke-Checked $adb @('-s', $serial, 'emu', 'kill')
    return
}

if ($Action -eq 'Install') {
    # Official package and SHA-256 published at https://developer.android.com/studio.
    # Build tools still use JDK 17 through dev-windows.ps1; SDK tooling may use Studio's JBR.
    $toolsVersion = '15859902'
    $toolsSha = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $toolsRoot = Join-Path $sdkRoot "cmdline-tools\temper-$toolsVersion"
    $sdkManager = Join-Path $toolsRoot 'bin\sdkmanager.bat'
    if (-not (Test-Path $sdkManager)) {
        $zip = Join-Path $runtimeRoot "commandlinetools-win-${toolsVersion}_latest.zip"
        if (-not (Test-Path $zip)) {
            Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-${toolsVersion}_latest.zip" -OutFile $zip
        }
        if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $toolsSha) {
            throw "Command-line tools checksum mismatch: $zip"
        }
        $unpackRoot = Join-Path $runtimeRoot "tools\$toolsVersion"
        if (-not (Test-Path (Join-Path $unpackRoot 'cmdline-tools\bin\sdkmanager.bat'))) {
            Expand-Archive -LiteralPath $zip -DestinationPath $unpackRoot
        }
        # avdmanager discovers the SDK relative to its installed tools directory.
        # Keep our pinned tools in their own folder; never replace Studio's version.
        New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
        Get-ChildItem -LiteralPath (Join-Path $unpackRoot 'cmdline-tools') |
            Copy-Item -Destination $toolsRoot -Recurse -Force
    }
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJava 'bin\java.exe')) { $env:JAVA_HOME = $studioJava }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK supported by Android command-line tools.' }
    if (-not (Test-Path -LiteralPath $imageProperties)) {
        Invoke-Checked $sdkManager @("--sdk_root=$sdkRoot", '--install', $image)
    }
    Assert-ImageRevision
    $avdManager = Join-Path $toolsRoot 'bin\avdmanager.bat'
    if (-not (Test-Path (Join-Path $env:ANDROID_AVD_HOME "$avdName.ini"))) {
        'no' | & $avdManager create avd --name $avdName --package $image --device 'Nexus 5X'
        if ($LASTEXITCODE -ne 0) { throw 'Creating the repository AVD failed.' }
    }
    $config = Join-Path $env:ANDROID_AVD_HOME "$avdName.avd\config.ini"
    $settings = [ordered]@{
        'hw.lcd.width' = '1080'; 'hw.lcd.height' = '1920'; 'hw.lcd.density' = '420'
        'hw.ramSize' = '2048'; 'hw.cpu.ncore' = '4'; 'disk.dataPartition.size' = '4G'
        'hw.keyboard' = 'yes'; 'hw.gpu.enabled' = 'yes'; 'hw.gpu.mode' = 'swiftshader_indirect'
        'showDeviceFrame' = 'no'; 'fastboot.forceColdBoot' = 'yes'
    }
    $lines = @(Get-Content -LiteralPath $config)
    foreach ($key in $settings.Keys) {
        $pattern = '^' + [regex]::Escape($key) + '\s*='
        $lines = @($lines | Where-Object { $_ -notmatch $pattern }) + "$key=$($settings[$key])"
    }
    [IO.File]::WriteAllLines($config, $lines, [Text.UTF8Encoding]::new($false))
    Write-Host "Installed $avdName ($image). Start it with -Action Start -Api $Api."
    return
}

if (-not (Test-Path (Join-Path $env:ANDROID_AVD_HOME "$avdName.ini"))) {
    throw "Run -Action Install -Api $Api first."
}
Assert-ImageRevision
$avdIni = Join-Path $env:ANDROID_AVD_HOME "$avdName.ini"
$expectedAvd = [IO.Path]::GetFullPath((Join-Path $env:ANDROID_AVD_HOME "$avdName.avd"))
$avdPathLine = Get-Content -LiteralPath $avdIni | Where-Object { $_ -match '^path=' } | Select-Object -First 1
if (-not $avdPathLine -or [IO.Path]::GetFullPath($avdPathLine.Substring(5)) -ne $expectedAvd) {
    throw 'The AVD points outside its repository-owned directory. Reinstall the profile.'
}
$imageLine = Get-Content -LiteralPath (Join-Path $expectedAvd 'config.ini') |
    Where-Object { $_ -match '^image\.sysdir\.1\s*=' } | Select-Object -First 1
$expectedImagePath = "system-images/android-$Api/default/x86_64/"
if (-not $imageLine -or $imageLine.Split('=', 2)[1].Trim().Replace('\', '/') -ne $expectedImagePath) {
    throw "AVD system-image mapping differs from $image. Reinstall the profile."
}
$emulatorVersion = ((& $emulator -version 2>$null | Select-Object -First 1) -join '').Trim()
if ($emulatorVersion -notmatch '^Android emulator version 37\.1\.11\.0\b') {
    throw "Expected the approved Emulator 37.1.11.0 renderer, found: $emulatorVersion. Review the profile before starting."
}
$devices = & $adb devices
if ($devices -match "^$serial\s") {
    Assert-OwnedDevice
    # Cold boot our dedicated test device to establish the actual renderer and
    # image; an existing Studio launch could have used another GPU or old binary.
    Invoke-Checked $adb @('-s', $serial, 'emu', 'kill')
    $shutdownDeadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 500
        $stillConnected = (& $adb devices) -match "^$serial\s"
    } while ($stillConnected -and [DateTime]::UtcNow -lt $shutdownDeadline)
    if ($stillConnected) { throw 'The dedicated emulator did not shut down.' }
}
& {
    $process = Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $avdName, '-port', "$port", '-no-window', '-no-audio', '-no-boot-anim',
        '-no-snapshot', '-gpu', 'swiftshader_indirect', '-accel', 'on',
        '-timezone', 'America/Toronto'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeRoot "$avdName.stdout.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "$avdName.stderr.log")
    $deadline = [DateTime]::UtcNow.AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        if ($process.HasExited) { throw "Emulator exited. Inspect build/android-runtime/$avdName.stderr.log." }
        $booted = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    } until (($booted -join '').Trim() -eq '1' -or [DateTime]::UtcNow -gt $deadline)
    if (($booted -join '').Trim() -ne '1') { throw 'Emulator did not boot within four minutes.' }
}
Assert-OwnedDevice
$guestApi = ((& $adb -s $serial shell getprop ro.build.version.sdk) -join '').Trim()
if ($guestApi -ne "$Api") { throw "Expected API $Api guest, found $guestApi." }
Invoke-Checked $adb @('-s', $serial, 'shell', 'wm', 'size', 'reset')
Invoke-Checked $adb @('-s', $serial, 'shell', 'wm', 'density', 'reset')
$zoneDeadline = [DateTime]::UtcNow.AddSeconds(20)
do {
    $timeZone = ((& $adb -s $serial shell getprop persist.sys.timezone) -join '').Trim()
    if ($timeZone -ne 'America/Toronto') { Start-Sleep -Milliseconds 500 }
} while ($timeZone -ne 'America/Toronto' -and [DateTime]::UtcNow -lt $zoneDeadline)
if ($timeZone -ne 'America/Toronto') { throw "Expected America/Toronto timezone, found '$timeZone'." }
$locale = ((& $adb -s $serial shell getprop persist.sys.locale) -join '').Trim()
if (-not $locale) { $locale = ((& $adb -s $serial shell getprop ro.product.locale) -join '').Trim() }
if ($locale -ne 'en-US') { throw "Expected en-US locale, found '$locale'. Restore this emulator's language in Settings before capturing." }
$size = ((& $adb -s $serial shell wm size) -join '').Trim()
$density = ((& $adb -s $serial shell wm density) -join '').Trim()
if ($size -ne 'Physical size: 1080x1920' -or $density -ne 'Physical density: 420') {
    throw "AVD display configuration has drifted: $size; $density. Reinstall the repository profile."
}
foreach ($setting in @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')) {
    Invoke-Checked $adb @('-s', $serial, 'shell', 'settings', 'put', 'global', $setting, '0')
}
Invoke-Checked $adb @('-s', $serial, 'shell', 'settings', 'put', 'system', 'font_scale', '1.0')
Invoke-Checked $adb @('-s', $serial, 'shell', 'input', 'keyevent', '82')
$manifest = [ordered]@{
    profile = $avdName; serial = $serial; systemImage = $image; imageRevision = $imageRevision
    fingerprint = ((& $adb -s $serial shell getprop ro.build.fingerprint) -join '').Trim()
    density = $density
    size = $size
    emulator = $emulatorVersion
    renderer = 'swiftshader_indirect'; fontScale = 1.0; animations = 0
    locale = $locale
    timeZone = $timeZone
    clockPolicy = 'Real civil clock for observations; legacy floor timers remain live (controlled-clock replacement in F3)'
    goldenProfile = 'windows-swiftshader37'
    capturedUtc = [DateTime]::UtcNow.ToString('o')
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot "$avdName.json") -Encoding utf8
Write-Host "Ready: $serial. Manifest: build/android-runtime/$avdName.json"
