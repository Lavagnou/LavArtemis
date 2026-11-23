package com.limelight.v2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import com.limelight.R;

/**
 * Design tokens of the v2 interface: Braise, Affiche and Ludique, each light and dark. Values are
 * copied from LavArtemis-Qt {@code app/gui/v2/Theme.qml} (itself from the validated prototype):
 * change them there and here together. Every v2 view reads its colours, radii and fonts from an
 * instance of this class, never from literals.
 */
public final class V2Theme {
    public static final String PREF_THEME = "v2_theme";
    public static final String PREF_MODE = "v2_theme_mode";

    public static final String BRAISE = "braise";
    public static final String AFFICHE = "affiche";
    public static final String LUDIQUE = "ludique";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    public final String id;
    public final int mode;
    public final boolean dark;

    public final int bg, surface, surface2, fg, muted, accent, onAccent, line, on, off, sel, selFg,
            badgeBg, badgeFg, shadow, live, onLive, borderColor;
    /** Radii and border, in dp */
    public final int r, rs, pill, border;
    public final boolean upper;
    /** Letter spacing of display text, in em */
    public final float tracking;
    /** Weight of display text (700 Braise, 900 Affiche, 800 Ludique) */
    public final int displayWeight;

    /** Grayscale, multiplied brightness and opacity of a dimmed card (Theme.qml dim()) */
    public static final class Dim {
        public final float gray, bright, opacity;

        Dim(float gray, float bright, float opacity) {
            this.gray = gray;
            this.bright = bright;
            this.opacity = opacity;
        }
    }

