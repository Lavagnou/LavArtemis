package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.limelight.utils.UpdateChecker;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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

    private static final List<String> BOTH_APKS = Arrays.asList(
            "LavArtemis-v20.9.0-android-arm64-v8a.apk",
            "LavArtemis-v20.9.0-android-armeabi-v7a.apk",
            "LavArtemis-v20.9.0-win-installer.exe");

    @Test
    public void picksArm64On64BitDevice() {
        assertEquals(0, UpdateChecker.pickAsset(BOTH_APKS, new String[]{"arm64-v8a", "armeabi-v7a", "armeabi"}));
    }

    @Test
    public void picks32BitApkOnTvStick() {
        // A Fire TV Stick 4K Max reports only 32-bit ABIs; the arm64 APK cannot install there.
        assertEquals(1, UpdateChecker.pickAsset(BOTH_APKS, new String[]{"armeabi-v7a", "armeabi"}));
    }

    @Test
    public void fallsBackToLaterAbiWhenPreferredIsMissing() {
        List<String> onlyV7 = Arrays.asList("LavArtemis-v20.9.0-android-armeabi-v7a.apk");
        assertEquals(0, UpdateChecker.pickAsset(onlyV7, new String[]{"arm64-v8a", "armeabi-v7a"}));
    }

    @Test
    public void noCompatibleApk() {
        assertEquals(-1, UpdateChecker.pickAsset(BOTH_APKS, new String[]{"x86_64", "x86"}));
        List<String> onlyArm64 = Arrays.asList("LavArtemis-v20.9.0-android-arm64-v8a.apk");
        assertEquals(-1, UpdateChecker.pickAsset(onlyArm64, new String[]{"armeabi-v7a", "armeabi"}));
        assertEquals(-1, UpdateChecker.pickAsset(BOTH_APKS, null));
    }

    @Test
    public void autoCheckIsThrottled() {
        long hour = 60L * 60 * 1000;
        long now = 1_000_000_000_000L;
        assertTrue(UpdateChecker.isDue(0, now));
        assertFalse(UpdateChecker.isDue(now - 11 * hour, now));
        assertTrue(UpdateChecker.isDue(now - 12 * hour, now));
        // Clock moved backwards: check rather than go silent until it catches up
        assertTrue(UpdateChecker.isDue(now + hour, now));
    }
    @Test
    public void devVersionsAreRecognised() {
        assertTrue(UpdateChecker.isDevVersion("21.8.0-dev.1"));
        assertTrue(UpdateChecker.isDevVersion("v21.8.0-dev.12"));
        assertFalse(UpdateChecker.isDevVersion("21.8.0"));
        assertFalse(UpdateChecker.isDevVersion("21.8.0-ci.40"));
        assertFalse(UpdateChecker.isDevVersion(null));
    }

    @Test
    public void devBuildsCompareByNumber() {
        assertTrue(UpdateChecker.compareVersions("21.8.0-dev.2", "21.8.0-dev.10") < 0);
        assertTrue(UpdateChecker.compareVersions("21.8.0-dev.9", "21.9.0-dev.1") < 0);
        assertEquals(0, UpdateChecker.compareVersions("21.8.0-dev.3", "21.8.0-dev.3"));
    }

    @Test
    public void devChannelSkipsStableCiAndDrafts() {
        JsonArray releases = JsonParser.parseString("["
                + "{\"tag_name\":\"v21.8.0-dev.4\",\"draft\":true},"
                + "{\"tag_name\":\"v21.7.2\",\"draft\":false},"
                + "{\"tag_name\":\"v21.7.2-ci.80\",\"draft\":false},"
                + "{\"tag_name\":\"v21.8.0-dev.3\",\"draft\":false},"
                + "{\"tag_name\":\"v21.8.0-dev.2\",\"draft\":false}"
                + "]").getAsJsonArray();
        assertEquals(3, UpdateChecker.pickDevRelease(releases));
    }

    @Test
    public void devChannelWithoutDevReleaseFindsNothing() {
        JsonArray releases = JsonParser.parseString(
                "[{\"tag_name\":\"v21.7.1\"},{\"tag_name\":\"v21.7.0\"}]").getAsJsonArray();
        assertEquals(-1, UpdateChecker.pickDevRelease(releases));
    }
}
