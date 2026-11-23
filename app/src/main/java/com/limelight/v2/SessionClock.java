package com.limelight.v2;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * "EN COURS · depuis 42 min": the host does not say when a session started, so the duration counts
 * from the first time this device saw that app running on that PC. Kept in preferences so a
 * relaunch of the app (or a stream in between) does not reset it; forgotten when nothing runs.
 */
public final class SessionClock {
    private static final String PREFS = "v2_session_clock";

    private SessionClock() {}

    /** Start time (ms) of the session of runningId on that PC; 0 when nothing runs. */
    public static long observe(Context context, String pcUuid, int runningId, long nowMs) {
        if (pcUuid == null) {
            return 0;
        }
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (runningId == 0) {
            if (p.contains(pcUuid)) {
                p.edit().remove(pcUuid).apply();
            }
            return 0;
        }
        String stored = p.getString(pcUuid, null);
        long since = parseSince(stored, runningId);
        if (since <= 0) {
            since = nowMs;
            p.edit().putString(pcUuid, runningId + ":" + nowMs).apply();
        }
        return since;
    }

    static long parseSince(String stored, int runningId) {
        if (stored == null) {
            return 0;
        }
        int colon = stored.indexOf(':');
        if (colon <= 0) {
            return 0;
        }
        try {
            if (Integer.parseInt(stored.substring(0, colon)) != runningId) {
                return 0;
            }
            return Long.parseLong(stored.substring(colon + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Whole minutes of the session, or -1 under a minute (nothing is shown then) */
    public static long minutes(long sinceMs, long nowMs) {
        if (sinceMs <= 0 || nowMs - sinceMs < 60_000) {
            return -1;
        }
        return (nowMs - sinceMs) / 60_000;
    }
}
