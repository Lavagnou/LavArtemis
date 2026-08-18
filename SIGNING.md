# Android release signing

> Why this matters: Android only allows an update to install over an existing
> app when **both APKs are signed with the same certificate**. If the
> certificate changes, the install fails with *« Le package est en conflit
> avec un package déjà présent »* and the only way forward is uninstall
> (data loss) + reinstall.

## One-time setup

Run, interactively, on your own machine:

```powershell
.\scripts\generate-release-keystore.ps1
```

The script:

1. Generates `key/release.keystore` (RSA 4096, alias `lavartemis`, 10 000 days
   validity). The `key/` folder is gitignored — it will never be committed.
2. Copies the base64-encoded keystore to your clipboard and to
   `key/release.keystore.b64`.
3. Prints the exact GitHub secrets to create.

Then create these repository secrets
(**Settings → Secrets and variables → Actions**):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | base64 blob from the script |
| `RELEASE_KEYSTORE_PASSWORD` | the password you typed during generation |
| `RELEASE_KEY_ALIAS` | `lavartemis` |
| `RELEASE_KEY_PASSWORD` | **the same password again** |

Why two password secrets with the same value? A Java keystore has two levels:
the file itself (`storePassword`) and each key inside it (`keyPassword`).
`keytool` defaults the key password to the store password — that's why the
script only asks once. `app/build.gradle` still reads them as two separate
variables (`CI_KEYSTORE_PASSWORD` / `CI_KEY_PASSWORD`), so the same value must
be entered in both secrets.

Delete `key/release.keystore.b64` once the secrets are saved, and back up
`key/release.keystore` + the passwords somewhere safe (password manager).

**Losing the keystore or its passwords = you can never publish an update
again** without forcing every user to uninstall first.

## How it is wired

- [.github/workflows/release.yml](.github/workflows/release.yml) decodes
  `RELEASE_KEYSTORE_BASE64` into `$RUNNER_TEMP/release.keystore` and exports
  `CI_KEYSTORE_PATH`, `CI_KEYSTORE_PASSWORD`, `CI_KEY_ALIAS`, `CI_KEY_PASSWORD`
  (build-android job).
- [app/build.gradle](app/build.gradle) uses `signingConfigs.ci` when
  `CI_KEYSTORE_PATH` is set. Without the secrets, CI falls back to the
  **debug key** (installable, but signed differently on every runner →
  updates break), and local builds are left unsigned.

## Verifying a release

```powershell
# Fingerprint of a released APK (build-tools of the Android SDK):
apksigner verify --print-certs LavArtemis-<tag>-android-arm64-v8a.apk

# Fingerprint of the installed app:
adb shell dumpsys package com.limelight.lav | Select-String "signatures"
```

Both SHA-256 fingerprints must match, otherwise the update will be rejected.
