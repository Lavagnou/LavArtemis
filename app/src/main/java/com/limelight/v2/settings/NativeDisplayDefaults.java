package com.limelight.v2.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

import androidx.preference.PreferenceManager;

/**
 * LavArtemis Dev, first launch: the stream starts at this device's native resolution and at the
 * highest refresh rate its screen offers at that resolution (user's rule of 2026-11-28; the
 * desktop does the same with the client's screen, StreamingPreferences::reload). Written once
 * into the base preferences while « Résolution » and « Images par seconde » were never set; a
 * value chosen since is never touched.
 */
public final class NativeDisplayDefaults {
    private NativeDisplayDefaults() {}

    public static final String KEY_RESOLUTION = "list_resolution";
    public static final String KEY_FPS = "list_fps";

    public static void applyOnce(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean res = !prefs.contains(KEY_RESOLUTION);
        boolean fps = !prefs.contains(KEY_FPS);
        if (!res && !fps) {
            return;
        }
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }
        int w;
        int h;
        float[] rates;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode current = display.getMode();
            w = current.getPhysicalWidth();
            h = current.getPhysicalHeight();
            Display.Mode[] modes = display.getSupportedModes();
            rates = new float[modes.length + 1];
            rates[0] = current.getRefreshRate();
            for (int i = 0; i < modes.length; i++) {
                boolean sameSize = modes[i].getPhysicalWidth() == w && modes[i].getPhysicalHeight() == h;
                rates[i + 1] = sameSize ? modes[i].getRefreshRate() : 0;
            }
        } else {
            DisplayMetrics m = new DisplayMetrics();
            display.getRealMetrics(m);
            w = m.widthPixels;
            h = m.heightPixels;
            rates = new float[]{display.getRefreshRate()};
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        SharedPreferences.Editor e = prefs.edit();
        if (res) {
            e.putString(KEY_RESOLUTION, resolution(w, h));
        }
        if (fps) {
            e.putString(KEY_FPS, frameRate(rates));
        }
        e.apply();
    }

    /** « 2400x1080 »: the long side first, as the resolution list stores it (landscape stream) */
    static String resolution(int w, int h) {
        return Math.max(w, h) + "x" + Math.min(w, h);
    }

    /** The highest rate, rounded (59.94 → 60, 119.88 → 120); 60 when the screen gives none */
    static String frameRate(float... rates) {
        float best = 0;
        for (float r : rates) {
            best = Math.max(best, r);
        }
        int rounded = Math.round(best);
        return Integer.toString(rounded > 0 ? rounded : 60);
    }
}
