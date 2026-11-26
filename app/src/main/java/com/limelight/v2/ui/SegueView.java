package com.limelight.v2.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.v2.V2Theme;

/**
 * The prototype's segue (isSegue, planche V8-25 « fermeture »): the picture of what runs at 32 %
 * under a radial fade to the page colour, the cover 132 × 198, « ● FERMETURE SUR ATLAS-PC… »,
 * the title in the theme's display font, a 280 × 6 track with a sliding bar (lavslide 1.3 s
 * ease-in-out) and an optional tip. Full screen, it eats the touches while it shows.
 */
public final class SegueView extends FrameLayout {
    private final ValueAnimator slide;

    public SegueView(Context ctx, V2Theme th, String line, String title, Bitmap cover, Bitmap background, boolean blur, String tip) {
        super(ctx);
        setClickable(true);
        setBackgroundColor(th.bg);
        CoverView bg = new CoverView(ctx);
        bg.setBlur(blur);
        bg.setBitmap(background != null ? background : cover);
        bg.setAlpha(0.32f);
        addView(bg, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(new Fade(ctx, th.bg), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        if (cover != null) {
            CoverView c = new CoverView(ctx);
            float r = Ui.dpf(ctx, th.rs);
            c.setRadii(r, r, r, r);
            c.setBitmap(cover);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                c.setElevation(Ui.dpf(ctx, 18));
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(ctx, 132), Ui.dp(ctx, 198));
            clp.bottomMargin = Ui.dp(ctx, 24);
            col.addView(c, clp);
        }
        TextView l = new TextView(ctx);
        l.setTypeface(th.textFont(ctx, 700));
        l.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        l.setLetterSpacing(0.06f);
        l.setAllCaps(true);
        l.setTextColor(th.muted);
        l.setText(line);
        int dot = Ui.dp(ctx, 8);
        l.setCompoundDrawablesRelativeWithIntrinsicBounds(new DotDrawable(th.accent, dot), null, null, null);
        l.setCompoundDrawablePadding(Ui.dp(ctx, 8));
        col.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = new TextView(ctx);
        t.setTypeface(th.displayFont(ctx, th.displayWeight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 46);
        t.setLetterSpacing(th.tracking);
        t.setTextColor(th.fg);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setIncludeFontPadding(false);
        t.setText(th.upper ? title.toUpperCase(java.util.Locale.FRENCH) : title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(ctx, 16);
        col.addView(t, tlp);

        FrameLayout track = new FrameLayout(ctx);
        track.setBackground(Ui.roundRect(th.off, Ui.dpf(ctx, 3), 0, 0));
        track.setClipChildren(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            track.setClipToOutline(true);
        }
        final View bar = new View(ctx);
        bar.setBackground(Ui.roundRect(th.on, Ui.dpf(ctx, 3), 0, 0));
        final int trackW = Ui.dp(ctx, 280);
        track.addView(bar, new LayoutParams(Math.round(trackW * 0.4f), Ui.dp(ctx, 6)));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(trackW, Ui.dp(ctx, 6));
        klp.topMargin = Ui.dp(ctx, 22);
        col.addView(track, klp);
        if (tip != null && !tip.isEmpty()) {
            TextView tipView = new TextView(ctx);
            tipView.setTypeface(th.textFont(ctx, 400));
            tipView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tipView.setTextColor(th.muted);
            tipView.setText(tip);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = Ui.dp(ctx, 26);
            col.addView(tipView, plp);
        }
        addView(col, new LayoutParams(Math.min(Ui.dp(ctx, 820), ViewGroup.LayoutParams.MATCH_PARENT), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // lavslide: translateX(-110 %) → translateX(260 %) of the bar's own width
        slide = ValueAnimator.ofFloat(-1.1f, 2.6f);
        slide.setDuration(1300);
        slide.setRepeatCount(ValueAnimator.INFINITE);
        slide.setInterpolator(new AccelerateDecelerateInterpolator());
        slide.addUpdateListener(a -> bar.setTranslationX((float) a.getAnimatedValue() * trackW * 0.4f));
        bar.setTranslationX(0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        slide.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        slide.cancel();
        super.onDetachedFromWindow();
    }

    /** radial-gradient(ellipse at center, transparent 0%, page 78%) */
    private static final class Fade extends View {
        private final Paint paint = new Paint();
        private final int color;

        Fade(Context ctx, int color) {
            super(ctx);
            this.color = color;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            // An ellipse the shape of the view: a circle scaled vertically
            float r = Math.max(1, w / 2f * 1.41f);
            paint.setShader(new RadialGradient(w / 2f, h / 2f, r,
                    new int[]{color & 0x00FFFFFF, color, color}, new float[]{0f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int save = canvas.save();
            float sy = getWidth() == 0 ? 1 : (float) getHeight() / getWidth();
            canvas.scale(1, sy, getWidth() / 2f, getHeight() / 2f);
            canvas.drawRect(-getWidth(), -getHeight() * 10f, getWidth() * 2f, getHeight() * 10f, paint);
            canvas.restoreToCount(save);
        }
    }
}
