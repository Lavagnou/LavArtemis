package com.limelight.utils;

/**
 * Whether the app running on the host is quit when a stream ends.
 *
 * <p>« Quitter la session » always quits it. Otherwise « Quitter l'appli sur le PC distant à la
 * fin » decides, as on the desktop (Session's DeferredSessionCleanupTask: {@code quitAppAfter}
 * and not an unexpected termination): a stream that ended on an error leaves the game running,
 * so the user can come back to it. Never for a benchmark run (the desktop's runner turns it off)
 * nor for the reconnection that follows a change of the host's display mode (Game.restartStream),
 * which must find the session still there.
 */
public final class StreamEndPolicy {
    private StreamEndPolicy() {}

    public static boolean quitHostApp(boolean askedToQuit, boolean quitAppAfter, boolean unexpectedTermination,
                                      boolean benchmarkRun, boolean reconnecting) {
        if (askedToQuit) {
            return true;
        }
        return quitAppAfter && !unexpectedTermination && !benchmarkRun && !reconnecting;
    }
}
