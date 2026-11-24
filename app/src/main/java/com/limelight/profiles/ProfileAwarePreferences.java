package com.limelight.profiles;

import android.content.Context;
import android.preference.PreferenceManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a setting where the next read will see it.
 *
 * Settings are read through {@link ProfilesManager#getOverlayingSharedPreferences}, but its
 * {@code edit()} writes to the base preferences: a key the active profile carries would mask
 * that write. So a key the active profile holds is written into the profile; anything else
 * goes to the base preferences, and profiles that never had the key do not gain it.
 */
public final class ProfileAwarePreferences {
    private ProfileAwarePreferences() {}

    public static void putBoolean(Context context, String key, boolean value) {
        if (!putInProfile(key, value)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply();
        }
    }

    public static void putString(Context context, String key, String value) {
        if (!putInProfile(key, value)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, value).apply();
        }
    }

    public static void putInt(Context context, String key, int value) {
        if (!putInProfile(key, value)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).apply();
        }
    }

    public static void putFloat(Context context, String key, float value) {
        if (!putInProfile(key, value)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putFloat(key, value).apply();
        }
    }

    /**
     * Removes the key from the active profile when it holds it (the base value shows through
     * again), otherwise from the base preferences.
     */
    public static void remove(Context context, String key) {
        SettingsProfile active = activeHolding(key);
        if (active != null) {
            Map<String, Object> options = new LinkedHashMap<>(active.getOptions());
            options.remove(key);
            save(active, options);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(key).apply();
    }

    /** The active profile when it carries this key, null otherwise */
    private static SettingsProfile activeHolding(String key) {
        if (ProfilesManager.isAppWideKey(key)) {
            // Read from the base preferences whatever the profile holds (ProfilesManager.APP_WIDE_KEYS)
            return null;
        }
        SettingsProfile active = ProfilesManager.getInstance().getActive();
        if (active != null && active.getOptions() != null && active.getOptions().containsKey(key)) {
            return active;
        }
        return null;
    }

    private static boolean putInProfile(String key, Object value) {
        SettingsProfile active = activeHolding(key);
        if (active == null) {
            return false;
        }
        Map<String, Object> options = new LinkedHashMap<>(active.getOptions());
        options.put(key, value);
        save(active, options);
        return true;
    }

    private static void save(SettingsProfile profile, Map<String, Object> options) {
        profile.setOptions(options);
        profile.setModifiedUtc(System.currentTimeMillis());
        // update() writes the profiles file itself (saveIfPossible)
        ProfilesManager.getInstance().update(profile);
    }
}
