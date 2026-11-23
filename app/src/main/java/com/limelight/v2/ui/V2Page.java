package com.limelight.v2.ui;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.Density;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

/**
 * A legacy full-screen activity dressed in the v2 theme without touching its logic (Benchmark,
 * Infos de débogage): the theme's window colours, a header with a back button and the title, and a
 * scrolling column for the screen's own content, plus the text, title, button and choice views it
 * fills it with. The activity forwards its input events to {@link #onTouch} / {@link #onKey} /
 * {@link #onMotion} so that the focus ring shows for a gamepad or a keyboard only.
 */
public final class V2Page {
    public final V2Theme theme;
    private final Activity activity;
    private final FrameLayout root;
    private final LinearLayout content;
    private final TextView title;

    public V2Page(Activity activity, V2Theme theme, String titleText, Runnable onBack) {
        this.activity = activity;
        this.theme = theme;
        paintWindow(activity, theme);
        Density d = Density.of(activity.getResources().getConfiguration());

        root = new FrameLayout(activity);
        root.setBackgroundColor(theme.bg);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(activity, d.side);
        page.setPadding(side, Ui.dp(activity, d.top), side, 0);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.noClip(root, page);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, d.headerHeight)));
        IconButton back = new IconButton(activity, theme, R.drawable.v2_ic_chevron_left, 48, 44, 20, theme.surface, true);
        back.setContentDescription(activity.getString(R.string.v2_back));
        back.setOnClickListener(v -> onBack.run());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        title = Ui.display(activity, theme, d.compact ? 24 : 30, theme.fg);
        title.setText(theme.displayText(titleText));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(activity, 14));
        header.addView(title, tlp);

        // The body clips: the scrolled content must not draw over the header
        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = Ui.dp(activity, d.compact ? 6 : 14);
        page.addView(scroll, slp);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        // Room for the focus rings of the first and last buttons, and the bottom of the screen
        int ring = Ui.dp(activity, 8);
        content.setPadding(ring, ring, ring, Ui.dp(activity, 28));
        Ui.noClip(content);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The view to give to setContentView() */
    public View getView() {
        return root;
    }

    /** The scrolling column the screen fills */
    public LinearLayout content() {
        return content;
    }

    public void setTitle(String text) {
        title.setText(theme.displayText(text));
    }

    @SuppressWarnings("deprecation")
    public static void paintWindow(Activity activity, V2Theme theme) {
        Window w = activity.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(theme.bg));
        w.setStatusBarColor(theme.bg);
        w.setNavigationBarColor(theme.bg);
        View decor = w.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(theme.dark ? 0 : light, light);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = decor.getSystemUiVisibility();
            flags = theme.dark ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    // ---- input: which device is in use, for the focus ring --------------------------------------

    public static void onTouch(MotionEvent ev) {
        V2Input.onTouch(ev);
    }

    public static void onMotion(MotionEvent ev) {
        V2Input.onGenericMotion(ev);
    }

    public static void onKey(KeyEvent ev) {
        V2Input.onKey(ev);
    }

    // ---- content ------------------------------------------------------------------------------

    /** A paragraph (wraps); muted for explanations, plain for values */
    public TextView text(ViewGroup parent, CharSequence s, boolean muted) {
        TextView t = Ui.text(activity, theme, 15, 400, muted ? theme.muted : theme.fg);
        t.setMaxLines(Integer.MAX_VALUE);
        t.setEllipsize(null);
        t.setLineSpacing(0, 1.2f);
        t.setText(s);
        t.setTextIsSelectable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 4);
        lp.bottomMargin = Ui.dp(activity, 4);
        parent.addView(t, lp);
        return t;
    }

    /** A section title */
    public TextView heading(ViewGroup parent, CharSequence s) {
        TextView t = Ui.text(activity, theme, 17, 700, theme.fg);
        t.setMaxLines(4);
        t.setText(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 18);
        lp.bottomMargin = Ui.dp(activity, 6);
        parent.addView(t, lp);
        return t;
    }

    /** A row of buttons that goes under itself when it does not fit */
    public LinearLayout buttonRow(ViewGroup parent) {
        V2Alert.ButtonRow row = new V2Alert.ButtonRow(activity, Ui.dp(activity, 12));
        Ui.noClip(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 8);
        lp.bottomMargin = Ui.dp(activity, 6);
        parent.addView(row, lp);
        return row;
    }

    public PillButton button(ViewGroup parent, String label, boolean primary, View.OnClickListener onClick) {
        PillButton b = new PillButton(activity, theme, primary ? PillButton.Style.PRIMARY : PillButton.Style.SECONDARY,
                0, label, 44, 16);
        b.setOnClickListener(onClick);
        parent.addView(b);
        return b;
    }

    /** A button that cannot be used (« Lancer » while the PC is offline) */
    public static void setUsable(View button, boolean usable) {
        button.setEnabled(usable);
        button.setFocusable(usable);
        button.setAlpha(usable ? 1f : 0.4f);
    }

    /**
     * One of several choices (radio): the chosen one framed in the accent with a check, the
     * others quiet. Focusable, A / Entrée / a tap picks it.
     */
    public View choice(ViewGroup parent, String label, boolean selected, Runnable onPick) {
        final float radius = Ui.dpf(activity, theme.isAffiche() ? 0 : Math.min(theme.rs, 14));
        final TextView row = new TextView(activity) {
            @Override
            protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
                super.onFocusChanged(gainFocus, direction, previous);
                paintChoice(this, selected, radius);
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                paintChoice(this, selected, radius);
            }
        };
        row.setTypeface(theme.textFont(activity, selected ? 700 : 500));
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        row.setIncludeFontPadding(false);
        row.setLineSpacing(0, 1.15f);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(Ui.dp(activity, 48));
        int ph = Ui.dp(activity, 14);
        row.setPadding(ph, Ui.dp(activity, 10), ph, Ui.dp(activity, 10));
        row.setFocusable(true);
        row.setClickable(true);
        row.setText(label);
        row.setOnClickListener(v -> onPick.run());
        paintChoice(row, selected, radius);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 6);
        parent.addView(row, lp);
        return row;
    }

    private void paintChoice(TextView row, boolean selected, float radius) {
        boolean focus = row.isFocused() && V2Input.showFocus(row.getContext());
        boolean highlighted = focus || row.isPressed();
        int fill = highlighted ? theme.sel : theme.surface;
        int fg = highlighted ? theme.selFg : theme.fg;
        row.setTextColor(fg);
        int stroke = focus ? 3 : (selected ? 2 : Math.max(1, theme.border));
        int strokeColor = focus ? theme.fg : (selected ? theme.accent : theme.borderColor);
        row.setBackground(Ui.roundRect(fill, radius, Ui.dp(row.getContext(), stroke), strokeColor));
        android.graphics.drawable.Drawable check = selected
                ? Ui.icon(row.getContext(), R.drawable.v2_ic_check, highlighted ? fg : theme.accent) : null;
        if (check != null) {
            int s = Ui.dp(row.getContext(), 18);
            check.setBounds(0, 0, s, s);
        }
        row.setCompoundDrawablesRelative(null, null, check, null);
        row.setCompoundDrawablePadding(Ui.dp(row.getContext(), 10));
    }
}
