package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

/**
 * Half-width card of the home (AppCard.qml): Bureau or Playnite Fullscreen. Its own cover on the
 * right, cut to the card's rounded corners (the user's cover from the host, never a game image),
 * name and action on the left. Running: "EN COURS" badge, warm tint, halo, "Rejoindre".
 * Something else running: dimmed.
 */
public class AppCard extends V2Frame {
    private final CoverView cover;
    private final LinearLayout column;
    private final TextView badge;
    private final TextView subtitle;
    private final TextView title;
    private final ImageView actionIcon;
    private final TextView action;
    private final IconButton more;
    private final int coverWidth;
    private final int border;
    private final boolean showSubtitle;
    private boolean running;
    private final Paint linePaint = new Paint();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private final String launchLabel, joinLabel;

    public AppCard(Context context, V2Theme theme, float heightDp, boolean compact) {
        super(context, theme);
        setScaleOnFocus(true);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        int h = Ui.dp(context, heightDp);
        border = Ui.dp(context, theme.border);
        radius = Ui.dpf(context, theme.r);
        coverWidth = Math.round(h * 0.75f);
        showSubtitle = !compact;
        launchLabel = context.getString(R.string.v2_launch);
        joinLabel = context.getString(R.string.v2_join);

        setBackground(Ui.roundRect(theme.surface, radius, border, theme.borderColor));
        setRing(theme.r, 6, theme.accent);
        setMinimumHeight(h);

        cover = new CoverView(context);
        float inner = Math.max(0, radius - border);
        cover.setRadii(0, inner, inner, 0);
        LayoutParams clp = new LayoutParams(coverWidth - border, h - 2 * border, Gravity.END | Gravity.TOP);
        clp.setMargins(0, border, border, border);
        addView(cover, clp);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams colp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        colp.setMargins(Ui.dp(context, compact ? 16 : 20), 0, coverWidth + Ui.dp(context, 12), 0);
        addView(column, colp);

        badge = Ui.liveBadge(context, theme, "", compact ? 11 : 12, true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = Ui.dp(context, compact ? 5 : 8);
        column.addView(badge, blp);

        subtitle = Ui.text(context, theme, 14, 500, theme.muted);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = Ui.dp(context, 6);
        column.addView(subtitle, slp);

        title = Ui.display(context, theme, compact ? 22 : 28, theme.fg);
        // A narrow card wraps the title on two lines instead of cutting it (desktop AppCard)
        title.setMaxLines(2);
        title.setLineSpacing(0, 0.95f);
        column.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(context, compact ? 6 : 10);
        column.addView(actionRow, alp);
        actionIcon = new ImageView(context);
        int is = Ui.dp(context, 11);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        ilp.setMarginEnd(Ui.dp(context, 7));
        actionRow.addView(actionIcon, ilp);
        action = Ui.text(context, theme, 15, 700, theme.accent);
        actionRow.addView(action);

        more = new IconButton(context, theme, R.drawable.v2_ic_dots, 48, 40, 20, Ui.withAlpha(theme.surface2, 0.92f), false);
        more.setFocusable(false);
        more.setContentDescription(context.getString(R.string.v2_options));
        LayoutParams mlp = new LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48), Gravity.TOP | Gravity.END);
        mlp.setMargins(0, Ui.dp(context, compact ? 2 : 6), coverWidth + Ui.dp(context, compact ? 2 : 6), 0);
        addView(more, mlp);

        linePaint.setColor(theme.borderColor);
        refreshInput();
    }

    /** @param liveLine badge text while running ("EN COURS", "EN COURS · Hades") */
    public void bind(String titleText, String subtitleText, String liveLine, boolean running, boolean dimmed) {
        this.running = running;
        title.setText(titleText);
        setContentDescription(titleText);
        subtitle.setText(subtitleText);
        Ui.setVisible(subtitle, showSubtitle && !running && subtitleText != null && !subtitleText.isEmpty());
        badge.setText(liveLine);
        Ui.setVisible(badge, running);
        int actionColor = theme.accent;
        if (theme.isAffiche() || (theme.isLudique() && !theme.dark)) {
            // Affiche's accent is the text colour; Ludique's lime is unreadable on white
            actionColor = theme.fg;
        }
        action.setText(running ? joinLabel : launchLabel);
        action.setTextColor(actionColor);
        actionIcon.setImageDrawable(Ui.icon(getContext(), R.drawable.v2_ic_play, actionColor));
        setLive(running, theme.r);
        setDimmed(dimmed);
        invalidate();
    }

    public void setCover(Bitmap bitmap) {
        cover.setBitmap(bitmap);
    }

    public void setOnMoreClickListener(View.OnClickListener l) {
        more.setOnClickListener(l);
    }

    public void refreshInput() {
        more.setVisibility(V2Input.get(getContext()) == V2Input.Kind.TOUCH ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (running) {
            // Warm tint of the running card, stronger on the text side (validated density boards)
            float inset = border;
            rect.set(inset, inset, getWidth() - coverWidth, getHeight() - inset);
            tintPaint.setShader(new LinearGradient(0, 0, getWidth() - coverWidth, 0,
                    Ui.withAlpha(theme.live, theme.dark ? 0.34f : 0.26f), Ui.withAlpha(theme.live, 0f), Shader.TileMode.CLAMP));
            float r = Math.max(0, radius - border);
            canvas.save();
            canvas.clipRect(rect);
            canvas.drawRoundRect(new RectF(inset, inset, getWidth() - inset, getHeight() - inset), r, r, tintPaint);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
        if (border > 0) {
            // Separator between the text and the cover
            float x = getWidth() - coverWidth;
            linePaint.setStrokeWidth(border);
            canvas.drawLine(x - border / 2f, 0, x - border / 2f, getHeight(), linePaint);
        }
    }
}
