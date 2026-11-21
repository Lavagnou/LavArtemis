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
