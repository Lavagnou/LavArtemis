package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.nvstream.http.NvApp;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The details screen: its note, the lock of the Écran row, and the steps of its rows (prototype launch). */
public class LaunchRowsTest {
    private static final NvApp DESKTOP = new NvApp("Desktop", "d", 1, false);
    private static final NvApp PLAYNITE = new NvApp("Playnite", "p", 2, false);
    private static final NvApp RDR = new NvApp("Red Dead Redemption 2", "g1", 100, false);
    private static final NvApp SPLIT = new NvApp("Split Fiction", "g2", 101, false);

    static {
        PLAYNITE.setPlayniteHost(true);
        RDR.setPlayniteGame(true);
        SPLIT.setPlayniteGame(true);
    }

    private static List<NvApp> apps() {
        return Arrays.asList(DESKTOP, PLAYNITE, RDR, SPLIT);
    }

    @Test
    public void notesFollowWhatRuns() {
        HomeModel idle = HomeModel.build(apps(), 0, "", false, null, false);
        assertEquals(LaunchRows.Note.GAME, LaunchRows.note(idle, SPLIT));
        assertEquals(LaunchRows.Note.APP, LaunchRows.note(idle, DESKTOP));
        assertFalse(LaunchRows.warns(idle, SPLIT));

        // V8-68: RDR2 inside Playnite, Split Fiction picked: it starts inside, RDR2 closes (warning)
        HomeModel inside = HomeModel.build(apps(), 2, "Red Dead Redemption 2", true, null, false);
        assertEquals(LaunchRows.Note.INSIDE, LaunchRows.note(inside, SPLIT));
        assertTrue(LaunchRows.warns(inside, SPLIT));
        assertTrue(LaunchRows.joining(inside, SPLIT));
        assertEquals(LaunchRows.Note.RUNNING, LaunchRows.note(inside, RDR));
        assertFalse(LaunchRows.warns(inside, RDR));

        // The desktop runs: anything else closes it, the Écran row is free
        HomeModel desk = HomeModel.build(apps(), 1, "", false, null, false);
        assertEquals(LaunchRows.Note.CLOSES, LaunchRows.note(desk, SPLIT));
        assertTrue(LaunchRows.warns(desk, SPLIT));
        assertFalse(LaunchRows.joining(desk, SPLIT));
        assertTrue(LaunchRows.joining(desk, DESKTOP));
    }

    @Test
    public void codecWrapsInThePrototypeOrder() {
        assertEquals("forceav1", LaunchRows.nextCodec("auto", 1));
        assertEquals("neverh265", LaunchRows.nextCodec("auto", -1));
        assertEquals("auto", LaunchRows.nextCodec("neverh265", 1));
        // PyroWave counts as Auto in this row
        assertEquals("forceav1", LaunchRows.nextCodec("forcepyrowave", 1));
    }

    @Test
    public void bitrateStepsAreClamped() {
        assertEquals(80000, LaunchRows.nextRate(50000, 1));
        assertEquals(100000, LaunchRows.nextRate(100000, 1));
        assertEquals(15000, LaunchRows.nextRate(15000, -1));
        // From between two steps: the next one in that direction
        assertEquals(30000, LaunchRows.nextRate(20000, 1));
        assertEquals(15000, LaunchRows.nextRate(20000, -1));
        assertEquals(100000, LaunchRows.nextRate(400000, 1));
        assertEquals(100000, LaunchRows.nextRate(400000, -1));
        assertEquals(1, LaunchRows.rateIndex(29000));
    }

    @Test
    public void profilesWrap() {
        assertEquals(0, LaunchRows.wrap(2, 3, 1));
        assertEquals(2, LaunchRows.wrap(0, 3, -1));
        assertEquals(0, LaunchRows.wrap(0, 0, 1));
    }
}
