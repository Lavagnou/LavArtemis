package com.limelight.v2.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** First launch of the Dev: native resolution and refresh rate, written once, never over a choice */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, qualifiers = "w915dp-h412dp-xxhdpi")
public class NativeDisplayDefaultsTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        prefs().edit().clear().commit();
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    @Test
    public void resolutionIsLandscape() {
        assertEquals("2400x1080", NativeDisplayDefaults.resolution(1080, 2400));
        assertEquals("2400x1080", NativeDisplayDefaults.resolution(2400, 1080));
        assertEquals("1920x1080", NativeDisplayDefaults.resolution(1920, 1080));
    }

    @Test
    public void frameRateIsTheHighestRounded() {
        assertEquals("120", NativeDisplayDefaults.frameRate(60f, 119.88f, 90f));
        assertEquals("60", NativeDisplayDefaults.frameRate(59.94f));
        assertEquals("144", NativeDisplayDefaults.frameRate(0f, 144f));
        assertEquals("60", NativeDisplayDefaults.frameRate(0f));
        assertEquals("60", NativeDisplayDefaults.frameRate());
    }

    @Test
    public void firstLaunchWritesTheScreensMode() {
        NativeDisplayDefaults.applyOnce(ctx);
        String res = prefs().getString(NativeDisplayDefaults.KEY_RESOLUTION, null);
        String fps = prefs().getString(NativeDisplayDefaults.KEY_FPS, null);
        assertTrue(res, res != null && res.matches("\\d+x\\d+"));
        String[] wh = res.split("x");
        assertTrue(res, Integer.parseInt(wh[0]) >= Integer.parseInt(wh[1]));
        assertTrue(fps, fps != null && Integer.parseInt(fps) > 0);
    }

    @Test
    public void aChoiceIsNeverOverwritten() {
        prefs().edit().putString(NativeDisplayDefaults.KEY_RESOLUTION, "1280x720")
                .putString(NativeDisplayDefaults.KEY_FPS, "30").commit();
        NativeDisplayDefaults.applyOnce(ctx);
        assertEquals("1280x720", prefs().getString(NativeDisplayDefaults.KEY_RESOLUTION, null));
        assertEquals("30", prefs().getString(NativeDisplayDefaults.KEY_FPS, null));
    }

    @Test
    public void onlyTheMissingOneIsWritten() {
        prefs().edit().putString(NativeDisplayDefaults.KEY_FPS, "30").commit();
        NativeDisplayDefaults.applyOnce(ctx);
        assertEquals("30", prefs().getString(NativeDisplayDefaults.KEY_FPS, null));
        assertTrue(prefs().contains(NativeDisplayDefaults.KEY_RESOLUTION));
    }
}
