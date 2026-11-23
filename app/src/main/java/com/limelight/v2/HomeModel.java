package com.limelight.v2;

import com.limelight.nvstream.http.NvApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the v2 home shows for one host, computed from plain data (the host's app list in host
 * order, what runs, the hidden games). No Android here: the rules are tested on the JVM.
 * Same rules as the desktop home (LavArtemis-Qt AppModel::kindOf, startsInsidePlaynite, HomeView).
 */
public final class HomeModel {
    /** Number of covers in the home's "Récents" row */
    public static final int RECENTS_MAX = 10;

    public enum Kind { DESKTOP, PLAYNITE, GAME, APP }

    public enum Launch {
        /** Nothing runs: start it */
        START,
        /** It is the running app: join it */
        RESUME,
        /** A Playnite game while Playnite runs: LavApollo starts it inside Playnite, nothing quits */
        INSIDE_PLAYNITE,
        /** Something else runs: ask before quitting it */
        ASK_QUIT
    }

    public final List<NvApp> apps;
    /** Bureau then Playnite Fullscreen cards (hidden apps ignored: these cards always show) */
    public final List<NvApp> cards;
    /** Playnite games, host order (most recently played first), hidden ones only when shown */
    public final List<NvApp> games;
    /** First games of the library, hidden ones never */
    public final List<NvApp> recents;
    public final int hiddenCount;
    public final int runningId;
    /** Game running inside Playnite ("" when none) */
    public final String playniteGame;
    public final boolean playniteHostRunning;
    private final Set<Integer> hidden;

    private HomeModel(List<NvApp> apps, int runningId, String playniteGame, boolean playniteHostRunning,
                      Set<Integer> hidden, boolean showHidden) {
        this.apps = apps;
        this.runningId = runningId;
        this.playniteGame = playniteGame == null ? "" : playniteGame;
        this.playniteHostRunning = playniteHostRunning;
        this.hidden = hidden;

        List<NvApp> desktop = new ArrayList<>();
        List<NvApp> playnite = new ArrayList<>();
        List<NvApp> allGames = new ArrayList<>();
        List<NvApp> shownGames = new ArrayList<>();
        int hiddenGames = 0;
        for (NvApp app : apps) {
            switch (kindOf(app)) {
                case DESKTOP:
                    desktop.add(app);
                    break;
                case PLAYNITE:
                    playnite.add(app);
                    break;
                case GAME:
                    allGames.add(app);
                    boolean isHidden = hidden.contains(app.getAppId());
                    if (isHidden) {
                        hiddenGames++;
                    }
                    if (!isHidden || showHidden) {
                        shownGames.add(app);
                    }
                    break;
                default:
                    // The host's other apps are not shown (the home holds only these sections)
                    break;
            }
        }
        List<NvApp> c = new ArrayList<>(desktop);
        c.addAll(playnite);
        cards = Collections.unmodifiableList(c);
        games = Collections.unmodifiableList(shownGames);

        List<NvApp> r = new ArrayList<>();
        for (NvApp g : allGames) {
            if (r.size() >= RECENTS_MAX) {
                break;
            }
            if (!hidden.contains(g.getAppId())) {
                r.add(g);
            }
        }
        recents = Collections.unmodifiableList(r);
        hiddenCount = hiddenGames;
    }

    public static HomeModel build(List<NvApp> apps, int runningId, String playniteGame,
                                  boolean playniteHostRunning, Set<Integer> hidden, boolean showHidden) {
        return new HomeModel(apps == null ? Collections.<NvApp>emptyList() : new ArrayList<>(apps),
                runningId, playniteGame, playniteHostRunning,
                hidden == null ? Collections.<Integer>emptySet() : hidden, showHidden);
    }

    /** Port of AppModel::kindOf (desktop). */
    public static Kind kindOf(NvApp app) {
        if (app.isPlayniteGame()) {
            return Kind.GAME;
        }
        String name = app.getAppName() == null ? "" : app.getAppName().toLowerCase(Locale.ROOT);
        if (app.isPlayniteHost() || name.contains("playnite")) {
            return Kind.PLAYNITE;
        }
        if (name.startsWith("desktop") || name.startsWith("bureau")) {
            return Kind.DESKTOP;
        }
        return Kind.APP;
    }

    public boolean sessionRunning() {
        return runningId != 0;
    }

    /** The running app when it is in the list */
    public NvApp runningApp() {
        if (runningId == 0) {
            return null;
        }
        for (NvApp app : apps) {
            if (app.getAppId() == runningId) {
                return app;
            }
        }
        return null;
    }

    public Kind runningKind() {
        NvApp app = runningApp();
        return app == null ? null : kindOf(app);
    }

    /**
     * What the Scène shows: the game played inside Playnite, else the card's name for Bureau and
     * Playnite Fullscreen, else the app's own name.
     */
    public String sceneTitle(String desktopLabel, String playniteLabel) {
        if (!playniteGame.isEmpty() && sessionRunning()) {
            return playniteGame;
        }
        return runningTitle(desktopLabel, playniteLabel);
    }

    /** The running app as the cards name it ("Desktop" is shown as Bureau) */
    public String runningTitle(String desktopLabel, String playniteLabel) {
        NvApp app = runningApp();
        if (app == null) {
            return "";
        }
        Kind kind = kindOf(app);
        if (kind == Kind.DESKTOP) {
            return desktopLabel;
        }
        if (kind == Kind.PLAYNITE) {
            return playniteLabel;
        }
        return app.getAppName();
    }

    /** The running app glows; so does the game running inside Playnite (matched by name) */
    public boolean isRunning(NvApp app) {
        if (runningId == 0) {
            return false;
        }
        if (app.getAppId() == runningId) {
            return true;
        }
        return kindOf(app) == Kind.GAME && !playniteGame.isEmpty() && playniteGame.equals(app.getAppName());
    }

    /** Projecteur: while a session runs, everything else is dimmed */
    public boolean isDimmed(NvApp app) {
        return sessionRunning() && !isRunning(app);
    }

    public boolean isHidden(NvApp app) {
        return hidden.contains(app.getAppId());
    }

    /** AppModel::startsInsidePlaynite */
    public boolean startsInsidePlaynite(NvApp app) {
        return app.isPlayniteGame() && playniteHostRunning && runningId != 0 && runningId != app.getAppId();
    }

    public Launch decide(NvApp app) {
        if (runningId == 0) {
            return Launch.START;
        }
        if (app.getAppId() == runningId) {
            return Launch.RESUME;
        }
        if (startsInsidePlaynite(app)) {
            return Launch.INSIDE_PLAYNITE;
        }
        return Launch.ASK_QUIT;
    }

    /** Backdrop of the Scène: the game inside Playnite when known, else the running app */
    public NvApp backdropApp() {
        if (!playniteGame.isEmpty()) {
            for (NvApp app : apps) {
                if (kindOf(app) == Kind.GAME && playniteGame.equals(app.getAppName())) {
                    return app;
                }
            }
        }
        return runningApp();
    }
}
