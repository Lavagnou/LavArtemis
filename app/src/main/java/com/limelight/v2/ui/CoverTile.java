package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

/**
 * A game of the Playnite library (CoverTile.qml): portrait cover; without a cover (none on the
 * host, not fetched yet, or the client's grey placeholder) its name, big, on the second surface.
 * Running: "EN COURS" badge and halo. Something else running: dimmed. "…" for options on touch.
 */
public class CoverTile extends V2Frame {
    private final CoverView cover;
    private final TextView name;
    private final TextView badge;
    private final IconButton more;
    private int appId = Integer.MIN_VALUE;
    private boolean hiddenGame;
    private final float nameSize;
    private final int nameWidthPx;
    // Only reached by one long word on the phone's narrow tiles in Affiche's wide capitals (« REDEMPTION »)
    private static final float MIN_NAME_DP = 8;

    public CoverTile(Context context, V2Theme theme, float widthDp, float heightDp, String liveLabel) {
        super(context, theme);
        setScaleOnFocus(true);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        int b = Ui.dp(context, theme.border);
        float rs = Ui.dpf(context, Math.min(theme.rs, 16));

        setBackground(Ui.roundRect(theme.surface2, rs, b, theme.borderColor));
        setRing(Math.min(theme.rs, 16), 6, theme.accent);

        // Affiche writes names in wide black capitals: a size down so common words fit a line.
        // Three lines at most, ellipsized, and a long name one size down: nothing is cut by the tile
        nameSize = (widthDp < 90 ? 15 : 17) - (theme.upper ? 2.5f : 0);
        name = Ui.display(context, theme, nameSize, theme.muted);
        name.setMaxLines(3);
        name.setGravity(Gravity.BOTTOM | Gravity.START);
        int pad = Ui.dp(context, widthDp < 90 ? 8 : 10);
        name.setPadding(pad, pad, pad, pad);
        nameWidthPx = Ui.dp(context, widthDp) - 2 * pad;
        addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        cover = new CoverView(context);
        float inner = Math.max(0, rs - b);
        cover.setRadii(inner, inner, inner, inner);
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        clp.setMargins(b, b, b, b);
        addView(cover, clp);

        badge = Ui.liveBadge(context, theme, liveLabel, 10, false);
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        blp.setMargins(Ui.dp(context, 6), 0, 0, Ui.dp(context, 6));
        addView(badge, blp);
        badge.setVisibility(GONE);

        more = new IconButton(context, theme, R.drawable.v2_ic_dots, 40, 30, 16, Ui.withAlpha(theme.surface, 0.85f), false);
        more.setFocusable(false);
        more.setContentDescription(context.getString(R.string.v2_options));
        LayoutParams mlp = new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40), Gravity.TOP | Gravity.END);
        addView(more, mlp);

        setLayoutParams(new LayoutParams(Ui.dp(context, widthDp), Ui.dp(context, heightDp)));
        refreshInput();
    }

    public void bind(int appId, String title, boolean running, boolean dimmed, boolean hidden) {
        if (this.appId != appId) {
            cover.setBitmap(null);
            this.appId = appId;
        }
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, fittedSize(title));
        name.setText(title);
        setContentDescription(title);
        badge.setVisibility(running ? VISIBLE : GONE);
        setLive(running, Math.min(theme.rs, 16));
        setDimmed(dimmed);
        hiddenGame = hidden;
        // Hidden games show (faded) only with « Afficher les jeux masqués »
        setAlpha(hidden ? 0.45f : 1f);
        updateNameVisibility();
    }

    /** A name whose words would not fit three lines at the base size (« Red Dead Redemption 2 ») */
    public static boolean longName(String title) {
        if (title == null) {
            return false;
        }
        int longestWord = 0;
        for (String w : title.split("\\s+")) {
            longestWord = Math.max(longestWord, w.length());
        }
        return title.length() > 18 || longestWord > 9;
    }

    /**
     * The name's size: one size down for a long name, then smaller until its longest word fits the
     * tile's width, so a word is never broken in two (« Redem / ption ») and three lines hold it.
     */
    private float fittedSize(String title) {
        float size = longName(title) ? nameSize - 2 : nameSize;
        if (title == null || nameWidthPx <= 0) {
            return size;
        }
        String text = theme.upper ? title.toUpperCase(java.util.Locale.FRENCH) : title;
        android.text.TextPaint p = new android.text.TextPaint(name.getPaint());
        float longest;
        do {
            p.setTextSize(Ui.dpf(getContext(), size));
            longest = 0;
            for (String w : text.split("\\s+")) {
                longest = Math.max(longest, p.measureText(w));
            }
            if (longest <= nameWidthPx) {
                break;
            }
            size -= 0.5f;
        } while (size > MIN_NAME_DP);
        return Math.max(size, MIN_NAME_DP);
    }

    public int getAppId() {
        return appId;
    }

    /** null: no cover, the name shows */
    public void setCover(Bitmap bitmap) {
        cover.setBitmap(bitmap);
        updateNameVisibility();
    }

    public boolean hasCover() {
        return cover.getBitmap() != null;
    }

    private void updateNameVisibility() {
        name.setVisibility(cover.getBitmap() == null ? VISIBLE : INVISIBLE);
    }

    public void setOnMoreClickListener(View.OnClickListener l) {
        more.setOnClickListener(l);
    }

    /** "…" on touch only; gamepad and keyboard use Y / Menu */
    public void refreshInput() {
        more.setVisibility(V2Input.get(getContext()) == V2Input.Kind.TOUCH ? VISIBLE : GONE);
        invalidate();
    }

    public boolean isHiddenGame() {
        return hiddenGame;
    }
}
