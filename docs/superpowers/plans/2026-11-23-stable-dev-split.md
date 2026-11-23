# Stable / Dev split Implementation Plan

> **For agentic workers:** executed natively (the user is away and pre-approved the spec).

**Goal:** ship "LavArtemis Dev" next to the stable app on Android and Windows, with its own releases.

**Architecture:** the channel is read from the version string (`-dev.` ⇒ Dev). Gradle, qmake and WiX
derive ids, names and installer codes from it; both updaters pick their channel from it.

**Spec:** `docs/superpowers/specs/2026-11-23-stable-dev-split-design.md`

## Global Constraints

- Release applicationId suffix `.lav` stays literally in `build.gradle`; Dev appends `.dev`.
- Dev Qt organisation: `LavArtemis Dev`. Stable values unchanged byte for byte.
- Dev tags `v<x.y.z>-dev.<n>` are prereleases and skip the Linux job.

## Review Focus

1. Stable build unchanged: same applicationId, label, MSI UpgradeCode, registry keys, `art://`.
2. `21.8.0-dev.1` must not reach qmake `VERSION` or the MSI as-is (non-numeric).
3. Dev first-run import must never write to the stable settings.
4. `publish` must still run when `build-linux` is skipped, and still fail when it fails.
5. A stable app must never be offered a `-dev.` release, and vice versa.

---

### Task 1: Android channel (LavArtemis)
- Modify `app/build.gradle`: `lavDev`, suffixes, labels, `BuildConfig.LAV_DEV`, dev res dir.
- Create `app/src/dev/res/values/ic_launcher_background.xml` (`#E8541A`).
- Modify `UpdateChecker`: `releasesUrl(boolean dev)`, `pickRelease(JsonArray, boolean dev)`; drop prerelease pref.
- Remove `checkbox_update_prerelease` from `preferences.xml` and its callers.
- Test `UpdateCheckerChannelTest`: `pickRelease` skips non-dev tags for Dev; `isDevVersion("21.8.0-dev.1")`.
- Verify: debug build with versionName `21.8.0-dev.1` → package `com.limelight.lavdebug.dev`.

### Task 2: Desktop channel (LavArtemis-Qt)
- `app/app.pro`: numeric `VERSION`, `LAV_DEV_CHANNEL`.
- `main.cpp`: organisation name per channel, first-run import (`settings/channelmigration.{h,cpp}`).
- `autoupdatechecker.cpp`: channel-based URL and release pick; drop CI-channel setting.
- WiX: `LavChannel` define → names, UpgradeCodes, AppData, no `art://` for Dev, same-version upgrades.
- `scripts/build-arch.bat` / `generate-bundle.bat`: export `LAV_CHANNEL`.
- Verify: local build of both channels, MSI install of Dev beside stable.

### Task 3: CI + docs
- `release.yml`: prerelease for `-dev.`, skip Linux and freshness check, `publish` condition.
- CLAUDE.md (both repos): channel section.
- Bump dev branch to `21.8.0-dev.1`, push both `dev` branches, tag, watch CI, install and test.
