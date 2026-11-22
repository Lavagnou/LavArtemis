# Zoom au pincement en mode trackpad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In trackpad mouse modes, pinching zooms the stream (1×–10×) and the view follows the cursor, which the app owns and sends to the host as absolute positions; touch modes reset to 1×.

**Architecture:** Pure-Java pieces carry the logic and the tests: `TrackedCursor` (cursor position), `ZoomFollow` (calm-zone maths), `PinchArbiter` (pinch vs. scroll), and `TrackpadZoomController` (orchestration, talks to a `ZoomView` and a `PositionSender` interface). `PanZoomHandler` implements `ZoomView`. The two trackpad touch contexts send their moves through a `MouseMover` hook instead of `conn.sendMouseMove`. `Game` wires it all behind one setting.

**Tech Stack:** Java 11, Android (minSdk 21 — no `java.util.function`), JUnit 4, Robolectric 4.16, Mockito 5.

**Spec:** `docs/superpowers/specs/2026-11-22-trackpad-pinch-zoom-design.md`

## Global Constraints

- Setting `checkbox_trackpad_pinch_zoom`, default **false**, in *Input Settings* right after `checkbox_absolute_mouse_mode`.
- Trackpad modes = `mouse_mode_list` **2** (natural, `TrackpadContext`) and **3** (gaming, `RelativeTouchContext`). Touch modes = 0, 1, 5; 4 = disabled.
- Zoom bounds **[1, 10]** (`PanZoomHandler.MAX_SCALE`). Calm zone = **[0.25, 0.75]** of the parent in each axis.
- Cursor starts at the **centre** of the stream; position in stream pixels, clamped to `[0, width] × [0, height]` (`prefConfig.width/height`); sent with `conn.sendMousePosition(x, y, width, height)`.
- Speed at 1× = **today's** per-mode deltas; deltas are **divided by the current zoom**.
- Follow runs **only on cursor moves**, for **any** zoom > 1×; never on a pinch frame.
- Entering a touch mode (stream start or `applyMouseMode`) with the setting on → **reset to 1×**.
- With the setting on, gaming's `absoluteMouseMode` is **supplanted** (our mover wins).
- Option off ⇒ behaviour byte-for-byte as today (no mover installed, no pinch interception).
- Strings EN (`values/strings.xml`) + FR (`values-fr/strings.xml`). Code/commits in English, CLAUDE.md in French.
- Build env (PowerShell): `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`. Test cmd: `.\gradlew.bat :app:testNonRoot_gameDebugUnitTest --tests "<class>"`. 5 known red tests on `main` (LayoutInflation, SimpleStartup, Startup, ProfilesNavigation ×2).

## Review Focus

1. **A pinch that starts with one finger already moving the cursor** (second finger lands mid-move) → no cursor jump, no scroll sent once the pinch is decided. (Task 4 `secondFingerMidMoveThenPinchSendsNoScroll`.)
2. **Lifting one finger of a pinch and continuing with the other** → the remaining finger must not move the cursor by the accumulated pinch motion (contexts were cancelled). (Task 4 `liftOneFingerAfterPinchDoesNotMoveCursor`.)
3. **Cursor pinned at the stream edge while zoomed** → follow brings the view to the image edge and stops, never past it. (Task 1 `cursorAtStreamEdgeStopsAtImageEdge`.)
4. **Rapid mouse-mode switch trackpad → touch → trackpad during a stream** → zoom reset to 1×, cursor position resynced from the last absolute touch, no stale mover left on a touch context. (Task 4 `modeSwitchToTouchResetsZoom` + `absoluteTouchResyncsCursor`.)
5. **Momentum (flick) moves of the natural trackpad after the finger lifts** → they go through the mover too, so the view follows the coasting cursor. (Task 5 `flickMomentumGoesThroughMover`.)

---

### Task 1: Cursor and follow maths

**Files:**
- Create: `app/src/main/java/com/limelight/binding/input/touch/TrackedCursor.java`
- Create: `app/src/main/java/com/limelight/utils/ZoomFollow.java`
- Test: `app/src/test/java/com/limelight/binding/input/touch/TrackedCursorTest.java`, `app/src/test/java/com/limelight/utils/ZoomFollowTest.java` (plain JUnit)

