package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Reference table shared with the desktop client (LavArtemis-Qt tests/hostdisplay)
public class HostDisplayFitTest {
    private static void assertMode(int w, int h, int fps, HostDisplayFit.Mode m) {
        assertEquals(w, m.width);
        assertEquals(h, m.height);
        assertEquals(fps, m.fps);
    }

    @Test
    public void hostSmallerThanSetting() {
        assertMode(1920, 1080, 60, HostDisplayFit.fit(1920, 1080, 60, 2400, 1080, 90));
    }

    @Test
    public void phoneSessionOnLaptop() {
        assertMode(1920, 864, 60, HostDisplayFit.fit(2400, 1080, 90, 1920, 1080, 60));
    }

    @Test
    public void fpsCappedByHost() {
        assertMode(1920, 864, 90, HostDisplayFit.fit(2400, 1080, 90, 1920, 1080, 120));
    }

    @Test
    public void neverUpscale() {
        assertMode(1280, 720, 60, HostDisplayFit.fit(1280, 720, 60, 1920, 1080, 60));
    }

    @Test
    public void fourKIntoFullHd() {
        assertMode(1920, 1080, 60, HostDisplayFit.fit(3840, 2160, 60, 1920, 1080, 60));
    }

    @Test
    public void roundsDownToEven() {
        assertMode(1280, 718, 60, HostDisplayFit.fit(1366, 768, 60, 1280, 720, 60));
    }

    @Test
    public void vrrDisplayKeepsClientFps() {
        assertMode(1920, 1080, 90, HostDisplayFit.fit(1920, 1080, 1000, 1920, 1080, 90));
    }

    @Test
    public void appliesWhenResumingEvenWithVirtualDisplay() {
        assertTrue(HostDisplayFit.applies(true, true));
    }

    @Test
    public void appliesWhenLaunchingWithoutVirtualDisplay() {
        assertTrue(HostDisplayFit.applies(false, false));
    }

    @Test
    public void notWhenLaunchingWithVirtualDisplay() {
        assertFalse(HostDisplayFit.applies(false, true));
    }
}
