package com.limelight.computers;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.nvstream.http.ComputerDetails;

/**
 * Names given to PCs in LavArtemis (« Renommer » of the v2 « Mes PC »), by uuid. Kept apart from
 * the computer database, whose schema stays as is: every poll copies the host's own name into the
 * details ({@link ComputerDetails#update}), so the chosen name is applied again after each one
 * ({@link #apply}) and written to the database with it. Removed when the PC is forgotten.
 */
public final class HostNames {
    static final String PREF_FILE = "HostNames";

    private HostNames() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    /** The name chosen for this PC, or null */
    public static String get(Context context, String uuid) {
        if (uuid == null) {
            return null;
        }
        String name = prefs(context).getString(uuid, null);
        return name == null || name.trim().isEmpty() ? null : name;
    }

    /** null or empty: back to the name the host gives */
    public static void set(Context context, String uuid, String name) {
        if (uuid == null) {
            return;
        }
        String clean = clean(name);
        SharedPreferences.Editor e = prefs(context).edit();
        if (clean == null) {
            e.remove(uuid);
        } else {
            e.putString(uuid, clean);
        }
        e.apply();
    }

    public static void remove(Context context, String uuid) {
        set(context, uuid, null);
    }

    /** The chosen name over the host's, when there is one */
    public static void apply(Context context, ComputerDetails details) {
        String name = get(context, details.uuid);
        if (name != null) {
            details.name = name;
        }
    }

    /** Trimmed, at most 64 characters (as the desktop), null when empty */
    public static String clean(String name) {
        if (name == null) {
            return null;
        }
        String t = name.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