**Interfaces:**
- Produces:
  - `TrackedCursor(int streamWidth, int streamHeight)` — starts at `(w/2, h/2)`.
  - `void move(float dx, float dy, float zoom)` — adds `dx/zoom`, `dy/zoom` (zoom < 1 treated as 1), clamps to `[0,w]×[0,h]`, keeps fractions (float).
  - `void setFraction(float fx, float fy)` — clamps to [0,1].
  - `short getX()`, `short getY()` (rounded), `float getFractionX()`, `float getFractionY()`, `int getWidth()`, `int getHeight()`.
  - `ZoomFollow.followTranslation(float childX, float childY, float scale, float cursorFx, float cursorFy, float viewW, float viewH, float parentW, float parentH) → float[]{childX, childY}`: cursor on screen = `child + f * view * scale`; if outside `[0.25·parent, 0.75·parent]`, shift child by exactly the overshoot; then clamp child to `[parent − view·scale, 0]` (when `view·scale > parent`); returns input unchanged when `scale <= 1`.

- [ ] **Step 1: Write the failing tests**

```java
// TrackedCursorTest
@Test public void startsAtCentre() { TrackedCursor c = new TrackedCursor(1920,1080); assertEquals(960, c.getX()); assertEquals(540, c.getY()); }
@Test public void moveDividesByZoom() { TrackedCursor c = new TrackedCursor(1920,1080); c.move(100, 40, 4f); assertEquals(985, c.getX()); assertEquals(550, c.getY()); }
@Test public void fractionalMovesAccumulate() { TrackedCursor c = new TrackedCursor(1920,1080); for (int i=0;i<4;i++) c.move(1,0,4f); assertEquals(961, c.getX()); }
@Test public void clampsToStream() { TrackedCursor c = new TrackedCursor(1920,1080); c.move(-5000, 5000, 1f); assertEquals(0, c.getX()); assertEquals(1080, c.getY()); }
@Test public void zoomBelowOneCountsAsOne() { TrackedCursor c = new TrackedCursor(1920,1080); c.move(10,0,0.5f); assertEquals(970, c.getX()); }
@Test public void setFractionClamps() { TrackedCursor c = new TrackedCursor(1920,1080); c.setFraction(1.5f, -1f); assertEquals(1920, c.getX()); assertEquals(0, c.getY()); }

// ZoomFollowTest — parent 1000×500, view 1000×500
@Test public void noFollowAtOneX() { assertArrayEquals(new float[]{0,0}, ZoomFollow.followTranslation(0,0,1f, 0.99f,0.99f, 1000,500,1000,500), 0.01f); }
@Test public void insideCalmZoneDoesNotMove() { // scale 2, child -500,-250 → cursor 0.5 on screen at 500,250
    assertArrayEquals(new float[]{-500,-250}, ZoomFollow.followTranslation(-500,-250,2f, 0.5f,0.5f, 1000,500,1000,500), 0.01f); }
@Test public void pastRightEdgeShiftsByOvershootOnly() { // fx 0.65 → screen -500+1300=800, limit 750 → child -550
    assertArrayEquals(new float[]{-550,-250}, ZoomFollow.followTranslation(-500,-250,2f, 0.65f,0.5f, 1000,500,1000,500), 0.01f); }
@Test public void pastTopEdgeShiftsDown() { // fy 0.3 → screen -250+300=50, limit 125 → child -175
    assertArrayEquals(new float[]{-500,-175}, ZoomFollow.followTranslation(-500,-250,2f, 0.5f,0.3f, 1000,500,1000,500), 0.01f); }
@Test public void cursorAtStreamEdgeStopsAtImageEdge() { // fx 1.0 at scale 2 → wants child -1250, clamp -1000
    assertArrayEquals(new float[]{-1000,-250}, ZoomFollow.followTranslation(-500,-250,2f, 1f,0.5f, 1000,500,1000,500), 0.01f); }
```

