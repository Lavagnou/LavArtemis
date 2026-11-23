package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.limelight.v2.V2Theme;

/** Small helpers shared by the v2 views: sizes, texts, icons, shapes. */
public final class Ui {
    private Ui() {}

    public static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    public static float dpf(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    /**
     * A text in the theme's text font. Sizes are in dp, not sp, like the desktop's pixel sizes:
     * the cards and tiles have fixed heights the validated layouts were drawn for.
     */
    public static TextView text(Context context, V2Theme theme, float sizeDp, int weight, int color) {
        TextView t = new TextView(context);
        t.setTypeface(theme.textFont(context, weight));
        t.getPaint().setFakeBoldText(V2Theme.needsFakeBold(weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** A title in the theme's display font (Space Grotesk, Archivo Black, Bricolage) */
    public static TextView display(Context context, V2Theme theme, float sizeDp, int color) {
        TextView t = new TextView(context);
        t.setTypeface(theme.displayFont(context));
        t.getPaint().setFakeBoldText(V2Theme.needsFakeBold(theme.displayWeight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(theme.tracking);
        t.setAllCaps(theme.upper);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** One of the v2 line icons (res/drawable/v2_ic_*), in the given colour */
    public static Drawable icon(Context context, int resId, int color) {
        Drawable d = ContextCompat.getDrawable(context, resId);
        if (d == null) {
            return null;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, color);
        return d;
    }

    public static GradientDrawable roundRect(int fill, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            g.setStroke(strokePx, strokeColor);
        }
        return g;
    }

    /** The "EN COURS" badge: live colour, bold text, a dot on the cards (not on the small covers) */
    public static TextView liveBadge(Context context, V2Theme theme, String label, float sizeDp, boolean dot) {
        TextView t = text(context, theme, sizeDp, 800, theme.onLive);
        t.setText(label);
        t.setGravity(Gravity.CENTER_VERTICAL);
        int h = dp(context, sizeDp * 2 + 2);
        t.setMinHeight(h);
        t.setPadding(dp(context, dot ? 8 : 7), 0, dp(context, dot ? 9 : 7), 0);
        t.setBackground(roundRect(theme.live, theme.square() ? 0 : h / 2f, 0, 0));
        if (dot) {
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(new DotDrawable(theme.onLive, dp(context, 6)), null, null, null);
            t.setCompoundDrawablePadding(dp(context, 6));
        }
        return t;
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Color.alpha(color) * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Colour a over b (both opaque or not), like Qt.tint */
    public static int blend(int base, int over, float overAlpha) {
        float a = overAlpha;
        int r = Math.round(Color.red(base) * (1 - a) + Color.red(over) * a);
        int g = Math.round(Color.green(base) * (1 - a) + Color.green(over) * a);
        int b = Math.round(Color.blue(base) * (1 - a) + Color.blue(over) * a);
        return Color.argb(Color.alpha(base), r, g, b);
    }

    /** Focus rings and halos are drawn outside their view: no ancestor up to the root may clip them */
    public static void noClip(ViewGroup... groups) {
        for (ViewGroup g : groups) {
            g.setClipChildren(false);
            g.setClipToPadding(false);
        }
    }

    public static ViewGroup.MarginLayoutParams margins(ViewGroup.MarginLayoutParams lp, int l, int t, int r, int b) {
        lp.setMargins(l, t, r, b);
        return lp;
    }

    public static void setVisible(View v, boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (v.getVisibility() != vis) {
            v.setVisibility(vis);
        }
    }
}
