package com.limelight.v2;

import android.graphics.Bitmap;

import com.limelight.R;

/**
 * The prototype's segue before the stream (goSegue 'start' / 'resume', planches V8-24 and V8-71) and
 * the toast that follows it (launch()). A v2 screen prepares it just before starting Game, which shows
 * it as its connection screen (Dev, instead of the spinner) and takes it down, with the toast, when the
 * stream is up and the prototype's 1.5 s have passed. A process-wide hand-off: bitmaps do not travel in
 * an intent. The choice of texts is pure logic, tested by PendingSegueTest.
 */
public final class PendingSegue {
    public enum Mode {
        /** Something new starts (« Lancement sur ATLAS-PC… », « Connecté à ATLAS-PC ») */
        START,
        /** The running session is joined (« Reprise sur … » / « Connexion à la session de … ») */
        RESUME,
        /** A game starts inside Playnite Fullscreen, already running */
        INSIDE,
        /** The session was quit and starts again for this device */
        RELAUNCH
    }

    /** Prototype goSegue: 1500 ms before the stream shows (1100 for a quit) */
    public static final long MIN_MS = 1500;
    /** A segue older than this was not for the Game that starts now */
    static final long STALE_MS = 30_000;

    public String line = "", title = "", toast = "", tip = "";
    public Bitmap cover, background;
    public boolean blur;
    public long createdAt;

    private static PendingSegue pending;

    public static synchronized void set(PendingSegue s) {
        pending = s;
    }

    /** The prepared segue, once (null when none, or too old) */
    public static synchronized PendingSegue take(long now) {
        PendingSegue s = pending;
        pending = null;
        return s != null && now - s.createdAt <= STALE_MS && now >= s.createdAt ? s : null;
    }

    /** « Lancement sur %1$s… », « Reprise sur %1$s… » or « Connexion à la session de %1$s… » */
    public static int lineRes(Mode mode, boolean otherDevice) {
        if (mode == Mode.RESUME) {
            return otherDevice ? R.string.v2_segue_join : R.string.v2_segue_resume;
        }
        return R.string.v2_segue_launch;
    }

    /** The toast once the stream shows (prototype launch() msg) */
    public static int toastRes(Mode mode, boolean otherDevice) {
        switch (mode) {
            case RESUME:
                return otherDevice ? R.string.v2_toast_joined : R.string.v2_toast_resumed;
            case INSIDE:
                return R.string.v2_launched_inside;
            case RELAUNCH:
                return R.string.v2_toast_relaunched;
            default:
                return R.string.v2_toast_connected;
        }
    }

    /** How long the segue still has to show when the stream is up (never negative) */
    public static long remaining(long createdAt, long now) {
        return Math.max(0, Math.min(MIN_MS, createdAt + MIN_MS - now));
    }
}
