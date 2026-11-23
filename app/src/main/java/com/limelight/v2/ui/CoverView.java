package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * A cover, cropped to fill (centre crop) and cut to a rounded shape whose corners can differ (the
 * card's cover touches only its right corners). Antialiased in every pipeline: the shape is drawn
 * in a layer and the bitmap composited into it (SRC_IN), which also works with hardware bitmaps.
 */
public class CoverView extends View {
    private Bitmap bitmap;
    private final float[] radii = new float[8];
    private boolean rounded;
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint plainPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    public CoverView(Context context) {
        super(context);
        bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    public void setBitmap(Bitmap bitmap) {
        if (this.bitmap != bitmap) {
            this.bitmap = bitmap;
            invalidate();
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    /** Corner radii in px: top-left, top-right, bottom-right, bottom-left */
    public void setRadii(float tl, float tr, float br, float bl) {
        radii[0] = radii[1] = tl;
        radii[2] = radii[3] = tr;
        radii[4] = radii[5] = br;
        radii[6] = radii[7] = bl;
        rounded = tl > 0 || tr > 0 || br > 0 || bl > 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        // Centre crop
        float vw = getWidth(), vh = getHeight();
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float scale = Math.max(vw / bw, vh / bh);
        float cw = vw / scale, ch = vh / scale;
        int left = Math.round((bw - cw) / 2f), top = Math.round((bh - ch) / 2f);
        src.set(left, top, left + Math.round(cw), top + Math.round(ch));
        dst.set(0, 0, vw, vh);

        if (!rounded) {
            canvas.drawBitmap(bitmap, src, dst, plainPaint);
            return;
        }
        bounds.set(0, 0, vw, vh);
        path.reset();
        path.addRoundRect(bounds, radii, Path.Direction.CW);
        int save = canvas.saveLayer(0, 0, vw, vh, null);
        canvas.drawPath(path, shapePaint);
        canvas.drawBitmap(bitmap, src, dst, bitmapPaint);
        canvas.restoreToCount(save);
    }
}
