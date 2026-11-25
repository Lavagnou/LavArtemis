<#
.SYNOPSIS
    Builds a LavArtemis release on this PC and publishes it on GitHub, without GitHub Actions.

.DESCRIPTION
    The release workflow (.github/workflows/release.yml) is manual-only since 2026-11-23: the
    Actions quota is short. This script does what its Android and Windows jobs did:

      1. Android: assembleNonRoot_gameRelease, signed with key/release.keystore (password in
         key/keystore.properties, read by app/build.gradle, never printed here);
      2. Windows: desktop/ built from the standalone LavArtemis-Qt clone (it must sit on the
         commit LavArtemis pins), x64 only (no Qt arm64 kit on this PC): portable zip + installer;
      3. tag vX.Y.Z (from lavVersionName in app/build.gradle), push, gh release create with the
         files. A "-dev." version is a prerelease (LavArtemis Dev).

    No Linux AppImage: a stable release that needs it can still run the workflow by hand.

.PARAMETER NotesFile
    Markdown file with the release notes (French).

.PARAMETER SkipAndroid / SkipWindows
    Reuse what an earlier run built.

.PARAMETER SkipTests
    Skip the desktop testbench that normally runs before any build (a visible warning is printed). The
    gate (python tools\testbench\testbench.py run --suite desktop --rerun-failed-once) stops the release on
    exit 1 (a scenario fails), 3 (FLAKY: one passed only on its rerun), 4 (display off: it needs the display
    on, the script does not turn it on) or any other code (runner error). Report: tools\testbench\testbench-reports\.

.PARAMETER NoPublish
    Build and collect the files in dist\, but do not tag or publish.
#>
param(
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [switch]$SkipAndroid,
    [switch]$SkipWindows,
    [switch]$SkipTests,
    [switch]$NoPublish
)
$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$qt = Join-Path (Split-Path -Parent $repo) "LavArtemis-Qt"
Set-Location $repo

$version = (Select-String -Path app\build.gradle -Pattern 'def lavVersionName = "([^"]+)"').Matches[0].Groups[1].Value
$tag = "v$version"
$dev = $version -like "*-dev.*"
$dist = Join-Path $repo "dist\$tag"
Write-Host "LavArtemis $tag ($(if ($dev) { 'Dev, prerelease' } else { 'stable' }))" -ForegroundColor Cyan
New-Item -ItemType Directory -Force $dist | Out-Null

# The Qt clone must hold exactly the commit LavArtemis pins in desktop/
$pinned = (git ls-tree HEAD desktop).Split()[2]
$qtHead = (git -C $qt rev-parse HEAD).Trim()
if ($pinned -ne $qtHead) {
    throw "LavArtemis-Qt is on $qtHead but desktop/ pins ${pinned}: check out the pinned commit first."
}
if (git status --porcelain --untracked-files=no) { throw "LavArtemis has uncommitted changes." }

# --- Tests: the desktop testbench, before anything is built -------------------------------------
# It builds the test client from the pinned LavArtemis-Qt clone (checked above) if it is stale.
# LAV_TESTBENCH_SELFTEST swaps the real scenarios for a built-in one: it may verify the gate, never ship a release.
if ($env:LAV_TESTBENCH_SELFTEST) {
    if (-not $NoPublish) {
        throw "LAV_TESTBENCH_SELFTEST est defini : une vraie release exige le vrai banc (Remove-Item Env:LAV_TESTBENCH_SELFTEST)."
    }
    Write-Host "WARNING: LAV_TESTBENCH_SELFTEST=$($env:LAV_TESTBENCH_SELFTEST): the built-in selftest replaces the real scenarios (-NoPublish only)." -ForegroundColor Yellow
}
if ($SkipTests) {
    Write-Host "WARNING: -SkipTests: the desktop testbench did NOT run for this release." -ForegroundColor Yellow
} else {
    Write-Host "Testbench: python tools\testbench\testbench.py run --suite desktop --rerun-failed-once" -ForegroundColor Cyan
    $reports = Join-Path $repo "tools\testbench\testbench-reports"
    $prevEncoding = $null
    try {
        $prevEncoding = [Console]::OutputEncoding
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    } catch { $prevEncoding = $null }
    $env:PYTHONIOENCODING = "utf-8"
    $null = Get-Command python -CommandType Application  # missing python: stop here (still under "Stop")
    $prevEap = $ErrorActionPreference
    try {
        # python writes to stderr (pytest, warnings). Windows PowerShell 5.1 turns a native command's stderr into
        # error records when this script's output is redirected (a log file, a caller capturing it), and under
        # "Stop" the first such line would end the script with a NativeCommandError instead of the gate's verdict.
        # The exit code alone decides; -1 stays if python never ran.
        $ErrorActionPreference = "Continue"
        $global:LASTEXITCODE = -1
        python tools\testbench\testbench.py run --suite desktop --rerun-failed-once
        $testCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEap
        if ($prevEncoding) { try { [Console]::OutputEncoding = $prevEncoding } catch { } }
        Remove-Item Env:PYTHONIOENCODING -ErrorAction SilentlyContinue
    }
    if ($testCode -ne 0) {
        $why = switch ($testCode) {
            1 { "a scenario fails" }
            3 { "FLAKY: a scenario passed only on its rerun" }
            4 { "display off: the testbench needs the display on" }
            default { "runner error (exit code $testCode)" }
        }
        $newest = Get-ChildItem $reports -Directory -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $where = if ($newest) { $newest.FullName } else { $reports }
        throw "Testbench stopped the release (exit $testCode, $why). Report: $where. -SkipTests skips the gate."
    }
    Write-Host "Testbench: all scenarios passed." -ForegroundColor Green
}

