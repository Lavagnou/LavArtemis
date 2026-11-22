package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

public class TouchSpaceTest {
    @Test
    public void streamSpaceFollowsTheZoomedView() {
        // Absolute touch: a finger at 600 on a 2x view placed at -200 points at 400 in the view
        assertArrayEquals(new float[]{400, 100},
                TouchSpace.forContexts(600, 300, -200, 100, 2f, 2f, false), 0.01f);
    }

    @Test
    public void panningMovesAStillFingerInStreamSpace() {
        // The feedback the trackpad must not see: the view slides, the finger does not move
        float before = TouchSpace.forContexts(600, 300, -200, 100, 2f, 2f, false)[0];
        float after = TouchSpace.forContexts(600, 300, -260, 100, 2f, 2f, false)[0];
        assertNotEquals(before, after, 0.01f);
    }

    @Test
    public void screenSpaceIgnoresPanAndZoom() {
        assertArrayEquals(new float[]{600, 300},
                TouchSpace.forContexts(600, 300, -200, 100, 2f, 2f, true), 0.01f);
        assertArrayEquals(new float[]{600, 300},
                TouchSpace.forContexts(600, 300, -260, 40, 3f, 3f, true), 0.01f);
    }
}
