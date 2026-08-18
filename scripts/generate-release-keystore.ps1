<#
.SYNOPSIS
    Generates the LavArtemis Android release keystore locally.

.DESCRIPTION
    Run this script ONCE, interactively, on your own machine. It creates
    key/release.keystore (gitignored) and prints the exact values to paste
    into the GitHub repository secrets:

        Settings -> Secrets and variables -> Actions -> New repository secret

        RELEASE_KEYSTORE_BASE64   (printed by this script)
        RELEASE_KEYSTORE_PASSWORD (the store password you just typed)
        RELEASE_KEY_ALIAS         lavartemis
        RELEASE_KEY_PASSWORD      (the key password you just typed)

    SECURITY RULES:
    - NEVER commit key/, *.keystore, *.jks or *.b64 files.
    - NEVER paste the passwords or the base64 blob into issues, PRs, chat,
      or any file in the repo. GitHub secrets only.
    - Keep a backup of key/release.keystore somewhere safe (password manager,
      encrypted drive). If you lose it, you can NEVER update an installed app
      again without uninstalling it first (data loss).

    After the secrets are set, every CI release will be signed with this
    keystore, and updates will install over previous releases normally.

    NOTE: apps already installed with an older certificate (debug key or a
    different keystore) can never be updated by these new builds. One final
    uninstall + reinstall is unavoidable for those installs.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot     = Split-Path -Parent $PSScriptRoot
$keyDir       = Join-Path $repoRoot 'key'
$keystorePath = Join-Path $keyDir 'release.keystore'
$b64Path      = Join-Path $keyDir 'release.keystore.b64'

# --- Sanity checks -----------------------------------------------------------

if (Test-Path $keystorePath) {
    Write-Error @"
A keystore already exists at $keystorePath.
Do NOT regenerate it: any app signed with it could no longer be updated.
If you just need the base64 again, run:
    [Convert]::ToBase64String([IO.File]::ReadAllBytes('$keystorePath'))
"@
    exit 1
}

$keytoolCmd = 'keytool'
$found = Get-Command keytool -ErrorAction SilentlyContinue
if ($found) {
    $keytoolCmd = $found.Source
} else {
    # Not on PATH: probe common JDK locations (JAVA_HOME, Android Studio JBR,
    # Program Files\Java\jdk-*) and pick the highest version found.
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
    $candidates += "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    $candidates += 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
    $candidates += Get-ChildItem 'C:\Program Files\Java\jdk-*\bin\keytool.exe' `
        -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName
    $resolved = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if ($resolved) {
        $keytoolCmd = $resolved
        Write-Host "keytool not on PATH; using: $resolved" -ForegroundColor DarkYellow
    } else {
        Write-Error "keytool not found. Install a JDK (17+) or add its bin folder to PATH."
        exit 1
    }
}

New-Item -ItemType Directory -Force -Path $keyDir | Out-Null

# --- Generate (interactive: passwords are prompted, never in the script) -----

Write-Host ""
Write-Host "keytool will now prompt for the keystore password (twice, to confirm)." -ForegroundColor Cyan
Write-Host "One single password protects both the store and the key inside." -ForegroundColor Cyan
Write-Host "Choose a strong password and store it in your password manager NOW." -ForegroundColor Cyan
Write-Host "You will need it again for the GitHub secrets." -ForegroundColor Cyan
Write-Host ""

& $keytoolCmd -genkeypair -v `
    -keystore $keystorePath `
    -alias lavartemis `
    -dname "CN=Lavagnou, OU=LavArtemis, O=LavArtemis" `
    -keyalg RSA -keysize 4096 -validity 10000

if ($LASTEXITCODE -ne 0) {
    Write-Error "keytool failed (exit $LASTEXITCODE)."
    exit $LASTEXITCODE
}

# keytool asks "Is CN=... correct?" — answer yes, and reuse the store password
# for the key password (it only asks when they would differ... it asks anyway).
# The dname above prevents the interactive prompt loop entirely.

# --- Produce the base64 blob for the GitHub secret ---------------------------

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
Set-Content -Path $b64Path -Value $b64 -NoNewline
Set-Clipboard -Value $b64

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " Keystore created: $keystorePath" -ForegroundColor Green
Write-Host " Base64 copy:      $b64Path" -ForegroundColor Green
Write-Host " (already copied to your clipboard)" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Now create these 4 secrets in GitHub" -ForegroundColor Yellow
Write-Host "(repo -> Settings -> Secrets and variables -> Actions):" -ForegroundColor Yellow
Write-Host ""
Write-Host "  RELEASE_KEYSTORE_BASE64   = <clipboard / content of the .b64 file>"
Write-Host "  RELEASE_KEYSTORE_PASSWORD = <the password you just typed>"
Write-Host "  RELEASE_KEY_ALIAS         = lavartemis"
Write-Host "  RELEASE_KEY_PASSWORD      = <the SAME password again>"
Write-Host ""
Write-Host "(A Java keystore has a store password AND a per-key password;" -ForegroundColor DarkGray
Write-Host " keytool silently reused the same one, so both secrets get it.)" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Then DELETE $b64Path once the secret is saved." -ForegroundColor Yellow
Write-Host ""
Write-Host "Verification (should print a SHA-256 certificate fingerprint):" -ForegroundColor Cyan
Write-Host "  & `"$keytoolCmd`" -list -v -keystore `"$keystorePath`" -alias lavartemis"
Write-Host ""
