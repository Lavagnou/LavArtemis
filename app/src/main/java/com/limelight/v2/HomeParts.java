package com.limelight.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.limelight.R;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.DotDrawable;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

/**
 * The pieces of the home, one look per theme, copied from the prototype's markup (Proto.dc.html,
 * home of each theme): app cards, recent covers, the "Tout voir" tile, the PC state card, and the
 * text / pill helpers they share. Sizes are prototype px through {@link HomeLayout}.
 */
final class HomeParts {
    private HomeParts() {}

    // ---- text and shapes ---------------------------------------------------------------------

    /** A text in the theme's display (true) or text font, size in prototype px */
    static TextView text(Context ctx, V2Theme th, HomeLayout l, float px, int weight, int color, boolean display) {
        TextView t = new TextView(ctx);
        t.setTypeface(display ? th.displayFont(ctx, weight) : th.textFont(ctx, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, l.t(px));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static void lineHeight(TextView t, Context ctx, HomeLayout l, float px, float lh) {
        TextViewCompat.setLineHeight(t, Math.max(1, Math.round(Ui.dpf(ctx, l.t(px) * lh))));
    }

    /**
     * CSS line box of a one-line title: Android gives the text its font's ascent + descent (about
     * 1.25 em for these fonts), CSS gives it line-height x size. Negative margins take the
     * difference back, half above and half below. Call after the view has its layout params.
     */
    static void cssLine(TextView t, float lh) {
        if (!(t.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        android.graphics.Paint.FontMetrics fm = t.getPaint().getFontMetrics();
        float natural = fm.descent - fm.ascent;
        int delta = Math.round((natural - lh * t.getTextSize()) / 2f);
        if (delta > 0) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) t.getLayoutParams();
            lp.topMargin -= delta;
            lp.bottomMargin -= delta;
            t.setLayoutParams(lp);
        }
    }

    static int px(Context ctx, HomeLayout l, float protoPx) {
        return Ui.dp(ctx, l.b(protoPx));
    }

    static float pxf(Context ctx, HomeLayout l, float protoPx) {
        return Ui.dpf(ctx, l.b(protoPx));
    }

    static GradientDrawable rect(int fill, float radiusPx, int strokePx, int strokeColor) {
        return Ui.roundRect(fill, radiusPx, strokePx, strokeColor);
    }

    static GradientDrawable dashed(int fill, float radiusPx, int strokePx, int strokeColor, float dash, float gap) {
        GradientDrawable g = Ui.roundRect(fill, radiusPx, 0, 0);
        g.setStroke(strokePx, strokeColor, dash, gap);
        return g;
    }

    static int argb(int rgb, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    /** A live dot in front of a text (prototype .lav-live, 8 px) */
    static void liveDot(TextView t, Context ctx, HomeLayout l, int color, float gapPx) {
        int s = Math.max(Ui.dp(ctx, 5), px(ctx, l, 8));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(new DotDrawable(color, s), null, null, null);
        t.setCompoundDrawablePadding(px(ctx, l, gapPx));
    }

    static ImageView icon(Context ctx, int res, int color) {
        ImageView i = new ImageView(ctx);
        i.setImageDrawable(Ui.icon(ctx, res, color));
        return i;
    }

    /** A row: optional icon, then a label */
    static LinearLayout iconLabel(Context ctx, HomeLayout l, int iconRes, float iconPx, float gapPx, TextView label, int iconColor) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (iconRes != 0) {
            int s = px(ctx, l, iconPx);
            row.addView(icon(ctx, iconRes, iconColor), new LinearLayout.LayoutParams(s, s));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(px(ctx, l, gapPx));
            row.addView(label, lp);
        } else {
            row.addView(label);
        }
        return row;
    }

    /** "N h jouées" / "Jamais lancé" (prototype hours()) */
    static String hours(Context ctx, com.limelight.nvstream.http.NvApp app) {
        long s = app.getPlaynitePlaytime();
        if (s <= 0) {
            return ctx.getString(R.string.v2_never_played);
        }
        long h = Math.max(1, Math.round(s / 3600.0));
        return ctx.getString(R.string.v2_hours_played, (int) h);
    }

    // ---- a button with a background and content ------------------------------------------------

    /** A focusable frame: the pills, the round buttons, Reprendre / Quitter */
    static final class Button extends V2Frame {
        Button(Context ctx, V2Theme th) {
            super(ctx, th);
            setFocusable(true);
            setClickable(true);
            Ui.noClip(this);
        }

        void radius(float radiusDp) {
            setRing(radiusDp, 0, 0);
        }
    }

    // ---- the "…" button -------------------------------------------------------------------------

    static View more(Context ctx, V2Theme th, HomeLayout l, float sizePx, float iconPx, int bg, int fg, boolean round, boolean shadow) {
        FrameLayout b = new FrameLayout(ctx);
        int s = px(ctx, l, sizePx);
        b.setBackground(rect(bg, round ? s / 2f : 0, 0, 0));
        if (shadow && android.os.Build.VERSION.SDK_INT >= 21) {
            b.setElevation(Ui.dpf(ctx, 2));
        }
        int is = px(ctx, l, iconPx);
        b.addView(icon(ctx, R.drawable.v2_ic_p_dots, fg), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        b.setContentDescription(ctx.getString(R.string.v2_options));
        b.setClickable(true);
        return b;
    }

    // ---- app cards (Bureau, Playnite Fullscreen) -------------------------------------------------

    /** What a card shows */
    static final class CardData {
        String name, sub, num, run, action;
        boolean running, dimmed, playnite;
        Bitmap art;
    }

    static final class AppCard extends V2Frame {
        private final V2Theme th;
        private final HomeLayout l;
        private final Context ctx;
        private final CoverView art, blurArt;
        private final TextView name, sub, action, pill, num;
        private final View glowOverlay;
        final View more;
        private CardData data;

        AppCard(Context ctx, V2Theme th, HomeLayout l, boolean playnite) {
            super(ctx, th);
            this.ctx = ctx;
            this.th = th;
            this.l = l;
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setScaleOnFocus(true);
            Ui.noClip(this);
            blurArt = new CoverView(ctx);
            art = new CoverView(ctx);
            if (th.isAffiche()) {
                // Text column, art column 220 with an ink rule, ink "…" square
                setBackground(rect(th.bg, 0, 0, 0));
                setRing(0, 0, 0);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                LinearLayout text = new LinearLayout(ctx);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setPadding(px(ctx, l, 26), px(ctx, l, 22), px(ctx, l, 26), px(ctx, l, 22));
                row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
                num = HomeParts.text(ctx, th, l, 14, 800, th.fg, false);
                text.addView(num);
                text.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
                name = HomeParts.text(ctx, th, l, 52, 900, th.fg, true);
                name.setLetterSpacing(-0.045f);
                name.setMaxLines(2);
                lineHeight(name, ctx, l, 52, 0.9f);
                text.addView(name);
                // Ink and yellow in both modes (the prototype does not invert this pill)
                pill = HomeParts.text(ctx, th, l, 13, 800, V2Theme.YELLOW, false);
                pill.setBackgroundColor(V2Theme.FILL_INK);
                pill.setMaxLines(2);
                pill.setPadding(px(ctx, l, 9), px(ctx, l, 5), px(ctx, l, 9), px(ctx, l, 5));
                liveDot(pill, ctx, l, V2Theme.YELLOW, 7);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                plp.topMargin = px(ctx, l, 10);
                text.addView(pill, plp);
                sub = HomeParts.text(ctx, th, l, 15, 400, th.fg, false);
                sub.setMaxLines(2);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                slp.topMargin = px(ctx, l, 10);
                text.addView(sub, slp);
                text.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
                action = HomeParts.text(ctx, th, l, 20, 900, th.fg, false);
                text.addView(action);
                FrameLayout artCol = new FrameLayout(ctx);
                artCol.setBackgroundColor(th.lit.band);
                artCol.addView(art, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(px(ctx, l, 220), LayoutParams.MATCH_PARENT);
                row.addView(ruleV(ctx, l, th.fg), new LinearLayout.LayoutParams(px(ctx, l, 3), LayoutParams.MATCH_PARENT));
                row.addView(artCol, alp);
                row.addView(ruleV(ctx, l, th.fg), new LinearLayout.LayoutParams(px(ctx, l, 3), LayoutParams.MATCH_PARENT));
                more = HomeParts.more(ctx, th, l, 38, 18, th.lit.band, th.lit.onBand, false, false);
                LayoutParams mlp = new LayoutParams(px(ctx, l, 38), px(ctx, l, 38), Gravity.TOP | Gravity.END);
                mlp.setMargins(0, px(ctx, l, 14), px(ctx, l, 17), 0);
                addView(more, mlp);
                glowOverlay = null;
            } else if (th.isLudique()) {
                // Colour block, rotated art, navy pills
                float r = pxf(ctx, l, 36);
                setBackground(rect(playnite ? th.lit.lime : th.lit.coral, r, 0, 0));
                setRing(l.b(36), 0, 0);
                int ink = 0xFF1B1530;
                art.setRadii(pxf(ctx, l, 22), pxf(ctx, l, 22), pxf(ctx, l, 22), pxf(ctx, l, 22));
                art.setRotation(6);
                if (android.os.Build.VERSION.SDK_INT >= 21 && !th.dark) {
                    art.setElevation(Ui.dpf(ctx, 6));
                    art.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), pxf(ctx, l, 22));
                        }
                    });
                }
                LayoutParams alp = new LayoutParams(px(ctx, l, 118), px(ctx, l, 158), Gravity.TOP | Gravity.END);
                alp.setMargins(0, px(ctx, l, 22), px(ctx, l, 70), 0);
                addView(art, alp);
                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.BOTTOM);
                col.setPadding(px(ctx, l, 24), px(ctx, l, 24), px(ctx, l, 24), px(ctx, l, 24));
                addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                pill = HomeParts.text(ctx, th, l, 13, 800, th.lit.bandLive, false);
                pill.setGravity(Gravity.CENTER_VERTICAL);
                pill.setPadding(px(ctx, l, 14), 0, px(ctx, l, 14), 0);
                pill.setBackground(rect(th.lit.band, pxf(ctx, l, 15), 0, 0));
                liveDot(pill, ctx, l, th.lit.bandLive, 7);
                LayoutParams plp = new LayoutParams(LayoutParams.WRAP_CONTENT, px(ctx, l, 30), Gravity.TOP | Gravity.START);
                plp.setMargins(px(ctx, l, 24), px(ctx, l, 22), 0, 0);
                addView(pill, plp);
                name = HomeParts.text(ctx, th, l, 34, 800, ink, true);
                name.setLetterSpacing(-0.03f);
                col.addView(name, new LinearLayout.LayoutParams(px(ctx, l, 400), LayoutParams.WRAP_CONTENT));
                sub = HomeParts.text(ctx, th, l, 15, 600, ink, false);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                slp.topMargin = px(ctx, l, 4);
                col.addView(sub, slp);
                action = HomeParts.text(ctx, th, l, 16, 800, th.lit.onBand, false);
                LinearLayout actionPill = iconLabel(ctx, l, R.drawable.v2_ic_p_play, 14, 8, action, th.lit.onBand);
                actionPill.setPadding(px(ctx, l, 20), 0, px(ctx, l, 20), 0);
                actionPill.setBackground(rect(th.lit.band, pxf(ctx, l, 21), 0, 0));
                LinearLayout.LayoutParams aplp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, px(ctx, l, 42));
                aplp.topMargin = px(ctx, l, 12);
                col.addView(actionPill, aplp);
                more = HomeParts.more(ctx, th, l, 40, 18, th.lit.card, th.fg, true, !th.dark);
                LayoutParams mlp = new LayoutParams(px(ctx, l, 40), px(ctx, l, 40), Gravity.TOP | Gravity.END);
                mlp.setMargins(0, px(ctx, l, 14), px(ctx, l, 14), 0);
                addView(more, mlp);
                num = null;
                glowOverlay = null;
            } else {
                // Braise: blurred art under a veil, sharp art on the right, accent gradient when live
                float r = pxf(ctx, l, 22);
                setBackground(rect(th.lit.card, r, 0, 0));
                setRing(l.b(22), 0, 0);
                setClipToOutline21(this, r);
                blurArt.setBlur(true);
                addView(blurArt, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                addView(new GradientView(ctx, 0f, new int[]{argb(th.lit.veil, 0.92f), argb(th.lit.veil, 0.7f), argb(th.lit.veil, 0.35f)},
                        new float[]{0f, 0.55f, 1f}), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                addView(art, new LayoutParams(px(ctx, l, 127), LayoutParams.MATCH_PARENT, Gravity.END));
                glowOverlay = new GradientView(ctx, 100f, new int[]{0x6BFF6A2B, 0x1FFF6A2B, 0x00FF6A2B}, new float[]{0f, 0.55f, 0.8f});
                addView(glowOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                LinearLayout text = new LinearLayout(ctx);
                text.setOrientation(LinearLayout.VERTICAL);
                LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                tlp.setMargins(px(ctx, l, 26), px(ctx, l, 20), px(ctx, l, 150), px(ctx, l, 20));
                addView(text, tlp);
                pill = HomeParts.text(ctx, th, l, 12, 700, 0xFF0E0D0C, false);
                pill.setGravity(Gravity.CENTER_VERTICAL);
                pill.setPadding(px(ctx, l, 12), 0, px(ctx, l, 12), 0);
                pill.setBackground(rect(0xFFFF6A2B, pxf(ctx, l, 13), 0, 0));
                liveDot(pill, ctx, l, 0xFF0E0D0C, 7);
                text.addView(pill, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, px(ctx, l, 26)));
                sub = HomeParts.text(ctx, th, l, 14, 400, th.lit.muted2, false);
                text.addView(sub);
                text.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
                name = HomeParts.text(ctx, th, l, 34, 700, th.fg, true);
                name.setLetterSpacing(-0.01f);
                text.addView(name);
                text.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
                action = HomeParts.text(ctx, th, l, 15, 700, th.lit.accentText, false);
                text.addView(iconLabel(ctx, l, R.drawable.v2_ic_p_play, 14, 8, action, th.lit.accentText));
                more = HomeParts.more(ctx, th, l, 38, 18, argb(th.lit.veil, 0.72f), th.fg, true, false);
                LayoutParams mlp = new LayoutParams(px(ctx, l, 38), px(ctx, l, 38), Gravity.TOP | Gravity.END);
                mlp.setMargins(0, px(ctx, l, 14), px(ctx, l, 141), 0);
                addView(more, mlp);
                num = null;
            }
        }

        void bind(CardData d) {
            data = d;
            name.setText(d.name);
            setContentDescription(d.name);
            if (num != null) {
                num.setText(d.num);
            }
            action.setText(d.action);
            pill.setText(d.run);
            Ui.setVisible(pill, d.running);
            if (th.isLudique()) {
                sub.setText(d.sub);
            } else {
                sub.setText(d.sub);
                Ui.setVisible(sub, !d.running);
            }
            if (glowOverlay != null) {
                Ui.setVisible(glowOverlay, d.running);
            }
            setDimmed(d.dimmed);
            setLive(d.running, th.isAffiche() ? 0 : l.b(th.isLudique() ? 36 : 22),
                    th.isAffiche() ? LiveStyle.BAR : LiveStyle.HALO);
        }

        void setArt(Bitmap b) {
            art.setBitmap(b);
            blurArt.setBitmap(b);
        }
    }

    static View ruleV(Context ctx, HomeLayout l, int color) {
        View v = new View(ctx);
        v.setBackgroundColor(color);
        return v;
    }

    static void setClipToOutline21(View v, final float radius) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            v.setClipToOutline(true);
        }
    }

    /** A linear gradient that fills its view: angle in CSS degrees (0 = 90deg left-to-right here, 180 = top-down) */
    static final class GradientView extends View {
        private final Paint paint = new Paint();
        private final float angle;
        private final int[] colors;
        private final float[] stops;

        /** @param cssAngle 0 means "90deg" (left to right); 180 means top to bottom; other = CSS angle */
        GradientView(Context ctx, float cssAngle, int[] colors, float[] stops) {
            super(ctx);
            this.angle = cssAngle;
            this.colors = colors;
            this.stops = stops;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float x0, y0, x1, y1;
            if (angle == 0f) {
                x0 = 0; y0 = 0; x1 = w; y1 = 0;
            } else if (angle == 180f) {
                x0 = 0; y0 = 0; x1 = 0; y1 = h;
            } else {
                // CSS angle: 90deg = to the right, 180deg = down
                double a = Math.toRadians(angle);
                float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
                float half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2f;
                x0 = w / 2f - dx * half; y0 = h / 2f - dy * half;
                x1 = w / 2f + dx * half; y1 = h / 2f + dy * half;
            }
            paint.setShader(new LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

    // ---- recent covers --------------------------------------------------------------------------

    static final class Cover extends V2Frame {
        private final CoverView cover;
        private final TextView name;
        final View more;
        private final V2Theme th;
        private final HomeLayout l;
        int appId = Integer.MIN_VALUE;

        Cover(Context ctx, V2Theme th, HomeLayout l, float wPx, float hPx) {
            this(ctx, th, l, wPx, hPx, -1);
        }

        /** radiusPx: prototype px of the corners (library: Braise 12), -1 for the home's */
        Cover(Context ctx, V2Theme th, HomeLayout l, float wPx, float hPx, float radiusPx) {
            super(ctx, th);
            this.th = th;
            this.l = l;
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setScaleOnFocus(true);
            Ui.noClip(this);
            float rp = radiusPx >= 0 ? radiusPx : (th.isLudique() ? 24 : 14);
            float r = th.isAffiche() ? 0 : pxf(ctx, l, rp);
            int bg = th.isAffiche() ? th.lit.band : (th.isLudique() ? th.surface2 : th.lit.card);
            setBackground(rect(bg, r, 0, 0));
            setRing(th.isAffiche() ? 0 : l.b(rp), 0, 0);
            // Affiche: the outline alone (ring2), the cover is not filled yellow
            setFocusFill(false);
            name = HomeParts.text(ctx, th, l, 15, 800, th.isAffiche() ? th.lit.onBand : th.muted, true);
            name.setMaxLines(3);
            name.setGravity(Gravity.BOTTOM | Gravity.START);
            int pad = px(ctx, l, 10);
            name.setPadding(pad, pad, pad, pad);
            addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            cover = new CoverView(ctx);
            cover.setRadii(r, r, r, r);
            addView(cover, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (th.isAffiche()) {
                more = HomeParts.more(ctx, th, l, 32, 16, th.lit.band, th.lit.onBand, false, false);
                addView(more, new LayoutParams(px(ctx, l, 32), px(ctx, l, 32), Gravity.TOP | Gravity.END));
            } else if (th.isLudique()) {
                more = HomeParts.more(ctx, th, l, 32, 16, th.lit.card, th.fg, true, !th.dark);
                LayoutParams mlp = new LayoutParams(px(ctx, l, 32), px(ctx, l, 32), Gravity.TOP | Gravity.END);
                mlp.setMargins(0, px(ctx, l, 8), px(ctx, l, 8), 0);
                addView(more, mlp);
            } else {
                more = HomeParts.more(ctx, th, l, 30, 16, argb(th.lit.veil, 0.78f), th.fg, true, false);
                LayoutParams mlp = new LayoutParams(px(ctx, l, 30), px(ctx, l, 30), Gravity.TOP | Gravity.END);
                mlp.setMargins(0, px(ctx, l, 6), px(ctx, l, 6), 0);
                addView(more, mlp);
            }
        }

        void bind(int id, String title, boolean running, boolean dimmed) {
            if (appId != id) {
                cover.setBitmap(null);
                appId = id;
            }
            name.setText(title);
            setContentDescription(title);
            setDimmed(dimmed);
            setLive(running, th.isAffiche() ? 0 : l.b(th.isLudique() ? 24 : 14));
            name.setVisibility(cover.getBitmap() == null ? VISIBLE : INVISIBLE);
        }

        void setCover(Bitmap b) {
            cover.setBitmap(b);
            name.setVisibility(b == null ? VISIBLE : INVISIBLE);
        }
    }

    // ---- "Tout voir" tile ----------------------------------------------------------------------

    static final class LibTile extends V2Frame {
        final TextView count;

        LibTile(Context ctx, V2Theme th, HomeLayout l) {
            super(ctx, th);
            setFocusable(true);
            setClickable(true);
            setScaleOnFocus(true);
            Ui.noClip(this);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView title;
            if (th.isAffiche()) {
                setBackground(rect(th.bg, 0, px(ctx, l, 3), th.fg));
                setRing(0, 0, 0);
                col.setPadding(px(ctx, l, 16), px(ctx, l, 16), px(ctx, l, 16), px(ctx, l, 16));
                count = HomeParts.text(ctx, th, l, 13, 800, th.fg, false);
                title = HomeParts.text(ctx, th, l, 34, 900, th.fg, true);
                title.setLetterSpacing(-0.03f);
                title.setMaxLines(2);
                lineHeight(title, ctx, l, 34, 0.95f);
            } else if (th.isLudique()) {
                float r = pxf(ctx, l, 24);
                setBackground(rect(Color.TRANSPARENT, r, px(ctx, l, 3), th.lit.band));
                setRing(l.b(24), 0, 0);
                col.setPadding(px(ctx, l, 16), px(ctx, l, 16), px(ctx, l, 16), px(ctx, l, 16));
                count = HomeParts.text(ctx, th, l, 13, 800, argb(th.fg, 0.75f), false);
                title = HomeParts.text(ctx, th, l, 22, 800, th.fg, true);
                title.setMaxLines(2);
                lineHeight(title, ctx, l, 22, 1.05f);
            } else {
                float r = pxf(ctx, l, 14);
                setBackground(dashed(argb(th.lit.card, 0.8f), r, Math.max(1, Math.round(pxf(ctx, l, 1.5f))), th.lit.chipBorder,
                        pxf(ctx, l, 5), pxf(ctx, l, 4)));
                setRing(l.b(14), 0, 0);
                col.setPadding(px(ctx, l, 14), px(ctx, l, 14), px(ctx, l, 14), px(ctx, l, 14));
                count = HomeParts.text(ctx, th, l, 13, 400, th.muted, false);
                title = HomeParts.text(ctx, th, l, 22, 700, th.fg, true);
                title.setMaxLines(2);
                lineHeight(title, ctx, l, 22, 1.05f);
            }
            title.setText(R.string.v2_see_all_arrow);
            col.addView(count);
            col.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
            col.addView(title);
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setContentDescription(ctx.getString(R.string.v2_see_all));
        }
    }

    // ---- PC state card (offline, searching, loading, no PC) ------------------------------------

    static final class StateCard extends V2Frame {
        final TextView title, action, text;

        StateCard(Context ctx, V2Theme th, HomeLayout l) {
            super(ctx, th);
            setFocusable(true);
            setClickable(true);
            Ui.noClip(this);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            if (th.isAffiche()) {
                setBackground(rect(th.bg, 0, px(ctx, l, 3), th.fg));
                setRing(0, 0, 0);
                col.setPadding(px(ctx, l, 32), px(ctx, l, 28), px(ctx, l, 32), px(ctx, l, 28));
                title = HomeParts.text(ctx, th, l, 15, 800, th.fg, false);
                action = HomeParts.text(ctx, th, l, 88, 900, th.fg, true);
                action.setLetterSpacing(-0.05f);
                text = HomeParts.text(ctx, th, l, 15, 400, th.fg, false);
            } else if (th.isLudique()) {
                float r = pxf(ctx, l, 40);
                setBackground(dashed(th.lit.card, r, px(ctx, l, 3), th.lit.chipBorder, pxf(ctx, l, 9), pxf(ctx, l, 7)));
                setRing(l.b(40), 0, 0);
                col.setPadding(px(ctx, l, 30), px(ctx, l, 30), px(ctx, l, 30), px(ctx, l, 30));
                title = HomeParts.text(ctx, th, l, 16, 700, th.lit.muted2, false);
                action = HomeParts.text(ctx, th, l, 40, 800, th.fg, true);
                action.setLetterSpacing(-0.03f);
                text = HomeParts.text(ctx, th, l, 15, 600, th.muted, false);
            } else {
                float r = pxf(ctx, l, 22);
                setBackground(rect(th.lit.card, r, 0, 0));
                setRing(l.b(22), 0, 0);
                col.setPadding(px(ctx, l, 28), px(ctx, l, 24), px(ctx, l, 28), px(ctx, l, 24));
                title = HomeParts.text(ctx, th, l, 14, 400, th.lit.muted2, false);
                action = HomeParts.text(ctx, th, l, 44, 700, th.fg, true);
                action.setLetterSpacing(-0.02f);
                text = HomeParts.text(ctx, th, l, 14, 400, th.lit.muted2, false);
            }
            text.setMaxLines(2);
            col.addView(title);
            col.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
            col.addView(action);
            col.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
            col.addView(text);
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }
}
