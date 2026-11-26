package com.limelight.v2;

/**
 * The Ludique dock of the in-game menu (V2GameMenu): how many rows, how wide the tiles and how
 * big their text, so that a word of a tile is never cut in two. Pure maths, DockFitTest.
 *
 * The prototype has one row of 96 x 108 tiles at 1280 x 720 and no portrait board. Rule (a gap of
 * the prototype, to be validated): as few rows as possible; in a row, the tile keeps its 96 px if
 * the longest word fits, else it widens to that word, else it narrows to what the row leaves and
 * the text shrinks with it, down to a smallest readable size. When even that does not fit, one row
 * more (9 tiles: 5 + 4, then 3 + 3 + 3).
 */
public final class DockFit {
    /** Tiles per row, from the top (the first rows take the extra tile: 5 + 4) */
    public final int[] rows;
    /** Width of a tile */
    public final float tile;
    /** Factor of the text sizes (1 = the size of the theme, at least minScale) */
    public final float scale;

    private DockFit(int[] rows, float tile, float scale) {
        this.rows = rows;
        this.tile = tile;
        this.scale = scale;
    }

    /**
     * @param n        number of tiles
     * @param word     widest word of the labels and values, at the theme's size
     * @param pad      room left and right of the text inside a tile
     * @param minTile  the prototype's tile width (96 px)
     * @param gap      space between two tiles
     * @param avail    width the tiles may take on one row (screen minus margins and dock padding)
     * @param minScale smallest factor of the text sizes that stays readable
     */
    public static DockFit of(int n, float word, float pad, float minTile, float gap, float avail, float minScale) {
        n = Math.max(1, n);
        float need = Math.max(minTile, word + 2 * pad);
        for (int r = 1; r <= n; r++) {
            int per = (n + r - 1) / r;
            if (r > 1 && per == (n + r - 2) / (r - 1)) {
                // As many tiles per row as with one row less: not tried twice
                continue;
            }
            float room = (avail - (per - 1) * gap) / per;
            if (need <= room) {
                return new DockFit(split(n, r), need, 1f);
            }
            float s = word <= 0 ? 1f : (room - 2 * pad) / word;
            if (s >= minScale) {
                return new DockFit(split(n, r), room, Math.min(1f, s));
            }
        }
        // Not even one tile a row: one column at the smallest size, as wide as its longest word
        return new DockFit(split(n, n), word * minScale + 2 * pad, minScale);
    }

    /** n tiles over r rows, the first rows taking one more */
    static int[] split(int n, int r) {
        int[] rows = new int[r];
        for (int i = 0; i < r; i++) {
            rows[i] = n / r + (i < n % r ? 1 : 0);
        }
        return rows;
    }
}
