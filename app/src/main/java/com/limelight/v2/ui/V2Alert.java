package com.limelight.v2.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The LavDialog of the v2 theme for everything the legacy screens asked of an AlertDialog or a
 * ProgressDialog: a title, a text (links stay clickable), an optional spinner or progress bar,
 * and up to three buttons, the first in the accent. Buttons close the dialog and then run their
 * action; Back / B / a touch outside cancel it only when it is cancelable.
 *
 * <pre>
 * new V2Alert(activity, theme).title("Oups").text(message)
 *         .button("OK", onOk).button("Aide", onHelp).cancelable(false).show();
 * </pre>
 */
public final class V2Alert {
    private final Context context;
    private final V2Theme theme;
    private String title;
    private CharSequence text;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private boolean cancelable = true;
    private Boolean outside;
    private Runnable onCancel;
    private int progress = NONE;
    private View extra;

    private static final int NONE = -2;
    private static final int SPINNER = -1;

    public V2Alert(Context context, V2Theme theme) {
        this.context = context;
        this.theme = theme;
    }

    public V2Alert title(String title) {
        this.title = title;
        return this;
    }

    public V2Alert text(CharSequence text) {
        this.text = text;
        return this;
    }

    /** A button: the first one is the main action (accent), the others are quiet */
    public V2Alert button(String label, Runnable action) {
        if (label != null) {
            labels.add(label);
            actions.add(action);
        }
        return this;
    }

    /** Back, B, Escape and a touch outside: false keeps the dialog until a button is pressed */
    public V2Alert cancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    /** A touch outside cancels too (default: when cancelable) */
    public V2Alert cancelOnTouchOutside(boolean outside) {
        this.outside = outside;
        return this;
    }

    /** Run on Back / B / outside (not after a button) */
    public V2Alert onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    /** A view under the text (a slider) */
    public V2Alert content(View view) {
        this.extra = view;
        return this;
    }

    /** An indeterminate spinner beside the text (« Connexion en cours… ») */
    public V2Alert spinner() {
        progress = SPINNER;
        return this;
    }

    /** A progress bar under the text, 0 to 100 (a download) */
    public V2Alert progressBar(int percent) {
        progress = Math.max(0, Math.min(100, percent));
        return this;
    }

    /** The dialog on screen, to change its text or progress while it is shown */
    public static final class Shown {
        public final Dialog dialog;
        private final TextView body;
        private final ProgressBar bar;

        Shown(Dialog dialog, TextView body, ProgressBar bar) {
            this.dialog = dialog;
            this.body = body;
            this.bar = bar;
        }

        public void setText(CharSequence text) {
            if (body != null) {
                body.setText(text);
                body.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
            }
        }

        public void setProgress(int percent) {
            if (bar != null) {
                bar.setProgress(Math.max(0, Math.min(100, percent)));
            }
        }

        public void dismiss() {
            if (dialog.isShowing()) {
                try {
                    dialog.dismiss();
                } catch (IllegalArgumentException ignored) {
                    // The window is already gone (the activity was destroyed)
                }
            }
        }
    }

