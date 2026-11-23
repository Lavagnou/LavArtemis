package com.limelight.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfileAwarePreferences;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes the rows of Réglages v2. Reads go through the active profile
 * (ProfilesManager.getOverlayingSharedPreferences), writes through ProfileAwarePreferences: a key
 * the active profile holds is written into the profile, anything else into the base preferences,
 * so the next read always sees the value just written. Global rows ({@link SettingDef#global}: the
 * appearance, and the two keys their readers take from the base preferences, the automatic update
 * check and the on-screen key layout) are read from and written to the base preferences. Every write applies at once; the only side effect handled here is
 * the legacy screen's: a new resolution or frame rate resets the bitrate to its default.
 */
public final class SettingsStore implements SettingDef.Values {
    private final Context context;

    public SettingsStore(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    private SharedPreferences prefs() {
        return ProfilesManager.getInstance().getOverlayingSharedPreferences(context);
    }

    private SharedPreferences base() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static Object defaultOf(String key) {
        SettingDef d = SettingsCatalog.find(key);
        return d == null ? null : d.def;
    }

    // ---- reads ----------------------------------------------------------------------------------

    @Override
    public boolean getBool(String key) {
        Object def = defaultOf(key);
        return prefs().getBoolean(key, def instanceof Boolean && (Boolean) def);
    }

    @Override
    public String getString(String key) {
        Object def = defaultOf(key);
        String v = prefs().getString(key, def instanceof String ? (String) def : "");
        return v == null ? "" : v;
    }

    @Override
    public int getInt(String key) {
        if (SettingsCatalog.KEY_BITRATE.equals(key)) {
            int v = prefs().getInt(key, 0);
            return v == 0 ? defaultBitrate() : v;
        }
        Object def = defaultOf(key);
        return prefs().getInt(key, def instanceof Integer ? (Integer) def : 0);
    }

    public boolean expertMode() {
        return base().getBoolean(SettingsCatalog.PREF_EXPERT, false);
    }

    public void setExpertMode(boolean on) {
        base().edit().putBoolean(SettingsCatalog.PREF_EXPERT, on).apply();
    }

    /** The value a row shows, as the string its control compares (choices) */
    public String choiceValue(SettingDef d) {
        if (SettingsCatalog.KEY_PROFILE.equals(d.key)) {
            SettingsProfile active = ProfilesManager.getInstance().getActive();
            return active == null ? "" : active.getUuid().toString();
        }
        if (d.kind == SettingDef.Kind.INT) {
            return String.valueOf(d.global ? base().getInt(d.key, (Integer) d.def) : getInt(d.key));
        }
        return d.global ? base().getString(d.key, (String) d.def) : getString(d.key);
    }

    /** What a toggle shows: the stored value, or its opposite for an inverted row */
    public boolean toggleValue(SettingDef d) {
        boolean stored = d.global ? base().getBoolean(d.key, (Boolean) d.def) : getBool(d.key);
        return stored != d.inverted;
    }

    public int defaultBitrate() {
        return PreferenceConfiguration.getDefaultBitrate(getString("list_resolution"), getString("list_fps"));
    }

    // ---- writes ---------------------------------------------------------------------------------

    public void putBool(String key, boolean value) {
        ProfileAwarePreferences.putBoolean(context, key, value);
    }

    public void putString(String key, String value) {
        ProfileAwarePreferences.putString(context, key, value);
    }

    public void putInt(String key, int value) {
        ProfileAwarePreferences.putInt(context, key, value);
    }

    /** A toggle switched to {@code shown} (inverted rows store the opposite) */
    public void setToggle(SettingDef d, boolean shown) {
        if (d.global) {
            base().edit().putBoolean(d.key, shown != d.inverted).apply();
        } else {
            putBool(d.key, shown != d.inverted);
        }
    }

    /** A choice written, with the bitrate reset of the legacy screen for resolution and frame rate */
    public void setChoice(SettingDef d, String value) {
        if (d.global) {
            if (d.kind == SettingDef.Kind.INT) {
                base().edit().putInt(d.key, Integer.parseInt(value)).apply();
            } else {
                base().edit().putString(d.key, value).apply();
            }
            return;
        }
        if (d.kind == SettingDef.Kind.INT) {
            putInt(d.key, Integer.parseInt(value));
        } else {
            putString(d.key, value);
        }
        if ("list_resolution".equals(d.key) || "list_fps".equals(d.key)) {
            resetBitrate();
        }
    }

    public void resetBitrate() {
        putInt(SettingsCatalog.KEY_BITRATE, defaultBitrate());
    }

    // ---- profiles ---------------------------------------------------------------------------------

    /**
     * Keys a new profile copies: every setting of the screen with its current value (default
     * included), so the profile holds them all and the changes made under it stay in it. The
     * language and the appearance stay common to all profiles (as on the desktop).
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (SettingDef d : SettingsCatalog.all()) {
            if (d.global || "list_languages".equals(d.key)) {
                continue;
            }
            switch (d.kind) {
                case BOOL:
                    out.put(d.key, getBool(d.key));
                    break;
                case STRING:
                    out.put(d.key, getString(d.key));
                    break;
                case INT:
                    out.put(d.key, getInt(d.key));
                    break;
                default:
                    break;
            }
        }
        // Settings without a row of their own that a profile carries too
        SharedPreferences p = prefs();
        for (String key : new String[]{"edit_diy_w_h", "custom_refresh_rate"}) {
            String v = p.getString(key, null);
            if (v != null) {
                out.put(key, v);
            }
        }
        return out;
    }

    /** A new profile from the current settings, made active */
    public SettingsProfile createProfile(String name) {
        long now = System.currentTimeMillis();
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), name, now, now, snapshot());
        ProfilesManager pm = ProfilesManager.getInstance();
        pm.add(p);
        pm.setActive(p.getUuid());
        return p;
    }

    public void setActiveProfile(String uuid) {
        ProfilesManager.getInstance().setActive(uuid == null || uuid.isEmpty() ? null : UUID.fromString(uuid));
    }

    public SettingsProfile activeProfile() {
        return ProfilesManager.getInstance().getActive();
    }

    public void renameActiveProfile(String name) {
        SettingsProfile p = activeProfile();
        if (p != null) {
            p.setName(name);
            p.setModifiedUtc(System.currentTimeMillis());
            ProfilesManager.getInstance().update(p);
        }
    }

    public void deleteActiveProfile() {
        SettingsProfile p = activeProfile();
        if (p != null) {
            ProfilesManager.getInstance().delete(p.getUuid());
        }
    }

    /** « Profil 2 »: the first free number */
    public String nextProfileName(String prefix) {
        int n = ProfilesManager.getInstance().getProfiles().size() + 1;
        while (true) {
            String candidate = prefix + " " + n;
            boolean taken = false;
            for (SettingsProfile p : ProfilesManager.getInstance().getProfiles()) {
                if (candidate.equalsIgnoreCase(p.getName())) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
            n++;
        }
    }
}
