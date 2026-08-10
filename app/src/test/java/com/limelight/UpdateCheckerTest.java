package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.limelight.utils.UpdateChecker;

import org.junit.Test;

/**
 * Unit tests for the semantic version comparison used by the in-app updater.
 * No Android runtime or JNI involved, so no shadows are required.
 */
public class UpdateCheckerTest {

    @Test
    public void equalVersions() {
        assertEquals(0, UpdateChecker.compareVersions("20.4.0", "20.4.0"));
    }

    @Test
    public void newerPatch() {
        assertTrue(UpdateChecker.compareVersions("20.4.0", "20.4.1") < 0);
        assertTrue(UpdateChecker.compareVersions("20.4.1", "20.4.0") > 0);
    }

    @Test
    public void newerMinor() {
        assertTrue(UpdateChecker.compareVersions("20.4.9", "20.5.0") < 0);
    }

    @Test
    public void newerMajor() {
        assertTrue(UpdateChecker.compareVersions("20.9.9", "21.0.0") < 0);
    }

    @Test
    public void missingComponentsAreZero() {
        assertEquals(0, UpdateChecker.compareVersions("20.4", "20.4.0"));
        assertTrue(UpdateChecker.compareVersions("20.4", "20.4.1") < 0);
    }

    @Test
    public void stableIsNewerThanPrereleaseWithSameBase() {
        assertTrue(UpdateChecker.compareVersions("20.4.0", "20.4.0-ci.42") > 0);
        assertTrue(UpdateChecker.compareVersions("20.4.0-ci.42", "20.4.0") < 0);
    }

    @Test
    public void prereleaseBuildNumbersAreOrdered() {
        assertTrue(UpdateChecker.compareVersions("20.4.0-ci.41", "20.4.0-ci.42") < 0);
        assertTrue(UpdateChecker.compareVersions("20.4.0-ci.42", "20.4.0-ci.41") > 0);
        assertEquals(0, UpdateChecker.compareVersions("20.4.0-ci.42", "20.4.0-ci.42"));
    }

    @Test
    public void prereleaseWithHigherBaseWins() {
        assertTrue(UpdateChecker.compareVersions("20.3.9", "20.4.0-ci.1") < 0);
        assertTrue(UpdateChecker.compareVersions("20.4.0-ci.1", "20.3.9") > 0);
    }

    @Test
    public void multiDigitComponents() {
        assertTrue(UpdateChecker.compareVersions("9.9.9", "10.0.0") < 0);
        assertTrue(UpdateChecker.compareVersions("20.10.0", "20.9.0") > 0);
    }

    @Test
    public void malformedComponentsAreTolerated() {
        // Should not throw, garbage parses as 0
        assertEquals(0, UpdateChecker.compareVersions("x.y.z", "0.0.0"));
    }
}
