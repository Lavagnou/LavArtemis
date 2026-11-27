package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The prototype's focus grid rule (logic move()), as FocusGrid ports it. */
public class FocusGridTest {
    /** The home with a session: header, Reprendre / Quitter, the two cards, 6 covers + Tout voir */
    private static final List<List<String>> HOME = Arrays.asList(
            Arrays.asList("nh", "ns"),
            Arrays.asList("resume", "squit"),
            Arrays.asList("a0", "a1"),
            Arrays.asList("r0", "r1", "r2", "r3", "r4", "r5", "lib"));

    @Test
    public void upAndDownKeepTheColumnClamped() {
        assertEquals("a1", FocusGrid.move(HOME, "r5", 0, -1));
        assertEquals("r1", FocusGrid.move(HOME, "a1", 0, 1));
        assertEquals("squit", FocusGrid.move(HOME, "a1", 0, -1));
        assertEquals("ns", FocusGrid.move(HOME, "squit", 0, -1));
        assertEquals("a1", FocusGrid.move(HOME, "lib", 0, -1));
    }

    @Test
    public void leftAndRightStayInTheRowWithoutWrapping() {
        assertEquals("r1", FocusGrid.move(HOME, "r0", 1, 0));
        assertEquals("r0", FocusGrid.move(HOME, "r0", -1, 0));
        assertEquals("lib", FocusGrid.move(HOME, "lib", 1, 0));
        assertEquals("a0", FocusGrid.move(HOME, "a1", -1, 0));
    }

    @Test
    public void edgesKeepTheFocus() {
        assertEquals("nh", FocusGrid.move(HOME, "nh", 0, -1));
        assertEquals("r3", FocusGrid.move(HOME, "r3", 0, 1));
    }

    @Test
    public void unknownFocusGoesToTheFirstItem() {
        assertEquals("nh", FocusGrid.move(HOME, "zz", 0, 1));
        assertEquals("nh", FocusGrid.move(HOME, null, 1, 0));
    }

    @Test
    public void aListOfOneItemPerRowIgnoresLeftAndRight() {
        // Bibliothèque Affiche, détails, dialogue à une colonne (L1, D1, O2): left / right do nothing
        List<List<String>> launch = Arrays.asList(Collections.singletonList("o0"), Collections.singletonList("o1"),
                Collections.singletonList("go"));
        assertEquals("o0", FocusGrid.move(launch, "o0", 1, 0));
        assertEquals("o1", FocusGrid.move(launch, "o1", -1, 0));
        assertEquals("go", FocusGrid.move(launch, "go", -1, 0));
        assertEquals("go", FocusGrid.move(launch, "o1", 0, 1));
    }

    @Test
    public void aSingleRowIgnoresUpAndDown() {
        // Mes PC on one row, the Ludique dock on one row (M3, G1)
        List<List<String>> hosts = Collections.singletonList(Arrays.asList("h0", "h1", "hadd"));
        assertEquals("h0", FocusGrid.move(hosts, "h0", 0, 1));
        assertEquals("h1", FocusGrid.move(hosts, "h1", 0, -1));
        assertEquals("hadd", FocusGrid.move(hosts, "h1", 1, 0));
    }

    @Test
    public void downToAShorterRowGoesToItsLastItem() {
        // Mes PC: 4 cards, then 2 (M4)
        List<List<String>> hosts = Arrays.asList(Arrays.asList("h0", "h1", "h2", "h3"), Arrays.asList("h4", "hadd"));
        assertEquals("hadd", FocusGrid.move(hosts, "h3", 0, 1));
        // Measured, left-aligned cards: the closest centre is the last one too
        assertEquals("hadd", FocusGrid.move(hosts, "h3", 0, 1, LEFT_ALIGNED));
        assertEquals("h4", FocusGrid.move(hosts, "h0", 0, 1, LEFT_ALIGNED));
    }

    /** Cards 100 wide, side by side from x = 0 */
    private static final FocusGrid.Centers<String> LEFT_ALIGNED = item -> {
        int i = Integer.parseInt(item.equals("hadd") ? "5" : item.substring(1));
        return (i % 4) * 100 + 50;
    };

    @Test
    public void aRowChangeGoesToTheClosestCentre() {
        // The home (A2): the two cards over the seven covers. Playnite (right) is over the 4th to 7th
        // covers, Bureau over the 1st to 3rd: Down from Playnite lands under it, not on the 2nd cover
        java.util.Map<String, Float> x = new java.util.HashMap<>();
        x.put("nh", 60f);
        x.put("ns", 1200f);
        x.put("resume", 150f);
        x.put("squit", 330f);
        x.put("a0", 300f);
        x.put("a1", 900f);
        for (int i = 0; i < 6; i++) {
            x.put("r" + i, 90f + i * 170f);
        }
        x.put("lib", 90f + 6 * 170f);
        FocusGrid.Centers<String> c = x::get;
        assertEquals("r5", FocusGrid.move(HOME, "a1", 0, 1, c));
        assertEquals("r1", FocusGrid.move(HOME, "a0", 0, 1, c));
        assertEquals("a0", FocusGrid.move(HOME, "r2", 0, -1, c));
        assertEquals("a1", FocusGrid.move(HOME, "r4", 0, -1, c));
        // [Reprendre, Quitter] over the cards: Quitter is over Bureau
        assertEquals("a0", FocusGrid.move(HOME, "squit", 0, 1, c));
        assertEquals("squit", FocusGrid.move(HOME, "a1", 0, -1, c));
        // Not laid out (no positions): the column, clamped
        FocusGrid.Centers<String> none = item -> Float.NaN;
        assertEquals("r1", FocusGrid.move(HOME, "a1", 0, 1, none));
    }

    @Test
    public void emptyGrid() {
        assertNull(FocusGrid.move(Collections.<List<String>>emptyList(), "x", 1, 0));
    }
}
