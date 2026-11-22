package com.limelight.utils;

import com.limelight.preferences.PreferenceConfiguration.ScaleMode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RotationPolicyTest {
    @Test
    public void followNeverInverts() {
        assertFalse(RotationPolicy.shouldInvertResolution(true, true, true));
    }

    @Test
    public void withoutFollowKeepsTodayInversion() {
        assertTrue(RotationPolicy.shouldInvertResolution(false, true, true));
        assertFalse(RotationPolicy.shouldInvertResolution(false, false, true));
        assertFalse(RotationPolicy.shouldInvertResolution(false, true, false));
    }

    @Test
    public void portraitFollowForcesFit() {
        assertEquals(ScaleMode.FIT, RotationPolicy.effectiveScaleMode(ScaleMode.STRETCH, true, true));
        assertEquals(ScaleMode.FIT, RotationPolicy.effectiveScaleMode(ScaleMode.FILL, true, true));
    }

    @Test
    public void stretchComesBackInLandscape() {
        assertEquals(ScaleMode.STRETCH, RotationPolicy.effectiveScaleMode(ScaleMode.STRETCH, true, false));
    }

    @Test
    public void noFollowKeepsChosen() {
        assertEquals(ScaleMode.STRETCH, RotationPolicy.effectiveScaleMode(ScaleMode.STRETCH, false, true));
    }
}
