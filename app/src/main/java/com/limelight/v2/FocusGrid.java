package com.limelight.v2;

import java.util.List;

/**
 * The focus grid of the v2 screens (prototype logic {@code move()}, with the gamepad rules of
 * 2026-11-27): a screen is rows of items. Up / down go to the previous / next row, to the item whose
 * horizontal centre is the closest to the current one's (measured on screen; without positions, the
 * same column clamped to the row's length); left / right move within the row. Nothing wraps and
 * nothing jumps: at an edge the focus stays, a single row ignores up / down, a list of one item per
 * row ignores left / right (a row with a value changes it before the grid is asked). An item that
 * is not in the grid sends the focus to the first one. Pure logic, tested by FocusGridTest; screens
 * map ids to their views.
 */
public final class FocusGrid {
    private FocusGrid() {}

    /** Horizontal centre of an item on screen, or NaN when unknown (not laid out) */
    public interface Centers<T> {
        float centerX(T item);
    }

    /** Row and column of an item, or null */
    public static int[] find(List<? extends List<?>> rows, Object item) {
        for (int r = 0; r < rows.size(); r++) {
            int c = rows.get(r).indexOf(item);
            if (c >= 0) {
                return new int[]{r, c};
            }
        }
        return null;
    }

    /**
     * Where the focus goes from {@code current} for a move of (dx, dy), each -1, 0 or 1. Returns
     * {@code current} when the move hits an edge, null for an empty grid.
     */
    public static <T> T move(List<? extends List<T>> rows, T current, int dx, int dy) {
        return move(rows, current, dx, dy, null);
    }

    /** As {@link #move(List, Object, int, int)}, a row change going to the closest centre */
    public static <T> T move(List<? extends List<T>> rows, T current, int dx, int dy, Centers<T> centers) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) {
            return null;
        }
        int[] at = find(rows, current);
        if (at == null) {
            return rows.get(0).get(0);
        }
        int r = at[0], c = at[1];
        if (dy != 0) {
            int nr = r + dy;
            if (nr < 0 || nr >= rows.size() || rows.get(nr).isEmpty()) {
                return current;
            }
            return closest(rows.get(nr), c, centers == null ? Float.NaN : centers.centerX(current), centers);
        }
        if (dx != 0) {
            c = Math.max(0, Math.min(rows.get(r).size() - 1, c + dx));
        }
        return rows.get(r).get(c);
    }

    /** The item of {@code row} whose centre is the closest to {@code x}; the column clamped without positions */
    private static <T> T closest(List<T> row, int column, float x, Centers<T> centers) {
        T fallback = row.get(Math.min(column, row.size() - 1));
        if (centers == null || Float.isNaN(x)) {
            return fallback;
        }
        T best = null;
        float bestD = Float.MAX_VALUE;
        for (T item : row) {
            float cx = centers.centerX(item);
            if (Float.isNaN(cx)) {
                return fallback;
            }
            float d = Math.abs(cx - x);
            // Equal distances: the first one (left), so a move is predictable
            if (d < bestD) {
                bestD = d;
                best = item;
            }
        }
        return best == null ? fallback : best;
    }
}
