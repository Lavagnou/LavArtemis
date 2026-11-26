package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** « Plus » of the in-game menu and the « Menu » pill (prototype MORE_AN, moreIds, pill rule). */
public class StreamMenuTest {
    @Test
    public void plusListsThePrototypeEntriesInItsOrder() {
        // V8-29: voice button shown, a game inside Playnite
        List<StreamMenu.More> all = StreamMenu.more(true, true, true, true);
        assertEquals(Arrays.asList(StreamMenu.More.MIC, StreamMenu.More.VOICE, StreamMenu.More.VOICE_MOVE,
                StreamMenu.More.ROTATE, StreamMenu.More.ON_SCREEN_CONTROLLER, StreamMenu.More.SPECIAL_KEYS,
                StreamMenu.More.FULL_KEYBOARD, StreamMenu.More.TOUCH_SENSITIVITY, StreamMenu.More.QUIT_SESSION,
                StreamMenu.More.MENU_PILL), all);
    }

    @Test
    public void plusDropsWhatDoesNotApply() {
        List<StreamMenu.More> few = StreamMenu.more(false, false, false, false);
        assertFalse(few.contains(StreamMenu.More.VOICE_MOVE));
        assertFalse(few.contains(StreamMenu.More.ROTATE));
        assertFalse(few.contains(StreamMenu.More.ON_SCREEN_CONTROLLER));
        // « Quitter » already quits the session when no game runs inside Playnite
        assertFalse(few.contains(StreamMenu.More.QUIT_SESSION));
        assertTrue(few.contains(StreamMenu.More.MIC));
        assertTrue(few.contains(StreamMenu.More.VOICE));
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
