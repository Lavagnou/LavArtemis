package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import com.limelight.v2.V2Theme;

/**
 * Slider track of the v2 interface (SettingSlider.qml): a groove filled with the theme's "on"
 * colour up to the thumb. Works in track positions (0..1); the row converts to values (a bitrate
 * track is curved). Dragging reports every position, and the release once more as finished.
 * Not focusable: the row takes the keys.
 */
public class V2Slider extends View {
    public interface Listener {
        void onPosition(float position, boolean finished);
    }

    private final V2Theme theme;
    private float position;
    private Listener listener;
    private boolean dragging;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int naturalWidthPx;
    private final int heightPx;

    public V2Slider(Context context, V2Theme theme, float naturalWidthDp) {
        this(context, theme, naturalWidthDp, 44, 12);
    }

    /** Prototype slider: a 24 px band, 22 px knob ({@code knobRadiusDp} 11) */
    public V2Slider(Context context, V2Theme theme, float naturalWidthDp, float heightDp, float knobRadiusDp) {
        super(context);
        this.theme = theme;
        naturalWidthPx = Ui.dp(context, naturalWidthDp);
        heightPx = Ui.dp(context, heightDp);
        knobRadiusPx = Ui.dpf(context, knobRadiusDp);
        setFocusable(false);
    }

    private final float knobRadiusPx;

    public void setListener(Listener l) {
        listener = l;
    }

    public void setPosition(float p) {
        p = Math.max(0f, Math.min(1f, p));
        if (p != position) {
            position = p;
            invalidate();
        }
    }

    public float getPosition() {
        return position;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(naturalWidthPx, widthMeasureSpec);
        setMeasuredDimension(w, heightPx);
    }

    private float thumbRadius() {
        return knobRadiusPx;
    }

    private float grooveStart() {
        return thumbRadius();
    }

    private float grooveWidth() {
        return Math.max(1, getWidth() - 2 * thumbRadius());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float gh = Ui.dpf(getContext(), 6);
        float x0 = grooveStart();
        float gw = grooveWidth();
        // Prototype: rounded 6 px track (off), filled part (on), plain round knob in fg, every theme
        float r = gh / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.off);
        rect.set(x0, cy - gh / 2f, x0 + gw, cy + gh / 2f);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setColor(theme.on);
        rect.set(x0, cy - gh / 2f, x0 + gw * position, cy + gh / 2f);
        canvas.drawRoundRect(rect, r, r, paint);

        float tr = thumbRadius() * (dragging ? 1.12f : 1f);
        float tx = x0 + gw * position;
        paint.setColor(theme.fg);
        canvas.drawCircle(tx, cy, tr, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        float p = (event.getX() - grooveStart()) / grooveWidth();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent parent = getParent();
                if (parent != null) {
                    // A scrolling page must not steal a horizontal drag
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                dragging = true;
                report(p, false);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                report(p, false);
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                report(p, true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                report(position, true);
                return true;
            default:
                return true;
        }
    }

    private void report(float p, boolean finished) {
        p = Math.max(0f, Math.min(1f, p));
        invalidate();
        if (listener != null) {
            listener.onPosition(p, finished);
        }
    }
}
