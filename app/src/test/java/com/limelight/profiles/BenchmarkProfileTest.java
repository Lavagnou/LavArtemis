package com.limelight.profiles;

import android.content.Context;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;
import com.limelight.benchmark.BenchmarkSettings;
import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * « Appliquer comme profil » after a benchmark makes a profile that is a full snapshot of the
 * settings in effect plus the run's configuration, as a profile made in Réglages (SettingsStore
 * .snapshot) and on the desktop (ProfileManager::createProfile). With no profile active it used to
 * hold the run's keys only: everything else followed the base preferences from then on.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class BenchmarkProfileTest {
    private Context context;

    @BeforeClass
    public static void quiet() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ProfilesManager.instance = null;
        deleteRecursively(new File(context.getFilesDir(), "profiles"));
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        ProfilesManager.getInstance().load(context);
    }

    @After
    public void tearDown() {
        ProfilesManager.instance = null;
        deleteRecursively(new File(context.getFilesDir(), "profiles"));
    }

    @Test
    public void profileWithoutActiveProfileIsAFullSnapshot() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("list_fps", "120").putBoolean("checkbox_host_audio", true).commit();

        BenchmarkSettings.applyAsProfile(context, new RunConfig(Codec.HEVC, 30000, "default"), "Bench");

        SettingsProfile active = ProfilesManager.getInstance().getActive();
        assertNotNull(active);
        Map<String, Object> options = active.getOptions();
        assertEquals("the settings in effect, frozen", "120", options.get("list_fps"));
        assertEquals(true, options.get("checkbox_host_audio"));
        assertEquals("the run's codec", "forceh265", options.get("video_format"));
        assertEquals(30000, options.get("seekbar_bitrate_kbps"));
        assertFalse("the language stays common to all profiles", options.containsKey("list_languages"));
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
