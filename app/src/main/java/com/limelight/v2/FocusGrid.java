package com.limelight.v2;

import java.util.List;

/**
 * The prototype's focus grid (logic {@code move()}): a screen is rows of items. Up / down go to the
 * previous / next row and keep the column, clamped to the row's length; left / right move within the
 * row, clamped, never wrapping. When every row holds one item, left / right act as up / down; when
 * there is a single row, up / down act as left / right. An item that is not in the grid sends the
 * focus to the first one. Pure logic, tested by FocusGridTest; screens map ids to their views.
 */
public final class FocusGrid {
    private FocusGrid() {}

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
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) {
            return null;
        }
        boolean allSingle = true;
        for (List<T> row : rows) {
            if (row.size() != 1) {
                allSingle = false;
                break;
            }
        }
        if (dx != 0 && allSingle) {
            dy = dx;
            dx = 0;
        }
        if (dy != 0 && rows.size() == 1) {
            dx = dy;
            dy = 0;
        }
        int[] at = find(rows, current);
        if (at == null) {
            return rows.get(0).get(0);
        }
        int r = at[0], c = at[1];
        if (dy != 0) {
            r = Math.max(0, Math.min(rows.size() - 1, r + dy));
            c = Math.min(c, rows.get(r).size() - 1);
        } else {
            c = Math.max(0, Math.min(rows.get(r).size() - 1, c + dx));
        }
        return rows.get(r).get(c);
    }
}
