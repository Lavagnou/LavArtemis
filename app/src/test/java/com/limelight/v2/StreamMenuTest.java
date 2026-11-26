package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** « Plus » of the in-game menu and the « Menu » pill (prototype MORE_AN, moreIds, pill rule). */
public class StreamMenuTest {
    /** Menu en jeu (atelier du 2026-11-26): the tiles in the PC's order, Quitter {jeu} for a Playnite game only */
    @Test
    public void tilesFollowTheWorkshopOrder() {
        assertEquals(Arrays.asList(StreamMenu.Tile.RESUME, StreamMenu.Tile.DISCONNECT, StreamMenu.Tile.QUIT_GAME,
                StreamMenu.Tile.QUIT_SESSION, StreamMenu.Tile.TOUCH_MODE, StreamMenu.Tile.KEYBOARD, StreamMenu.Tile.MIC,
                StreamMenu.Tile.STATS, StreamMenu.Tile.MORE), StreamMenu.tiles(true, true));
    }

    @Test
    public void noQuitGameTileOutsidePlaynite() {
        // Bureau or Playnite Fullscreen: « Quitter la session » is the only way out (no duplicate)
        List<StreamMenu.Tile> desktop = StreamMenu.tiles(false, true);
        assertFalse(desktop.contains(StreamMenu.Tile.QUIT_GAME));
        assertEquals(Arrays.asList(StreamMenu.Tile.RESUME, StreamMenu.Tile.DISCONNECT, StreamMenu.Tile.QUIT_SESSION,
                StreamMenu.Tile.TOUCH_MODE, StreamMenu.Tile.KEYBOARD, StreamMenu.Tile.MIC, StreamMenu.Tile.STATS,
                StreamMenu.Tile.MORE), desktop);
        // External display: the touch mode is imposed (Trackpad), no tile to switch it
        assertFalse(StreamMenu.tiles(false, false).contains(StreamMenu.Tile.TOUCH_MODE));
    }

    @Test
    public void plusStartsWithTheCommonEntriesThenAndroidOnes() {
        List<StreamMenu.More> all = StreamMenu.more(true, true, true);
        assertEquals(Arrays.asList(StreamMenu.More.CLIPBOARD, StreamMenu.More.SERVER_COMMANDS, StreamMenu.More.ZOOM,
                StreamMenu.More.VOICE, StreamMenu.More.VOICE_MOVE, StreamMenu.More.ROTATE,
                StreamMenu.More.ON_SCREEN_CONTROLLER, StreamMenu.More.SPECIAL_KEYS, StreamMenu.More.FULL_KEYBOARD,
                StreamMenu.More.TOUCH_SENSITIVITY, StreamMenu.More.MENU_PILL), all);
    }

    @Test
    public void plusDropsWhatDoesNotApply() {
        List<StreamMenu.More> few = StreamMenu.more(false, false, false);
        assertFalse(few.contains(StreamMenu.More.VOICE_MOVE));
        assertFalse(few.contains(StreamMenu.More.ROTATE));
        assertFalse(few.contains(StreamMenu.More.ON_SCREEN_CONTROLLER));
        assertTrue(few.contains(StreamMenu.More.VOICE));
        assertTrue(few.contains(StreamMenu.More.CLIPBOARD));
    }

    @Test
    public void sendKeysIsGoneAndNothingIsInBothPlaces() {
        for (StreamMenu.More m : StreamMenu.More.values()) {
            // The micro and « Quitter la session » are tiles now; « Envoyer des touches » is retired
            assertFalse(m.name(), m.name().equals("MIC") || m.name().equals("QUIT_SESSION") || m.name().contains("KEYS") && !m.name().equals("SPECIAL_KEYS"));
        }
        for (StreamMenu.Tile t : StreamMenu.Tile.values()) {
            assertFalse(t.name(), t.name().contains("KEYS") || t.name().equals("ZOOM") || t.name().equals("CLIPBOARD"));
        }
    }

    /** Workshop: an icon before every entry of « Plus », as on the tiles */
    @Test
    public void everyPlusEntryHasItsIcon() {
        for (StreamMenu.More m : StreamMenu.More.values()) {
            int icon = V2GameMenu.icon(m);
            assertTrue(m.name(), icon != 0 && icon != com.limelight.R.drawable.v2_ic_m_custom);
        }
    }

    @Test
    public void valuesFollowTheLabel() {
        assertEquals("Micro · Coupé", StreamMenu.withValue("Micro", "Coupé"));
        assertEquals("Pivoter l’écran", StreamMenu.withValue("Pivoter l’écran", ""));
    }

    @Test
    public void pillHidesAtTheGamepad() {
        assertTrue(StreamMenu.pillVisible(true, false, V2Input.Kind.TOUCH));
        assertTrue(StreamMenu.pillVisible(true, false, V2Input.Kind.KEYS));
        assertFalse(StreamMenu.pillVisible(true, false, V2Input.Kind.PAD));
        assertFalse(StreamMenu.pillVisible(false, false, V2Input.Kind.TOUCH));
        assertFalse(StreamMenu.pillVisible(true, true, V2Input.Kind.TOUCH));
        assertEquals(68, StreamMenu.statsTopPx(true));
        assertEquals(16, StreamMenu.statsTopPx(false));
    }

    @Test
    public void sheetColumnsAndWidth() {
        assertEquals(1, V2Sheet.columns(8));
        assertEquals(2, V2Sheet.columns(10));
        assertEquals(3, V2Sheet.columns(16));
        assertEquals(520, V2Sheet.boxWidthPx(false, false, 1));
        assertEquals(770, V2Sheet.boxWidthPx(false, false, 2));
        assertEquals(1060, V2Sheet.boxWidthPx(false, true, 3));
    }
}
