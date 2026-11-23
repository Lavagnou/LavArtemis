package com.limelight.v2.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

/**
 * Base of every interactive v2 view. Draws, around its children:
 * <ul>
 * <li>the live halo of the running item (blurred glow, or Affiche's hard offset block), outside
 * its bounds and pulsing (LiveHalo.qml);</li>
 * <li>the Projecteur dimming: grayscale, multiplied brightness and opacity (Theme.dim()), applied to
 * the whole face through one layer, so covers and texts dim alike;</li>
 * <li>the focus ring, outside its bounds, only for gamepad and keyboard (V2Input).</li>
 * </ul>
 * Ancestors must not clip children ({@link Ui#noClip}) or the halo and the ring are cut.
 */
public class V2Frame extends FrameLayout {
    protected V2Theme theme;
    private float ringRadiusPx;
    private float ringOutsetPx;
    private int ringColor;
    private float ringStrokePx;

    private boolean dimmed;
    private boolean live;
    private float liveRadiusPx;

    private final Paint layerPaint = new Paint();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private Bitmap glow;
    private int glowW, glowH, glowColor;
    private float glowPad;
    /** Pulse phase 0..1; a static mid-high value when not animating (screenshots, no animation) */
    private float pulse = 1f;
    private ValueAnimator pulseAnimator;

    public V2Frame(Context context, V2Theme theme) {
        super(context);
        this.theme = theme;
        setWillNotDraw(false);
        ringStrokePx = Ui.dpf(context, 3);
        ringOutsetPx = Ui.dpf(context, 6);
        ringColor = theme.accent;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ringStrokePx);
    }

    public void setRing(float radiusDp, float outsetDp, int color) {
        ringRadiusPx = Ui.dpf(getContext(), radiusDp);
        ringOutsetPx = Ui.dpf(getContext(), outsetDp);
        ringColor = color;
        invalidate();
    }

    public void setDimmed(boolean dimmed) {
        if (this.dimmed != dimmed) {
            this.dimmed = dimmed;
            invalidate();
        }
    }

    public boolean isDimmed() {
        return dimmed;
    }

    public void setLive(boolean live, float radiusDp) {
        this.liveRadiusPx = Ui.dpf(getContext(), radiusDp);
        if (this.live != live) {
            this.live = live;
            updatePulse();
            invalidate();
        }
    }

    public boolean isLive() {
        return live;
    }

    protected boolean showFocus() {
        return isFocused() && V2Input.showFocus(getContext());
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
        // The ring and the dim are drawn outside / over the view: the parent repaints too
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    private void updatePulse() {
        boolean animations = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
        boolean want = live && isAttachedToWindow() && animations;
        if (want && pulseAnimator == null) {
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(theme.isAffiche() ? 800 : 1300);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.addUpdateListener(a -> {
                pulse = (float) a.getAnimatedValue();
                invalidate();
                if (getParent() instanceof View) {
                    ((View) getParent()).invalidate();
                }
            });
            pulseAnimator.start();
        } else if (!want && pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
            pulse = 1f;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (live) {
            drawHalo(canvas);
        }
        boolean layered = dimmed;
        int save = -1;
        if (layered) {
            V2Theme.Dim d = theme.dim(showFocus());
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(1f - d.gray);
            ColorMatrix bright = new ColorMatrix();
            bright.setScale(d.bright, d.bright, d.bright, 1f);
            m.postConcat(bright);
            layerPaint.setColorFilter(new ColorMatrixColorFilter(m));
            layerPaint.setAlpha(Math.round(255 * d.opacity));
            save = canvas.saveLayer(0, 0, getWidth(), getHeight(), layerPaint);
        }
        super.draw(canvas);
        if (save >= 0) {
            canvas.restoreToCount(save);
        }
        if (showFocus()) {
            ringPaint.setColor(ringColor);
            float o = ringOutsetPx - ringStrokePx / 2f;
            rect.set(-o, -o, getWidth() + o, getHeight() + o);
            float rad = ringRadiusPx + ringOutsetPx;
            canvas.drawRoundRect(rect, rad, rad, ringPaint);
        }
    }

    private void drawHalo(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (theme.isAffiche()) {
            // A hard offset block, blinking like a poster light (1.0 <-> 0.55)
            haloPaint.setColor(theme.live);
            haloPaint.setAlpha(Math.round(255 * (0.55f + 0.45f * pulse)));
            float off = Ui.dpf(getContext(), 10);
            canvas.drawRect(off, off, w + off, h + off, haloPaint);
            return;
        }
        ensureGlow(w, h);
        if (glow == null) {
            return;
        }
        haloPaint.setAlpha(Math.round(255 * (0.35f + 0.40f * pulse)));
        rect.set(-glowPad, -glowPad, w + glowPad, h + glowPad);
        canvas.drawBitmap(glow, null, rect, haloPaint);
    }

    /**
     * The glow is rendered once per size in software (BlurMaskFilter is not supported by every
     * hardware pipeline), at a quarter of the resolution: the blur hides the upscaling.
     */
    private void ensureGlow(int w, int h) {
        if (glow != null && glowW == w && glowH == h && glowColor == theme.live) {
            return;
        }
        float scale = 0.25f;
        float spread = Ui.dpf(getContext(), 6);
        float blur = Ui.dpf(getContext(), 16);
        glowPad = spread + blur * 2;
        int bw = Math.max(1, Math.round((w + 2 * glowPad) * scale));
        int bh = Math.max(1, Math.round((h + 2 * glowPad) * scale));
        try {
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(theme.live);
            p.setMaskFilter(new BlurMaskFilter(Math.max(1f, blur * scale), BlurMaskFilter.Blur.NORMAL));
            float inset = (glowPad - spread) * scale;
            float rad = (liveRadiusPx + spread) * scale;
            c.drawRoundRect(new RectF(inset, inset, bw - inset, bh - inset), rad, rad, p);
            glow = b;
            glowW = w;
            glowH = h;
            glowColor = theme.live;
        } catch (OutOfMemoryError e) {
            glow = null;
        }
    }
}
