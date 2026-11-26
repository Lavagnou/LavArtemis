package com.limelight.v2;

import com.limelight.nvstream.http.NvApp;

/**
 * What launching an app or a game means while something may run (prototype running(), inPlaynite(),
 * closes()). Pure logic, tested by SessionChoiceTest.
 * <ul>
 * <li>running: the target is exactly what runs (the app, or the game played inside Playnite):
 * launching it rejoins the session.</li>
 * <li>inPlaynite: a game picked while Playnite Fullscreen runs starts inside it, the stream goes
 * on; it closes the game Playnite was running, if any.</li>
 * <li>closes: the launch would close something: the options open with the warning first.</li>
 * </ul>
 */
public final class SessionChoice {
    public final boolean running;
    public final boolean inPlaynite;
    public final boolean closes;

    private SessionChoice(boolean running, boolean inPlaynite, boolean closes) {
        this.running = running;
        this.inPlaynite = inPlaynite;
        this.closes = closes;
    }

    public static SessionChoice of(HomeModel m, NvApp app) {
        if (m == null || app == null || !m.sessionRunning()) {
            return new SessionChoice(false, false, false);
        }
        boolean game = HomeModel.kindOf(app) == HomeModel.Kind.GAME;
        boolean running;
        if (game && m.runningKind() == HomeModel.Kind.PLAYNITE) {
            // Inside Playnite, the game played there is the target that runs
            running = !m.playniteGame.isEmpty() && m.playniteGame.equals(app.getAppName());
        } else {
            running = app.getAppId() == m.runningId;
        }
        if (running) {
            return new SessionChoice(true, false, false);
        }
        boolean inPlaynite = game && m.playniteHostRunning && m.runningKind() == HomeModel.Kind.PLAYNITE;
        boolean closes = inPlaynite ? !m.playniteGame.isEmpty() : true;
        return new SessionChoice(false, inPlaynite, closes);
    }
}
