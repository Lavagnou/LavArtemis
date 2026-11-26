package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.v2.V2Theme;

/**
 * The stream's statistics in the theme's look (LavArtemis Dev; prototype stats overlay, V8-66,
 * design-port-spec §1.9): Braise a dark translucent box, rounded 8, monospace 12; Affiche a black
 * square box with yellow text 12/600; Ludique a rounded 18 box, 13/600, further from the edge. The
 * text itself stays the one the decoder writes.
 */
public final class StatsStyle {
    private StatsStyle() {}

    public static void apply(TextView t, V2Theme th) {
        Context ctx = t.getContext();
        int padH, padV, left;
        if (th.isAffiche()) {
            t.setBackgroundColor(0xFF111111);
            t.setTextColor(0xFFFFD60A);
            t.setTypeface(th.textFont(ctx, 600));
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            padH = 12;
            padV = 8;
            left = 16;
        } else if (th.isLudique()) {
            t.setBackground(Ui.roundRect(th.dark ? 0xE615112A : 0xD91B1530, Ui.dpf(ctx, 18), 0, 0));
            t.setTextColor(th.dark ? 0xFFF3EEFF : 0xFFFFFFFF);
            t.setTypeface(th.textFont(ctx, 600));
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            padH = 16;
            padV = 10;
            left = 20;
        } else {
            t.setBackground(Ui.roundRect(0xA6000000, Ui.dpf(ctx, 8), 0, 0));
            t.setTextColor(0xFFF3EEE8);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            padH = 12;
            padV = 8;
            left = 16;
        }
        t.setPadding(Ui.dp(ctx, padH), Ui.dp(ctx, padV), Ui.dp(ctx, padH), Ui.dp(ctx, padV));
        t.setGravity(Gravity.START);
        ViewGroup.LayoutParams lp = t.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams ll = (LinearLayout.LayoutParams) lp;
            ll.gravity = Gravity.START;
            ll.topMargin = 0;
            ll.setMarginStart(Ui.dp(ctx, left));
            ll.leftMargin = Ui.dp(ctx, left);
            t.setLayoutParams(ll);
        }
    }
}
