package com.limelight.binding.input;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class VoicePttButtonPositionTest {
    @Test
    public void unsetGoesBottomRightWithMargin() {
        assertArrayEquals(new float[]{1920 - 150 - 42, 1080 - 150 - 42},
                VoicePttButtonPosition.toPixels(-1f, -1f, 1920, 1080, 150, 42), 0.01f);
    }

    @Test
    public void roundTripKeepsPosition() {
        float[] f = VoicePttButtonPosition.toFraction(300f, 200f, 1920, 1080, 150);
        assertArrayEquals(new float[]{300f, 200f},
                VoicePttButtonPosition.toPixels(f[0], f[1], 1920, 1080, 150, 42), 0.5f);
    }

    @Test
    public void fractionStaysOnScreenAfterRotation() {
        // Bottom-right corner in landscape...
        float[] f = VoicePttButtonPosition.toFraction(1770f, 930f, 1920, 1080, 150);
        // ...must still be on screen in portrait
        float[] p = VoicePttButtonPosition.toPixels(f[0], f[1], 1080, 1920, 150, 42);
        assertTrue(p[0] >= 0 && p[0] <= 1080 - 150);
        assertTrue(p[1] >= 0 && p[1] <= 1920 - 150);
    }

    @Test
    public void offScreenInputIsClamped() {
        assertArrayEquals(new float[]{1f, 0f},
                VoicePttButtonPosition.toFraction(5000f, -40f, 1920, 1080, 150), 0.0001f);
    }

    @Test
    public void parentSmallerThanButtonGivesZero() {
        assertArrayEquals(new float[]{0f, 0f},
                VoicePttButtonPosition.toFraction(10f, 10f, 100, 100, 150), 0.0001f);
    }

    @Test
    public void pixelsNeverOffScreenWhenParentSmallerThanButton() {
        float[] p = VoicePttButtonPosition.toPixels(-1f, -1f, 100, 100, 150, 42);
        assertArrayEquals(new float[]{0f, 0f}, p, 0.0001f);
    }
}
