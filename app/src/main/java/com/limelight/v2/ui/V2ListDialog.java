package com.limelight.v2.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of choices in the v2 theme, for any context (the stream activity, the external display's
 * control screen): the in-game menu of LavArtemis Dev. Rows are focusable with a highlight for
 * gamepad and keyboard; A picks, B / Y / Back / a touch outside cancel, and the « Annuler » entry
 * becomes a button at the bottom. A long list scrolls, in two columns on a wide, short screen.
 */
public final class V2ListDialog {
    private V2ListDialog() {}

    public interface Pick {
        void onPick(int index);
    }

    /** Two columns when the list is long and the screen wide but short (a phone in landscape) */
    public static int columnsFor(int entries, int screenWidthDp, int screenHeightDp) {
        return entries > 7 && screenWidthDp >= 700 && screenHeightDp < 600 ? 2 : 1;
    }

    /**
     * @param cancelIndex the entry that only closes (« Annuler »), shown as a button; -1 for none
     * @param onPick      called after the dialog closed, with the index in {@code labels}
     * @param onCancel    called when the dialog is cancelled (not after a pick)
     */
    public static Dialog show(Context context, V2Theme theme, String title, List<String> labels, int cancelIndex,
                              Pick onPick, Runnable onCancel) {
        return show(context, theme, title, labels, cancelIndex, -1, onPick, onCancel);
    }

