package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A held direction moves at once, again 380 ms later, then every 110 ms (prototype pollPads). */
public class DirRepeatTest {
    private static final int UP = 19, DOWN = 20, LEFT = 21, RIGHT = 22;

    @Test
    public void firstThenFasterRepeats() {
        DirRepeat r = new DirRepeat();
        assertTrue(r.press(DOWN, 1000));
        assertEquals(380, r.delay(1000));
        assertFalse(r.due(1379));
        assertTrue(r.due(1380));
        assertEquals(110, r.delay(1380));
        assertFalse(r.due(1489));
        assertTrue(r.due(1490));
        assertTrue(r.due(1600));
    }

    @Test
    public void systemRepeatsDoNotMove() {
        DirRepeat r = new DirRepeat();
        assertTrue(r.press(DOWN, 0));
        // The system's own repeats of the same key while it is held
        assertFalse(r.press(DOWN, 50));
        assertFalse(r.press(DOWN, 500));
    }

    @Test
    public void releaseStopsAndAnotherDirectionTakesOver() {
        DirRepeat r = new DirRepeat();
        r.press(DOWN, 0);
        assertTrue(r.press(UP, 100));
        assertEquals(UP, r.held());
        // Releasing the direction no longer moving changes nothing
        r.release(DOWN, 200);
        assertEquals(UP, r.held());
        r.release(UP, 300);
        assertEquals(0, r.held());
        assertFalse(r.due(10_000));
        assertEquals(-1, r.delay(10_000));
        // Pressed again after release: moves at once
        assertTrue(r.press(UP, 20_000));
    }

    @Test
    public void releasingTheMovingDirectionHandsTheRepeatToOneStillHeld() {
        // E5: Right held, Down pressed then released: Right repeats again, after the first delay
        DirRepeat r = new DirRepeat();
        assertTrue(r.press(RIGHT, 0));
        assertTrue(r.press(DOWN, 1000));
        assertEquals(DOWN, r.held());
        r.release(DOWN, 2000);
        assertEquals(RIGHT, r.held());
        assertFalse(r.due(2000));
        assertEquals(380, r.delay(2000));
        assertTrue(r.due(2380));
        // Up held, Down pressed and released: Up again
        DirRepeat v = new DirRepeat();
        v.press(UP, 0);
        v.press(DOWN, 10);
        v.release(DOWN, 20);
        assertEquals(UP, v.held());
    }

    @Test
    public void aDiagonalMovesVerticallyFirst() {
        // E3: Down held, Right pressed: Right does not move, Down keeps repeating
        DirRepeat r = new DirRepeat();
        assertTrue(r.press(DOWN, 0));
        assertFalse(r.press(RIGHT, 10));
        assertEquals(DOWN, r.held());
        assertTrue(r.due(380));
        // Down released: Right takes over, after the first delay
        r.release(DOWN, 400);
        assertEquals(RIGHT, r.held());
        assertEquals(380, r.delay(400));
        // Left held, Up pressed: Up moves at once and repeats
        DirRepeat h = new DirRepeat();
        assertTrue(h.press(LEFT, 0));
        assertTrue(h.press(UP, 5));
        assertEquals(UP, h.held());
    }

    @Test
    public void cancelStops() {
        DirRepeat r = new DirRepeat();
        r.press(DOWN, 0);
        r.cancel();
        assertFalse(r.due(1000));
    }
}
