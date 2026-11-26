package com.limelight.v2.ui;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.v2.V2Theme;

/**
 * The prototype's toast: one short message at the top of the screen, centred, for 2.4 s; a new one
 * replaces the old. Braise: a 40 dp pill in the inverse colours (#F3EEE8 on dark). Affiche: a yellow
 * tab hanging from the top edge, 3 dp ink border without its top side. Ludique: a 48 dp lime pill
 * with a soft shadow. Drawn inside the activity (not a system toast), above everything.
 */
public final class V2Toast {
    public static final long DURATION_MS = 2400;
    private static final Object TAG = new Object();

    private V2Toast() {}

    public static void show(Activity activity, V2Theme theme, CharSequence text) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) {
            android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // One at a time: the new one replaces the old
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View c = content.getChildAt(i);
            if (c.getTag() == TAG) {
                content.removeView(c);
            }
        }
        final View toast = build(activity, theme, text);
        toast.setTag(TAG);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = theme.isAffiche() ? 0 : Ui.dp(activity, theme.isLudique() ? 28 : 24);
        content.addView(toast, lp);
        if (Build.VERSION.SDK_INT >= 21) {
            toast.setElevation(Ui.dpf(activity, 30));
        }
        toast.postDelayed(() -> {
            if (toast.getParent() == content) {
                content.removeView(toast);
            }
        }, DURATION_MS);
    }

    /** The toast's view, also used by the screenshot tests */
    public static View build(android.content.Context ctx, V2Theme theme, CharSequence text) {
        TextView t;
        if (theme.isAffiche()) {
            t = Ui.text(ctx, theme, 15, 800, V2Theme.FILL_INK);
            t.setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 12), Ui.dp(ctx, 20), Ui.dp(ctx, 12));
            t.setBackground(new AfficheTab(Ui.dpf(ctx, 3)));
        } else if (theme.isLudique()) {
            t = Ui.text(ctx, theme, 15, 800, android.graphics.Color.parseColor("#1B1530"));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinHeight(Ui.dp(ctx, 48));
            t.setPadding(Ui.dp(ctx, 22), 0, Ui.dp(ctx, 22), 0);
            t.setBackground(Ui.roundRect(theme.lit.lime, Ui.dpf(ctx, 24), 0, 0));
        } else {
            // fg on bg, inverted: the page's text colour as the fill
            t = Ui.text(ctx, theme, 14, 600, theme.bg);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinHeight(Ui.dp(ctx, 40));
            t.setPadding(Ui.dp(ctx, 18), 0, Ui.dp(ctx, 18), 0);
            t.setBackground(Ui.roundRect(theme.fg, Ui.dpf(ctx, 20), 0, 0));
        }
        t.setText(text);
        t.setMaxLines(2);
        t.setMaxWidth(Ui.dp(ctx, 760));
        return t;
    }

    /** Yellow fill, 3 dp ink border on the left, right and bottom (border-top: 0) */
    private static final class AfficheTab extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint fill = new android.graphics.Paint();
        private final float border;

        AfficheTab(float border) {
            this.border = border;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            fill.setColor(V2Theme.FILL_INK);
            c.drawRect(b, fill);
            fill.setColor(V2Theme.YELLOW);
            c.drawRect(b.left + border, b.top, b.right - border, b.bottom - border, fill);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }
}
