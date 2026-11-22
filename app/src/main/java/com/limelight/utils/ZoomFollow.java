package com.limelight.utils;

/**
 * Keeps the cursor inside a calm zone of the screen while the stream is zoomed
 * (trackpad pinch-zoom, Chrome Remote Desktop style).
 *
 * The calm zone is the central half of the screen in each axis. While the cursor is inside it the
 * view stays put; once it leaves, the view slides by exactly the overshoot -- no recentring -- and
 * never past the edges of the image.
 */
public final class ZoomFollow {
    static final float CALM_MIN = 0.25f;
    static final float CALM_MAX = 0.75f;

    private ZoomFollow() {}

    /**
     * @param childX, childY  current top-left of the zoomed view in its parent
     * @param scale           current zoom
     * @param cursorFx, cursorFy  cursor position as a fraction of the stream
     * @param viewW, viewH    unscaled view size
     * @param parentW, parentH  parent (screen) size
     * @return the new top-left {x, y}
     */
    public static float[] followTranslation(float childX, float childY, float scale,
                                            float cursorFx, float cursorFy,
                                            float viewW, float viewH,
                                            float parentW, float parentH) {
        if (scale <= 1f) {
            return new float[]{childX, childY};
        }
        return new float[]{
                followAxis(childX, cursorFx, viewW * scale, parentW),
                followAxis(childY, cursorFy, viewH * scale, parentH)
        };
    }

    private static float followAxis(float child, float cursorFraction, float scaledSize, float parent) {
        float onScreen = child + cursorFraction * scaledSize;
        float min = CALM_MIN * parent;
        float max = CALM_MAX * parent;
        if (onScreen < min) {
            child += min - onScreen;
        } else if (onScreen > max) {
            child -= onScreen - max;
        }
        if (scaledSize > parent) {
            child = Math.max(parent - scaledSize, Math.min(0f, child));
        }
        return child;
    }
}
