package com.limelight.binding.input.touch;

import com.limelight.utils.ZoomView;

import org.junit.Before;
import org.junit.Test;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackpadZoomControllerTest {
    /** Arithmetic stand-in for PanZoomHandler: view and parent 1000x500. */
    static final class FakeZoomView implements ZoomView {
        float scale = 1f, childX, childY, viewW = 1000;
        @Override public float getScaleFactor() { return scale; }
        @Override public float getChildX() { return childX; }
        @Override public float getChildY() { return childY; }
        @Override public float getViewWidth() { return viewW; }
        @Override public float getViewHeight() { return 500; }
        @Override public float getParentWidth() { return 1000; }
        @Override public float getParentHeight() { return 500; }
        @Override public void scaleAround(float factor, float fx, float fy) { scale = Math.max(1f, Math.min(10f, scale * factor)); }
        @Override public void setTranslation(float x, float y) { childX = x; childY = y; }
        @Override public void resetZoom() { scale = 1f; childX = 0; childY = 0; }
    }

    static final class RecordingSender implements TrackpadZoomController.PositionSender {
        int sends; short lastX, lastY, lastW, lastH;
        @Override public void send(short x, short y, short w, short h) { sends++; lastX = x; lastY = y; lastW = w; lastH = h; }
    }

    static final class CountingCanceller implements TrackpadZoomController.ContextCanceller {
        int cancels;
        @Override public void cancelAll() { cancels++; }
    }

    private FakeZoomView v;
    private RecordingSender s;
    private CountingCanceller k;
    private TrackpadZoomController c;

    @Before
    public void setUp() {
        v = new FakeZoomView();
        s = new RecordingSender();
        k = new CountingCanceller();
        c = new TrackpadZoomController(v, s, k, 1920, 1080, 24);
        c.setTrackpadMode(true);
    }

    @Test
    public void moveSendsAbsoluteFromCentre() {
        c.move((short) 10, (short) 0);
        assertEquals(979, s.lastX);       // 960 + 10 * 1920 / 1000
        assertEquals(540, s.lastY);
        assertEquals(1920, s.lastW);
        assertEquals(1080, s.lastH);
    }

    @Test
    public void screenDeltaIsDividedByZoomOnce() {
        // Trackpad moves arrive in screen pixels (Game skips the zoomed-view normalization for them,
        // which made a still finger move whenever the view panned): the zoom is applied here, once
        v.scale = 2f;
        c.move((short) 10, (short) 0);
        assertEquals(970, s.lastX);       // 960 + 10 * 1920 / (1000 * 2)
    }

    @Test
    public void moveFollowsWhenZoomed() {
        v.scale = 4f;
        v.childX = -1500;
        c.move((short) 2000, (short) 0);
        assertTrue(v.childX < -1500);
    }

    @Test
    public void moveIsOneToOneOnScreen() {
        v.scale = 2f;
        c.move((short) 100, (short) 0);
        assertEquals(1056, s.lastX);      // 100 screen px = 100 * 1920 / (1000 * 2) stream px
    }

    @Test
    public void conversionUsesCurrentViewSize() {
        c.move((short) 100, (short) 0);   // +192
        v.viewW = 500;                    // rotated: the image is now half as wide on screen
        c.move((short) 100, (short) 0);   // +384
        assertEquals(960 + 192 + 384, s.lastX);
    }

    @Test
    public void pinchCancelsContextsAndZooms() {
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        boolean consumed = c.onTouch(ACTION_MOVE, 2, 380, 500, 620, 500);
        assertTrue(consumed);
        assertEquals(1, k.cancels);
        assertTrue(v.scale > 1f);
    }

    @Test
    public void scrollIsLeftToContexts() {
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        assertFalse(c.onTouch(ACTION_MOVE, 2, 400, 530, 600, 530));
        assertEquals(0, k.cancels);
    }

    @Test
    public void pinchDoesNotMoveView() {
        v.scale = 2f;
        v.childX = -500;
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        c.onTouch(ACTION_MOVE, 2, 380, 500, 620, 500);
        assertEquals(0, s.sends);
        assertEquals(-500f, v.childX, 0.01f);
    }

    @Test
    public void liftOneFingerAfterPinchDoesNotMoveCursor() {
        pinch();
        assertTrue(c.onTouch(ACTION_POINTER_UP, 2, 380, 500, 620, 500));
        assertTrue(c.onTouch(ACTION_MOVE, 1, 300, 500, 0, 0));
        assertEquals(0, s.sends);
        assertFalse(c.onTouch(ACTION_DOWN, 1, 300, 500, 0, 0));
    }

    @Test
    public void secondFingerMidMoveThenPinchSendsNoScroll() {
        c.move((short) 5, (short) 0);
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        assertTrue(c.onTouch(ACTION_MOVE, 2, 380, 500, 620, 500));
        assertEquals(1, k.cancels);
    }

    @Test
    public void modeSwitchToTouchResetsZoom() {
        v.scale = 3f;
        c.setTrackpadMode(false);
        assertEquals(1f, v.scale, 0.01f);
        assertFalse(c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500));
    }

    @Test
    public void absoluteTouchResyncsCursor() {
        c.onAbsolutePosition(0.25f, 0.5f);
        c.move((short) 0, (short) 0);
        assertEquals(480, s.lastX);
    }

    @Test
    public void threeFingersAreLeftToContexts() {
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        assertFalse(c.onTouch(ACTION_POINTER_DOWN, 3, 400, 500, 600, 500));
        assertFalse(c.onTouch(ACTION_MOVE, 2, 300, 500, 700, 500));
        assertEquals(0, k.cancels);
    }

    @Test
    public void fingerPutBackMidPinchDoesNotJumpZoom() {
        pinch();                                                  // fingers 240 px apart
        float zoomed = v.scale;
        c.onTouch(ACTION_POINTER_UP, 2, 380, 500, 620, 500);
        c.onTouch(ACTION_POINTER_DOWN, 2, 380, 500, 480, 500);   // put back 100 px away
        c.onTouch(ACTION_MOVE, 2, 380, 500, 480, 500);           // first move of the new pair
        assertEquals(zoomed, v.scale, 0.01f);
    }

    @Test
    public void undecidedTwoFingerMovesAreHeldBack() {
        // Until the gesture is known, the contexts must not see moves: a gaming-mode context
        // would already be scrolling
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        assertTrue(c.onTouch(ACTION_MOVE, 2, 395, 500, 605, 500));
        assertEquals(0, k.cancels);
    }

    @Test
    public void setStreamSizeUsesNewSize() {
        c.setStreamSize(1280, 720);
        c.move((short) 0, (short) 0);
        assertEquals(1280, s.lastW);
        assertEquals(720, s.lastH);
        assertEquals(640, s.lastX);
    }

    private void pinch() {
        c.onTouch(ACTION_POINTER_DOWN, 2, 400, 500, 600, 500);
        c.onTouch(ACTION_MOVE, 2, 380, 500, 620, 500);
    }
}
