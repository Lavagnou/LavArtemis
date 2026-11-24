package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProfilesManager {
    private static final String PROFILES_DIR = "profiles";
    private static final String PROFILES_FILE = "profiles.json";

    static ProfilesManager instance;

    private final Map<UUID, SettingsProfile> profiles = new LinkedHashMap<>();
    private UUID activeProfileId;
    private final List<ProfileChangeListener> listeners = new ArrayList<>();
    private Context appContext; // Application context for auto-save

    private ProfilesManager() {}

    public static synchronized ProfilesManager getInstance() {
        if (instance == null) {
            instance = new ProfilesManager();
        }
        return instance;
    }

    public boolean load(Context context) {
        LimeLog.info("ArtemisProfile: Loading profile...");
        if (context == null) {
            return false;
        }

        try {
            this.appContext = context.getApplicationContext();
        } catch (Exception e) {
            // If getApplicationContext() fails (e.g., during app startup), use the context directly
            this.appContext = context;
        }

        // Additional safety check
        if (this.appContext == null) {
            return false;
        }

        try {
            File dir = new File(this.appContext.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            if (!file.exists()) {
                // We don't want to warn user about profile not exist
                return true;
            }
            try (Reader reader = new FileReader(file)) {
                Gson gson = new Gson();
                Type type = new TypeToken<ProfilesData>(){}.getType();
                ProfilesData data = gson.fromJson(reader, type);
                if (data != null && data.profiles != null) {
                    profiles.clear();
                    for (SettingsProfile p : data.profiles) {
                        profiles.put(p.getUuid(), p);
                    }
                    activeProfileId = data.activeProfileId;
                    if (appWideKeysEnabled()) {
                        migrateAppWideKeys();
                    }
                }
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to load profiles from file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to load profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean save(Context context) {
        if (context == null) {
            return false;
        }

        try {
            File dir = new File(context.getFilesDir(), PROFILES_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File file = new File(dir, PROFILES_FILE);
            try (Writer writer = new FileWriter(file)) {
                Gson gson = new Gson();
                ProfilesData data = new ProfilesData();
                data.profiles = new ArrayList<>(profiles.values());
                data.activeProfileId = activeProfileId;
                gson.toJson(data, writer);
            } catch (IOException e) {
                LimeLog.warning("ArtemisProfile: Failed to save profiles to file:" + e);
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            LimeLog.warning("ArtemisProfile: Failed to save profiles:" + e);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    // ---- settings of this device, not of a stream --------------------------------------------------

    /**
     * Keys that describe this device rather than a stream: a profile never carries them, like the
     * desktop's app-wide keys (ProfileManager::isGlobalKey). The microphone is the one the user
     * plugs in here, whatever the profile. They were profile-scoped before 2026-11-24, so
     * {@link #load} moves them out of existing profiles (LavArtemis Dev only: the stable keeps
     * its profiles as they were).
     */
    static final String[] APP_WIDE_KEYS = {
            PreferenceConfiguration.MIC_PASSTHROUGH_PREF_STRING,
            PreferenceConfiguration.MIC_DEVICE_PREF_STRING,
    };

    static boolean appWideKeysEnabled() {
        return BuildConfig.LAV_DEV;
    }

    /** True for a key no profile may carry (always false in the stable) */
    public static boolean isAppWideKey(String key) {
        if (!appWideKeysEnabled() || key == null) {
            return false;
        }
        for (String k : APP_WIDE_KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes the app-wide keys out of every profile. What the user sees now is the active
     * profile's value when it has one: that value goes into {@code toBase}, to be written to the
     * base preferences. The other profiles' copies would never be read again. Returns true when a
     * profile changed.
     */
    static boolean extractAppWideKeys(Iterable<SettingsProfile> profiles, UUID activeId, Map<String, Object> toBase) {
        boolean changed = false;
        for (SettingsProfile p : profiles) {
            if (p.getOptions() == null) {
                continue;
            }
            Map<String, Object> options = null;
            for (String key : APP_WIDE_KEYS) {
                if (!p.getOptions().containsKey(key)) {
                    continue;
                }
                if (options == null) {
                    options = new LinkedHashMap<>(p.getOptions());
                }
                Object value = options.remove(key);
                if (p.getUuid().equals(activeId) && value != null) {
                    toBase.put(key, value);
                }
            }
            if (options != null) {
                p.setOptions(options);
                changed = true;
            }
        }
        return changed;
    }

    private void migrateAppWideKeys() {
        Map<String, Object> toBase = new LinkedHashMap<>();
        if (!extractAppWideKeys(profiles.values(), activeProfileId, toBase)) {
            return;
        }
        SharedPreferences.Editor editor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext).edit();
        for (Map.Entry<String, Object> e : toBase.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                editor.putBoolean(e.getKey(), (Boolean) v);
            } else if (v instanceof String) {
                editor.putString(e.getKey(), (String) v);
            }
        }
        editor.apply();
        LimeLog.info("ArtemisProfile: moved app-wide settings out of the profiles");
        save(appContext);
    }

    public List<SettingsProfile> getProfiles() {
        return new ArrayList<>(profiles.values());
    }

    public void add(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void update(SettingsProfile profile) {
        profiles.put(profile.getUuid(), profile);
        notifyListeners();
        saveIfPossible();
    }

    public void delete(UUID uuid) {
        profiles.remove(uuid);
        if (uuid.equals(activeProfileId)) {
            activeProfileId = null;
        }
        notifyListeners();
        saveIfPossible();
    }

    public void setActive(UUID uuid) {
        activeProfileId = uuid;
        notifyListeners();
        saveIfPossible();
    }

    public SettingsProfile getActive() {
        return activeProfileId == null ? null : profiles.get(activeProfileId);
    }

    @NonNull
    public String getActiveName() {
        SettingsProfile active = getActive();
        return active == null ? "" : active.getName();
    }

    public void addListener(ProfileChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProfileChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ProfileChangeListener listener : listeners) {
            listener.onProfilesChanged();
        }
    }

    private static class ProfilesData {
        List<SettingsProfile> profiles;
        UUID activeProfileId;
    }

    public interface ProfileChangeListener {
        void onProfilesChanged();
    }

    /**
     * Returns a SharedPreferences that overlays the active profile's options on top of the real prefs.
     */
    public SharedPreferences getOverlayingSharedPreferences(Context context) {
        SharedPreferences base = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        SettingsProfile active = getActive();
        if (active == null || active.getOptions() == null) {
            return base;
        }
        Map<String, Object> patch = active.getOptions();
        for (String key : APP_WIDE_KEYS) {
            if (isAppWideKey(key) && patch.containsKey(key)) {
                // A profile written by an older screen may still carry one: the base value wins
                patch = new LinkedHashMap<>(patch);
                patch.remove(key);
            }
        }
        return new OverlaySharedPreferences(base, patch);
    }

    /**
     * Wraps a SharedPreferences to override and shadow values from a profile's options map.
     */
    private static class OverlaySharedPreferences implements SharedPreferences {
        private final SharedPreferences base;
        private final Map<String, Object> patch;
        OverlaySharedPreferences(SharedPreferences base, Map<String, Object> patch) {
            this.base = base;
            this.patch = patch;
        }
        @Override public Map<String, ?> getAll() {
            Map<String, Object> combined = new LinkedHashMap<>(base.getAll());
            combined.putAll(patch);
            return combined;
        }
        @Override public String getString(String key, String defValue) {
            if (patch.containsKey(key)) return (String) patch.get(key);
            return base.getString(key, defValue);
        }
        @Override public int getInt(String key, int defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).intValue();
            return base.getInt(key, defValue);
        }
        @Override public long getLong(String key, long defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).longValue();
            return base.getLong(key, defValue);
        }
        @Override public float getFloat(String key, float defValue) {
            if (patch.containsKey(key)) return ((Number) patch.get(key)).floatValue();
            return base.getFloat(key, defValue);
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            if (patch.containsKey(key)) return (Boolean) patch.get(key);
            return base.getBoolean(key, defValue);
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            if (patch.containsKey(key)) return (Set<String>) patch.get(key);
            return base.getStringSet(key, defValues);
        }
        @Override public boolean contains(String key) {
            return patch.containsKey(key) || base.contains(key);
        }
        @Override public Editor edit() { return base.edit(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.registerOnSharedPreferenceChangeListener(listener);
        }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            base.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    private boolean saveIfPossible() {
        if (appContext != null) {
            return save(appContext);
        }
        return false;
    }
}