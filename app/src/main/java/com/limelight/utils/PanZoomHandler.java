package com.limelight.utils;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.limelight.Game;
import com.limelight.LimeLog;
import com.limelight.preferences.PreferenceConfiguration;

public class PanZoomHandler implements ZoomView {
    static private final float MAX_SCALE = 10.0f;

    private final Game game;
    private final View streamView;
    private final PreferenceConfiguration prefConfig;
    private final boolean isTopMode;
    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;
    private View parent;
    private float scaleFactor = 1.0f;
    private float childX, childY = 0;
    private float parentWidth, parentHeight = 0;
    private float childWidth, childHeight = 0;

    public PanZoomHandler(Context context, Game game, View streamView, View parent, PreferenceConfiguration prefConfig) {
        this.game = game;
        this.streamView = streamView;
        this.parent = parent;
        this.prefConfig = prefConfig;
        this.isTopMode = prefConfig.alignDisplayTopCenter;
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());

        // Everything gets easier with 0,0 as the pivot point
        streamView.setPivotX(0);
        streamView.setPivotY(0);
    }

    public void handleTouchEvent(MotionEvent motionEvent) {
        scaleGestureDetector.onTouchEvent(motionEvent);
        gestureDetector.onTouchEvent(motionEvent);
    }

    private void updateDimensions() {
        childHeight = streamView.getHeight() * scaleFactor;
        childWidth = streamView.getWidth() * scaleFactor;
        parentWidth = parent.getWidth();
        parentHeight = parent.getHeight();
    }

    private void constrainToBounds() {
        updateDimensions();

        if (parentWidth >= childWidth) {
            childX = (parentWidth - childWidth) / 2;
        } else {
            float boundaryX = parentWidth - childWidth;
            childX = Math.max(boundaryX, Math.min(childX, 0));
        }

        if (parentHeight >= childHeight) {
            if (isTopMode) {
                childY = 0;
            } else {
                childY = (parentHeight - childHeight) / 2;
            }
        } else {
            float boundaryY = parentHeight - childHeight;
            childY = Math.max(boundaryY, Math.min(childY, 0));
        }

        streamView.setX(childX);
        streamView.setY(childY);
    }

    public void handleSurfaceChange() {
        if (childWidth == 0 || parent == null) {
            // Retrieve parent, should handle both built-in display and external display
            parent = (View)streamView.getParent();
            return;
        }

        float prevChildWidth = childWidth;
        float prevChildHeight = childHeight;
        float prevParentWidth = parentWidth;
        float prevParentHeight = parentHeight;

        updateDimensions();

        float viewScaleX = childWidth / prevChildWidth;
        float viewScaleY = childHeight / prevChildHeight;

        float dPivotX1 = childX - prevParentWidth / 2;
        float dPivotY1 = childY - prevParentHeight / 2;

        float dPivotX2 = dPivotX1 * viewScaleX;
        float dPivotY2 = dPivotY1 * viewScaleY;

        childX = dPivotX2 + parentWidth / 2;
        childY = dPivotY2 + parentHeight / 2;

        streamView.setX(childX);
        streamView.setY(childY);

        constrainToBounds();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleAround(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (game != null) {
                game.updatePipAutoEnter();
            }
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            childX = streamView.getX() - distanceX;
            childY = streamView.getY() - distanceY;

            streamView.setX(childX);
            streamView.setY(childY);

            constrainToBounds();
            return true;
        }
    }

    public void setInitialZoomAndPan(float scale, float offsetX, float offsetY) {
        this.scaleFactor = scale;
        // apply to view
        streamView.setScaleX(scaleFactor);
        streamView.setScaleY(scaleFactor);
        this.childX = offsetX;
        this.childY = offsetY;
        streamView.setX(childX);
        streamView.setY(childY);
    }

    @Override public float getScaleFactor() { return scaleFactor; }
    @Override public float getChildX() { return childX; }
    @Override public float getChildY() { return childY; }
    @Override public float getViewWidth() { return streamView.getWidth(); }
    @Override public float getViewHeight() { return streamView.getHeight(); }
    @Override public float getParentWidth() { return parent != null ? parent.getWidth() : 0; }
    @Override public float getParentHeight() { return parent != null ? parent.getHeight() : 0; }

    /** One code path for the zoom mode's pinch and the trackpad pinch-zoom. */
    @Override
    public void scaleAround(float factor, float focusX, float focusY) {
        float newScaleFactor = Math.max(1, Math.min(scaleFactor * factor, MAX_SCALE));

        // Keep the focus point where it is on screen
        childX = focusX + (childX - focusX) / scaleFactor * newScaleFactor;
        childY = focusY + (childY - focusY) / scaleFactor * newScaleFactor;

        scaleFactor = newScaleFactor;
        streamView.setScaleX(scaleFactor);
        streamView.setScaleY(scaleFactor);
        streamView.setX(childX);
        streamView.setY(childY);

        constrainToBounds();
    }

    @Override
    public void setTranslation(float x, float y) {
        childX = x;
        childY = y;
        constrainToBounds();
    }

    @Override
    public void resetZoom() {
        scaleFactor = 1f;
        streamView.setScaleX(1f);
        streamView.setScaleY(1f);
        constrainToBounds();
    }
}
