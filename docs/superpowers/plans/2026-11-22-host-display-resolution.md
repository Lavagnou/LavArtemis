# Résolution et fréquence calées sur l'écran du PC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LavApollo announces the captured display's mode; when a client joins a running session or launches without a virtual display, it streams at the host display's aspect, fitted inside its own settings and never above the host display, at `min(fps)`; mid-session mode changes trigger an automatic reconnect.

**Architecture:** Host: a portable `current_display` module (pure combine + Windows query) feeds three new `serverinfo` fields. Each client: a pure fit function and a pure change watcher (same reference table in Java and C++), wired into the existing launch/resume decision; a reconnect re-runs the normal start path.

**Tech Stack:** C++20 / gtest (LavApollo, MSYS2 UCRT64); Java 11 / JUnit / Robolectric (Android); C++ / Qt 6.11 QtTest (desktop).

**Spec:** `docs/superpowers/specs/2026-11-22-host-display-resolution-design.md`

## Global Constraints

- `serverinfo` fields (paired clients only): `CurrentDisplayWidth`, `CurrentDisplayHeight`, `CurrentDisplayRefreshRate` (integers). Absent when unknown. Non-LavApollo host ⇒ clients behave exactly as today.
- Fit rule: `scale = min(1, settingW / hostW, settingH / hostH)`; `w = floor(hostW·scale)`, `h = floor(hostH·scale)`, each rounded **down to even**; `fps = min(settingFps, round(hostRefresh))`.
- Reference table (identical in both clients' tests):
  | host | setting | result |
  |---|---|---|
  | 1920×1080@60 | 2400×1080@90 | 1920×1080@60 |
  | 2400×1080@90 | 1920×1080@60 | 1920×864@60 |
  | 2400×1080@90 | 1920×1080@120 | 1920×864@90 |
  | 1280×720@60 | 1920×1080@60 | 1280×720@60 |
  | 3840×2160@60 | 1920×1080@60 | 1920×1080@60 |
- Applies when **resuming** (`currentgame` is the requested app) **or launching without a virtual display**; not when launching with one. Not for the desktop emulated multi-display. Effective size/fps are **in memory only** (never written to prefs/profiles).
- Watch: poll `serverinfo` every **2500 ms** during the stream; trigger after **2 consecutive** polls whose *fit target* differs from the *current stream* size/fps; then reconnect (resume) with the new values. Off during benchmark runs. Off when fields are absent.
- Host query must not block the HTTPS thread (no wait on display calls beyond `EnumDisplaySettingsW`).
- Local builds only (Actions quota): Android `JAVA_HOME=…\Android Studio\jbr`; LavApollo tests `cmake -B build/t -G Ninja -DBUILD_TESTS=ON` + `ninja -C build/t -j 3 test_sunshine` from PowerShell with `C:\msys64\ucrt64\bin;C:\msys64\usr\bin` on PATH (31 known failures DisplayDevice*/Mdns*); desktop tests `qmake tests/tests.pro CONFIG+=tests` + `jom` under VS + Qt 6.11 msvc env, built in the standalone `Github/LavArtemis-Qt` clone (see `local-build-recipes` memory).
- Commits per repo; desktop pointer bumped in LavArtemis afterwards. Docs in each repo's `CLAUDE.md` (French) in the task that changes behaviour.

## Review Focus

1. **Reconnect loop**: host 2400×1080 streamed at 1920×864 must be *stable* — the watcher compares fit target to current stream, never host to stream. (Task 4 `scaledButStableDoesNotTrigger`, Task 6 same name.)
2. **Windows passing through an intermediate mode** while the user changes resolution → no reconnect until two consecutive identical targets. (Tasks 4 and 6 `changingTargetRestartsDebounce`.)
3. **Composite multi-display session joined from the phone** → host reports the canvas bounding box. (Task 1 `combineUsesBoundingBox`.)
4. **Odd host modes** (1366×768 into 1280×720: scale 0.937 → 1280×719.6 → 1280×718) → never odd, never 0. (Tasks 2 and 6 `roundsDownToEven`.)
5. **VRR 1000 Hz virtual display** → fps stays the client's. (Tasks 2 and 6 `vrrDisplayKeepsClientFps`.)

---

## LavApollo (repo `Github/LavApollo`)

### Task 1: `current_display` + `serverinfo` fields

**Files:**
- Create: `src/current_display.h`, `src/current_display.cpp` (portable), Windows query in `src/platform/windows/current_display_win.cpp` (or inside `current_display.cpp` under `#ifdef _WIN32`)
- Modify: `src/nvhttp.cpp` (serverinfo, next to `MicPassthroughState` ~l.1119), `cmake/targets/*.cmake` (add sources), `CLAUDE.md`
- Test: `tests/unit/test_current_display.cpp`

**Interfaces:**
- Produces: `namespace current_display { struct mode_t { int x, y, width, height, refresh; }; std::optional<mode_t> combine(const std::vector<mode_t>&); std::optional<mode_t> captured(); }`
  - `combine`: bounding box of all modes (x/y/width/height), refresh = **min** of refreshes; empty → `nullopt`.
  - `captured()`: names = `platf::split_display_names(proc::proc.get_display_name())` when an app runs and the name is non-empty; else `config::video.output_name`; else `VDISPLAY::getPrimaryDisplay()`. Each name → `VDISPLAY::getDeviceSettings()` (`dmPosition`, `dmPelsWidth/Height`, `dmDisplayFrequency`); `combine`. Non-Windows → `nullopt`.

- [ ] **Step 1: Failing tests** — `CurrentDisplay.combineSingle` ({0,0,1920,1080,60} → same), `CurrentDisplay.combineUsesBoundingBox` ({0,0,1920,1080,60},{1920,-200,1080,1920,144} → {0,-200,3000,1920,60}), `CurrentDisplay.combineEmpty` (→ nullopt).
- [ ] **Step 2: Run** `ninja -C build/t -j 3 test_sunshine && build/t/tests/test_sunshine --gtest_filter='CurrentDisplay.*'` → compile FAIL.
- [ ] **Step 3: Implement** the module, the three `tree.put` lines (only `if (auto m = current_display::captured())`), sources in cmake.
- [ ] **Step 4: Run** → 3 PASS; full `test_sunshine` → only the 31 known failures. Also `ninja -C build` (full host) builds.
- [ ] **Step 5: Document** in `CLAUDE.md` (fork-specific section: fields, which display is reported, ⚠️ HTTPS thread), commit `Report the captured display's mode in serverinfo`.

---

## Android (repo `Github/LavArtemis`)

### Task 2: `HostDisplayFit`

**Files:** Create `app/src/main/java/com/limelight/utils/HostDisplayFit.java`; Test `app/src/test/java/com/limelight/utils/HostDisplayFitTest.java` (plain JUnit)

**Interfaces:**
- Produces: `final class HostDisplayFit { static final class Mode { final int width, height, fps; } static Mode fit(int hostW, int hostH, int hostRefresh, int settingW, int settingH, int settingFps); static boolean applies(boolean resuming, boolean launchWithVirtualDisplay); }` — `applies` = `resuming || !launchWithVirtualDisplay`.

- [ ] **Step 1: Failing tests** — the 5 reference rows (one test each, names `hostSmallerThanSetting`, `phoneSessionOnLaptop`, `fpsCappedByHost`, `neverUpscale`, `fourKIntoFullHd`), `roundsDownToEven` (1366×768 into 1280×720 → 1280×718), `vrrDisplayKeepsClientFps` (host refresh 1000, setting 90 → 90), `appliesWhenResumingEvenWithVirtualDisplay`, `appliesWhenLaunchingWithoutVirtualDisplay`, `notWhenLaunchingWithVirtualDisplay`.
- [ ] **Step 2: Run** `--tests "com.limelight.utils.HostDisplayFitTest"` → compile FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `Fit the stream to the host display's mode`.

### Task 3: Apply it at connection time

**Files:**
- Modify: `nvstream/http/NvHTTP.java` (`int[] getCurrentDisplayMode(String serverInfo)` → `{w,h,refresh}` or `null` when any field is missing, next to `getMicPassthroughState` l.622)
- Modify: `nvstream/StreamConfiguration.java` (package-private `void applyHostDisplay(int width, int height, int fps)`: sets width/height/refreshRate/launchRefreshRate)
- Modify: `nvstream/NvConnection.java` (before `launchApp(…"resume"…)` l.339 and `launchApp(…"launch"…)` l.400: if mode non-null and `HostDisplayFit.applies(resuming, streamConfig.getVirtualDisplay())` → fit with the config's current values, `applyHostDisplay`, then `listener.streamModeDecided(w, h, fps)`)
- Modify: `nvstream/NvConnectionListener.java` (`default void streamModeDecided(int width, int height, int fps) {}`)
- Modify: `Game.java` (`streamModeDecided` → on UI thread: `displayWidth/displayHeight` = values, `applyStreamScaleMode(false)`, `trackpadZoom.setStreamSize(w, h)` when present; remember `effectiveFps`)
- Modify: `binding/input/touch/TrackpadZoomController.java` (`void setStreamSize(int w, int h)` — keeps the cursor's fractional position), `TrackedCursor` (`void resize(int w, int h)`)
- Test: `NvHTTPDisplayModeTest` (Robolectric or plain: XML with/without the fields), `TrackedCursorTest.resizeKeepsFraction`, `TrackpadZoomControllerTest.setStreamSizeUsesNewSize`

- [ ] **Step 1: Failing tests** — `parsesCurrentDisplayMode` (→ {2400,1080,90}), `missingFieldMeansNoMode` (→ null), `resizeKeepsFraction` (cursor at 0.25/0.5 of 1920×1080, resize 1280×720 → 320,360), `setStreamSizeUsesNewSize` (after `setStreamSize(1280,720)`, `move(0,0)` sends ref 1280×720).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** the six changes.
- [ ] **Step 4: Run** the new tests + full suite (5 known failures) + `assembleNonRoot_gameDebug`.
- [ ] **Step 5: Commit** `Stream at the host display's mode when joining or without a virtual display`.

### Task 4: `HostDisplayWatch` (pure)

**Files:** Create `app/src/main/java/com/limelight/utils/HostDisplayWatch.java`; Test `HostDisplayWatchTest`

**Interfaces:**
- Produces: `HostDisplayWatch(int settingW, int settingH, int settingFps, int currentW, int currentH, int currentFps)`; `HostDisplayFit.Mode onPoll(int[] hostMode)` → returns the new target **once** when the same differing target was seen on 2 consecutive polls, else `null`; `null`/absent host mode resets the count.

- [ ] **Step 1: Failing tests** — `scaledButStableDoesNotTrigger` (current 1920×864@60, setting 1920×1080@60, host 2400×1080@90 polled 3× → never), `triggersAfterTwoConsecutive` (host 1920×1080@60 → poll1 null, poll2 = 1920×1080@60), `changingTargetRestartsDebounce` (A, B, B → triggers on the 3rd with B), `absentModeResets`, `firesOnce` (4th identical poll → null).
- [ ] **Step 2–4:** RED, implement, GREEN.
- [ ] **Step 5: Commit** `Detect a host display mode change worth reconnecting for`.

### Task 5: Watch and reconnect in `Game`

**Files:** Modify `Game.java`, `nvstream/NvConnection.java` (expose `String fetchServerInfo()` on a background thread using the connection's `NvHTTP`), `CLAUDE.md`

- Start a poll every 2500 ms after `connectionStarted()` on a `HandlerThread` when the host announced a mode at connection time and this is not a benchmark run; stop it in `stopConnection()`.
- On a target from `HostDisplayWatch`: post to the UI thread `restartStream()` = `stopConnection()` then `recreate()` (the new `onCreate` takes the resume path and Task 3 applies the new mode). Toast `host_display_changed` "Écran du PC modifié : reconnexion…" / "Host display changed: reconnecting…".
- [ ] **Step 1:** Edits.
- [ ] **Step 2:** Full suite + `assembleNonRoot_gameDebug` + `lintVitalNonRoot_gameRelease`.
- [ ] **Step 3:** Manual (Lavagnou): session from the laptop then join from the Pixel → 1920×1080@60, no bars; change the PC's resolution in Windows mid-stream → reconnect in ~2 s at the new mode; Pixel-started session joined from the laptop → 1920×864.
- [ ] **Step 4: Document** in `CLAUDE.md` (section with the rule, the watcher, ⚠️ target-vs-stream comparison, ⚠️ reconnect = `recreate()` on the resume path), commit `Reconnect when the host display's mode changes`.

---

## Desktop (repo `Github/LavArtemis-Qt`)

### Task 6: `HostDisplayFit` + `HostDisplayWatch` in C++

**Files:** Create `app/backend/hostdisplayfit.{h,cpp}` (both: `HostDisplayFit::fit`, `applies`, `HostDisplayWatch` with the same semantics as Tasks 2/4); Test `tests/hostdisplay/tst_hostdisplay.cpp` + `.pro`, added to `tests/tests.pro`

- [ ] **Step 1: Failing tests** — the same names and values as Tasks 2 and 4.
- [ ] **Step 2–4:** RED (compile), implement, GREEN.
- [ ] **Step 5: Commit** `Fit the stream to the host display's mode`.

### Task 7: Wire into `Session`

**Files:** Modify `app/backend/nvcomputer.{h,cpp}` (parse the 3 fields l.~170, `ASSIGN_IF_CHANGED` l.~612), `app/streaming/session.{h,cpp}`, `app/gui/StreamSegue.qml`, `CLAUDE.md`

- `Session::initialize()` (after l.1095, skipping when `useMultiDisplay` applies): when `m_Computer` has the mode and `HostDisplayFit::applies(m_Computer->currentGameId == m_App.id, m_Preferences->useVirtualDisplay)`, override `m_StreamConfig.width/height/fps` (in memory).
- Watch: in the SDL loop tick (`Session::exec`, next to `benchmarkTick()` l.2766 — Qt timers don't run during a stream), every 2500 ms fetch serverinfo on a worker (`NvHTTP::getServerInfo`), feed `HostDisplayWatch`; skip in benchmark mode.
- On a target: `m_RestartRequested = true`, push `SDL_QUIT`; `Q_PROPERTY(bool restartRequested)`; `StreamSegue.qml` `sessionFinished` starts a new session on the same computer/app when `restartRequested`, without the quit dialog.
- [ ] **Step 1:** Edits. **Step 2:** desktop build (`build.bat` x64) + `tests` suite. **Step 3:** manual on the laptop (join a Pixel session → 1920×864; Windows resolution change → reconnect). **Step 4:** Document in `desktop/CLAUDE.md`, commit `Stream at the host display's mode and follow its changes`.

### Task 8: Release plumbing

- [ ] LavApollo: release tag (host must ship first or together). LavArtemis-Qt: push, then in LavArtemis `desktop/` pointer to the new commit (fetch from the local clone, per `local-build-recipes`), root `CLAUDE.md` cross-repo section, version bump, tag.
