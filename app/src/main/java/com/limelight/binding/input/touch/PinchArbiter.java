package com.limelight.binding.input.touch;

/**
 * Decides, at the start of a two-finger gesture, whether it is a pinch (zoom) or one of the
 * trackpad's own two-finger gestures (scroll, right click).
 *
 * A pinch changes the distance between the fingers; a scroll moves them together. Whichever crosses
 * the threshold first wins, and the decision holds for the rest of the gesture. In a pinch the span
 * changes twice as fast as either finger moves, so this decides before the touch contexts confirm a
 * scroll on their own per-finger thresholds.
 */
public class PinchArbiter {
    public enum Decision { UNDECIDED, PINCH, OTHER }

    private final float threshold;
    private float startSpan;
    private float startFocusX, startFocusY;
    private Decision decision = Decision.UNDECIDED;

    public PinchArbiter(float thresholdPx) {
        this.threshold = thresholdPx;
    }

    public void start(float x0, float y0, float x1, float y1) {
        startSpan = span(x0, y0, x1, y1);
        startFocusX = focusX(x0, x1);
        startFocusY = focusY(y0, y1);
        decision = Decision.UNDECIDED;
    }

    public Decision update(float x0, float y0, float x1, float y1) {
        if (decision != Decision.UNDECIDED) {
            return decision;
        }
        float spanChange = Math.abs(span(x0, y0, x1, y1) - startSpan);
        float focusMove = (float) Math.hypot(focusX(x0, x1) - startFocusX, focusY(y0, y1) - startFocusY);
        if (spanChange >= threshold && spanChange >= focusMove) {
            decision = Decision.PINCH;
        } else if (focusMove >= threshold) {
            decision = Decision.OTHER;
        }
        return decision;
    }

    public static float span(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    public static float focusX(float x0, float x1) {
        return (x0 + x1) / 2f;
    }

    public static float focusY(float y0, float y1) {
        return (y0 + y1) / 2f;
    }
}