    /**
     * @param selectedIndex the current choice (Réglages): a check and the accent, and the focus
     *                      starts there; -1 for none
     */
    public static Dialog show(Context context, V2Theme theme, String title, List<String> labels, int cancelIndex,
                              int selectedIndex, Pick onPick, Runnable onCancel) {
        final Dialog dialog = new ThemedDialog(context);
        Configuration config = context.getResources().getConfiguration();
        int entries = labels.size() - (cancelIndex >= 0 ? 1 : 0);
        int cols = columnsFor(entries, config.screenWidthDp, config.screenHeightDp);
        float widthDp = V2Dialogs.dialogWidthDp(context, cols == 2 ? 660 : 400);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(context, 14);
        panel.setPadding(pad, pad, pad, pad);
        float radius = Ui.dpf(context, theme.isAffiche() ? 0 : (theme.isLudique() ? 24 : 16));
        panel.setBackground(Ui.roundRect(theme.surface, radius, Ui.dp(context, Math.max(1, theme.border)), theme.borderColor));
        // The panel clips its children (no Ui.noClip): that keeps the scrolled rows inside the list
        panel.setClipToPadding(false);

        TextView t = Ui.display(context, theme, 20, theme.fg);
        t.setText(theme.displayText(title));
        t.setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 10));
        panel.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The list: the screen height less the title, the footer and the margins; it scrolls past that
        int screenH = Ui.dp(context, config.screenHeightDp > 0 ? config.screenHeightDp : 600);
        MaxHeightScrollView scroll = new MaxHeightScrollView(context, Math.round(screenH * 0.92f) - Ui.dp(context, 120));
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        // Room for the focus highlight of the first and last rows
        list.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 2));
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final boolean[] picked = {false};
        List<View> rows = new ArrayList<>();
        View selectedRow = null;
        LinearLayout line = null;
        int shown = 0;
        for (int i = 0; i < labels.size(); i++) {
            if (i == cancelIndex) {
                continue;
            }
            final int index = i;
            View row = row(context, theme, labels.get(i), i == selectedIndex, () -> {
                picked[0] = true;
                dialog.dismiss();
                if (onPick != null) {
                    onPick.onPick(index);
                }
            });
            if (i == selectedIndex) {
                selectedRow = row;
            }
            if (shown % cols == 0) {
                line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (shown > 0) {
                    llp.topMargin = Ui.dp(context, 2);
                }
                list.addView(line, llp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (shown % cols != 0) {
                rlp.setMarginStart(Ui.dp(context, 6));
            }
            line.addView(row, rlp);
            rows.add(row);
            shown++;
        }
        if (line != null && shown % cols != 0) {
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, 1, 1f);
            slp.setMarginStart(Ui.dp(context, 6));
            line.addView(new View(context), slp);
        }

        // Footer: the gamepad / keyboard hint, and « Annuler »
        V2Input.Kind k = V2Input.get(context);
        // A message (no entries) has only its button: no hint, the button in the accent
        boolean message = shown == 0;
        String hintText = message ? null : k == V2Input.Kind.PAD ? context.getString(R.string.v2_game_menu_hint_pad)
                : (k == V2Input.Kind.KEYS ? context.getString(R.string.v2_game_menu_hint_keys) : null);
        if (hintText != null || cancelIndex >= 0) {
            LinearLayout footer = new LinearLayout(context);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            // Room inside the footer for the focus ring of « Annuler », drawn outside the button
            footer.setPadding(Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 6));
            Ui.noClip(footer);
            TextView hint = Ui.text(context, theme, 13, 500, theme.muted);
            hint.setText(hintText == null ? "" : hintText);
            footer.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (cancelIndex >= 0) {
                PillButton cancel = new PillButton(context, theme, message ? PillButton.Style.PRIMARY : PillButton.Style.SECONDARY,
                        0, labels.get(cancelIndex), 44, 16);
                cancel.setOnClickListener(v -> dialog.cancel());
                footer.addView(cancel);
                rows.add(cancel);
            }
            panel.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        FrameLayout host = new FrameLayout(context);
        host.addView(panel, new FrameLayout.LayoutParams(Ui.dp(context, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(host);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener(new DialogKeys(dialog, null));
        dialog.setOnCancelListener(d -> {
            if (!picked[0] && onCancel != null) {
                onCancel.run();
            }
        });
        dialog.show();
        if (!rows.isEmpty() && V2Input.showFocus(context)) {
            (selectedRow != null ? selectedRow : rows.get(0)).requestFocus();
        } else if (selectedRow != null) {
            final View target = selectedRow;
            // A long list opens on the current choice
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, ((View) target.getParent()).getTop() - Ui.dp(context, 48))));
        }
        return dialog;
    }

    /** A message with one button, same look (« Aucune commande de serveur ») */
    public static Dialog message(Context context, V2Theme theme, String title, String text, String ok) {
        List<String> none = new ArrayList<>();
        none.add(ok);
        Dialog d = show(context, theme, title, none, 0, null, null);
        // The text goes under the title
        View host = d.findViewById(android.R.id.content);
        if (host instanceof ViewGroup && ((ViewGroup) host).getChildCount() > 0) {
            ViewGroup frame = (ViewGroup) ((ViewGroup) host).getChildAt(0);
            ViewGroup panel = (ViewGroup) frame.getChildAt(0);
            TextView body = Ui.text(context, theme, 15, 400, theme.muted);
            body.setMaxLines(10);
            body.setLineSpacing(0, 1.15f);
            body.setText(text);
            body.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), Ui.dp(context, 4));
            panel.addView(body, 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return d;
    }

    private static View row(Context context, V2Theme theme, String label, boolean selected, Runnable click) {
        final float radius = Ui.dpf(context, theme.isAffiche() ? 0 : (theme.isLudique() ? 16 : 10));
        final TextView row = new TextView(context) {
            @Override
            protected void onFocusChanged(boolean gainFocus, int direction, Rect previous) {
                super.onFocusChanged(gainFocus, direction, previous);
                paint(this, theme, radius);
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                paint(this, theme, radius);
            }
        };
        row.setSelected(selected);
        row.setTypeface(theme.textFont(context, 600));
        row.getPaint().setFakeBoldText(V2Theme.needsFakeBold(600));
        row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        row.setIncludeFontPadding(false);
        row.setMaxLines(2);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(Ui.dp(context, 48));
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 6), Ui.dp(context, 14), Ui.dp(context, 6));
        row.setFocusable(true);
        row.setClickable(true);
        row.setText(label);
        row.setOnClickListener(v -> click.run());
        paint(row, theme, radius);
        return row;
    }

    private static void paint(TextView row, V2Theme theme, float radius) {
        boolean focus = row.isFocused() && V2Input.showFocus(row.getContext());
        boolean highlighted = focus || row.isPressed();
        int bg = highlighted ? theme.sel : 0;
        int fg = highlighted ? theme.selFg : theme.fg;
        row.setTextColor(fg);
        if (row.isSelected()) {
            // The current choice: a check at the end
            android.graphics.drawable.Drawable check = Ui.icon(row.getContext(), R.drawable.v2_ic_check, highlighted ? fg : theme.accent);
            if (check != null) {
                int s = Ui.dp(row.getContext(), 18);
                check.setBounds(0, 0, s, s);
            }
            row.setCompoundDrawablesRelative(null, null, check, null);
            row.setCompoundDrawablePadding(Ui.dp(row.getContext(), 10));
            row.setTypeface(theme.textFont(row.getContext(), 700));
        }
        if (focus) {
            // The ring of the v2 focus, inside the row: the list scrolls and would cut it outside
            row.setBackground(Ui.roundRect(bg, radius, Ui.dp(row.getContext(), 2), theme.accent));
        } else {
            row.setBackground(bg == 0 ? null : Ui.roundRect(bg, radius, 0, 0));
        }
    }

    /** Touch switches the dialog to touch (no highlight); keys go through DialogKeys */
    private static final class ThemedDialog extends Dialog {
        ThemedDialog(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            V2Input.onTouch(ev);
            return super.dispatchTouchEvent(ev);
        }
    }

    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            maxHeight = Math.max(Ui.dp(context, 120), maxHeightPx);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
}
