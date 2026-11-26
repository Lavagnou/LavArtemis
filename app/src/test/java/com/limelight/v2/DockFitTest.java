package com.limelight.v2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** DockFit: rows, tile width and text scale of the Ludique dock (in prototype px, k = 1) */
public class DockFitTest {
    @Test
    public void oneRowOfPrototypeTilesWhenEverythingFits() {
        // 1280 x 720: 9 tiles of 96, the longest word 80 + 2 x 6
        DockFit f = DockFit.of(9, 80, 6, 96, 8, 1280 - 48 - 32, 0.9f);
        assertArrayEquals(new int[]{9}, f.rows);
        assertEquals(96, f.tile, 0.001);
        assertEquals(1f, f.scale, 0.001);
    }

    @Test
    public void tileWidensToItsLongestWord() {
        DockFit f = DockFit.of(9, 110, 6, 96, 8, 1500, 0.9f);
        assertArrayEquals(new int[]{9}, f.rows);
        assertEquals(122, f.tile, 0.001);
        assertEquals(1f, f.scale, 0.001);
    }

    @Test
    public void textShrinksBeforeASecondRow() {
        // room per tile = (1000 - 64) / 9 = 104; the word needs 110 + 12: scale (104 - 12) / 110 = 0.836
        DockFit f = DockFit.of(9, 110, 6, 96, 8, 1000, 0.8f);
        assertArrayEquals(new int[]{9}, f.rows);
        assertEquals(104, f.tile, 0.001);
        assertEquals(92f / 110f, f.scale, 0.001);
    }

    @Test
    public void secondRowWhenTheTextWouldBeTooSmall() {
        // Phone in portrait: one row would need a scale of 0.5; two rows (5 + 4), the word fits
        DockFit f = DockFit.of(9, 110, 6, 96, 8, 660, 0.9f);
        assertArrayEquals(new int[]{5, 4}, f.rows);
        assertEquals(122, f.tile, 0.001);
        assertEquals(1f, f.scale, 0.001);
    }

    @Test
    public void thirdRowOnlyWhenTwoDoNotFit() {
        // Two rows of 5: (400 - 32) / 5 = 73.6, scale (73.6 - 12) / 110 = 0.56 < 0.9; three rows of 3
        DockFit f = DockFit.of(9, 110, 6, 96, 8, 400, 0.9f);
        assertArrayEquals(new int[]{3, 3, 3}, f.rows);
        assertEquals(122, f.tile, 0.001);
        assertEquals(1f, f.scale, 0.001);
    }

    @Test
    public void rowsAreBalanced() {
        assertArrayEquals(new int[]{5, 4}, DockFit.split(9, 2));
        assertArrayEquals(new int[]{3, 3, 2}, DockFit.split(8, 3));
        assertArrayEquals(new int[]{4, 4}, DockFit.split(8, 2));
    }
}
