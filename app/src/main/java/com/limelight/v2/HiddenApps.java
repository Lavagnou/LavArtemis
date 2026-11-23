package com.limelight.v2;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.AppView;

import java.util.HashSet;
import java.util.Set;

/**
 * Games hidden with « Masquer »: the same preferences as the legacy AppView
 * ({@code HiddenApps}, key = PC uuid, value = set of app ids), so both interfaces agree.
 */
public final class HiddenApps {
    private HiddenApps() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, Context.MODE_PRIVATE);
    }

    public static Set<Integer> read(Context context, String pcUuid) {
        Set<Integer> ids = new HashSet<>();
        if (pcUuid == null) {
            return ids;
        }
        for (String s : prefs(context).getStringSet(pcUuid, new HashSet<String>())) {
            try {
                ids.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    /** Hides or shows one app again; returns the new set. */
    public static Set<Integer> setHidden(Context context, String pcUuid, int appId, boolean hide) {
        Set<Integer> ids = read(context, pcUuid);
        if (hide) {
            ids.add(appId);
        } else {
            ids.remove(appId);
        }
        Set<String> strings = new HashSet<>();
        for (Integer id : ids) {
            strings.add(id.toString());
        }
        // A new set object: SharedPreferences must never be handed back the set it returned
        prefs(context).edit().putStringSet(pcUuid, strings).apply();
        return ids;
    }
}
