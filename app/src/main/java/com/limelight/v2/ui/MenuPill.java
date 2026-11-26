package com.limelight.v2.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Theme;

/**
 * The prototype's « Menu » pill over the stream (LavArtemis Dev, replaces the floating button):
 * centred at the top, 40 dp high, the theme's surface and border, 88 % opaque, a hamburger and
 * « Menu ». A tap opens the in-game menu. The voice button stays where it is.
 */
public final class MenuPill {
    private MenuPill() {}

    public static LinearLayout create(Context ctx, V2Theme th) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), 0);
        pill.setBackground(Ui.roundRect(th.surface, Ui.dpf(ctx, th.pill), Ui.dp(ctx, th.border), th.borderColor));
        pill.setAlpha(0.88f);
        pill.setClickable(true);
        pill.setFocusable(false);
        pill.setContentDescription(ctx.getString(R.string.v2_menu_pill));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            pill.setElevation(Ui.dpf(ctx, 8));
        }
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(Ui.icon(ctx, R.drawable.v2_ic_m_menu, th.fg));
        int is = Ui.dp(ctx, 16);
        pill.addView(icon, new LinearLayout.LayoutParams(is, is));
        TextView t = Ui.text(ctx, th, 14, 700, th.fg);
        t.setText(R.string.v2_menu_pill);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Ui.dp(ctx, 10));
        pill.addView(t, tlp);
        return pill;
    }

    public static FrameLayout.LayoutParams layoutParams(Context ctx) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(ctx, 40),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.dp(ctx, 14);
        return lp;
    }
}
