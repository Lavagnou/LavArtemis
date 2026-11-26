package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.NvApp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The home's sections and launch rules (port of the desktop's AppModel / HomeView), on plain data. */
public class HomeModelTest {
    static NvApp app(String name, int id) {
        return new NvApp(name, "uuid-" + id, id, false);
    }

    static NvApp game(String name, int id) {
        NvApp a = app(name, id);
        a.setPlayniteGame(true);
        return a;
    }

    static NvApp playniteHost(String name, int id) {
        NvApp a = app(name, id);
        a.setPlayniteHost(true);
        return a;
    }

    private static List<NvApp> hostList() {
        List<NvApp> apps = new ArrayList<>();
        apps.add(app("Desktop", 1));
        apps.add(app("Steam Big Picture", 2));
        apps.add(playniteHost("Playnite", 3));
        apps.add(app("Virtual Desktop", 4));
        // Host order = most recently played first
        for (int i = 0; i < 14; i++) {
            apps.add(game("Game " + i, 100 + i));
        }
        return apps;
    }

    @Test
    public void kindsFollowTheDesktopRules() {
        assertEquals(HomeModel.Kind.GAME, HomeModel.kindOf(game("Playnite Launcher Game", 9)));
        assertEquals(HomeModel.Kind.PLAYNITE, HomeModel.kindOf(playniteHost("Fullscreen", 9)));
        assertEquals(HomeModel.Kind.PLAYNITE, HomeModel.kindOf(app("My PLAYNITE", 9)));
        assertEquals(HomeModel.Kind.DESKTOP, HomeModel.kindOf(app("Desktop", 9)));
        assertEquals(HomeModel.Kind.DESKTOP, HomeModel.kindOf(app("desktop (HDR)", 9)));
        assertEquals(HomeModel.Kind.DESKTOP, HomeModel.kindOf(app("Bureau", 9)));
        assertEquals(HomeModel.Kind.APP, HomeModel.kindOf(app("Virtual Desktop", 9)));
        assertEquals(HomeModel.Kind.APP, HomeModel.kindOf(app("Steam Big Picture", 9)));
    }

    @Test
    public void cardsAreBureauThenPlayniteAndOtherAppsAreNotShown() {
        HomeModel m = HomeModel.build(hostList(), 0, null, false, null, false);
        assertEquals(2, m.cards.size());
        assertEquals("Desktop", m.cards.get(0).getAppName());
        assertEquals("Playnite", m.cards.get(1).getAppName());
        for (NvApp g : m.games) {
            assertTrue(g.isPlayniteGame());
        }
        assertEquals(14, m.games.size());
    }

    @Test
    public void recentsKeepHostOrderAndStopAtTen() {
        HomeModel m = HomeModel.build(hostList(), 0, null, false, null, false);
        assertEquals(HomeModel.RECENTS_MAX, m.recents.size());
        for (int i = 0; i < m.recents.size(); i++) {
            assertEquals(100 + i, m.recents.get(i).getAppId());
        }
    }

    @Test
    public void hiddenGamesLeaveRecentsAndTheLibraryUntilShown() {
        Set<Integer> hidden = new HashSet<>(Arrays.asList(100, 102));
        HomeModel m = HomeModel.build(hostList(), 0, null, false, hidden, false);
        assertEquals(2, m.hiddenCount);
        assertEquals(12, m.games.size());
        assertEquals(101, m.recents.get(0).getAppId());
        assertEquals(103, m.recents.get(1).getAppId());

        HomeModel shown = HomeModel.build(hostList(), 0, null, false, hidden, true);
        assertEquals(14, shown.games.size());
        assertTrue(shown.isHidden(shown.games.get(0)));
        // The home's row never shows them, even when the library does
        assertEquals(101, shown.recents.get(0).getAppId());
        assertEquals(2, shown.hiddenCount);
    }

    @Test
    public void hiddenDesktopStillShowsItsCard() {
        HomeModel m = HomeModel.build(hostList(), 0, null, false, Collections.singleton(1), false);
        assertEquals("Desktop", m.cards.get(0).getAppName());
        assertEquals(0, m.hiddenCount);
    }

    @Test
    public void nothingRunningMeansNothingDimmed() {
        HomeModel m = HomeModel.build(hostList(), 0, null, false, null, false);
        assertFalse(m.sessionRunning());
        for (NvApp a : m.apps) {
            assertFalse(m.isDimmed(a));
            assertFalse(m.isRunning(a));
            assertEquals(HomeModel.Launch.START, m.decide(a));
        }
        assertNull(m.runningApp());
    }