    private V2Theme(String id, int mode, boolean dark) {
        this.id = id;
        this.mode = mode;
        this.dark = dark;

        String[] p = palette(id, dark);
        bg = c(p[0]);
        surface = c(p[1]);
        surface2 = c(p[2]);
        fg = c(p[3]);
        muted = c(p[4]);
        accent = c(p[5]);
        onAccent = c(p[6]);
        line = c(p[7]);
        on = c(p[8]);
        off = c(p[9]);
        sel = c(p[10]);
        selFg = c(p[11]);
        badgeBg = c(p[12]);
        badgeFg = c(p[13]);
        shadow = c(p[14]);
        // The colour that says "running": Braise orange, Affiche yellow, Ludique lime
        live = AFFICHE.equals(id) ? c("#FFD60A") : (LUDIQUE.equals(id) ? c("#C6F432") : accent);
        onLive = BRAISE.equals(id) ? onAccent : c("#111111");

        r = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 28 : 18);
        rs = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 20 : 12);
        pill = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 22 : 14);
        border = AFFICHE.equals(id) ? 3 : (BRAISE.equals(id) ? 1 : 0);
        borderColor = AFFICHE.equals(id) ? fg : line;

        upper = AFFICHE.equals(id);
        tracking = AFFICHE.equals(id) ? -0.02f : (LUDIQUE.equals(id) ? -0.03f : -0.01f);
        displayWeight = AFFICHE.equals(id) ? 900 : (LUDIQUE.equals(id) ? 800 : 700);
    }

    private static int c(String hex) {
        return Color.parseColor(hex);
    }

    // bg, surface, surface2, fg, muted, accent, onAccent, line, on, off, sel, selFg, badgeBg, badgeFg, shadow
    private static String[] palette(String id, boolean dark) {
        switch (id) {
            case AFFICHE:
                return dark
                        ? new String[]{"#141414", "#141414", "#262624", "#EDEDE8", "#A3A39C", "#EDEDE8", "#141414", "#EDEDE8", "#FFD60A", "#3A3A37", "#EDEDE8", "#141414", "#FFD60A", "#111111", "#000000"}
                        : new String[]{"#ECECE8", "#ECECE8", "#E0E0DA", "#111111", "#555550", "#111111", "#ECECE8", "#111111", "#111111", "#D2D2CC", "#111111", "#ECECE8", "#111111", "#FFD60A", "#111111"};
            case LUDIQUE:
                return dark
                        ? new String[]{"#15112A", "#221C3D", "#2E2650", "#F3EEFF", "#B3A9D6", "#C6F432", "#1B1530", "#2E2650", "#C6F432", "#3A3166", "#C6F432", "#1B1530", "#C6F432", "#1B1530", "#000000"}
                        : new String[]{"#F3EEFF", "#FFFFFF", "#E4DEFF", "#1B1530", "#5A527A", "#C6F432", "#1B1530", "#E4DEFF", "#C6F432", "#E4DEFF", "#1B1530", "#FFFFFF", "#1B1530", "#C6F432", "#1B1530"};
            default:
                return dark
                        ? new String[]{"#0E0D0C", "#1A1816", "#24211E", "#F3EEE8", "#A39A90", "#FF6A2B", "#0E0D0C", "#2C2724", "#FF6A2B", "#3A342F", "#2A221C", "#F3EEE8", "#FF6A2B", "#0E0D0C", "#000000"}
                        : new String[]{"#F6F1EA", "#FFFFFF", "#EFE7DD", "#1E1A16", "#6B6158", "#E8541A", "#FFFFFF", "#E2D8CC", "#E8541A", "#DCD1C4", "#F3E3D6", "#1E1A16", "#E8541A", "#FFFFFF", "#3C2814"};
        }
    }

    /** A theme built from explicit values (tests, previews). */
    public static V2Theme of(String id, boolean dark) {
        return new V2Theme(normalize(id), dark ? MODE_DARK : MODE_LIGHT, dark);
    }

    public static String normalize(String id) {
        return AFFICHE.equals(id) || LUDIQUE.equals(id) ? id : BRAISE;
    }

    /** The theme chosen in the preferences, resolved against the device's night mode. */
    public static V2Theme load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String id = normalize(prefs.getString(PREF_THEME, BRAISE));
        int mode = clampMode(prefs.getInt(PREF_MODE, MODE_SYSTEM));
        return new V2Theme(id, mode, resolveDark(mode, isSystemDark(context)));
    }

    public static boolean isSystemDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static int clampMode(int mode) {
        return mode < MODE_SYSTEM || mode > MODE_DARK ? MODE_SYSTEM : mode;
    }

    /** Système follows the device, Clair and Sombre force it. */
    public static boolean resolveDark(int mode, boolean systemDark) {
        return mode == MODE_DARK || (mode == MODE_SYSTEM && systemDark);
    }

    /** Système -> Clair -> Sombre -> Système, as the desktop's cycleMode() */
    public static int nextMode(int mode) {
        return (clampMode(mode) + 1) % 3;
    }

    public static void saveMode(Context context, int mode) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_MODE, clampMode(mode)).apply();
    }

    public static void saveTheme(Context context, String id) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_THEME, normalize(id)).apply();
    }

    public boolean isAffiche() {
        return AFFICHE.equals(id);
    }

    public boolean isLudique() {
        return LUDIQUE.equals(id);
    }

    public boolean isBraise() {
        return BRAISE.equals(id);
    }

    /** Theme.qml dim(): a card or cover dimmed while another app runs. */
    public Dim dim(boolean focused) {
        if (BRAISE.equals(id)) {
            return dark ? (focused ? new Dim(0.35f, 0.85f, 1.0f) : new Dim(0.9f, 0.42f, 1.0f))
                        : (focused ? new Dim(0.3f, 1.0f, 0.9f) : new Dim(0.9f, 1.0f, 0.45f));
        }
        if (AFFICHE.equals(id)) {
            return focused ? new Dim(0.5f, 1.0f, 0.75f) : new Dim(1.0f, 1.0f, 0.3f);
        }
        return dark ? (focused ? new Dim(0.3f, 1.0f, 0.95f) : new Dim(1.0f, 0.7f, 0.55f))
                    : (focused ? new Dim(0.4f, 1.0f, 0.85f) : new Dim(1.0f, 1.0f, 0.4f));
    }

    public String displayText(String s) {
        return upper && s != null ? s.toUpperCase(java.util.Locale.FRENCH) : s;
    }

    /** Radius of a small pill (badges): 0 for Affiche, round otherwise */
    public boolean square() {
        return AFFICHE.equals(id);
    }

    // ---- fonts ---------------------------------------------------------------------------

    public Typeface displayFont(Context context) {
        return font(context, displayFamily(), displayWeight);
    }

    public Typeface displayFont(Context context, int weight) {
        return font(context, displayFamily(), weight);
    }

    public Typeface textFont(Context context, int weight) {
        return font(context, textFamily(), weight);
    }

    private String displayFamily() {
        return AFFICHE.equals(id) ? "archivo" : (LUDIQUE.equals(id) ? "bricolage" : "space");
    }

    private String textFamily() {
        return AFFICHE.equals(id) ? "archivo" : (LUDIQUE.equals(id) ? "bricolage" : "inter");
    }

    private static int[] weights(String family) {
        switch (family) {
            case "archivo":
                return new int[]{400, 500, 600, 700, 800, 900};
            case "space":
                return new int[]{400, 500, 600, 700};
            default:
                return new int[]{400, 500, 600, 700, 800};
        }
    }

    private static int resource(String family, int weight) {
        switch (family) {
            case "archivo":
                switch (weight) {
                    case 400: return R.font.v2_archivo_400;
                    case 500: return R.font.v2_archivo_500;
                    case 600: return R.font.v2_archivo_600;
                    case 700: return R.font.v2_archivo_700;
                    case 800: return R.font.v2_archivo_800;
                    default: return R.font.v2_archivo_900;
                }
            case "bricolage":
                switch (weight) {
                    case 400: return R.font.v2_bricolage_400;
                    case 500: return R.font.v2_bricolage_500;
                    case 600: return R.font.v2_bricolage_600;
                    case 700: return R.font.v2_bricolage_700;
                    default: return R.font.v2_bricolage_800;
                }
            case "space":
                switch (weight) {
                    case 400: return R.font.v2_space_grotesk_400;
                    case 500: return R.font.v2_space_grotesk_500;
                    case 600: return R.font.v2_space_grotesk_600;
                    default: return R.font.v2_space_grotesk_700;
                }
            default:
                switch (weight) {
                    case 400: return R.font.v2_inter_400;
                    case 500: return R.font.v2_inter_500;
                    case 600: return R.font.v2_inter_600;
                    case 700: return R.font.v2_inter_700;
                    default: return R.font.v2_inter_800;
                }
        }
    }

    /** Nearest available weight of a family (exposed for tests). */
    static int nearestWeight(String family, int weight) {
        int best = 400;
        int bestDistance = Integer.MAX_VALUE;
        for (int w : weights(family)) {
            int d = Math.abs(w - weight);
            if (d < bestDistance) {
                bestDistance = d;
                best = w;
            }
        }
        return best;
    }

    private static Typeface font(Context context, String family, int weight) {
        int w = nearestWeight(family, weight);
        Typeface tf = null;
        try {
            tf = ResourcesCompat.getFont(context, resource(family, w));
        } catch (Exception ignored) {
            // A font that fails to load must never take the screen down
        }
        if (tf == null) {
            tf = Typeface.create(Typeface.SANS_SERIF, w >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        return tf;
    }

    /**
     * Variable fonts carry their weights as variation settings, which Android applies from API 26
     * only. Older devices get the font's default instance: a fake bold keeps the hierarchy readable.
     */
    public static boolean needsFakeBold(int weight) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O && weight >= 600;
    }
}