# --- Android ---------------------------------------------------------------------------------
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
if (-not $SkipAndroid) {
    .\gradlew.bat assembleNonRoot_gameRelease
    if ($LASTEXITCODE -ne 0) { throw "Android build failed" }
    .\gradlew.bat --stop | Out-Null
}
$apkDir = "app\build\outputs\apk\nonRoot_game\release"
foreach ($abi in "arm64-v8a", "armeabi-v7a") {
    $apk = Get-ChildItem $apkDir -Filter "*$abi*.apk" | Select-Object -First 1
    if (-not $apk) { throw "No $abi APK in $apkDir" }
    Copy-Item $apk.FullName (Join-Path $dist "LavArtemis-$tag-android-$abi.apk") -Force
}
# Same certificate as the installed apps, or the update would be refused on the devices
$cert = & "$env:JAVA_HOME\bin\keytool.exe" -printcert -jarfile (Join-Path $dist "LavArtemis-$tag-android-arm64-v8a.apk") 2>&1 | Out-String
if ($cert -notmatch "CN=Lavagnou") { throw "The APK is not signed with the LavArtemis release key" }

# --- Windows ---------------------------------------------------------------------------------
if (-not $SkipWindows) {
    $qtBin = "C:\Qt\6.11.1\msvc2022_64\bin"
    $versionTxt = Join-Path $qt "app\version.txt"
    $oldVersion = Get-Content $versionTxt -Raw
    Set-Content -Path $versionTxt -Value $version -NoNewline
    try {
        $env:PATH = "$qtBin;C:\Program Files\7-Zip;$env:PATH"
        $env:CI_VERSION = $version
        $env:LAV_X64_ONLY = "1"
        Push-Location $qt
        cmd /c "scripts\build-arch.bat Release x64"
        if ($LASTEXITCODE -ne 0) { throw "Windows x64 build failed" }
        cmd /c "scripts\generate-bundle.bat Release"
        if ($LASTEXITCODE -ne 0) { throw "Installer build failed" }
        Pop-Location
    } finally {
        Set-Content -Path $versionTxt -Value $oldVersion -NoNewline
        Remove-Item Env:CI_VERSION, Env:LAV_X64_ONLY -ErrorAction SilentlyContinue
    }
}
$zip = Join-Path $dist "LavArtemis-$tag-win-x64.zip"
Remove-Item $zip -ErrorAction SilentlyContinue
& "C:\Program Files\7-Zip\7z.exe" a -tzip $zip "$qt\build\deploy-x64-release\*" | Out-Null
$setup = Get-ChildItem "$qt\build" -Recurse -Filter "LavArtemisSetup-$version.exe" | Select-Object -First 1
if (-not $setup) { throw "No installer LavArtemisSetup-$version.exe under $qt\build" }
Copy-Item $setup.FullName (Join-Path $dist "LavArtemis-$tag-win-installer.exe") -Force

Get-ChildItem $dist | Format-Table Name, Length -AutoSize
if ($NoPublish) { return }

# --- Publish ---------------------------------------------------------------------------------
git tag $tag
git push origin HEAD
git push origin $tag
$title = if ($dev) { "LavArtemis Dev $version" } else { "LavArtemis $version" }
$flags = @("--title", $title, "--notes-file", $NotesFile, "--verify-tag")
if ($dev) { $flags += "--prerelease" }
gh release create $tag @flags (Get-ChildItem $dist).FullName
if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }
Write-Host "Published $tag" -ForegroundColor Green
