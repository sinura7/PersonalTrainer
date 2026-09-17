# Run from PowerShell: .\tools\dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug
# With no arguments, checks the toolchain and prints the Gradle version.
# Environment changes apply only to this process; generated helpers live under build/.
[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$javaCandidates = @($env:JAVA_HOME)
$jdkRoot = Join-Path $env:USERPROFILE '.jdks'
if (Test-Path $jdkRoot) {
    $javaCandidates += @(Get-ChildItem $jdkRoot -Directory | Sort-Object Name -Descending | ForEach-Object FullName)
}
$selectedJava = $null
foreach ($candidate in $javaCandidates) {
    if (-not $candidate) { continue }
    $releaseFile = Join-Path $candidate 'release'
    if ((Test-Path $releaseFile) -and
        ((Get-Content $releaseFile -Raw) -match 'JAVA_VERSION="17[.\"]')) {
        $selectedJava = $candidate
        break
    }
}
if (-not $selectedJava) { throw 'JDK 17 was not found. Set JAVA_HOME to an installed JDK 17.' }

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
$selectedSdk = $sdkCandidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ 'platforms\android-36\android.jar'))
} | Select-Object -First 1
if (-not $selectedSdk) { throw 'Android SDK platform 36 was not found or is inaccessible.' }

$gitRoot = Join-Path $env:ProgramFiles 'Git'
$shellBin = Join-Path $gitRoot 'usr\bin'
if (-not (Test-Path (Join-Path $shellBin 'sh.exe'))) { throw 'Git for Windows shell tools were not found.' }
$pythonExe = & py -3 -c 'import sys; assert sys.version_info >= (3, 11); print(sys.executable)'
if ($LASTEXITCODE -ne 0 -or -not $pythonExe) { throw 'Python 3.11 or newer is required via the py launcher.' }
$pythonExe = ($pythonExe | Select-Object -Last 1).Trim()

$helperBin = Join-Path $repoRoot 'build\windows-env\bin'
New-Item -ItemType Directory -Force -Path $helperBin | Out-Null
$pythonShellPath = $pythonExe.Replace('\', '/')
if ($pythonShellPath.Contains("'")) { throw 'Python paths containing apostrophes are not supported.' }
$shim = "#!/bin/sh`nexec '$pythonShellPath' " + '"$@"' + "`n"
[IO.File]::WriteAllText((Join-Path $helperBin 'python3'), $shim, [Text.UTF8Encoding]::new($false))

$env:JAVA_HOME = $selectedJava
$env:ANDROID_HOME = $selectedSdk
$env:ANDROID_SDK_ROOT = $selectedSdk
$env:Path = "$helperBin;$selectedJava\bin;$shellBin;$gitRoot\bin;$selectedSdk\platform-tools;$env:Path"
Write-Host "Java: $selectedJava"
Write-Host "Android SDK: $selectedSdk"
Write-Host "Python: $pythonExe"
if (-not $GradleArgs) { $GradleArgs = @('--version') }
$serial = if ($env:ANDROID_SERIAL) { $env:ANDROID_SERIAL } else { 'emulator-5554' }
$owned = @{ 'emulator-5554' = 'temper-tests-api29'; 'emulator-5556' = 'temper-tests-api36'; 'emulator-5558' = 'temper-tests-api26' }
if (-not $owned.ContainsKey($serial)) { throw "This test/build wrapper only targets repository emulators; refusing $serial." }
$env:ANDROID_SERIAL = $serial
# The wrapper deliberately accepts full task names. Gradle's task abbreviation
# resolution must not bypass connected-test preconditions.
$supportedTasks = @(
    'help', 'tasks', 'properties', 'dependencies', 'buildEnvironment', 'clean',
    'testDebugUnitTest', 'testReleaseUnitTest', 'assembleDebug', 'assembleRelease',
    'lintDebug', 'lintRelease', 'assembleDebugAndroidTest', 'compileDebugAndroidTestKotlin',
    'compileDebugKotlin', 'compileReleaseKotlin', 'staticChecks', 'jacocoTestReport',
    'connectedDebugAndroidTest', 'connectedAndroidTest', 'connectedCheck'
)
foreach ($argument in $GradleArgs) {
    if (-not $argument.StartsWith('-') -and ($argument -split ':')[-1] -notin $supportedTasks) {
        throw "Use a supported full task name, not '$argument'. Extend the wrapper's explicit task list for a new workflow."
    }
}
if ($GradleArgs -match '(^|:)connected') {
    $runningName = & (Join-Path $selectedSdk 'platform-tools\adb.exe') -s $serial emu avd name 2>$null
    if ($LASTEXITCODE -ne 0 -or $runningName -notcontains $owned[$serial]) {
        throw "Start the repository AVD $($owned[$serial]) before running connected tests."
    }
    $env:ANDROID_SERIAL = $serial
    Write-Host "Connected test target: $serial ($($owned[$serial]))"
    $api = switch ($serial) { 'emulator-5554' { 29 }; 'emulator-5556' { 36 }; 'emulator-5558' { 26 } }
    & (Join-Path $PSScriptRoot 'android-emulator.ps1') -Action Start -Api $api
    $artifactRunId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff')
    $GradleArgs += "-Pandroid.testInstrumentationRunnerArguments.artifactRunId=$artifactRunId"
    $runRoot = Join-Path $repoRoot 'build\android-runtime\runs'
    New-Item -ItemType Directory -Force -Path $runRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot "build\android-runtime\temper-tests-api$api.json") -Destination (Join-Path $runRoot "$artifactRunId.json")
    Write-Host "Native artifact run: $artifactRunId"
    if (-not ($GradleArgs -match 'android.testInstrumentationRunnerArguments.goldenProfile=')) {
        $GradleArgs += '-Pandroid.testInstrumentationRunnerArguments.goldenProfile=windows-swiftshader37'
    }
}
Push-Location $repoRoot
try {
    & .\gradlew.bat @GradleArgs
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($gradleExit -ne 0) { throw "Gradle failed with exit code $gradleExit." }
