package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.limelight.v2.V2Theme;

/**
 * On / off switch of the v2 interface (LavSwitch.qml): on = the theme's "on" colour with the thumb
 * on the right, off = "off" with the fg thumb on the left; square and framed for Affiche. Visual
 * only: the row that holds it takes the touch and the keys.
 */
public class V2Switch extends View {
    private final V2Theme theme;
    private final int widthPx, heightPx;
    private boolean on;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public V2Switch(Context context, V2Theme theme, float widthDp, float heightDp) {
        super(context);
        this.theme = theme;
        widthPx = Ui.dp(context, widthDp);
        heightPx = Ui.dp(context, heightDp);
        setFocusable(false);
        setClickable(false);
    }

    public void setOn(boolean on) {
        if (this.on != on) {
            this.on = on;
            invalidate();
        }
    }

    public boolean isOn() {
        return on;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthPx, heightPx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        boolean square = theme.square();
        float r = square ? 0 : h / 2f;
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? theme.on : theme.off);
        canvas.drawRoundRect(rect, r, r, paint);
        if (theme.isAffiche()) {
            float b = Ui.dpf(getContext(), 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(b);
            paint.setColor(theme.fg);
            rect.inset(b / 2f, b / 2f);
            canvas.drawRect(rect, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float inset = Ui.dpf(getContext(), 4);
        float d = h - 2 * inset;
        float x = on ? w - inset - d : inset;
        paint.setColor(on ? theme.onAccent : theme.fg);
        rect.set(x, inset, x + d, inset + d);
        float tr = square ? 0 : d / 2f;
        canvas.drawRoundRect(rect, tr, tr, paint);
    }
}
