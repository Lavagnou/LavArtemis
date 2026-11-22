package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

// Same cases as the desktop client's tests/hostdisplay
public class HostDisplayWatchTest {
    @Test
    public void scaledButStableDoesNotTrigger() {
        // Streaming a 2400x1080 host at 1920x864 is the fitted state, not a change to chase
        HostDisplayWatch w = new HostDisplayWatch(1920, 1080, 60, 1920, 864, 60);
        int[] host = {2400, 1080, 90};
        assertNull(w.onPoll(host));
        assertNull(w.onPoll(host));
        assertNull(w.onPoll(host));
    }

    @Test
    public void triggersAfterTwoConsecutive() {
        HostDisplayWatch w = new HostDisplayWatch(1920, 1080, 60, 1920, 864, 60);
        int[] host = {1920, 1080, 60};
        assertNull(w.onPoll(host));
        assertEquals(new HostDisplayFit.Mode(1920, 1080, 60), w.onPoll(host));
    }

    @Test
    public void changingTargetRestartsDebounce() {
        // Windows passes through an intermediate mode: only the settled one counts
        HostDisplayWatch w = new HostDisplayWatch(1920, 1080, 60, 1920, 1080, 60);
        assertNull(w.onPoll(new int[]{1280, 720, 60}));
        assertNull(w.onPoll(new int[]{1600, 900, 60}));
        assertEquals(new HostDisplayFit.Mode(1600, 900, 60), w.onPoll(new int[]{1600, 900, 60}));
    }

    @Test
    public void absentModeResets() {
        HostDisplayWatch w = new HostDisplayWatch(1920, 1080, 60, 1920, 1080, 60);
        assertNull(w.onPoll(new int[]{1280, 720, 60}));
        assertNull(w.onPoll(null));
        assertNull(w.onPoll(new int[]{1280, 720, 60}));
        assertEquals(new HostDisplayFit.Mode(1280, 720, 60), w.onPoll(new int[]{1280, 720, 60}));
    }

    @Test
    public void firesOnce() {
        HostDisplayWatch w = new HostDisplayWatch(1920, 1080, 60, 1920, 1080, 60);
        int[] host = {1280, 720, 60};
        w.onPoll(host);
        w.onPoll(host);
        assertNull(w.onPoll(host));
        assertNull(w.onPoll(host));
    }
}
