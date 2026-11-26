package com.limelight.v2.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;

/**
 * The small round "live" dot (prototype .lav-live): it pulses like the prototype's lavpulse,
 * 1.4 s ease-in-out, opacity 1 -> .35 and scale 1 -> .7 at mid-period. Time-based: it redraws itself
 * about 30 times a second while it is drawn, and stops as soon as its view is no longer drawn.
 * Static when the system turned animations off (and in screenshots).
 */
public class DotDrawable extends Drawable implements Runnable {
    private static final long PERIOD_MS = 1400;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int baseAlpha;
    private boolean pulsing = true;

    public DotDrawable(int color, int sizePx) {
        paint.setColor(color);
        baseAlpha = paint.getAlpha();
        size = sizePx;
    }

    /** A dot that does not pulse (when a parent already animates) */
    public DotDrawable still() {
        pulsing = false;
        return this;
    }

    private static boolean animationsOn() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /** 0 at rest, 1 at the middle of the period, ease-in-out between */
    static float phase(long nowMs) {
        float t = (nowMs % PERIOD_MS) / (float) PERIOD_MS;
        float tri = t < 0.5f ? t * 2f : (1f - t) * 2f;
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * tri));
    }

    @Override
    public void draw(Canvas canvas) {
        boolean animate = pulsing && animationsOn() && getCallback() != null;
        float p = animate ? phase(SystemClock.uptimeMillis()) : 0f;
        float scale = 1f - 0.3f * p;
        paint.setAlpha(Math.round(baseAlpha * (1f - 0.65f * p)));
        canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), size / 2f * scale, paint);
        if (animate) {
            unscheduleSelf(this);
            scheduleSelf(this, SystemClock.uptimeMillis() + 33);
        }
    }

    @Override
    public void run() {
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
