package com.limelight.binding.input.touch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TrackedCursorTest {
    @Test
    public void startsAtCentre() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        assertEquals(960, c.getX());
        assertEquals(540, c.getY());
    }

    @Test
    public void moveDividesByZoom() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        c.move(100, 40, 4f);
        assertEquals(985, c.getX());
        assertEquals(550, c.getY());
    }

    @Test
    public void fractionalMovesAccumulate() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        for (int i = 0; i < 4; i++) {
            c.move(1, 0, 4f);
        }
        assertEquals(961, c.getX());
    }

    @Test
    public void clampsToStream() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        c.move(-5000, 5000, 1f);
        assertEquals(0, c.getX());
        assertEquals(1080, c.getY());
    }

    @Test
    public void zoomBelowOneCountsAsOne() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        c.move(10, 0, 0.5f);
        assertEquals(970, c.getX());
    }

    @Test
    public void setFractionClamps() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        c.setFraction(1.5f, -1f);
        assertEquals(1920, c.getX());
        assertEquals(0, c.getY());
    }

    @Test
    public void resizeKeepsFraction() {
        TrackedCursor c = new TrackedCursor(1920, 1080);
        c.setFraction(0.25f, 0.5f);
        c.resize(1280, 720);
        assertEquals(320, c.getX());
        assertEquals(360, c.getY());
        assertEquals(1280, c.getWidth());
    }
}