    public Shown show() {
        final Dialog dialog = new TouchDialog(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(context, 24);
        panel.setPadding(pad, pad, pad, pad);
        float radius = Ui.dpf(context, theme.isAffiche() ? 0 : (theme.isLudique() ? 24 : 16));
        panel.setBackground(Ui.roundRect(theme.surface, radius, Ui.dp(context, Math.max(1, theme.border)), theme.borderColor));

        if (title != null && !title.isEmpty()) {
            TextView t = Ui.display(context, theme, 22, theme.fg);
            t.setMaxLines(3);
            t.setText(theme.displayText(title));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(context, 10);
            panel.addView(t, lp);
        }

        TextView body = Ui.text(context, theme, 16, 400, theme.muted);
        body.setMaxLines(Integer.MAX_VALUE);
        body.setEllipsize(null);
        body.setLineSpacing(0, 1.15f);
        body.setText(text);
        body.setLinkTextColor(theme.accent);
        if (text instanceof Spanned && ((Spanned) text).getSpans(0, text.length(), URLSpan.class).length > 0) {
            body.setMovementMethod(LinkMovementMethod.getInstance());
        }
        body.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);

        ProgressBar bar = null;
        if (progress == SPINNER) {
            // Spinner and text side by side
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ProgressBar spin = new ProgressBar(context);
            spin.setIndeterminate(true);
            spin.setIndeterminateTintList(ColorStateList.valueOf(theme.accent));
            int s = Ui.dp(context, 32);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(s, s);
            slp.setMarginEnd(Ui.dp(context, 16));
            row.addView(spin, slp);
            row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(context, 4);
            lp.bottomMargin = Ui.dp(context, labels.isEmpty() ? 0 : 18);
            panel.addView(row, lp);
            body.setVisibility(View.VISIBLE);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(context, progress >= 0 || !labels.isEmpty() ? 18 : 0);
            panel.addView(body, lp);
            if (progress >= 0) {
                bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                bar.setIndeterminate(false);
                bar.setMax(100);
                bar.setProgress(progress);
                bar.setProgressTintList(ColorStateList.valueOf(theme.accent));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(theme.surface2));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 8));
                blp.bottomMargin = Ui.dp(context, labels.isEmpty() ? 0 : 18);
                panel.addView(bar, blp);
            }
        }

        if (extra != null) {
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.bottomMargin = Ui.dp(context, labels.isEmpty() ? 0 : 18);
            panel.addView(extra, elp);
        }

        PillButton first = null;
        if (!labels.isEmpty()) {
            ButtonRow buttons = new ButtonRow(context, Ui.dp(context, 14));
            Ui.noClip(buttons, panel);
            panel.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int i = 0; i < labels.size(); i++) {
                final Runnable action = actions.get(i);
                PillButton b = new PillButton(context, theme, i == 0 ? PillButton.Style.PRIMARY : PillButton.Style.SECONDARY,
                        0, labels.get(i), 48, 17);
                b.setOnClickListener(v -> {
                    dialog.setOnCancelListener(null);
                    dialog.dismiss();
                    if (action != null) {
                        action.run();
                    }
                });
                buttons.addView(b);
                if (first == null) {
                    first = b;
                }
            }
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(panel);
        FrameLayout host = new FrameLayout(context);
        host.addView(scroll, new FrameLayout.LayoutParams(Ui.dp(context, V2Dialogs.dialogWidthDp(context, 460)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(host);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
        }
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable && (outside == null || outside));
        // A on the focused button, B / Y cancel (when allowed); a release whose press came
        // before the dialog does nothing
        dialog.setOnKeyListener(new DialogKeys(dialog, () -> {
            if (cancelable) {
                dialog.cancel();
            }
        }));
        if (onCancel != null) {
            dialog.setOnCancelListener(d -> onCancel.run());
        }
        dialog.show();
        if (first != null) {
            first.requestFocus();
        }
        return new Shown(dialog, body, bar);
    }

    /** Touch switches the dialog to touch mode (no focus ring), as in the v2 screens */
    private static final class TouchDialog extends Dialog {
        TouchDialog(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            V2Input.onTouch(ev);
            return super.dispatchTouchEvent(ev);
        }
    }

    /** Buttons side by side, or one under the other when they do not fit (long French labels) */
    static final class ButtonRow extends LinearLayout {
        private final int gap;

        ButtonRow(Context context, int gapPx) {
            super(context);
            this.gap = gapPx;
            setOrientation(HORIZONTAL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = MeasureSpec.getSize(widthMeasureSpec);
            int total = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                c.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                total += c.getMeasuredWidth() + (i > 0 ? gap : 0);
            }
            boolean vertical = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && total > available;
            setOrientation(vertical ? VERTICAL : HORIZONTAL);
            for (int i = 0; i < getChildCount(); i++) {
                LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
                // Left as well as start: the start margin is resolved into the left one once,
                // before this measure, so changing only the start would never apply
                int start = !vertical && i > 0 ? gap : 0;
                lp.setMarginStart(start);
                lp.leftMargin = start;
                lp.topMargin = vertical && i > 0 ? gap / 2 : 0;
                lp.width = vertical ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
