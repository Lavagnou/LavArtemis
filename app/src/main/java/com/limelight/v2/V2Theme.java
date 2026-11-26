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

    /** Note of a launch that closes something (prototype tk.warnBg / tk.warnLine) */
    public final int warnBg, warnLine;
    /** Radius of the options popover, of its rows and of its thumbnail (tk.popR / popRow / thumbR) */
    public final int popR, popRow, thumbR;

    /**
     * Focus ring of the prototype (T.ring), drawn outside the focused view: a gap in the page
     * colour, then the ring colour. Braise 3 / 6 px accent, Ludique 4 / 9 px (light #1B1530, dark
     * lime). Affiche has no ring: the focused item is filled yellow with dark text, plus a 4 px
     * outline 4 px away (T.ring2).
     */
    public final float focusGap, focusOuter;
    public final int focusColor;
    public final boolean focusFill;
    /** Affiche's focus fill and live yellow, both modes */
    public static final int YELLOW = 0xFFFFD60A;
    /** Ink of Affiche's focus fill */
    public static final int FILL_INK = 0xFF111111;

    /** Live glow (prototype keyframes): colour and full period of lavember / lavplum / lavbar-lavpop */
    public final int glowColor;
    public final int glowPeriodMs;
    public final float glowAlphaMin, glowAlphaMax, glowBlurMin, glowBlurMax;

    /**
     * Colours the prototype writes literally in a theme's markup (not tokens), per mode: see
     * {@link Lit}. Copy of the "literal colours" table of design-port-spec §2.1.
     */
    public final Lit lit;

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

        if (AFFICHE.equals(id)) {
            warnBg = dark ? c("#3B330F") : YELLOW;
            warnLine = dark ? YELLOW : c("#111111");
        } else if (LUDIQUE.equals(id)) {
            warnBg = dark ? c("#3A2430") : c("#FFE1D8");
            warnLine = c("#FF7A59");
        } else {
            warnBg = dark ? c("#2A1A12") : c("#FBE3D4");
            warnLine = accent;
        }
        popR = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 24 : 16);
        popRow = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 16 : 10);
        thumbR = AFFICHE.equals(id) ? 0 : (LUDIQUE.equals(id) ? 10 : 8);

        focusFill = AFFICHE.equals(id);
        if (LUDIQUE.equals(id)) {
            focusGap = 4;
            focusOuter = 9;
            focusColor = dark ? c("#C6F432") : c("#1B1530");
        } else if (AFFICHE.equals(id)) {
            // ring2: outline 4 px, 4 px away from the item
            focusGap = 4;
            focusOuter = 8;
            focusColor = fg;
        } else {
            focusGap = 3;
            focusOuter = 6;
            focusColor = accent;
        }

        if (LUDIQUE.equals(id)) {
            glowColor = c("#7B5CFF");
            glowPeriodMs = 2400;
            glowAlphaMin = 0.5f;
            glowAlphaMax = 1f;
            glowBlurMin = 6;
            glowBlurMax = 20;
        } else if (AFFICHE.equals(id)) {
            glowColor = YELLOW;
            glowPeriodMs = 1600;
            glowAlphaMin = 1f;
            glowAlphaMax = 1f;
            glowBlurMin = 0;
            glowBlurMax = 0;
        } else {
            glowColor = c("#FF6A2B");
            glowPeriodMs = 2600;
            glowAlphaMin = 0.45f;
            glowAlphaMax = 0.95f;
            glowBlurMin = 10;
            glowBlurMax = 26;
        }
        lit = new Lit(id, dark);
    }

    /**
     * Literal colours of the prototype's markup, per theme and mode. Names describe the role; the
     * comment gives the dark / light values of Braise, then Affiche, then Ludique where they
     * differ from a token.
     */
    public static final class Lit {
        /** Secondary text over the page (Braise #E7E0D8 / #3E3731) */
        public final int text2;
        /** Muted text over a dark or light veil (Braise #CFC6BC / #6B6158; Ludique #D9D2F5 on navy) */
        public final int muted2;
        /** Accent used as text (Braise #FF8A57 / #C9480F) */
        public final int accentText;
        /** Thin border of the small buttons (Braise #5A524B / #B8AA9B) */
        public final int chipBorder;
        /** Colour of the page veils (Braise rgb of the background) */
        public final int veil;
        /** Ludique: navy block (#1B1530), inverted in dark (#F3EEFF); Affiche: ink band (#111 / #EDEDE8) */
        public final int band, onBand;
        /** Ludique: inner chip of the navy block (#2E2650 / #DCD3FF) and its text (#D9D2F5 / #5A527A) */
        public final int bandChip, onBandChip;
        /** Live text on the band (Affiche #FFD60A / #7A6000; Ludique #C6F432 / #3F5A00) */
        public final int bandLive;
        /** Ludique's purple (go button, chips): #5B3DF5 / #8C77FF */
        public final int purple;
        /** Ludique: app card blocks (Bureau #FF7A59, Playnite #C6F432) */
        public final int coral, lime;
        /** Ludique: the white surface (#FFFFFF / #221C3D) */
        public final int card;

        Lit(String id, boolean dark) {
            if (AFFICHE.equals(id)) {
                text2 = dark ? c("#EDEDE8") : c("#111111");
                muted2 = dark ? c("#A3A39C") : c("#555550");
                accentText = text2;
                chipBorder = text2;
                veil = dark ? c("#141414") : c("#ECECE8");
                band = dark ? c("#EDEDE8") : c("#111111");
                onBand = dark ? c("#141414") : c("#ECECE8");
                bandChip = band;
                onBandChip = onBand;
                bandLive = dark ? c("#7A6000") : YELLOW;
                purple = band;
                coral = c("#FF7A59");
                lime = c("#C6F432");
                card = veil;
            } else if (LUDIQUE.equals(id)) {
                text2 = dark ? c("#F3EEFF") : c("#1B1530");
                muted2 = dark ? c("#B3A9D6") : c("#5A527A");
                accentText = text2;
                chipBorder = dark ? c("#4A3F7A") : c("#B9AEE6");
                veil = dark ? c("#15112A") : c("#F3EEFF");
                band = dark ? c("#F3EEFF") : c("#1B1530");
                onBand = dark ? c("#221C3D") : c("#FFFFFF");
                bandChip = dark ? c("#DCD3FF") : c("#2E2650");
                onBandChip = dark ? c("#5A527A") : c("#D9D2F5");
                bandLive = dark ? c("#3F5A00") : c("#C6F432");
                purple = dark ? c("#8C77FF") : c("#5B3DF5");
                coral = c("#FF7A59");
                lime = c("#C6F432");
                card = dark ? c("#221C3D") : c("#FFFFFF");
            } else {
                text2 = dark ? c("#E7E0D8") : c("#3E3731");
                muted2 = dark ? c("#CFC6BC") : c("#6B6158");
                accentText = dark ? c("#FF8A57") : c("#C9480F");
                chipBorder = dark ? c("#5A524B") : c("#B8AA9B");
                veil = dark ? c("#0E0D0C") : c("#F6F1EA");
                band = dark ? c("#1A1816") : c("#FFFFFF");
                onBand = dark ? c("#F3EEE8") : c("#1E1A16");
                bandChip = dark ? c("#24201D") : c("#EFE7DD");
                onBandChip = muted2;
                bandLive = accentText;
                purple = c("#FF6A2B");
                coral = c("#FF7A59");
                lime = c("#C6F432");
                card = dark ? c("#1A1816") : c("#FFFFFF");
            }
        }
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

    /** Knob of a switch (prototype tk.onFg / tk.offFg), on the track {@link #on} / {@link #off} */
    public int switchKnob(boolean isOn) {
        if (AFFICHE.equals(id)) {
            return isOn ? (dark ? c("#111111") : c("#ECECE8")) : (dark ? c("#EDEDE8") : c("#111111"));
        }
        if (LUDIQUE.equals(id)) {
            return isOn ? c("#1B1530") : (dark ? c("#B3A9D6") : c("#5A527A"));
        }
        return isOn ? (dark ? c("#0E0D0C") : c("#FFFFFF")) : (dark ? c("#CFC6BC") : c("#6B6158"));
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
            tf = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? ResourcesCompat.getFont(context, resource(family, w))
                    : staticFont(context, family, w);
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
     * only: below, every weight would show the font's default instance (Archivo 600, Bricolage
     * 800, Space Grotesk 300). Those versions read static instances from assets/fonts instead
     * (scripts/v2-static-fonts.py): a regular, a bold, and the display weight of Affiche / Ludique.
     */
    private static final java.util.Map<String, Typeface> STATIC_FONTS = new java.util.HashMap<>();

    static int staticWeight(String family, int weight) {
        int[] available;
        switch (family) {
            case "archivo": available = new int[]{400, 700, 900}; break;
            case "bricolage": available = new int[]{400, 700, 800}; break;
            default: available = new int[]{400, 700}; break;
        }
        int best = 400;
        int bestDistance = Integer.MAX_VALUE;
        for (int w : available) {
            // A tie goes to the heavier one: 500 / 600 text keeps more weight than the body
            int d = Math.abs(w - weight);
            if (d < bestDistance || (d == bestDistance && w > best)) {
                bestDistance = d;
                best = w;
            }
        }
        return best;
    }

    private static Typeface staticFont(Context context, String family, int weight) {
        String path = "fonts/v2_" + family + "_" + staticWeight(family, weight) + ".ttf";
        synchronized (STATIC_FONTS) {
            Typeface tf = STATIC_FONTS.get(path);
            if (tf == null) {
                tf = Typeface.createFromAsset(context.getAssets(), path);
                STATIC_FONTS.put(path, tf);
            }
            return tf;
        }
    }
}
