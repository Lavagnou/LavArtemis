package com.limelight.profiles;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.TestLogSuppressor;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ProfileAwarePreferencesTest {
    private static final String KEY = "checkbox_voice_ptt_button";

    private Context context;
    private ProfilesManager manager;
    private File profilesDir;

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        ProfilesManager.instance = null;
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        manager = ProfilesManager.getInstance();
        manager.load(context);
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    @Test
    public void writesBaseWhenNoProfile() {
        ProfileAwarePreferences.putBoolean(context, KEY, true);
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false));
    }

    @Test
    public void writesBaseWhenActiveProfileLacksKey() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("other", 1);
        activate(options);

        ProfileAwarePreferences.putBoolean(context, KEY, true);

        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false));
        assertFalse(manager.getActive().getOptions().containsKey(KEY));
    }

    @Test
    public void writesIntoActiveProfileWhenItHasTheKey() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(KEY, false);
        activate(options);

        ProfileAwarePreferences.putBoolean(context, KEY, true);

        assertEquals(Boolean.TRUE, manager.getActive().getOptions().get(KEY));
        assertTrue(manager.getOverlayingSharedPreferences(context).getBoolean(KEY, false));
    }

    @Test
    public void profileWriteSurvivesReload() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(KEY, false);
        activate(options);

        ProfileAwarePreferences.putBoolean(context, KEY, true);

        ProfilesManager.instance = null;
        ProfilesManager fresh = ProfilesManager.getInstance();
        fresh.load(context);
        assertEquals(Boolean.TRUE, fresh.getActive().getOptions().get(KEY));
    }

    @Test
    public void stringIntFloatFollowTheSameRule() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("list_resolution", "1280x720");
        options.put("seekbar_bitrate_kbps", 20000.0); // as Gson reads it back
        activate(options);

        ProfileAwarePreferences.putString(context, "list_resolution", "1920x1080");
        ProfileAwarePreferences.putInt(context, "seekbar_bitrate_kbps", 40000);
        ProfileAwarePreferences.putString(context, "list_fps", "120");
        ProfileAwarePreferences.putFloat(context, "number_zoom_scale", 2.5f);

        Map<String, Object> held = manager.getActive().getOptions();
        assertEquals("1920x1080", held.get("list_resolution"));
        assertEquals(40000, held.get("seekbar_bitrate_kbps"));
        assertFalse(held.containsKey("list_fps"));
        assertFalse(held.containsKey("number_zoom_scale"));
        assertEquals("120", PreferenceManager.getDefaultSharedPreferences(context).getString("list_fps", null));
        assertEquals(2.5f, PreferenceManager.getDefaultSharedPreferences(context).getFloat("number_zoom_scale", 0f), 0f);
        assertEquals(40000, manager.getOverlayingSharedPreferences(context).getInt("seekbar_bitrate_kbps", 0));
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains("list_resolution"));
    }

    @Test
    public void removeTakesTheKeyOutOfWhereItIs() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("list_fps", "90").putString("list_resolution", "2560x1440").commit();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("list_resolution", "1280x720");
        activate(options);

        ProfileAwarePreferences.remove(context, "list_resolution");
        assertFalse(manager.getActive().getOptions().containsKey("list_resolution"));
        // The base value shows through again, untouched
        assertEquals("2560x1440", manager.getOverlayingSharedPreferences(context).getString("list_resolution", null));

        ProfileAwarePreferences.remove(context, "list_fps");
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context).contains("list_fps"));
    }

    private void activate(Map<String, Object> options) {
        long now = System.currentTimeMillis();
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "Active", now, now, options);
        manager.add(p);
        manager.setActive(p.getUuid());
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
