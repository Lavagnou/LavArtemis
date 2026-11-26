package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.BuildConfig;
import com.limelight.TestLogSuppressor;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * The microphone is a setting of the device, not of a profile (ProfilesManager.APP_WIDE_KEYS, as
 * the desktop's app-wide keys): profiles saved before lose their copy, the active profile's value
 * moves to the base preferences, and every read and write goes there.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class AppWideKeysTest {
    private static final String MIC = PreferenceConfiguration.MIC_PASSTHROUGH_PREF_STRING;
    private static final String MIC_DEVICE = PreferenceConfiguration.MIC_DEVICE_PREF_STRING;

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
    }

    @After
    public void tearDown() {
        ProfilesManager.instance = null;
        deleteRecursively(new File(context.getFilesDir(), "profiles"));
    }

    private static SettingsProfile profile(String name, Object... keyValues) {
        Map<String, Object> options = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            options.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new SettingsProfile(UUID.randomUUID(), name, 0, 0, options);
    }

    @Test
    public void extractKeepsTheActiveValueAndDropsTheOthers() {
        SettingsProfile active = profile("Maison", MIC, true, MIC_DEVICE, "usb:Casque", "list_fps", "60");
        SettingsProfile other = profile("Dehors", MIC, false, "list_fps", "30");
        Map<String, Object> toBase = new LinkedHashMap<>();

        assertTrue(ProfilesManager.extractAppWideKeys(Arrays.asList(active, other), active.getUuid(), toBase));

        assertEquals(true, toBase.get(MIC));
        assertEquals("usb:Casque", toBase.get(MIC_DEVICE));
        assertEquals(2, toBase.size());
        assertFalse(active.getOptions().containsKey(MIC));
        assertFalse(active.getOptions().containsKey(MIC_DEVICE));
        assertFalse(other.getOptions().containsKey(MIC));
        // Stream settings stay in their profiles
        assertEquals("60", active.getOptions().get("list_fps"));
        assertEquals("30", other.getOptions().get("list_fps"));
    }

    @Test
    public void extractWithoutActiveProfileWritesNothingToBase() {
        SettingsProfile p = profile("Maison", MIC, true);
        Map<String, Object> toBase = new LinkedHashMap<>();

        assertTrue(ProfilesManager.extractAppWideKeys(Arrays.asList(p), null, toBase));

        assertTrue(toBase.isEmpty());
        assertFalse(p.getOptions().containsKey(MIC));
    }

    @Test
    public void extractIsANoOpWhenNoProfileCarriesThem() {
        SettingsProfile p = profile("Maison", "list_fps", "60");
        SettingsProfile empty = new SettingsProfile(UUID.randomUUID(), "Vide", 0, 0, null);
        Map<String, Object> toBase = new LinkedHashMap<>();

        assertFalse(ProfilesManager.extractAppWideKeys(Arrays.asList(p, empty), p.getUuid(), toBase));
        assertTrue(toBase.isEmpty());
    }

    @Test
    public void loadMovesTheMicrophoneOutOfSavedProfiles() {
        Assume.assumeTrue("LavArtemis Dev only", BuildConfig.LAV_DEV);
        // A profile saved before the change, active, with the microphone on
        ProfilesManager pm = ProfilesManager.getInstance();
        pm.load(context);
        SettingsProfile saved = profile("Maison", MIC, true, MIC_DEVICE, "usb:Casque", "list_fps", "60");
        pm.add(saved);
        pm.setActive(saved.getUuid());

        // Next start
        ProfilesManager.instance = null;
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertTrue(fresh.load(context));

        SharedPreferences base = PreferenceManager.getDefaultSharedPreferences(context);
        assertTrue("the value the user saw is kept", base.getBoolean(MIC, false));
        assertEquals("usb:Casque", base.getString(MIC_DEVICE, ""));
        assertFalse(fresh.getActive().getOptions().containsKey(MIC));
        assertEquals("60", fresh.getActive().getOptions().get("list_fps"));

        // And it stays out after one more start (the profiles file was rewritten)
        ProfilesManager.instance = null;
        ProfilesManager again = ProfilesManager.getInstance();
        again.load(context);
        assertFalse(again.getActive().getOptions().containsKey(MIC_DEVICE));
    }

    @Test
    public void readsAndWritesGoToTheBasePreferences() {
        Assume.assumeTrue("LavArtemis Dev only", BuildConfig.LAV_DEV);
        ProfilesManager pm = ProfilesManager.getInstance();
        pm.load(context);
        // A profile that still carries the key (written by an older screen, not migrated yet)
        SettingsProfile p = profile("Maison", MIC, false);
        pm.add(p);
        pm.setActive(p.getUuid());
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(MIC, true).commit();

        assertTrue(pm.getOverlayingSharedPreferences(context).getBoolean(MIC, false));
        assertTrue(PreferenceConfiguration.readPreferences(context).enableMicPassthrough);

        ProfileAwarePreferences.putBoolean(context, MIC, false);
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(MIC, true));
        assertEquals("the profile is not written", false, pm.getActive().getOptions().get(MIC));

        // Switching profiles does not change the microphone
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(MIC, true).commit();
        pm.setActive(null);
        assertTrue(pm.getOverlayingSharedPreferences(context).getBoolean(MIC, false));
        pm.setActive(p.getUuid());
        assertTrue(pm.getOverlayingSharedPreferences(context).getBoolean(MIC, false));
    }

    /**
     * What the desktop keeps out of its profiles (ArtemisSettings: maxPendingAudioMs,
     * clipboardSync*, perfCsvLogging) stays out of Android's too, and Réglages writes each of
     * them to the base preferences, never into a new profile's snapshot (global rows).
     */
    @Test
    public void theDesktopsDeviceSettingsAreAppWide() {
        Assume.assumeTrue("LavArtemis Dev only", BuildConfig.LAV_DEV);
        String[] keys = {
                MIC, MIC_DEVICE,
                "seekbar_max_pending_audio_ms",
                "checkbox_smart_clipboard_sync",
                "checkbox_smart_clipboard_sync_toast",
                "checkbox_hide_clipboard_content",
                "checkbox_enable_perf_logging",
        };
        for (String key : keys) {
            assertTrue(key, ProfilesManager.isAppWideKey(key));
        }
        assertFalse("a stream setting stays in the profiles", ProfilesManager.isAppWideKey("list_fps"));
        // Every app-wide key is a global row of Réglages, so a new profile never copies it
        for (String key : ProfilesManager.APP_WIDE_KEYS) {
            com.limelight.v2.settings.SettingDef row = com.limelight.v2.settings.SettingsCatalog.find(key);
            assertNotNull(key, row);
            assertTrue(key + " is global in Réglages", row.global);
        }
    }

    @Test
    public void loadMovesTheAudioBufferAndClipboardOutOfSavedProfiles() {
        Assume.assumeTrue("LavArtemis Dev only", BuildConfig.LAV_DEV);
        ProfilesManager pm = ProfilesManager.getInstance();
        pm.load(context);
        SettingsProfile saved = profile("Maison", "seekbar_max_pending_audio_ms", 60,
                "checkbox_smart_clipboard_sync", true, "list_fps", "60");
        pm.add(saved);
        pm.setActive(saved.getUuid());

        ProfilesManager.instance = null;
        ProfilesManager fresh = ProfilesManager.getInstance();
        assertTrue(fresh.load(context));

        SharedPreferences base = PreferenceManager.getDefaultSharedPreferences(context);
        assertEquals(60, base.getInt("seekbar_max_pending_audio_ms", 40));
        assertTrue(base.getBoolean("checkbox_smart_clipboard_sync", false));
        assertFalse(fresh.getActive().getOptions().containsKey("seekbar_max_pending_audio_ms"));
        assertFalse(fresh.getActive().getOptions().containsKey("checkbox_smart_clipboard_sync"));
        assertEquals("60", fresh.getActive().getOptions().get("list_fps"));
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
