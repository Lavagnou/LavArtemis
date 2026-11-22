package com.limelight.binding.input.touch;

import android.view.MotionEvent;

import com.limelight.utils.ZoomFollow;
import com.limelight.utils.ZoomView;

/**
 * Pinch to zoom in trackpad modes, with the view following the cursor (Chrome Remote Desktop style).
 *
 * - Owns the host cursor: every trackpad move goes through {@link #move}, updates a
 *   {@link TrackedCursor} (slower when zoomed in) and is sent as an absolute position, so the cursor
 *   the view follows is the real one.
 * - Follows only on cursor moves: a pinch never pans, so one can zoom on a spot away from the cursor.
 * - Two fingers: {@link PinchArbiter} decides pinch or not; a pinch cancels the touch contexts (no
 *   scroll, no right click) and owns every event until all fingers are up.
 * - Outside trackpad modes it does nothing but bring the view back to 1x.
 */
public class TrackpadZoomController implements MouseMover {
    public interface PositionSender {
        void send(short x, short y, short referenceWidth, short referenceHeight);
    }

    public interface ContextCanceller {
        void cancelAll();
    }

    private final ZoomView view;
    private final PositionSender sender;
    private final ContextCanceller canceller;
    private final TrackedCursor cursor;
    private final PinchArbiter arbiter;

    private boolean trackpadMode;
    private boolean twoFingerGesture;
    private boolean pinching;
    private float lastSpan;

    public TrackpadZoomController(ZoomView view, PositionSender sender, ContextCanceller canceller,
                                  int streamWidth, int streamHeight, float pinchThresholdPx) {
        this.view = view;
        this.sender = sender;
        this.canceller = canceller;
        this.cursor = new TrackedCursor(streamWidth, streamHeight);
        this.arbiter = new PinchArbiter(pinchThresholdPx);
    }

    public void setTrackpadMode(boolean trackpad) {
        trackpadMode = trackpad;
        resetGesture();
        if (!trackpad) {
            view.resetZoom();
        }
    }

    public void onAbsolutePosition(float fx, float fy) {
        cursor.setFraction(fx, fy);
    }

    @Override
    public void move(short dx, short dy) {
        cursor.move(dx, dy, view.getScaleFactor());
        sender.send(cursor.getX(), cursor.getY(), (short) cursor.getWidth(), (short) cursor.getHeight());

        float[] t = ZoomFollow.followTranslation(view.getChildX(), view.getChildY(), view.getScaleFactor(),
                cursor.getFractionX(), cursor.getFractionY(),
                view.getViewWidth(), view.getViewHeight(), view.getParentWidth(), view.getParentHeight());
        if (t[0] != view.getChildX() || t[1] != view.getChildY()) {
            view.setTranslation(t[0], t[1]);
        }
    }

    /**
     * @return true when the event belongs to a pinch and must not reach the touch contexts
     */
    public boolean onTouch(int actionMasked, int pointerCount, float x0, float y0, float x1, float y1) {
        if (!trackpadMode) {
            return false;
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                // A new gesture, whatever happened to the last one
                resetGesture();
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean consumed = pinching;
                resetGesture();
                return consumed;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pinching) {
                    // A new pair of fingers: measure its span afresh, or the zoom jumps
                    lastSpan = 0;
                    return true;
                }
                if (pointerCount == 2 && !twoFingerGesture) {
                    twoFingerGesture = true;
                    arbiter.start(x0, y0, x1, y1);
                    lastSpan = PinchArbiter.span(x0, y0, x1, y1);
                } else {
                    // Three or more fingers belong to the existing multi-finger gestures
                    twoFingerGesture = false;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (pinching) {
                    if (pointerCount >= 2) {
                        scaleBy(x0, y0, x1, y1);
                    }
                    return true;
                }
                if (twoFingerGesture && pointerCount == 2) {
                    PinchArbiter.Decision d = arbiter.update(x0, y0, x1, y1);
                    if (d == PinchArbiter.Decision.PINCH) {
                        pinching = true;
                        canceller.cancelAll();
                        scaleBy(x0, y0, x1, y1);
                        return true;
                    }
                    // Hold moves back until the gesture is known: the gaming context would
                    // already scroll. Contexts work from their last seen position, so a scroll
                    // decided later catches up on its next move.
                    return d == PinchArbiter.Decision.UNDECIDED;
                }
                return false;

            case MotionEvent.ACTION_POINTER_UP:
                if (!pinching) {
                    twoFingerGesture = false;
                } else {
                    lastSpan = 0;
                }
                return pinching;

            default:
                return pinching;
        }
    }

    private void scaleBy(float x0, float y0, float x1, float y1) {
        float span = PinchArbiter.span(x0, y0, x1, y1);
        if (lastSpan > 0 && span > 0) {
            view.scaleAround(span / lastSpan, PinchArbiter.focusX(x0, x1), PinchArbiter.focusY(y0, y1));
        }
        lastSpan = span;
    }

    private void resetGesture() {
        twoFingerGesture = false;
        pinching = false;
        lastSpan = 0;
    }
}
