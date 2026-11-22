# Rotation qui suit le téléphone + trackpad 1:1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** (1) An opt-in setting makes the stream view follow the phone's rotation while the host resolution never changes, always fitted in portrait; (2) with trackpad pinch-zoom on, the cursor travels the same on-screen distance as the finger.

**Architecture:** A pure `RotationPolicy` decides resolution inversion and the effective scale mode; `Game` applies it at start and on every configuration change. For speed, the trackpad contexts hand the `MouseMover` raw finger deltas (no acceleration, no reference scaling) and `TrackpadZoomController` converts them to stream pixels with `streamW / (viewW × zoom)`.

**Tech Stack:** Java 11, Android minSdk 21, JUnit 4, Robolectric 4.16, Mockito 5.

**Spec:** `docs/superpowers/specs/2026-11-22-rotation-and-trackpad-speed-design.md`

## Global Constraints

- Setting `checkbox_follow_device_rotation`, default **false**, right after `checkbox_auto_invert_video_resolution` in `preferences.xml`.
- Follow on ⇒ `setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)` (respects Android's rotation lock); **never** invert the requested resolution; portrait ⇒ effective scale mode **FIT** whatever the user chose; landscape ⇒ the chosen mode. Not on an external display.
- Follow on ⇒ in-game "Pivoter l'écran" entry hidden.
- 1:1 only when `TrackpadZoomController` exists (pinch-zoom setting on). Natural: no `cbrt` acceleration on mover moves. Gaming: mover gets `rawDelta × touchPadSensitivity/100` (Y: `touchPadYSensitity`), no `referenceWidth/viewWidth` factor. Controller: stream delta = `d × streamW / viewW` per axis (`streamH / viewH` for Y), then `TrackedCursor.move(.., zoom)` divides by zoom.
- Option(s) off ⇒ behaviour byte-for-byte as today.
- Strings EN + FR. Code/commits English, CLAUDE.md French.
- Build env: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`; 5 known red JVM tests.

## Review Focus

1. **Rotating while zoomed in** → view stays within the image, cursor follow keeps working with the new view size (no stale `viewW`). (Task 2 `conversionUsesCurrentViewSize`.)
2. **Rotation with STRETCH chosen: portrait → landscape → portrait** → fit in portrait, stretch back in landscape, never stuck. (Task 1 `stretchComesBackInLandscape`.)
3. **Rotation lock on** → orientation stays put (FULL_USER honours it) — manual check, Task 3.
4. **Gaming sensitivity at 50 %** → half the finger distance. (Task 2 `gamingMoverScalesBySensitivity`.)
5. **Mover off** → natural trackpad keeps its acceleration exactly. (Task 2 `noMoverKeepsAcceleration`.)

---

### Task 1: `RotationPolicy`

**Files:**
- Create: `app/src/main/java/com/limelight/utils/RotationPolicy.java`
- Test: `app/src/test/java/com/limelight/utils/RotationPolicyTest.java` (plain JUnit)

**Interfaces:**
- Produces (static, no Android imports beyond `PreferenceConfiguration.ScaleMode`):
  - `boolean shouldInvertResolution(boolean followRotation, boolean portrait, boolean autoInvert)` → `!followRotation && portrait && autoInvert`.
  - `PreferenceConfiguration.ScaleMode effectiveScaleMode(ScaleMode chosen, boolean followRotation, boolean portrait)` → `FIT` when `followRotation && portrait`, else `chosen`.

- [ ] **Step 1: Failing tests** — `followNeverInverts` (`shouldInvertResolution(true,true,true)` false), `withoutFollowKeepsTodayInversion` (`(false,true,true)` true, `(false,false,true)` false), `portraitFollowForcesFit` (STRETCH and FILL → FIT when `(true, true)`), `stretchComesBackInLandscape` (`(STRETCH, true, false)` → STRETCH), `noFollowKeepsChosen` (`(STRETCH, false, true)` → STRETCH).
- [ ] **Step 2: Run** `--tests "com.limelight.utils.RotationPolicyTest"` → compilation FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → 5 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Decide inversion and scale mode for a view that follows rotation"`

---

### Task 2: Trackpad at finger speed

**Files:**
- Modify: `binding/input/touch/TrackpadContext.java` (single-finger and drag moves: when `mouseMover != null`, skip `precisionMultiplier` — keep `sensitivityX/Y`; momentum unchanged in shape, it reuses the resulting velocity)
- Modify: `binding/input/touch/RelativeTouchContext.java` (`touchMoveEvent`: when `mouseMover != null`, pass `(eventX − lastTouchX) × touchPadSensitivity/100`, Y likewise, i.e. before the `xFactor/yFactor` reference scaling)
- Modify: `binding/input/touch/TrackpadZoomController.java` (`move`: `cursor.move(dx × streamW / viewW, dy × streamH / viewH, zoom)`, factor 1 when the view size is 0; view size read **at each move**)
- Test: `TouchContextMoverTest` (record last dx/dy in `RecordingMover`), `TrackpadZoomControllerTest`

**Interfaces:**
- Consumes: `MouseMover.move(short, short)` — semantics now **raw finger pixels** (document it in `MouseMover`'s javadoc); `ZoomView.getViewWidth()/getViewHeight()`.
- Produces: nothing new.

- [ ] **Step 1: Failing tests**
  - `TouchContextMoverTest.trackpadMoverGetsRawFingerDelta`: move 100 px → `mover.lastDx == 100` (today ≈ 232 with `cbrt(100/8)`).
  - `TouchContextMoverTest.gamingMoverGetsRawFingerDelta`: view 1000 wide, sensitivity 100, move 100 px → `lastDx == 100` (today 128 via 1280/1000).
  - `TouchContextMoverTest.gamingMoverScalesBySensitivity`: sensitivity 50 → `lastDx == 50`.
  - `TouchContextMoverTest.noMoverKeepsAcceleration`: no mover, move 100 → `verify(conn).sendMouseMove((short) 232, (short) 0)` (pin today's value; compute it as `(short)(100 * Math.cbrt(100 / 8.0))` in the test).
  - `TrackpadZoomControllerTest.moveIsOneToOneOnScreen`: fake view 1000×500, stream 1920×1080, zoom 2, `move(100, 0)` → `lastX == 1056` (960 + 100·1920/(1000·2)).
  - `TrackpadZoomControllerTest.conversionUsesCurrentViewSize`: change the fake's view width to 500 between two moves → second move converts with the new size.
  - Update existing expectations for the new conversion: `moveSendsAbsoluteFromCentre` → 979, `moveDividesByZoom` → 970.
- [ ] **Step 2: Run** `--tests "com.limelight.binding.input.touch.*"` → the new/updated tests FAIL on values.
- [ ] **Step 3: Implement** the three changes.
- [ ] **Step 4: Run** → all touch-package tests PASS.
- [ ] **Step 5: Commit** — `git commit -m "Move the cursor as far as the finger on screen when the app owns it"`

---

### Task 3: Follow rotation in `Game`, settings, menu, docs

**Files:**
- Modify: `preferences/PreferenceConfiguration.java` — `CHECKBOX_FOLLOW_DEVICE_ROTATION`, default false, `public boolean followDeviceRotation`.
- Modify: `res/xml/preferences.xml` (after `checkbox_auto_invert_video_resolution`, **no** `dependency`), `values/strings.xml`, `values-fr/strings.xml`:
  `title_follow_device_rotation` "Follow phone rotation" / "Suivre la rotation du téléphone"; `summary_follow_device_rotation` "The stream turns with the phone during the stream; the host keeps its resolution. In portrait the image is fitted to the width." / "L\'image tourne avec le téléphone pendant le stream ; le PC garde sa résolution. En portrait, l\'image est ajustée à la largeur."
- Modify: `Game.java`
  - `boolean followRotation = prefConfig.followDeviceRotation && !isOnExternalDisplay()` (field `followDeviceRotation`), decided in the non-external branch (~l. 452): `currentOrientation` = device orientation when following; `shouldInvertDecoderResolution = RotationPolicy.shouldInvertResolution(...)`.
  - `setPreferredOrientationForActivity()` (~l. 1300): first line — when following, `setRequestedOrientation(SCREEN_ORIENTATION_FULL_USER)` and return.
  - Extract the scale block of `prepareDisplayForRendering` (~l. 1789) into `applyStreamScaleMode()`; when following, use `RotationPolicy.effectiveScaleMode(prefConfig.videoScaleMode, true, portraitNow)`: STRETCH ⇒ `streamContainer.setDesiredAspectRatio(0)` (container fills, as today's STRETCH path), else aspect `displayWidth/displayHeight` + `setFillDisplay(mode == FILL)`, then `requestLayout()`. Not following ⇒ exactly today's code path.
  - `onConfigurationChanged`: when following, call `applyStreamScaleMode()`.
  - `public boolean isFollowingDeviceRotation()`.
- Modify: `GameMenu.java:338` — rotate entry only when `dialogScreenContext == game && !game.isFollowingDeviceRotation()`.
- Modify: `CLAUDE.md` — section `## 🔄 Rotation qui suit le téléphone (Android)` (⚠️ resolution never inverted while following — the host can't change resolution mid-stream; ⚠️ portrait forces FIT; 📋 FULL_USER honours the rotation lock) + update the trackpad zoom section: 1:1 replaces "divided by zoom", mover gets raw finger pixels.
- Modify: `docs/superpowers/specs/2026-11-22-trackpad-pinch-zoom-design.md` — one line pointing to the 1:1 rule that supersedes the speed rule.

- [ ] **Step 1: Make the edits.**
- [ ] **Step 2: Build + full suite** — `.\gradlew.bat assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest --continue` → APKs built, only the 5 known failures.
- [ ] **Step 3: Release lint** — `.\gradlew.bat lintVitalNonRoot_gameRelease` → success.
- [ ] **Step 4: Manual check (Lavagnou)**: setting on, start in portrait → landscape image fitted, host still landscape; rotate both ways mid-stream, zoomed and not; rotation lock on → no turn; STRETCH chosen → fitted in portrait, stretched in landscape; rotate entry absent from the menu; trackpad with pinch-zoom → finger and cursor travel together, at 1× and zoomed, both orientations.
- [ ] **Step 5: Commit** — `git commit -m "Let the stream view follow the phone's rotation"`
