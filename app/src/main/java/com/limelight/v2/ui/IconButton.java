package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.v2.V2Theme;

/**
 * Round icon button (RoundIconButton.qml): chevrons, back, light/dark, the cards' "…", the
 * header's Mes PC / Réglages on a phone. Square for Affiche. The touch target is the whole view
 * (48 dp at least); the visible disc can be smaller. Can show a short text instead of the icon
 * ("LB") and a corner badge ("A" for the light/dark button in Système mode).
 */
public class IconButton extends V2Frame {
    private final ImageView icon;
    private final TextView text;
    private final int discPx;
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int fill;
    private int borderWidth;
    private int iconColor;
    private String badge;
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * @param sizeDp  touch size
     * @param discDp  visible disc
     * @param fill    disc colour (0 for none)
     * @param border  whether the disc has the theme's frame
     */
    public IconButton(Context context, V2Theme theme, int iconRes, float sizeDp, float discDp, float iconDp,
                      int fill, boolean border) {
        super(context, theme);
        setFocusable(true);
        setClickable(true);
        int size = Ui.dp(context, sizeDp);
        setMinimumWidth(size);
        setMinimumHeight(size);
        discPx = Ui.dp(context, discDp);
        this.fill = fill;
        this.borderWidth = border ? Ui.dp(context, Math.max(1, theme.border)) : 0;
        this.iconColor = theme.fg;

        icon = new ImageView(context);
        int is = Ui.dp(context, iconDp);
        addView(icon, new LayoutParams(is, is, Gravity.CENTER));
        setIcon(iconRes);

        text = Ui.text(context, theme, 12, 800, theme.fg);
        text.setGravity(Gravity.CENTER);
        addView(text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        text.setVisibility(GONE);

        discPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        badgePaint.setColor(theme.fg);
        badgePaint.setTextSize(Ui.dpf(context, 10));
        badgePaint.setTypeface(theme.textFont(context, 800));
        badgePaint.setFakeBoldText(V2Theme.needsFakeBold(800));
        // The ring circles the disc 4 dp out; it is drawn relative to the view's (bigger) bounds
        float outset = 4 - (sizeDp - discDp) / 2f;
        setRing(theme.square() ? 0 : sizeDp / 2f, outset, theme.accent);
    }

    public void setIcon(int iconRes) {
        if (iconRes == 0) {
            icon.setImageDrawable(null);
            return;
        }
        Drawable d = Ui.icon(getContext(), iconRes, iconColor);
        icon.setImageDrawable(d);
    }

    public void setIconColor(int color, int iconRes) {
        iconColor = color;
        setIcon(iconRes);
    }

    /** Shows a short label (gamepad "LB") instead of the icon; null shows the icon again */
    public void setLabel(String label) {
        if (label == null) {
            text.setVisibility(GONE);
            icon.setVisibility(VISIBLE);
        } else {
            text.setText(label);
            text.setVisibility(VISIBLE);
            icon.setVisibility(GONE);
        }
    }

    public void setBadge(String badge) {
        this.badge = badge;
        invalidate();
    }

    public void setFill(int fill) {
        this.fill = fill;
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.35f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = discPx / 2f;
        boolean square = theme.square();
        if (fill != 0 || isPressed()) {
            discPaint.setColor(isPressed() ? theme.surface2 : fill);
            if (square) {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, discPaint);
            } else {
                canvas.drawCircle(cx, cy, r, discPaint);
            }
        }
        if (borderWidth > 0) {
            borderPaint.setColor(theme.borderColor);
            float br = r - borderWidth / 2f;
            if (square) {
                canvas.drawRect(cx - br, cy - br, cx + br, cy + br, borderPaint);
            } else {
                canvas.drawCircle(cx, cy, br, borderPaint);
            }
        }
        super.dispatchDraw(canvas);
        if (badge != null && !badge.isEmpty()) {
            float bx = cx + Ui.dpf(getContext(), 7);
            float by = cy + Ui.dpf(getContext(), 13);
            canvas.drawText(badge, bx, by, badgePaint);
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }
}
