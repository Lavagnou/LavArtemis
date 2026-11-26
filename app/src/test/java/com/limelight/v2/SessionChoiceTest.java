package com.limelight.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.NvApp;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Launching while something runs: join, start inside Playnite, or warn first (prototype quick()). */
public class SessionChoiceTest {
    private static final NvApp DESKTOP = new NvApp("Desktop", "d", 1, false);
    private static final NvApp PLAYNITE = new NvApp("Playnite", "p", 2, false);
    private static final NvApp RDR = new NvApp("Red Dead Redemption 2", "g1", 100, false);
    private static final NvApp HADES = new NvApp("Hades II", "g2", 101, false);

    static {
        PLAYNITE.setPlayniteHost(true);
        RDR.setPlayniteGame(true);
        HADES.setPlayniteGame(true);
    }

    private static List<NvApp> apps() {
        return Arrays.asList(DESKTOP, PLAYNITE, RDR, HADES);
    }

    @Test
    public void nothingRunsNothingCloses() {
        HomeModel m = HomeModel.build(apps(), 0, "", false, null, false);
        SessionChoice sc = SessionChoice.of(m, RDR);
        assertFalse(sc.running);
        assertFalse(sc.closes);
    }

    @Test
    public void theGamePlayedInsidePlayniteIsJoined() {
        HomeModel m = HomeModel.build(apps(), 2, "Red Dead Redemption 2", true, null, false);
        assertTrue(SessionChoice.of(m, RDR).running);
        assertTrue(SessionChoice.of(m, PLAYNITE).running);
        assertFalse(SessionChoice.of(m, RDR).closes);
    }

    @Test
    public void anotherGameStartsInsidePlayniteAndClosesTheCurrentOne() {
        HomeModel m = HomeModel.build(apps(), 2, "Red Dead Redemption 2", true, null, false);
        SessionChoice sc = SessionChoice.of(m, HADES);
        assertTrue(sc.inPlaynite);
        assertTrue(sc.closes);
        // Playnite open on its home: the game starts inside it, nothing is closed
        HomeModel idle = HomeModel.build(apps(), 2, "", true, null, false);
        SessionChoice sc2 = SessionChoice.of(idle, HADES);
        assertTrue(sc2.inPlaynite);
        assertFalse(sc2.closes);
    }

    @Test
    public void theDesktopIsClosedFirst() {
        HomeModel m = HomeModel.build(apps(), 1, "", false, null, false);
        assertTrue(SessionChoice.of(m, RDR).closes);
        assertTrue(SessionChoice.of(m, PLAYNITE).closes);
        assertTrue(SessionChoice.of(m, DESKTOP).running);
    }
}
