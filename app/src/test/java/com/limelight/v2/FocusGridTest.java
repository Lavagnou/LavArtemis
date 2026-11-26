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
    public void singleColumnTurnsLeftRightIntoUpDown() {
        List<List<String>> launch = Arrays.asList(Collections.singletonList("o0"), Collections.singletonList("o1"),
                Collections.singletonList("go"));
        assertEquals("o1", FocusGrid.move(launch, "o0", 1, 0));
        assertEquals("o0", FocusGrid.move(launch, "o1", -1, 0));
    }

    @Test
    public void singleRowTurnsUpDownIntoLeftRight() {
        List<List<String>> hosts = Collections.singletonList(Arrays.asList("h0", "h1", "hadd"));
        assertEquals("h1", FocusGrid.move(hosts, "h0", 0, 1));
        assertEquals("h0", FocusGrid.move(hosts, "h1", 0, -1));
    }

    @Test
    public void emptyGrid() {
        assertNull(FocusGrid.move(Collections.<List<String>>emptyList(), "x", 1, 0));
    }
}
