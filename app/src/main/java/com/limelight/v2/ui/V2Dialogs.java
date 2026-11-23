package com.limelight.v2.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogs in the v2 theme: the options menu of an app (AppMenu.qml: entries with icons, the first
 * one in the accent) and the confirmation dialog (LavDialog.qml). Centred on Android. Work with
 * touch, D-pad, gamepad (A validates, B closes) and keyboard.
 */
public final class V2Dialogs {
    private V2Dialogs() {}

    public static final class Entry {
        public final String label;
        public final int icon;
        public final boolean main;
        public final Runnable action;

        public Entry(String label, int icon, boolean main, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.main = main;
            this.action = action;
        }
    }

    private static Dialog baseDialog(Activity activity, View content, float widthDp) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout host = new FrameLayout(activity);
        host.addView(content, new FrameLayout.LayoutParams(Ui.dp(activity, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(host);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            V2Input.onKey(event);
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                // A validates the focused entry (Android has no default for it)
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    View f = dialog.getCurrentFocus();
                    if (f != null) {
                        f.performClick();
                    }
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    dialog.cancel();
                }
                return true;
            }
            return false;
        });
        return dialog;
    }

    private static LinearLayout panel(Activity activity, V2Theme theme, int padDp) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, padDp);
        panel.setPadding(pad, pad, pad, pad);
        float radius = Ui.dpf(activity, theme.isAffiche() ? 0 : (theme.isLudique() ? 24 : 16));
        panel.setBackground(Ui.roundRect(theme.surface, radius, Ui.dp(activity, Math.max(1, theme.border)), theme.borderColor));
        return panel;
    }

    /** The options of an app or game. The first entry is the main action, in the accent. */
    public static Dialog showMenu(Activity activity, V2Theme theme, String title, Bitmap cover, List<Entry> entries) {
        LinearLayout panel = panel(activity, theme, 10);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 4), Ui.dp(activity, 6), Ui.dp(activity, 10));
        if (cover != null) {
            CoverView cv = new CoverView(activity);
            float r = Ui.dpf(activity, theme.square() ? 0 : 6);
            cv.setRadii(r, r, r, r);
            cv.setBitmap(cover);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 48));
            clp.setMarginEnd(Ui.dp(activity, 12));
            header.addView(cv, clp);
        }
        TextView name = Ui.display(activity, theme, 18, theme.fg);
        name.setTypeface(theme.displayFont(activity, 700));
        name.setText(title);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(header);

        final Dialog dialog = baseDialog(activity, wrapScroll(activity, panel), 320);
        List<View> rows = new ArrayList<>();
        for (final Entry e : entries) {
            View row = menuRow(activity, theme, e, () -> {
                dialog.dismiss();
                if (e.action != null) {
                    e.action.run();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48));
            lp.topMargin = Ui.dp(activity, 4);
            panel.addView(row, lp);
            rows.add(row);
        }
        dialog.show();
        if (!rows.isEmpty()) {
            rows.get(0).requestFocus();
        }
        return dialog;
    }

    private static View wrapScroll(Activity activity, View content) {
        ScrollView sv = new ScrollView(activity);
        sv.setFillViewport(false);
        sv.addView(content);
        return sv;
    }

    private static View menuRow(Activity activity, V2Theme theme, Entry e, Runnable click) {
        final float radius = Ui.dpf(activity, theme.isAffiche() ? 0 : (theme.isLudique() ? 16 : 10));
        final LinearLayout row = new LinearLayout(activity) {
            @Override
            protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
                super.onFocusChanged(gainFocus, direction, previous);
                refresh(this, theme, e, radius);
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                refresh(this, theme, e, radius);
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(e.label);

        ImageView icon = new ImageView(activity);
        int is = Ui.dp(activity, 18);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        ilp.setMarginEnd(Ui.dp(activity, 12));
        row.addView(icon, ilp);
        TextView label = Ui.text(activity, theme, 16, e.main ? 700 : 500, theme.fg);
        label.setText(e.label);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> click.run());
        refresh(row, theme, e, radius);
        return row;
    }

    private static void refresh(LinearLayout row, V2Theme theme, Entry e, float radius) {
        boolean highlighted = (row.isFocused() && V2Input.showFocus(row.getContext())) || row.isPressed();
        int bg = e.main ? theme.accent : (highlighted ? theme.sel : 0);
        int fg = e.main ? theme.onAccent : (highlighted ? theme.selFg : theme.fg);
        if (e.main && row.isFocused() && V2Input.showFocus(row.getContext())) {
            row.setBackground(Ui.roundRect(bg, radius, Ui.dp(row.getContext(), 3), theme.fg));
        } else {
            row.setBackground(bg == 0 ? null : Ui.roundRect(bg, radius, 0, 0));
        }
        ImageView icon = (ImageView) row.getChildAt(0);
        icon.setImageDrawable(e.icon == 0 ? null : Ui.icon(row.getContext(), e.icon, fg));
        ((TextView) row.getChildAt(1)).setTextColor(fg);
    }

    /** LavDialog: title, text, accept (accent) and cancel. Back / B / outside counts as cancel. */
    public static Dialog confirm(Activity activity, V2Theme theme, String title, String text,
                                 String acceptText, String rejectText, Runnable onAccept, Runnable onReject) {
        LinearLayout panel = panel(activity, theme, 24);
        if (title != null && !title.isEmpty()) {
            TextView t = Ui.display(activity, theme, 24, theme.fg);
            t.setMaxLines(Integer.MAX_VALUE);
            t.setText(title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 10);
            panel.addView(t, lp);
        }
        if (text != null && !text.isEmpty()) {
            TextView body = Ui.text(activity, theme, 16, 400, theme.muted);
            body.setMaxLines(12);
            body.setLineSpacing(0, 1.15f);
            body.setText(text);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 18);
            panel.addView(body, lp);
        }
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(buttons, panel);
        panel.addView(buttons);

        final boolean[] accepted = {false};
        final Dialog dialog = baseDialog(activity, panel, 460);
        PillButton accept = new PillButton(activity, theme, PillButton.Style.PRIMARY, 0, acceptText, 48, 17);
        accept.setOnClickListener(v -> {
            accepted[0] = true;
            dialog.dismiss();
            if (onAccept != null) {
                onAccept.run();
            }
        });
        buttons.addView(accept);
        if (rejectText != null) {
            PillButton reject = new PillButton(activity, theme, PillButton.Style.SECONDARY, 0, rejectText, 48, 17);
            reject.setOnClickListener(v -> dialog.cancel());
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMarginStart(Ui.dp(activity, 14));
            buttons.addView(reject, rlp);
        }
        dialog.setOnDismissListener(d -> {
            if (!accepted[0] && onReject != null) {
                onReject.run();
            }
        });
        dialog.show();
        accept.requestFocus();
        return dialog;
    }
}
