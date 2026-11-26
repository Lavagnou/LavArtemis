package com.limelight.v2;

import com.limelight.nvstream.http.NvApp;

/**
 * The rules of the details screen (prototype « launch », V8-67 / V8-68; design-port-spec §1.7),
 * without Android: which note it shows, its launch button, and how « Réglages de ce lancement »
 * (Profil, Écran, Codec, Débit) step through their values. The rows change the real settings, as
 * on the desktop (DetailsView.qml): the app has no per-launch settings.
 */
public final class LaunchRows {
    private LaunchRows() {}

    /** Codec values of the row, in the prototype's order: Auto (HEVC), AV1, HEVC, H.264 */
    public static final String[] CODECS = {"auto", "forceav1", "forceh265", "neverh265"};
    /** Bitrates of the row (prototype RATES), kbit/s */
    public static final int[] RATES_KBPS = {15000, 30000, 50000, 80000, 100000};

    /** What the note says (prototype note, logic of launch) */
    public enum Note {
        /** It runs: you join it as it is */
        RUNNING,
        /** A Playnite game while Playnite runs: it starts inside (closing the game in it, if any) */
        INSIDE,
        /** Something else runs and will be closed (warning) */
        CLOSES,
        /** Nothing runs, a game */
        GAME,
        /** Nothing runs, an app */
        APP
    }

    public static Note note(HomeModel m, NvApp app) {
        SessionChoice sc = SessionChoice.of(m, app);
        if (sc.running) {
            return Note.RUNNING;
        }
        if (sc.inPlaynite) {
            return Note.INSIDE;
        }
        if (m.sessionRunning()) {
            return Note.CLOSES;
        }
        return HomeModel.kindOf(app) == HomeModel.Kind.GAME ? Note.GAME : Note.APP;
    }

    /** The note is a warning (prototype closes()): the launch closes something */
    public static boolean warns(HomeModel m, NvApp app) {
        return SessionChoice.of(m, app).closes;
    }

    /** The PC's screen is imposed (prototype joining()): you join a session, which keeps its display */
    public static boolean joining(HomeModel m, NvApp app) {
        SessionChoice sc = SessionChoice.of(m, app);
        return sc.running || sc.inPlaynite;
    }

    /** Index in a list of n values after a step of d, wrapping (Profil, Codec) */
    public static int wrap(int index, int n, int d) {
        if (n <= 0) {
            return 0;
        }
        return ((index + d) % n + n) % n;
    }

    /** Position of a codec in the row; PyroWave (expert only) and unknown values count as Auto */
    public static int codecIndex(String value) {
        for (int i = 0; i < CODECS.length; i++) {
            if (CODECS[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    public static String nextCodec(String value, int d) {
        return CODECS[wrap(codecIndex(value), CODECS.length, d)];
    }

    /** The bitrate of the row nearest to a stored one */
    public static int rateIndex(int kbps) {
        int best = 0;
        for (int i = 1; i < RATES_KBPS.length; i++) {
            if (Math.abs(RATES_KBPS[i] - kbps) < Math.abs(RATES_KBPS[best] - kbps)) {
                best = i;
            }
        }
        return best;
    }

    /**
     * The next bitrate, clamped (prototype: no wrap). From a value between two steps, a step up
     * goes to the next one above it and a step down to the next one below.
     */
    public static int nextRate(int kbps, int d) {
        if (d > 0) {
            for (int r : RATES_KBPS) {
                if (r > kbps) {
                    return r;
                }
            }
            return RATES_KBPS[RATES_KBPS.length - 1];
        }
        if (d < 0) {
            for (int i = RATES_KBPS.length - 1; i >= 0; i--) {
                if (RATES_KBPS[i] < kbps) {
                    return RATES_KBPS[i];
                }
            }
            return RATES_KBPS[0];
        }
        return RATES_KBPS[rateIndex(kbps)];
    }
}
