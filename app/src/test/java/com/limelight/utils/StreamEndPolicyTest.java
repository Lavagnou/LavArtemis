package com.limelight.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** « Quitter l'appli sur le PC distant à la fin »: the desktop's rule (Session, quitAppAfter) */
public class StreamEndPolicyTest {
    @Test
    public void quitSessionAlwaysQuits() {
        assertTrue(StreamEndPolicy.quitHostApp(true, false, false, false, false));
        assertTrue(StreamEndPolicy.quitHostApp(true, false, true, true, true));
    }

    @Test
    public void settingOffLeavesTheGameRunning() {
        assertFalse(StreamEndPolicy.quitHostApp(false, false, false, false, false));
    }

    @Test
    public void settingOnQuitsAfterANormalEnd() {
        assertTrue(StreamEndPolicy.quitHostApp(false, true, false, false, false));
    }

    @Test
    public void anErrorLeavesTheGameRunning() {
        assertFalse(StreamEndPolicy.quitHostApp(false, true, true, false, false));
    }

    @Test
    public void neitherBenchmarkNorReconnection() {
        assertFalse(StreamEndPolicy.quitHostApp(false, true, false, true, false));
        assertFalse(StreamEndPolicy.quitHostApp(false, true, false, false, true));
    }
}
