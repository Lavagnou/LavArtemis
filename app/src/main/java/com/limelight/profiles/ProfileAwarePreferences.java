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
        ProfilesManager profiles = ProfilesManager.getInstance();
        SettingsProfile active = profiles.getActive();
        if (active != null && active.getOptions() != null && active.getOptions().containsKey(key)) {
            Map<String, Object> options = new LinkedHashMap<>(active.getOptions());
            options.put(key, value);
            active.setOptions(options);
            active.setModifiedUtc(System.currentTimeMillis());
            // update() writes the profiles file itself (saveIfPossible)
            profiles.update(active);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).apply();
    }
}
