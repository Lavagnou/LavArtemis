package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Theme;

/** Last tile of the "Récents" row: dashed frame, grid icon, "Tout voir" (prototype). */
public class SeeAllTile extends V2Frame {
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;

    public SeeAllTile(Context context, V2Theme theme, float widthDp, float heightDp) {
        super(context, theme);
        setScaleOnFocus(true);
        setFocusable(true);
        setClickable(true);
        radius = Ui.dpf(context, Math.min(theme.rs, 16));
        setRing(Math.min(theme.rs, 16), 6, theme.accent);
        setContentDescription(context.getString(R.string.v2_see_all));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(Ui.icon(context, R.drawable.v2_ic_grid, theme.fg));
        int is = Ui.dp(context, 24);
        col.addView(icon, new LinearLayout.LayoutParams(is, is));
        TextView t = Ui.text(context, theme, 13, 600, theme.fg);
        t.setText(R.string.v2_see_all);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(context, 8);
        col.addView(t, tlp);
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(Ui.dpf(context, 1.5f));
        dash.setColor(theme.isAffiche() ? theme.fg : theme.muted);
        dash.setPathEffect(new DashPathEffect(new float[]{Ui.dpf(context, 5), Ui.dpf(context, 4)}, 0));
        setLayoutParams(new LayoutParams(Ui.dp(context, widthDp), Ui.dp(context, heightDp)));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float i = dash.getStrokeWidth() / 2f;
        rect.set(i, i, getWidth() - i, getHeight() - i);
        canvas.drawRoundRect(rect, radius, radius, dash);
        super.dispatchDraw(canvas);
    }
}