- [ ] **Step 2: Run to verify they fail** — `--tests "com.limelight.binding.input.touch.TrackedCursorTest" --tests "com.limelight.utils.ZoomFollowTest"`; Expected: compilation FAIL.
- [ ] **Step 3: Implement `TrackedCursor` and `ZoomFollow`** per Interfaces.
- [ ] **Step 4: Run to verify they pass** — Expected: 11 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Add cursor tracking and follow maths for trackpad zoom"`

---

### Task 2: Pinch vs. two-finger arbitration

**Files:**
- Create: `app/src/main/java/com/limelight/binding/input/touch/PinchArbiter.java`
- Test: `app/src/test/java/com/limelight/binding/input/touch/PinchArbiterTest.java` (plain JUnit)

**Interfaces:**
- Produces: `enum Decision { UNDECIDED, PINCH, OTHER }`; `PinchArbiter(float thresholdPx)`; `void start(float x0, float y0, float x1, float y1)`; `Decision update(float x0, float y0, float x1, float y1)` — once PINCH or OTHER, sticky until next `start`. PINCH when `|span − startSpan| ≥ threshold` **before** the centroid has moved `≥ threshold`; OTHER when the centroid moves `≥ threshold` first. `float span(...)`, `float focusX/Y(...)` helpers static. Threshold used by `Game`: **24 px** (to be tuned on device, below the contexts' own scroll thresholds of 20/30 px *per finger*, which a pinch reaches at half the span rate).

- [ ] **Step 1: Write the failing tests**

```java
@Test public void spreadingFingersIsPinch() { PinchArbiter a = new PinchArbiter(24); a.start(400,500,600,500); assertEquals(UNDECIDED, a.update(390,500,610,500)); assertEquals(PINCH, a.update(385,500,615,500)); }
@Test public void movingTogetherIsOther() { PinchArbiter a = new PinchArbiter(24); a.start(400,500,600,500); assertEquals(OTHER, a.update(400,530,600,530)); }
@Test public void decisionIsSticky() { PinchArbiter a = new PinchArbiter(24); a.start(400,500,600,500); a.update(400,530,600,530); assertEquals(OTHER, a.update(300,530,700,530)); }
@Test public void startResets() { PinchArbiter a = new PinchArbiter(24); a.start(400,500,600,500); a.update(400,530,600,530); a.start(400,500,600,500); assertEquals(UNDECIDED, a.update(401,500,601,500)); }
```

- [ ] **Step 2: Run to verify they fail** — compilation FAIL.
- [ ] **Step 3: Implement `PinchArbiter`.**
- [ ] **Step 4: Run to verify they pass** — 4 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Tell a pinch from a two-finger scroll"`

---

### Task 3: `PanZoomHandler` as a `ZoomView`

**Files:**
- Create: `app/src/main/java/com/limelight/utils/ZoomView.java`
- Modify: `app/src/main/java/com/limelight/utils/PanZoomHandler.java`
- Test: `app/src/test/java/com/limelight/utils/PanZoomHandlerZoomViewTest.java` (Robolectric; `new PanZoomHandler(context, null, surface, parent, prefConfig)` with a `FrameLayout` parent 1000×500 and child `View` 1000×500 laid out; `new PreferenceConfiguration()` fields default)

**Interfaces:**
- Produces `interface ZoomView`: `float getScaleFactor()`, `float getChildX()`, `float getChildY()`, `float getViewWidth()`, `float getViewHeight()`, `float getParentWidth()`, `float getParentHeight()`, `void scaleAround(float factor, float focusX, float focusY)` (same maths as `ScaleListener.onScale`, clamped [1, 10], then `constrainToBounds()`), `void setTranslation(float x, float y)` (then `constrainToBounds()`), `void resetZoom()` (scale 1, `constrainToBounds()`).
- `ScaleListener.onScale` must call `scaleAround(...)` so the existing zoom mode keeps one code path. `onScaleEnd` keeps its `game.updatePipAutoEnter()` (null-guard it for the test).

- [ ] **Step 1: Write the failing tests**

```java
@Test public void scaleAroundClampsToTen() { h.scaleAround(50f, 500, 250); assertEquals(10f, h.getScaleFactor(), 0.01f); }
@Test public void scaleAroundKeepsFocusFixed() { h.scaleAround(2f, 0, 0); assertEquals(0f, h.getChildX(), 0.5f); assertEquals(0f, h.getChildY(), 0.5f); }
@Test public void setTranslationIsConstrained() { h.scaleAround(2f, 500, 250); h.setTranslation(100, -5000); assertEquals(0f, h.getChildX(), 0.5f); assertEquals(-500f, h.getChildY(), 0.5f); }
@Test public void resetZoomReturnsToOneX() { h.scaleAround(3f, 500, 250); h.resetZoom(); assertEquals(1f, h.getScaleFactor(), 0.01f); assertEquals(1f, surface.getScaleX(), 0.01f); }
```

- [ ] **Step 2: Run to verify they fail** — compilation FAIL.
- [ ] **Step 3: Implement `ZoomView` on `PanZoomHandler`** (refactor `onScale` onto `scaleAround`).
- [ ] **Step 4: Run to verify they pass** — 4 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Expose the stream zoom as a ZoomView"`

---

### Task 4: `TrackpadZoomController`

**Files:**
- Create: `app/src/main/java/com/limelight/binding/input/touch/MouseMover.java`
- Create: `app/src/main/java/com/limelight/binding/input/touch/TrackpadZoomController.java`
- Test: `app/src/test/java/com/limelight/binding/input/touch/TrackpadZoomControllerTest.java` (plain JUnit with a `FakeZoomView` implementing `ZoomView` arithmetically and a recording `PositionSender`)

**Interfaces:**
- Consumes: Task 1 (`TrackedCursor`, `ZoomFollow`), Task 2 (`PinchArbiter`), Task 3 (`ZoomView`).
- Produces:
  - `interface MouseMover { void move(short dx, short dy); }`
  - `interface TrackpadZoomController.PositionSender { void send(short x, short y, short refW, short refH); }`
  - `interface TrackpadZoomController.ContextCanceller { void cancelAll(); }` (Game cancels its touch contexts)
  - `TrackpadZoomController(ZoomView view, PositionSender sender, ContextCanceller canceller, int streamW, int streamH, float pinchThresholdPx)`; implements `MouseMover`: `cursor.move(dx, dy, view.getScaleFactor())` → `sender.send(...)` → `view.setTranslation(ZoomFollow.followTranslation(...))`.
  - `boolean onTouch(int actionMasked, int pointerCount, float x0, float y0, float x1, float y1)` — returns true when the event is consumed. Two pointers down → `arbiter.start`; while UNDECIDED or OTHER → false (contexts handle it); on first PINCH → `canceller.cancelAll()`, return true; while PINCH → `view.scaleAround(span/prevSpan, focus)`, return true; consume every event until all fingers are up (`ACTION_UP`, or `ACTION_CANCEL`), then reset. An `ACTION_DOWN` always starts a fresh gesture (reset, return false) even if no `ACTION_UP` was seen.
  - `void setTrackpadMode(boolean trackpad)` — false ⇒ `view.resetZoom()` and pinch disabled (`onTouch` always false).
  - `void onAbsolutePosition(float fx, float fy)` — `cursor.setFraction`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void moveSendsAbsoluteFromCentre() { c.move((short)10,(short)0); assertEquals(970, s.lastX); assertEquals(540, s.lastY); assertEquals(1920, s.lastW); }
@Test public void moveDividesByZoom() { v.scale=2f; c.move((short)10,(short)0); assertEquals(965, s.lastX); }
@Test public void moveFollowsWhenZoomed() { v.scale=4f; v.childX=-1500; c.move((short)2000,(short)0); assertTrue(v.childX < -1500); }
@Test public void pinchCancelsContextsAndZooms() { c.onTouch(ACTION_POINTER_DOWN,2, 400,500,600,500); boolean consumed = c.onTouch(ACTION_MOVE,2, 380,500,620,500); assertTrue(consumed); assertEquals(1, k.cancels); assertTrue(v.scale > 1f); }
@Test public void scrollIsLeftToContexts() { c.onTouch(ACTION_POINTER_DOWN,2, 400,500,600,500); assertFalse(c.onTouch(ACTION_MOVE,2, 400,530,600,530)); assertEquals(0, k.cancels); }
@Test public void pinchDoesNotMoveView() { v.scale=2f; v.childX=-500; c.onTouch(ACTION_POINTER_DOWN,2, 400,500,600,500); c.onTouch(ACTION_MOVE,2, 380,500,620,500); assertEquals(0, s.sends); }
@Test public void liftOneFingerAfterPinchDoesNotMoveCursor() { pinch(); assertTrue(c.onTouch(ACTION_POINTER_UP,2, 380,500,620,500)); assertTrue(c.onTouch(ACTION_MOVE,1, 300,500,0,0)); assertEquals(0, s.sends); assertFalse(c.onTouch(ACTION_DOWN,1, 300,500,0,0)); }
@Test public void secondFingerMidMoveThenPinchSendsNoScroll() { c.move((short)5,(short)0); c.onTouch(ACTION_POINTER_DOWN,2, 400,500,600,500); assertTrue(c.onTouch(ACTION_MOVE,2, 380,500,620,500)); assertEquals(1, k.cancels); }
@Test public void modeSwitchToTouchResetsZoom() { v.scale=3f; c.setTrackpadMode(false); assertEquals(1f, v.scale, 0.01f); assertFalse(c.onTouch(ACTION_POINTER_DOWN,2, 400,500,600,500)); }
@Test public void absoluteTouchResyncsCursor() { c.onAbsolutePosition(0.25f, 0.5f); c.move((short)0,(short)0); assertEquals(480, s.lastX); }
```

- [ ] **Step 2: Run to verify they fail** — compilation FAIL.
- [ ] **Step 3: Implement `MouseMover` and `TrackpadZoomController`.** A `move(0,0)` still sends (used by the resync test and harmless).
- [ ] **Step 4: Run to verify they pass** — 10 PASS.
- [ ] **Step 5: Commit** — `git commit -m "Drive trackpad zoom and cursor follow from one controller"`

---

### Task 5: Route trackpad moves through `MouseMover`

**Files:**
- Modify: `binding/input/touch/TrackpadContext.java` (the three `conn.sendMouseMove` calls: momentum `:89`, single-finger `:379`, drag `:386`)
- Modify: `binding/input/touch/RelativeTouchContext.java` (`:274-283`: mover wins over `absoluteMouseMode` and relative send; mover receives the **already sensitivity-scaled** deltas)
- Modify: `binding/input/touch/AbsoluteTouchContext.java` (`:129`: after `sendMousePosition`, notify an optional `AbsolutePositionObserver { void onAbsolutePosition(float fx, float fy); }` with `eventX/width`, `eventY/height`)
- Test: `app/src/test/java/com/limelight/binding/input/touch/TouchContextMoverTest.java` (Robolectric; `NvConnection` via `Mockito.mock`)

**Interfaces:**
- Consumes: `MouseMover` (Task 4).
- Produces: `void setMouseMover(MouseMover mover)` on `TrackpadContext` and `RelativeTouchContext` (null = today's behaviour); `void setPositionObserver(AbsolutePositionObserver o)` on `AbsoluteTouchContext`; `interface AbsolutePositionObserver` in `binding/input/touch/`.

- [ ] **Step 1: Write the failing tests** — `trackpadMoveGoesThroughMover` (single finger down + move on `TrackpadContext` → mover recorded, `verify(conn, never()).sendMouseMove(anyShort(), anyShort())`), `flickMomentumGoesThroughMover` (fast flick, then `ShadowLooper.idleMainLooper(100ms)` → mover received momentum moves, conn never), `gamingMoveGoesThroughMoverEvenInAbsoluteMode` (`prefConfig.absoluteMouseMode = true`, mover set → mover recorded, `verify(conn, never()).sendMouseMoveAsMousePosition(...)`), `noMoverKeepsRelativeSend` (mover null → `verify(conn).sendMouseMove(...)`), `absoluteTouchNotifiesObserver` (touch at 250,100 on a 1000×500 view → observer gets 0.25, 0.2).
- [ ] **Step 2: Run to verify they fail** — compilation FAIL.
- [ ] **Step 3: Implement the hooks.**
- [ ] **Step 4: Run to verify they pass** — 5 PASS; also run the whole touch package tests.
- [ ] **Step 5: Commit** — `git commit -m "Let trackpad contexts send moves through a MouseMover"`

---

### Task 6: Wire it into `Game`, settings and docs

**Files:**
- Modify: `app/src/main/res/xml/preferences.xml:475` — `CheckBoxPreference checkbox_trackpad_pinch_zoom`, default false, after `checkbox_absolute_mouse_mode`.
- Modify: `preferences/PreferenceConfiguration.java` — `CHECKBOX_TRACKPAD_PINCH_ZOOM`, `DEFAULT_TRACKPAD_PINCH_ZOOM = false`, `public boolean trackpadPinchZoom`.
- Modify: `Game.java`:
  - create `TrackpadZoomController trackpadZoom` after `panZoomHandler` (`:509`) when `prefConfig.trackpadPinchZoom`, with `sender = (x,y,w,h) -> conn.sendMousePosition(...)` (null-check `conn`), canceller = `cancelTouch()` on every non-null `touchContextMap` entry, `streamW/H = prefConfig.width/height`, threshold 24.
  - `applyMouseMode(int mode)` (`:4429`) and touch-context creation (`:4450`): when `trackpadZoom != null`, `setMouseMover(trackpadZoom)` on `TrackpadContext` / `RelativeTouchContext`, `setPositionObserver(trackpadZoom::onAbsolutePosition)` on `AbsoluteTouchContext`, and `trackpadZoom.setTrackpadMode(mode == 2 || mode == 3)` after the contexts exist.
  - finger branch (`:3273`, before `if (isPanZoomMode)`): if `trackpadZoom != null && !isPanZoomMode` and `trackpadZoom.onTouch(actionMasked, pointerCount, x0, y0, x1, y1)` → `return true` (pointer 1 coords = 0 when one finger).
- Modify: `values/strings.xml`, `values-fr/strings.xml`: `title_trackpad_pinch_zoom` "Pinch to zoom (trackpad)" / "Zoom au pincement (trackpad)"; `summary_trackpad_pinch_zoom` "In trackpad modes, pinch to zoom the stream; the view follows the cursor. The app sends the cursor position, so Windows mouse acceleration no longer applies." / "En mode trackpad, pincer zoome le stream et la vue suit le curseur. L\'appli envoie la position du curseur : l\'accélération de la souris de Windows ne s\'applique plus."
- Modify: `CLAUDE.md` — new section `## 🔍 Zoom au pincement en mode trackpad (Android)`: ⚠️ cursor owned by the app (absolute positions) and why (Windows acceleration drifts any estimate); ⚠️ `getNormalizedCoordinates` reads the container's scale (possible pre-existing bug, out of scope); 📋 follow only on cursor moves; 📋 option off = no mover, no interception.

- [ ] **Step 1: Make the edits above.**
- [ ] **Step 2: Build + full JVM suite** — `.\gradlew.bat assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest --continue`; Expected: APKs built; only the 5 known failures.
- [ ] **Step 3: Release lint** — `.\gradlew.bat lintVitalNonRoot_gameRelease`; Expected: success.
- [ ] **Step 4: Manual check on device** (Lavagnou): option on, trackpad natural then gaming — pinch zooms around the fingers; one finger moves the cursor and the view follows once it leaves the central half; two-finger scroll and two-finger tap still work at 1× and zoomed; switch to a touch mode in the in-game menu → back to full screen and no pinch; option off → everything as before.
- [ ] **Step 5: Commit** — `git commit -m "Pinch to zoom in trackpad modes, with the view following the cursor"`
