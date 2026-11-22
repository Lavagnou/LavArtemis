package com.limelight.binding.input.touch;

/**
 * The host cursor's position, owned by the client (trackpad pinch-zoom).
 *
 * In stream pixels, clamped to the stream. The client sends it to the host as an absolute
 * position, so it is always where the host cursor really is -- an estimate built from relative
 * moves would drift as soon as Windows' pointer acceleration reshapes them.
 */
public class TrackedCursor {
    private final int width;
    private final int height;
    private float x;
    private float y;

    public TrackedCursor(int streamWidth, int streamHeight) {
        this.width = streamWidth;
        this.height = streamHeight;
        this.x = streamWidth / 2f;
        this.y = streamHeight / 2f;
    }

    /** Moves by a finger delta; zoomed in, the same gesture covers less of the image. */
    public void move(float dx, float dy, float zoom) {
        float z = Math.max(1f, zoom);
        x = clamp(x + dx / z, width);
        y = clamp(y + dy / z, height);
    }

    public void setFraction(float fx, float fy) {
        x = clamp(fx * width, width);
        y = clamp(fy * height, height);
    }

    public short getX() {
        return (short) Math.round(x);
    }

    public short getY() {
        return (short) Math.round(y);
    }

    public float getFractionX() {
        return width > 0 ? x / width : 0f;
    }

    public float getFractionY() {
        return height > 0 ? y / height : 0f;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private static float clamp(float v, int max) {
        return Math.max(0f, Math.min(max, v));
    }
}
