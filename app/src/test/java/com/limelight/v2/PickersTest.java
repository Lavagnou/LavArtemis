package com.limelight.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** « ✓ » in front of the current theme or PC (prototype themePick / hostPick). */
public class PickersTest {
    @Test
    public void currentEntryIsChecked() {
        assertEquals("✓ Braise", Pickers.checked("Braise", true));
        assertEquals("Affiche", Pickers.checked("Affiche", false));
    }
}