    @Test
    public void projecteurDimsAllButTheRunningApp() {
        HomeModel m = HomeModel.build(hostList(), 1, null, false, null, false);
        assertTrue(m.isRunning(m.cards.get(0)));
        assertFalse(m.isDimmed(m.cards.get(0)));
        assertTrue(m.isDimmed(m.cards.get(1)));
        assertTrue(m.isDimmed(m.recents.get(0)));
        assertEquals("Bureau", m.sceneTitle("Bureau", "Playnite Fullscreen"));
        assertEquals(HomeModel.Kind.DESKTOP, m.runningKind());
    }

    @Test
    public void theGameInsidePlayniteGlowsWithPlaynite() {
        HomeModel m = HomeModel.build(hostList(), 3, "Game 4", true, null, false);
        NvApp playnite = m.cards.get(1);
        NvApp game4 = m.games.get(4);
        assertTrue(m.isRunning(playnite));
        assertTrue(m.isRunning(game4));
        assertFalse(m.isDimmed(game4));
        assertTrue(m.isDimmed(m.games.get(0)));
        assertEquals("Game 4", m.sceneTitle("Bureau", "Playnite Fullscreen"));
        assertEquals("Playnite Fullscreen", m.runningTitle("Bureau", "Playnite Fullscreen"));
        assertSame(game4, m.backdropApp());
    }

    @Test
    public void launchDecisions() {
        // Bureau runs: another app asks first, Bureau itself resumes
        HomeModel desktop = HomeModel.build(hostList(), 1, null, false, null, false);
        assertEquals(HomeModel.Launch.RESUME, desktop.decide(desktop.cards.get(0)));
        assertEquals(HomeModel.Launch.ASK_QUIT, desktop.decide(desktop.cards.get(1)));
        assertEquals(HomeModel.Launch.ASK_QUIT, desktop.decide(desktop.games.get(0)));

        // Playnite runs on the host: a Playnite game starts inside it, nothing quits
        HomeModel playnite = HomeModel.build(hostList(), 3, "", true, null, false);
        assertEquals(HomeModel.Launch.INSIDE_PLAYNITE, playnite.decide(playnite.games.get(0)));
        assertEquals(HomeModel.Launch.ASK_QUIT, playnite.decide(playnite.cards.get(0)));
        assertEquals(HomeModel.Launch.RESUME, playnite.decide(playnite.cards.get(1)));

        // Same game list, but Playnite is not running on the host: asking first again
        HomeModel noPlaynite = HomeModel.build(hostList(), 1, "", false, null, false);
        assertEquals(HomeModel.Launch.ASK_QUIT, noPlaynite.decide(noPlaynite.games.get(0)));
    }

    @Test
    public void runningAppMissingFromTheList() {
        HomeModel m = HomeModel.build(hostList(), 999, null, false, null, false);
        assertTrue(m.sessionRunning());
        assertNull(m.runningApp());
        assertEquals("", m.sceneTitle("Bureau", "Playnite Fullscreen"));
        assertTrue(m.isDimmed(m.cards.get(0)));
    }

    static NvApp played(String name, int id, long lastActivity) {
        NvApp a = game(name, id);
        a.setPlayniteLastActivity(lastActivity);
        return a;
    }

    @Test
    public void recentsFollowTheLastActivity() {
        // The host's order is stale (LavApollo before wave 5 rebuilt it only when nothing ran): the
        // dates decide, never played last, equal dates keep the host's order
        List<NvApp> apps = new ArrayList<>();
        apps.add(app("Desktop", 1));
        apps.add(played("Old", 10, 1_700_000_000L));
        apps.add(played("Never A", 11, 0));
        apps.add(played("Just played", 12, 1_795_000_000L));
        apps.add(played("Never B", 13, 0));
        apps.add(played("Middle", 14, 1_750_000_000L));
        HomeModel m = HomeModel.build(apps, 0, null, false, null, false);
        List<String> names = new ArrayList<>();
        for (NvApp g : m.recents) {
            names.add(g.getAppName());
        }
        assertEquals(Arrays.asList("Just played", "Middle", "Old", "Never A", "Never B"), names);
        assertEquals("Just played", m.games.get(0).getAppName());
    }

    @Test
    public void anOlderHostKeepsItsOrder() {
        // No PlayniteLastActivity at all: the host's order, untouched
        HomeModel m = HomeModel.build(hostList(), 0, null, false, null, false);
        for (int i = 0; i < m.games.size(); i++) {
            assertEquals("Game " + i, m.games.get(i).getAppName());
        }
    }

    /** "N jeux" (Tout voir) counts the whole library, hidden games included, as the prototype's "266 jeux" */
    @Test
    public void libraryCountsHiddenGames() {
        Set<Integer> hidden = new HashSet<>(Arrays.asList(100, 101));
        HomeModel m = HomeModel.build(hostList(), 0, null, false, hidden, false);
        assertEquals(12, m.games.size());
        assertEquals(14, m.library.size());
        assertEquals(2, m.hiddenCount);
    }
}
