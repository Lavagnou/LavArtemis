package com.limelight.utils;

import com.limelight.preferences.PreferenceConfiguration.ScaleMode;

/**
 * "Follow phone rotation": the view turns with the phone, the host's screen does not.
 *
 * The host cannot change resolution mid-stream, so while following, the requested resolution is
 * never inverted for portrait. And a landscape image stretched or cropped into a portrait screen is
 * unusable, so portrait is always fitted; landscape gets back the user's own scale mode.
 */
public final class RotationPolicy {
    private RotationPolicy() {}

    public static boolean shouldInvertResolution(boolean followRotation, boolean portrait, boolean autoInvert) {
        return !followRotation && portrait && autoInvert;
    }

    public static ScaleMode effectiveScaleMode(ScaleMode chosen, boolean followRotation, boolean portrait) {
        return followRotation && portrait ? ScaleMode.FIT : chosen;
    }
}
