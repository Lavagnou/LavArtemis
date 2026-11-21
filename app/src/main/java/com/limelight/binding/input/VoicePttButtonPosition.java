package com.limelight.binding.input;

/**
 * Where the voice push-to-talk button sits on screen.
 *
 * The position is stored as a fraction (0..1) of the free area -- the parent minus the
 * button -- rather than in pixels, so it survives rotation and resolution changes and
 * always maps back on screen. A negative fraction means "never placed": bottom-right.
 */
public final class VoicePttButtonPosition {
    private VoicePttButtonPosition() {}

    /** Top-left corner, in pixels, of a button whose stored position is (fx, fy). */
    public static float[] toPixels(float fx, float fy, int parentW, int parentH, int buttonSize, int marginPx) {
        float freeW = Math.max(0, parentW - buttonSize);
        float freeH = Math.max(0, parentH - buttonSize);
        if (fx < 0 || fy < 0) {
            return new float[]{
                    clamp(parentW - buttonSize - marginPx, 0, freeW),
                    clamp(parentH - buttonSize - marginPx, 0, freeH)
            };
        }
        return new float[]{clamp(fx * freeW, 0, freeW), clamp(fy * freeH, 0, freeH)};
    }

    /** Stored position, as a fraction of the free area, of a button whose top-left is (x, y). */
    public static float[] toFraction(float x, float y, int parentW, int parentH, int buttonSize) {
        return new float[]{fraction(x, parentW - buttonSize), fraction(y, parentH - buttonSize)};
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float fraction(float offset, int free) {
        if (free <= 0) {
            return 0f;
        }
        return clamp(offset / free, 0f, 1f);
    }
}
