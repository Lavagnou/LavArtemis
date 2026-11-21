# Bouton vocal push-to-talk Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A semi-transparent microphone button on the Android stream screen that holds the host's ² key while pressed, shown/hidden from settings and the in-game menu, moved through an in-game-menu "move" mode.

**Architecture:** Two pure helpers (position maths, profile-aware boolean write) and one view controller (`VoicePttButton`) that owns the touch state machine and never talks to the connection directly — it calls a `KeySender`. `Game` wires the controller to the layout, the connection and its lifecycle; `GameMenu` adds two entries.

**Tech Stack:** Java 11, Android SDK (minSdk 21), Robolectric 4.16 + JUnit 4 for tests.

**Spec:** `docs/superpowers/specs/2026-11-21-voice-ptt-button-design.md`

## Global Constraints

- Key sent: **`VK_OEM_3` = `0xC0`**, modifier `0`, flags `0` (normalized US layout) → scancode 0x29 → ² on the user's French host. Never `VK_OEM_7` (0xDE).
- Button: **56 dp**, alpha **0.5** idle, **0.9** while pressed; default position bottom-right, **16 dp** margin.
- Setting key `checkbox_voice_ptt_button`, default **false**, in *UI Settings* right after `checkbox_enable_floating_button`.
- Position keys `voice_ptt_button_x` / `voice_ptt_button_y`: floats in **0..1** (fraction of the free area), `-1` = unset. Stored in the **base** shared preferences (device-level, not per profile).
- Icon: a microphone that reads as "voice" at a glance (Material `mic` path below).
- Strings in `values/strings.xml` **and** `values-fr/strings.xml`.
- Code, comments, commits in English; CLAUDE.md prose in French (repo convention).
- Tests: `@Config(sdk = {33}, shadows = {ShadowMoonBridge.class, ShadowGameManager.class})` for Robolectric tests, as in `ProfilesManagerTest`.
- Build env (PowerShell): `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before any `gradlew.bat` call (default JDK 25 is rejected).

## Review Focus

1. **Finger slides off the button while held** → Android sends `ACTION_CANCEL` or an `ACTION_UP` outside the view: the key must still go up exactly once. (Task 3, `cancelReleasesKey`.)
2. **Second finger on the button / multi-touch while held** → `ACTION_POINTER_DOWN/UP` must not send extra downs or an early up. (Task 3, `secondPointerDoesNotRetrigger`.)
3. **Menu opened, app backgrounded, PiP, or stream stops while held** → key released before the connection goes. (Task 4 wiring; Task 3 `releaseKeyIsIdempotent` + `hideWhilePressedReleasesKey`.)
4. **Screen rotation / different resolution after placing the button** → stored fraction maps back inside the screen, never off-screen. (Task 1, `fractionStaysOnScreenAfterRotation`.)
5. **Profile active that already carries `checkbox_voice_ptt_button`** → the in-game toggle must change what the next stream reads, not a base value the profile masks. (Task 2, `writesIntoActiveProfileWhenItHasTheKey`.)

---

### Task 1: Position maths

**Files:**
- Create: `app/src/main/java/com/limelight/binding/input/VoicePttButtonPosition.java`
- Test: `app/src/test/java/com/limelight/binding/input/VoicePttButtonPositionTest.java` (plain JUnit, no Robolectric)

**Interfaces:**
- Produces (all `static`, final class, no Android imports):
  - `float[] toPixels(float fx, float fy, int parentW, int parentH, int buttonSize, int marginPx)` → `{x, y}` top-left in px. If `fx < 0 || fy < 0` → default bottom-right: `{parentW - buttonSize - marginPx, parentH - buttonSize - marginPx}`. Otherwise `x = fx * (parentW - buttonSize)`, `y = fy * (parentH - buttonSize)`, each clamped to `[0, parent - buttonSize]`.
  - `float[] toFraction(float x, float y, int parentW, int parentH, int buttonSize)` → `{fx, fy}` clamped to `[0,1]`; a free extent ≤ 0 gives fraction `0`.
  - `float clamp(float v, float min, float max)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void unsetGoesBottomRightWithMargin() {
    assertArrayEquals(new float[]{1920 - 150 - 42, 1080 - 150 - 42},
        VoicePttButtonPosition.toPixels(-1f, -1f, 1920, 1080, 150, 42), 0.01f);
}
@Test public void roundTripKeepsPosition() {
    float[] f = VoicePttButtonPosition.toFraction(300f, 200f, 1920, 1080, 150);
    assertArrayEquals(new float[]{300f, 200f},
        VoicePttButtonPosition.toPixels(f[0], f[1], 1920, 1080, 150, 42), 0.5f);
}
@Test public void fractionStaysOnScreenAfterRotation() {
    float[] f = VoicePttButtonPosition.toFraction(1770f, 930f, 1920, 1080, 150); // bottom-right in landscape
    float[] p = VoicePttButtonPosition.toPixels(f[0], f[1], 1080, 1920, 150, 42);  // portrait
    assertTrue(p[0] >= 0 && p[0] <= 1080 - 150);
    assertTrue(p[1] >= 0 && p[1] <= 1920 - 150);
}
@Test public void offScreenInputIsClamped() {
    assertArrayEquals(new float[]{1f, 0f},
        VoicePttButtonPosition.toFraction(5000f, -40f, 1920, 1080, 150), 0.0001f);
}
@Test public void parentSmallerThanButtonGivesZero() {
    assertArrayEquals(new float[]{0f, 0f},
        VoicePttButtonPosition.toFraction(10f, 10f, 100, 100, 150), 0.0001f);
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.binding.input.VoicePttButtonPositionTest"`
Expected: FAIL, compilation error (class missing).

- [ ] **Step 3: Implement `VoicePttButtonPosition`** per the Interfaces block.

- [ ] **Step 4: Run to verify they pass** — same command, Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/limelight/binding/input/VoicePttButtonPosition.java app/src/test/java/com/limelight/binding/input/VoicePttButtonPositionTest.java
git commit -m "Add position maths for the voice push-to-talk button"
```

---

### Task 2: Profile-aware boolean write

Why: `OverlaySharedPreferences.edit()` writes to the base preferences (`ProfilesManager.java:251`), so a key the active profile carries would mask the write.

**Files:**
- Create: `app/src/main/java/com/limelight/profiles/ProfileAwarePreferences.java`
- Test: `app/src/test/java/com/limelight/profiles/ProfileAwarePreferencesTest.java` (Robolectric, setup like `ProfilesManagerTest`: reset `ProfilesManager.instance = null`, clean `files/profiles`)

**Interfaces:**
- Produces: `public static void putBoolean(Context context, String key, boolean value)`.
  Rule: active profile exists **and** its options contain `key` → copy options into a new `LinkedHashMap`, put the value, `setOptions`, `setModifiedUtc(now)`, `profiles.update(active)`, `profiles.save(context)` (same steps as `BenchmarkSettings.applyToSettings`). Otherwise → `PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply()`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void writesBaseWhenNoProfile() {
    ProfileAwarePreferences.putBoolean(context, "checkbox_voice_ptt_button", true);
    assertTrue(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("checkbox_voice_ptt_button", false));
}
@Test public void writesBaseWhenActiveProfileLacksKey() { /* active profile with options {"other": 1}; after put: base true, profile options unchanged (no "checkbox_voice_ptt_button") */ }
@Test public void writesIntoActiveProfileWhenItHasTheKey() {
    /* active profile with options {"checkbox_voice_ptt_button": false}; put true;
       assertEquals(Boolean.TRUE, manager.getActive().getOptions().get("checkbox_voice_ptt_button"));
       assertTrue(manager.getOverlayingSharedPreferences(context).getBoolean("checkbox_voice_ptt_button", false)); */
}
```

- [ ] **Step 2: Run to verify they fail** — `.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.profiles.ProfileAwarePreferencesTest"`, Expected: compilation FAIL.
- [ ] **Step 3: Implement `ProfileAwarePreferences.putBoolean`** per the rule above.
- [ ] **Step 4: Run to verify they pass** — Expected: 3 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Write a boolean setting where the overlaid preferences will read it"`

---

### Task 3: `VoicePttButton` controller + resources

**Files:**
- Create: `app/src/main/java/com/limelight/binding/input/VoicePttButton.java`
- Create: `app/src/main/res/drawable/ic_voice_ptt.xml`, `app/src/main/res/drawable/voice_ptt_button_moving.xml`
- Test: `app/src/test/java/com/limelight/binding/input/VoicePttButtonTest.java` (Robolectric; build a `FrameLayout` 1920×1080 with an `ImageButton` child 150×150 and a shield `View`; feed `MotionEvent.obtain(...)` to the button's touch listener via `button.dispatchTouchEvent`)

**Interfaces:**
- Consumes: `VoicePttButtonPosition` (Task 1).
- Produces:
  - `public interface KeySender { void keyDown(); void keyUp(); }` (nested in `VoicePttButton`)
  - `public VoicePttButton(ImageButton button, View moveShield, SharedPreferences basePrefs, KeySender sender)` — installs listeners; `moveShield` is a full-screen view, `GONE` except in move mode.
  - `void setShown(boolean shown)` — `VISIBLE`/`GONE`; hiding calls `releaseKey()` and `endMove()`; showing applies the stored position (after layout: `button.post(...)`).
  - `boolean isShown()`
  - `void beginMove()` — `releaseKey()`, background → `voice_ptt_button_moving`, alpha 0.9, shield `VISIBLE`.
  - `boolean isMoving()`
  - `void endMove()` — saves the fraction (`voice_ptt_button_x/_y` via `basePrefs.edit().putFloat(...).apply()`), restores background/alpha, shield `GONE`. No-op if not moving.
  - `void releaseKey()` — if the key is down: `sender.keyUp()`, alpha 0.5. Idempotent.
  - `void reapplyPosition()` — re-reads the fraction and places the button (call on layout/rotation).
- Touch, normal mode: `ACTION_DOWN` → `sender.keyDown()`, alpha 0.9, consume. `ACTION_UP`/`ACTION_CANCEL` → `releaseKey()`. Other actions (incl. `ACTION_POINTER_DOWN/UP`, `ACTION_MOVE`) consumed, no send. Every event consumed (never reaches the stream).
- Touch, move mode: drag as in `Game.initFloatingButton()` (raw-coordinate offset, clamped to parent); never calls the sender. Shield touch (any `ACTION_DOWN`) → `endMove()`, consumed.
- Icon `ic_voice_ptt.xml`: 24 dp vector, white fill, path
  `M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z`
- `voice_ptt_button_moving.xml`: same oval as `floating_menu_button.xml` with a 3 dp accent stroke (e.g. `#FF4FC3F7`) — the "you are moving me" look.

- [ ] **Step 1: Write the failing tests** (a recording `KeySender` counts downs/ups)

```java
@Test public void pressHoldsKeyUntilRelease()      { down(); assertEquals(1, s.downs); assertEquals(0, s.ups); up(); assertEquals(1, s.ups); }
@Test public void cancelReleasesKey()              { down(); cancel(); assertEquals(1, s.ups); }
@Test public void secondPointerDoesNotRetrigger()  { down(); pointerDown(); pointerUp(); assertEquals(1, s.downs); assertEquals(0, s.ups); up(); assertEquals(1, s.ups); }
@Test public void releaseKeyIsIdempotent()         { down(); c.releaseKey(); c.releaseKey(); up(); assertEquals(1, s.ups); }
@Test public void hideWhilePressedReleasesKey()    { down(); c.setShown(false); assertEquals(1, s.ups); assertEquals(View.GONE, button.getVisibility()); }
@Test public void moveModeSendsNothing()           { c.beginMove(); down(); move(+200, -100); up(); assertEquals(0, s.downs); assertTrue(c.isMoving()); }
@Test public void beginMoveWhilePressedReleases()  { down(); c.beginMove(); assertEquals(1, s.ups); }
@Test public void shieldTapEndsMoveAndSaves()      {
    c.beginMove(); down(); move(-300, -300); up(); tapShield();
    assertFalse(c.isMoving()); assertEquals(View.GONE, shield.getVisibility());
    assertTrue(prefs.getFloat("voice_ptt_button_x", -1f) >= 0f);
}
@Test public void alphaFollowsPress()              { c.setShown(true); assertEquals(0.5f, button.getAlpha(), 0.01f); down(); assertEquals(0.9f, button.getAlpha(), 0.01f); up(); assertEquals(0.5f, button.getAlpha(), 0.01f); }
```

- [ ] **Step 2: Run to verify they fail** — `.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "com.limelight.binding.input.VoicePttButtonTest"`, Expected: compilation FAIL.
- [ ] **Step 3: Implement `VoicePttButton` and the two drawables** per the Interfaces block.
- [ ] **Step 4: Run to verify they pass** — Expected: 9 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add the voice push-to-talk button controller"`

---

### Task 4: Wire it into the stream, settings and in-game menu

**Files:**
- Modify: `app/src/main/res/layout/activity_game.xml` (after `overlayToggleZoomButton`): a full-screen `View` `@+id/voicePttMoveShield` (`gone`, transparent, clickable, elevation `9997dp`) then `ImageButton` `@+id/voicePttButton` (56 dp, `layout_gravity="top|start"`, background `@drawable/floating_menu_button`, `src="@drawable/ic_voice_ptt"`, padding 14 dp, `scaleType="fitCenter"`, `contentDescription="@string/voice_ptt_button"`, `gone`, elevation `9998dp`).
- Modify: `app/src/main/res/xml/preferences.xml:615` — `CheckBoxPreference` `checkbox_voice_ptt_button`, default `false`, title/summary below, right after the floating-button checkbox.
- Modify: `app/src/main/java/com/limelight/preferences/PreferenceConfiguration.java` — `CHECKBOX_VOICE_PTT_BUTTON`, `DEFAULT_VOICE_PTT_BUTTON = false`, `public boolean enableVoicePttButton`, read next to `enableFloatingButton` (`:983`).
- Modify: `app/src/main/java/com/limelight/Game.java`:
  - field `private VoicePttButton voicePttButton;` created after `initFloatingButton()` (`:930`) with `PreferenceManager.getDefaultSharedPreferences(this)` and a `KeySender` that calls `conn.sendKeyboardInput((short) 0xC0, KeyboardPacket.KEY_DOWN|KEY_UP, (byte) 0, (byte) 0)` when `conn != null`; then `setShown(prefConfig.enableVoicePttButton)`.
  - `reapplyPosition()` on configuration change / layout of the stream container (rotation).
  - `releaseKey()` in: `showGameMenu()` (`:4419`), `onWindowFocusChanged(false)` (`:1448`), `onPause()` (`:1832`, unconditionally), and `stopConnection()` (`:3542`) **before** the connection stops (next to `stopMicrophone()`).
  - PiP (`:1257` / `:1296`): hide and restore it exactly like `floatingMenuButton`.
  - Back while moving: in `onBackPressed()` (`:4103`), `if (voicePttButton != null && voicePttButton.isMoving()) { voicePttButton.endMove(); return; }` first.
  - Public: `boolean isVoicePttButtonShown()`, `void toggleVoicePttButton()` (flip `setShown`, then `ProfileAwarePreferences.putBoolean(this, "checkbox_voice_ptt_button", shown)`), `void startMovingVoicePttButton()` (`hideGameMenu()`, `beginMove()`, toast `voice_ptt_move_hint`).
- Modify: `app/src/main/java/com/limelight/GameMenu.java` `showMenu()` (`:317`, after "toggle keyboard"): entry `game_menu_show_voice_ptt_button` / `game_menu_hide_voice_ptt_button` by state → `game::toggleVoicePttButton`; when shown, entry `game_menu_move_voice_ptt_button` → `game::startMovingVoicePttButton`.
- Modify: `values/strings.xml` and `values-fr/strings.xml`:

| key | EN | FR |
|---|---|---|
| `title_voice_ptt_button` | Voice push-to-talk button | Bouton vocal push-to-talk |
| `summary_voice_ptt_button` | Shows a microphone button while streaming. Holding it holds the host\'s ² key (left of 1), for a dictation tool on the PC. | Affiche un bouton micro pendant le stream. Le maintenir enfonce la touche ² de l\'hôte (à gauche du 1), pour un outil de dictée sur le PC. |
| `voice_ptt_button` | Voice push-to-talk | Vocal push-to-talk |
| `game_menu_show_voice_ptt_button` | Show voice button | Afficher le bouton vocal |
| `game_menu_hide_voice_ptt_button` | Hide voice button | Masquer le bouton vocal |
| `game_menu_move_voice_ptt_button` | Move voice button | Déplacer le bouton vocal |
| `voice_ptt_move_hint` | Drag the button, then tap elsewhere to confirm | Faites glisser le bouton, puis touchez ailleurs pour valider |

- Modify: `CLAUDE.md` — under *Hot path* neighbours or a new short `### 🎤 Bouton vocal push-to-talk` section: ⚠️ `VK_OEM_3` not `VK_OEM_7` (normalized US VK → scancode → host layout); ⚠️ every path that can lose the finger releases the key (list the four hooks); 📋 position in base prefs, visibility through `ProfileAwarePreferences` (why: `OverlaySharedPreferences.edit()`).

- [ ] **Step 1: Make the edits above.**
- [ ] **Step 2: Build and run the full JVM suite**
  Run: `.\gradlew.bat assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest --continue`
  Expected: BUILD OK; new tests pass; the only failures are the 5 known ones (`LayoutInflationTest`, `SimpleStartupTest`, `StartupTest`, `ProfilesNavigationTest` ×2).
- [ ] **Step 3: Lint as the release does** — `.\gradlew.bat lintVitalNonRoot_gameRelease` (strings + layout changed). Expected: no error.
- [ ] **Step 4: Manual check on device** (the repo asks for hand testing; Claude cannot hold the button):
  1. Enable the setting → stream → button bottom-right, half transparent, mic icon.
  2. Hold it → the PC dictation tool starts; release → it stops. On a text editor on the host with dictation off, a short hold types `²` (proves the key).
  3. Hold, slide the finger off the screen edge → dictation stops.
  4. Hold, open the menu with the back gesture → dictation stops.
  5. Menu → Move → drag → tap elsewhere → button stays; next stream it is at the same place; rotate → still on screen.
  6. Menu → Hide → next stream it is hidden; settings checkbox is unchecked.
- [ ] **Step 5: Commit** — `git commit -m "Show a voice push-to-talk button while streaming"`
