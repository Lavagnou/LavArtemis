package com.limelight.v2.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.v2.V2Theme;

/**
 * The prototype's hints bar (gamepad and keyboard only): each hint is a key and what it does, and
 * a tap runs it. Braise: round keycaps in the inverse colours, labels in the secondary text colour,
 * bottom right. Affiche: ink blocks with the key in yellow, bottom left. Ludique: one white (dark:
 * #221C3D) capsule holding navy keycaps with lime letters, bottom right.
 */
public final class HintsBar extends LinearLayout {
    private final V2Theme theme;
    private final float scale;

    public HintsBar(Context context, V2Theme theme, float scale) {
        super(context);
        this.theme = theme;
        this.scale = scale;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        if (theme.isLudique()) {
            setPadding(px(8), 0, px(8), 0);
            setMinimumHeight(px(44));
            setBackground(Ui.roundRect(theme.lit.card, px(22), 0, 0));
        }
    }

    private int px(float protoPx) {
        return Ui.dp(getContext(), protoPx * scale);
    }

    /** Where the bar sits in a full-screen FrameLayout (prototype positions) */
    public FrameLayout.LayoutParams layoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                theme.isLudique() ? px(44) : ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | (theme.isAffiche() ? Gravity.START : Gravity.END));
        if (theme.isAffiche()) {
            lp.setMargins(px(32), 0, 0, px(16));
        } else if (theme.isLudique()) {
            lp.setMargins(0, 0, px(48), px(20));
        } else {
            lp.setMargins(0, 0, px(40), px(16));
        }
        return lp;
    }

    public void clear() {
        removeAllViews();
    }

    public void add(String key, String label, Runnable action) {
        Context ctx = getContext();
        LinearLayout hint = new LinearLayout(ctx);
        hint.setOrientation(HORIZONTAL);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        if (action != null) {
            hint.setOnClickListener(v -> action.run());
        }
        LayoutParams lp;
        if (theme.isAffiche()) {
            hint.setBackgroundColor(theme.lit.band);
            hint.setPadding(px(12), 0, px(12), 0);
            TextView k = Ui.text(ctx, theme, 13 * scale, 900, theme.lit.bandLive);
            k.setText(key);
            hint.addView(k);
            TextView l = Ui.text(ctx, theme, 13 * scale, 600, theme.lit.onBand);
            l.setText(label);
            LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            llp.setMarginStart(px(8));
            hint.addView(l, llp);
            lp = new LayoutParams(LayoutParams.WRAP_CONTENT, px(34));
            if (getChildCount() > 0) {
                lp.setMarginStart(px(6));
            }
        } else {
            boolean lu = theme.isLudique();
            if (lu) {
                hint.setPadding(px(4), 0, px(10), 0);
            }
            TextView k = Ui.text(ctx, theme, 12 * scale, lu ? 800 : 700, lu ? theme.lit.bandLive : theme.bg);
            k.setText(key);
            k.setGravity(Gravity.CENTER);
            k.setMinWidth(px(28));
            k.setPadding(px(8), 0, px(8), 0);
            k.setBackground(Ui.roundRect(lu ? theme.lit.band : theme.fg, px(14), 0, 0));
            hint.addView(k, new LayoutParams(LayoutParams.WRAP_CONTENT, px(28)));
            TextView l = Ui.text(ctx, theme, 14 * scale, lu ? 700 : 400, lu ? theme.fg : theme.lit.text2);
            l.setText(label);
            LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            llp.setMarginStart(px(8));
            hint.addView(l, llp);
            lp = new LayoutParams(LayoutParams.WRAP_CONTENT, px(lu ? 36 : 36));
            if (getChildCount() > 0) {
                lp.setMarginStart(px(lu ? 4 : 22));
            }
        }
        addView(hint, lp);
    }
}
