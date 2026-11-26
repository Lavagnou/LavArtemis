package com.limelight.v2.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * Base of every interactive v2 view. Draws, around its children:
 * <ul>
 * <li>the live glow of the running item, pulsing with the prototype's keyframes: Braise's ember
 * (lavember, 2.6 s) and Ludique's purple plume (lavplum, 2.4 s) as a blurred glow outside the
 * bounds; Affiche's hard yellow offset block (lavpop, 10 &lt;-&gt; 5 px) or, on an app card, its
 * inset yellow bar at the bottom (lavbar, 12 &lt;-&gt; 4 px), 1.6 s;</li>
 * <li>the Projecteur dimming: grayscale, multiplied brightness and opacity (Theme.dim()), applied to
 * the whole face through one layer, so covers and texts dim alike;</li>
 * <li>the focus decoration of the theme, only for gamepad and keyboard (V2Input): a ring in the page
 * colour then the ring colour (Braise 3 / 6 px, Ludique 4 / 9 px); for Affiche a yellow fill with dark
 * ink (ring), or a 4 px outline 4 px away (ring2) on the recent covers.</li>
 * </ul>
 * Ancestors must not clip children ({@link Ui#noClip}) or the halo and the ring are cut.
 */
public class V2Frame extends FrameLayout implements V2Input.Listener {
    /** How the running item glows */
    public enum LiveStyle { HALO, BAR }

    protected V2Theme theme;
    private float ringRadiusPx;

    private boolean dimmed;
    private boolean live;
    private LiveStyle liveStyle = LiveStyle.HALO;
    private float liveRadiusPx;
    /** Affiche: fill the focused item yellow (buttons, cards); false on images (outline only) */
    private boolean focusFill = true;
    private boolean fillApplied;
    /** Affiche: the border kept around the yellow fill (Mes PC cards keep their 3 px ink frame) */
    private int fillStrokePx, fillStrokeColor;

    /** Affiche's yellow fill keeps this border (prototype cards of Mes PC) */
    public void setFocusFillStroke(int strokePx, int color) {
        fillStrokePx = strokePx;
        fillStrokeColor = color;
    }

    /** A child that keeps its own colours under Affiche's yellow fill (a black button, a black pill) */
    public static void keepColors(View v) {
        v.setTag(com.limelight.R.id.v2_keep_colors, Boolean.TRUE);
    }
    private Drawable savedBackground;
    private final Map<TextView, ColorStateList> savedText = new HashMap<>();
    private final Map<ImageView, android.graphics.ColorFilter> savedImage = new HashMap<>();

    private final Paint layerPaint = new Paint();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private Bitmap glow;
    private int glowW, glowH;
    private float glowPad;
    /** Pulse phase 0..1 (0 = the keyframes' 0 %, 1 = their 50 %); a static value when not animating */
    private float pulse = 1f;
    private ValueAnimator pulseAnimator;

    public V2Frame(Context context, V2Theme theme) {
        super(context);
        this.theme = theme;
        setWillNotDraw(false);
        ringPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * Corner radius of the focus ring (the view's own radius). The outset and colour arguments are
     * kept for the callers but the ring now follows the theme ({@link V2Theme#focusGap}).
     */
    public void setRing(float radiusDp, float outsetDp, int color) {
        ringRadiusPx = Ui.dpf(getContext(), radiusDp);
        invalidate();
    }

    /** Affiche: whether focus fills the item yellow (true) or only outlines it (images) */
    public void setFocusFill(boolean fill) {
        focusFill = fill;
        focusOutline = !fill;
        applyFocusLook();
    }

    /**
     * Affiche: the 4 px outline 4 px away (prototype ring2). Only the recent covers (outline alone)
     * and the launch button (fill and outline) have it; every other item is just filled yellow.
     */
    public void setFocusOutline(boolean outline) {
        focusOutline = outline;
        invalidate();
    }

    private boolean focusOutline;

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
        setLive(live, radiusDp, LiveStyle.HALO);
    }

    public void setLive(boolean live, float radiusDp, LiveStyle style) {
        this.liveRadiusPx = Ui.dpf(getContext(), radiusDp);
        this.liveStyle = style;
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

    /** Gamepad/keyboard focus scales the view to 1.04, as on the desktop and in the prototype
     *  (`ring`). Only cards and covers, not buttons or settings rows. Never on Affiche (its ring
     *  has no scale). */
    private boolean scaleOnFocus;
    private static final float FOCUS_SCALE = 1.04f;

    protected void setScaleOnFocus(boolean on) {
        scaleOnFocus = on;
    }

    private void applyFocusScale() {
        if (!scaleOnFocus) {
            return;
        }
        float s = showFocus() && !theme.isAffiche() ? FOCUS_SCALE : 1f;
        if (getScaleX() != s) {
            animate().scaleX(s).scaleY(s).setDuration(150).start();
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        applyFocusScale();
        applyFocusLook();
        invalidate();
        // The ring and the dim are drawn outside / over the view: the parent repaints too
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    @Override
    public void onInputKindChanged(V2Input.Kind kind) {
        applyFocusScale();
        applyFocusLook();
        invalidate();
    }

    /**
     * Affiche's focus fill: the background becomes yellow and every text and icon inside turns to
     * the dark ink, then everything is put back when the focus leaves (or the input becomes touch).
     */
    private void applyFocusLook() {
        boolean want = theme.focusFill && focusFill && showFocus();
        if (want == fillApplied) {
            return;
        }
        fillApplied = want;
        if (want) {
            savedBackground = getBackground();
            float r = ringRadiusPx;
            setBackground(Ui.roundRect(V2Theme.YELLOW, r, fillStrokePx, fillStrokeColor));
            recolor(this, true);
        } else {
            setBackground(savedBackground);
            savedBackground = null;
            recolor(this, false);
        }
    }

    private void recolor(View v, boolean fill) {
        if (v != this && v.getTag(com.limelight.R.id.v2_keep_colors) != null) {
            return;
        }
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (fill) {
                savedText.put(t, t.getTextColors());
                t.setTextColor(V2Theme.FILL_INK);
            } else if (savedText.containsKey(t)) {
                t.setTextColor(savedText.remove(t));
            }
        } else if (v instanceof ImageView) {
            ImageView i = (ImageView) v;
            if (fill) {
                savedImage.put(i, i.getColorFilter());
                i.setColorFilter(new PorterDuffColorFilter(V2Theme.FILL_INK, PorterDuff.Mode.SRC_IN));
            } else if (savedImage.containsKey(i)) {
                i.setColorFilter(savedImage.remove(i));
            }
        }
        if (v instanceof ViewGroup && !(v instanceof V2Frame && v != this)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                recolor(g.getChildAt(i), fill);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        V2Input.addListener(this);
        updatePulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        V2Input.removeListener(this);
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
            // Keyframes 0 % = 100 % = rest, 50 % = peak, ease-in-out: half a period each way
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
            pulseAnimator.setDuration(theme.glowPeriodMs / 2);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
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
        if (live && liveStyle == LiveStyle.HALO) {
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
        if (live && liveStyle == LiveStyle.BAR) {
            // lavbar: inset 0 -12px 0 yellow <-> -4px (the 0 % keyframe is the thick one)
            float h = Ui.dpf(getContext(), 12 - 8 * pulse);
            haloPaint.setColor(theme.glowColor);
            haloPaint.setAlpha(255);
            canvas.drawRect(0, getHeight() - h, getWidth(), getHeight(), haloPaint);
        }
        if (save >= 0) {
            canvas.restoreToCount(save);
        }
        if (showFocus()) {
            drawFocus(canvas);
        }
    }

    private void drawFocus(Canvas canvas) {
        if (theme.focusFill && !focusOutline) {
            return;
        }
        float gap = Ui.dpf(getContext(), theme.focusGap);
        float outer = Ui.dpf(getContext(), theme.focusOuter);
        if (!theme.focusFill) {
            // box-shadow 0 0 0 gap bg, 0 0 0 outer colour: two rings that follow the corners
            ringPaint.setColor(theme.bg);
            ringPaint.setStrokeWidth(gap);
            strokeAround(canvas, gap / 2f, ringRadiusPx + gap / 2f);
        }
        ringPaint.setColor(theme.focusColor);
        ringPaint.setStrokeWidth(outer - gap);
        float mid = (gap + outer) / 2f;
        // An outline (Affiche) keeps square corners; a box-shadow spread grows the radius
        strokeAround(canvas, mid, theme.focusFill ? 0 : ringRadiusPx + mid);
    }

    private void strokeAround(Canvas canvas, float offset, float radius) {
        rect.set(-offset, -offset, getWidth() + offset, getHeight() + offset);
        canvas.drawRoundRect(rect, radius, radius, ringPaint);
    }

    private void drawHalo(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (theme.isAffiche()) {
            // lavpop: a hard yellow block offset 10 px, then 5 px at the peak
            haloPaint.setColor(theme.glowColor);
            haloPaint.setAlpha(255);
            float off = Ui.dpf(getContext(), 10 - 5 * pulse);
            canvas.drawRect(off, off, w + off, h + off, haloPaint);
            return;
        }
        ensureGlow(w, h);
        if (glow == null) {
            return;
        }
        // drop-shadow blur grows from glowBlurMin to glowBlurMax, alpha too
        float blurNow = theme.glowBlurMin + (theme.glowBlurMax - theme.glowBlurMin) * pulse;
        float grow = glowPad * (blurNow / theme.glowBlurMax);
        float alpha = theme.glowAlphaMin + (theme.glowAlphaMax - theme.glowAlphaMin) * pulse;
        haloPaint.setAlpha(Math.round(255 * alpha));
        rect.set(-grow, -grow, w + grow, h + grow);
        canvas.drawBitmap(glow, null, rect, haloPaint);
    }

    /**
     * The glow is rendered once per size in software (BlurMaskFilter is not supported by every
     * hardware pipeline), at a quarter of the resolution: the blur hides the upscaling. It is the
     * peak of the keyframes; the pulse shrinks and fades it.
     */
    private void ensureGlow(int w, int h) {
        if (glow != null && glowW == w && glowH == h) {
            return;
        }
        float scale = 0.25f;
        float blur = Ui.dpf(getContext(), theme.glowBlurMax);
        glowPad = blur;
        int bw = Math.max(1, Math.round((w + 2 * glowPad) * scale));
        int bh = Math.max(1, Math.round((h + 2 * glowPad) * scale));
        try {
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(theme.glowColor);
            p.setMaskFilter(new BlurMaskFilter(Math.max(1f, blur * scale / 2f), BlurMaskFilter.Blur.NORMAL));
            float inset = glowPad * scale;
            float rad = liveRadiusPx * scale;
            c.drawRoundRect(new RectF(inset, inset, bw - inset, bh - inset), rad, rad, p);
            glow = b;
            glowW = w;
            glowH = h;
        } catch (OutOfMemoryError e) {
            glow = null;
        }
    }
}
