package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.limelight.R;

/**
 * The app's own launcher icon as a rounded tile: the two layers of the adaptive icon
 * (ic_launcher_bg / ic_launcher_fg), cropped to the 72 dp a launcher shows of their 108 dp and
 * rounded like the prototype's logo tile (rx 22 of 100). In the Dev build these are the « plan
 * d'atelier » layers of app/src/dev/res (blue grid, white drawing, green DEV pill): user decision of
 * 2026-11-26, the Dev keeps its icon in the header instead of the theme's logo and DEV chip.
 * The Dev background has no gradient, so this works below Android 7 (the v2 screens are Dev only).
 */
public final class AppIconDrawable extends Drawable {
    private final Drawable bg, fg;
    private final Path clip = new Path();

    public AppIconDrawable(Context context) {
        bg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_bg);
        fg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_fg);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        // The visible 72 of 108: the layers overflow by a quarter of the tile on each side
        int ox = Math.round(b.width() * 0.25f), oy = Math.round(b.height() * 0.25f);
        Rect layer = new Rect(b.left - ox, b.top - oy, b.right + ox, b.bottom + oy);
        if (bg != null) {
            bg.setBounds(layer);
        }
        if (fg != null) {
            fg.setBounds(layer);
        }
        clip.reset();
        float r = Math.min(b.width(), b.height()) * 0.22f;
        clip.addRoundRect(new RectF(b), r, r, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        if (bg != null) {
            bg.draw(canvas);
        }
        if (fg != null) {
            fg.draw(canvas);
        }
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        if (bg != null) {
            bg.setAlpha(alpha);
        }
        if (fg != null) {
            fg.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
