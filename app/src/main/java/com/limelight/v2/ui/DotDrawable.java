package com.limelight.v2.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** The small round "live" dot of the EN COURS badges */
public class DotDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;

    public DotDrawable(int color, int sizePx) {
        paint.setColor(color);
        size = sizePx;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), size / 2f, paint);
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
