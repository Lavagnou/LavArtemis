package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

// Parent 1000x500, view 1000x500 throughout
public class ZoomFollowTest {
    @Test
    public void noFollowAtOneX() {
        assertArrayEquals(new float[]{0, 0},
                ZoomFollow.followTranslation(0, 0, 1f, 0.99f, 0.99f, 1000, 500, 1000, 500), 0.01f);
    }

    @Test
    public void insideCalmZoneDoesNotMove() {
        // scale 2, child -500,-250: the cursor at 0.5 sits at 500,250 on screen
        assertArrayEquals(new float[]{-500, -250},
                ZoomFollow.followTranslation(-500, -250, 2f, 0.5f, 0.5f, 1000, 500, 1000, 500), 0.01f);
    }

    @Test
    public void pastRightEdgeShiftsByOvershootOnly() {
        // fx 0.65 -> screen -500 + 1300 = 800, limit 750 -> child -550
        assertArrayEquals(new float[]{-550, -250},
                ZoomFollow.followTranslation(-500, -250, 2f, 0.65f, 0.5f, 1000, 500, 1000, 500), 0.01f);
    }

    @Test
    public void pastTopEdgeShiftsDown() {
        // fy 0.3 -> screen -250 + 300 = 50, limit 125 -> child -175
        assertArrayEquals(new float[]{-500, -175},
                ZoomFollow.followTranslation(-500, -250, 2f, 0.5f, 0.3f, 1000, 500, 1000, 500), 0.01f);
    }

    @Test
    public void cursorAtStreamEdgeStopsAtImageEdge() {
        // fx 1.0 at scale 2 wants child -1250; the image edge is -1000
        assertArrayEquals(new float[]{-1000, -250},
                ZoomFollow.followTranslation(-500, -250, 2f, 1f, 0.5f, 1000, 500, 1000, 500), 0.01f);
    }
}
