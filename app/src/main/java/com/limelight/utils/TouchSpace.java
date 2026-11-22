package com.limelight.utils;

/**
 * Which coordinates the touch contexts get for a finger.
 *
 * Absolute touch needs the point under the finger in the zoomed stream view's own space:
 * (raw - view position) / scale. A trackpad must not: that space moves whenever the view pans to
 * follow the cursor, so a finger that holds still would "move", the cursor would go further, the
 * view would pan more -- the cursor accelerates at the edge. Trackpads get screen space, which pan
 * and zoom never touch, and the pinch-zoom controller applies the zoom itself.
 */
public final class TouchSpace {
    private TouchSpace() {}

    public static float[] forContexts(float rawX, float rawY, float viewX, float viewY,
                                      float scaleX, float scaleY, boolean screenSpace) {
        if (screenSpace) {
            return new float[]{rawX, rawY};
        }
        return new float[]{(rawX - viewX) / scaleX, (rawY - viewY) / scaleY};
    }
}
