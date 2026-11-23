package com.limelight.v2.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

/**
 * Text button of the v2 interface, in three styles:
 * PRIMARY / SECONDARY are LavButton.qml (Reprendre, Lancer: accent fill, or quiet surface2);
 * HEADER is LavPill.qml (Mes PC, Réglages: surface, framed by the theme).
 * An optional hint ("A", "Entrée") shows only for gamepad and keyboard.
 */
public class PillButton extends V2Frame {
    public enum Style { PRIMARY, SECONDARY, HEADER }

    private final Style style;
    private final LinearLayout row;
    private final TextView hint;
    private final ImageView icon;
    private final TextView label;
    private String padHint, keysHint;
    private final int heightPx;
    private int iconGap;
    private int fgColor;

    public PillButton(Context context, V2Theme theme, Style style, int iconRes, String text, float heightDp, float textDp) {
        super(context, theme);
        this.style = style;
        heightPx = Ui.dp(context, heightDp);
        setFocusable(true);
        setClickable(true);
        setMinimumHeight(heightPx);

        boolean primary = style == Style.PRIMARY;
        int fg = primary ? theme.onAccent : theme.fg;

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int padH = Ui.dp(context, style == Style.HEADER ? (text == null || text.isEmpty() ? 12 : 16) : 24);
        row.setPadding(padH, 0, padH, 0);
        addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, heightPx, Gravity.CENTER));

        hint = Ui.text(context, theme, 12, 800, primary ? theme.accent : theme.bg);
        hint.setGravity(Gravity.CENTER);
        int hh = Ui.dp(context, 22);
        hint.setMinWidth(hh);
        hint.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0);
        hint.setBackground(Ui.roundRect(primary ? theme.onAccent : theme.fg, theme.square() ? 0 : hh / 2f, 0, 0));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, hh);
        hlp.setMarginEnd(Ui.dp(context, 10));
        row.addView(hint, hlp);
        hint.setVisibility(View.GONE);

        icon = new ImageView(context);
        int iconSize = Ui.dp(context, style == Style.HEADER ? 22 : 16);
        if (iconRes != 0) {
            icon.setImageDrawable(Ui.icon(context, iconRes, fg));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
            row.addView(icon, ilp);
        }
        iconGap = Ui.dp(context, style == Style.HEADER ? 8 : 9);
        fgColor = fg;

        if (style == Style.HEADER) {
            label = Ui.text(context, theme, textDp, 700, fg);
            label.setText(text);
        } else {
            label = Ui.display(context, theme, textDp, fg);
            label.setTypeface(theme.displayFont(context, 700));
            label.setText(text);
        }
        row.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setText(text);

        float radius;
        if (style == Style.HEADER) {
            radius = Ui.dpf(context, Math.min(theme.pill, heightDp / 2f));
            setBackground(Ui.roundRect(theme.surface, radius, Ui.dp(context, Math.max(1, theme.border)), theme.borderColor));
            setRing(Math.min(theme.pill, heightDp / 2f), 5, theme.accent);
        } else {
            radius = theme.square() ? 0 : heightPx / 2f;
            setBackground(Ui.roundRect(primary ? theme.accent : theme.surface2, radius,
                    theme.isAffiche() ? Ui.dp(context, 3) : 0, theme.fg));
            setRing(theme.square() ? 0 : heightDp / 2f, 5, theme.fg);
        }
    }

    public void setText(String text) {
        boolean has = text != null && !text.isEmpty();
        label.setText(text);
        label.setVisibility(has ? VISIBLE : GONE);
        // Icon and label are spaced only when both show
        if (icon.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) icon.getLayoutParams()).setMarginEnd(has ? iconGap : 0);
            icon.requestLayout();
        }
        setContentDescription(text);
    }

    public void setIcon(int iconRes) {
        icon.setImageDrawable(iconRes == 0 ? null : Ui.icon(getContext(), iconRes, fgColor));
    }

    /** Gamepad and keyboard hints, e.g. "A" and "Entrée" */
    public void setHints(String pad, String keys) {
        this.padHint = pad;
        this.keysHint = keys;
        refreshHint();
    }

    public void refreshHint() {
        V2Input.Kind k = V2Input.get(getContext());
        String h = k == V2Input.Kind.PAD ? padHint : (k == V2Input.Kind.KEYS ? keysHint : null);
        if (h == null || h.isEmpty()) {
            hint.setVisibility(View.GONE);
        } else {
            hint.setText(h);
            hint.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        float s = pressed ? 0.97f : 1f;
        setScaleX(s);
        setScaleY(s);
    }

    public Style getStyle() {
        return style;
    }
}
