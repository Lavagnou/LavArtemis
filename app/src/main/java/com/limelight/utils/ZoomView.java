package com.limelight.utils;

/**
 * The stream's zoom and position on screen, as the trackpad pinch-zoom drives it.
 * Implemented by {@link PanZoomHandler}; a plain interface so the controller is testable on the JVM.
 */
public interface ZoomView {
    float getScaleFactor();

    /** Top-left of the zoomed view in its parent. */
    float getChildX();
    float getChildY();

    /** Unscaled size of the view. */
    float getViewWidth();
    float getViewHeight();

    float getParentWidth();
    float getParentHeight();

    /** Multiplies the zoom by {@code factor} (clamped to [1, 10]), keeping (focusX, focusY) fixed. */
    void scaleAround(float factor, float focusX, float focusY);

    /** Moves the view's top-left, kept within the image's edges. */
    void setTranslation(float x, float y);

    /** Back to full screen (1x). */
    void resetZoom();
}
