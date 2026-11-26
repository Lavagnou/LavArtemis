package com.limelight.v2.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
        // A validates the focused entry, B / Y close; a release whose press came before the
        // dialog (A held to open the options) is ignored
        DialogKeys.install(dialog);
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

    /** A message with a single button (errors: "Oups") */
    public static Dialog message(Activity activity, V2Theme theme, String title, String text, String okText) {
        return confirm(activity, theme, title, text, okText, null, null, null);
    }

    public interface TextResult {
        void onText(String text);
    }

    /**
     * LavDialog with a text field (Renommer, Ajouter un PC). The accept button stays off while the
     * field is empty; Entrée in the field accepts. The trimmed text is handed over.
     */
    public static Dialog prompt(Activity activity, V2Theme theme, String title, String text, String initial,
                                String hint, int inputType, int maxLength, String acceptText, String rejectText,
                                final TextResult onAccept) {
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        if (maxLength > 0) {
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        }
        field.setTypeface(theme.textFont(activity, 500));
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        field.setTextColor(theme.fg);
        field.setHintTextColor(Ui.withAlpha(theme.muted, 0.7f));
        field.setHint(hint);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // The cursor in the theme's accent, not the app theme's
            android.graphics.drawable.GradientDrawable cursor = Ui.roundRect(theme.accent, 0, 0, 0);
            cursor.setSize(Ui.dp(activity, 2), -1);
            field.setTextCursorDrawable(cursor);
        }
        int ph = Ui.dp(activity, 14);
        field.setPadding(ph, 0, ph, 0);
        field.setMinHeight(Ui.dp(activity, 52));
        final float fr = Ui.dpf(activity, theme.isAffiche() ? 0 : Math.min(theme.rs, 14));
        field.setBackground(Ui.roundRect(theme.surface2, fr, Ui.dp(activity, Math.max(1, theme.border)), theme.borderColor));
        field.setOnFocusChangeListener((v, has) -> v.setBackground(Ui.roundRect(theme.surface2, fr,
                Ui.dp(activity, has ? 2 : Math.max(1, theme.border)), has ? theme.accent : theme.borderColor)));
        if (initial != null) {
            field.setText(initial);
            field.setSelection(field.getText().length());
        }

        final Dialog[] holder = new Dialog[1];
        final PillButton[] acceptHolder = new PillButton[1];
        Runnable accept = () -> {
            String value = field.getText().toString().trim();
            if (value.isEmpty()) {
                return;
            }
            holder[0].dismiss();
            if (onAccept != null) {
                onAccept.onText(value);
            }
        };
        holder[0] = build(activity, theme, title, text, field, acceptText, rejectText, accept, acceptHolder);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                accept.run();
                return true;
            }
            return false;
        });
        final PillButton acceptButton = acceptHolder[0];
        Runnable updateEnabled = () -> {
            boolean has = field.getText().toString().trim().length() > 0;
            acceptButton.setEnabled(has);
            acceptButton.setAlpha(has ? 1f : 0.4f);
        };
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateEnabled.run();
            }
        });
        updateEnabled.run();
        Window w = holder[0].getWindow();
        if (w != null) {
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        holder[0].show();
        field.requestFocus();
        return holder[0];
    }

    /** LavDialog: title, text, accept (accent) and cancel. Back / B / outside counts as cancel. */
    public static Dialog confirm(Activity activity, V2Theme theme, String title, String text,
                                 String acceptText, String rejectText, Runnable onAccept, Runnable onReject) {
        // The prototype's centred dialog (V2Sheet): title, calm note, the accept option first
        com.limelight.v2.V2Sheet.Spec spec = new com.limelight.v2.V2Sheet.Spec();
        spec.title = title == null ? "" : title;
        spec.info = true;
        spec.note = text;
        final boolean[] accepted = {false};
        spec.option(acceptText, true, false, () -> {
            accepted[0] = true;
            if (onAccept != null) {
                onAccept.run();
            }
        });
        if (rejectText != null) {
            spec.option(rejectText, false, false, null);
        }
        Dialog shown = com.limelight.v2.V2Sheet.show(activity, theme, spec);
        // Back, B, outside or the second option: the reject callback, as before (after the accept ran)
        shown.setOnDismissListener(d -> {
            if (!accepted[0] && onReject != null) {
                onReject.run();
            }
        });
        return shown;
    }

    /** The LavDialog version (kept for the callers that need its field) */
    private static Dialog confirmPanel(Activity activity, V2Theme theme, String title, String text,
                                       String acceptText, String rejectText, Runnable onAccept, Runnable onReject) {
        final Dialog[] holder = new Dialog[1];
        final PillButton[] acceptHolder = new PillButton[1];
        final boolean[] accepted = {false};
        holder[0] = build(activity, theme, title, text, null, acceptText, rejectText, () -> {
            accepted[0] = true;
            holder[0].dismiss();
            if (onAccept != null) {
                onAccept.run();
            }
        }, acceptHolder);
        holder[0].setOnDismissListener(d -> {
            if (!accepted[0] && onReject != null) {
                onReject.run();
            }
        });
        holder[0].show();
        acceptHolder[0].requestFocus();
        return holder[0];
    }

    /** The LavDialog panel: title, text, an optional field, the buttons. Not shown yet. */
    private static Dialog build(Activity activity, V2Theme theme, String title, String text, View field,
                                String acceptText, String rejectText, Runnable accept,
                                PillButton[] acceptOut) {
        LinearLayout panel = panel(activity, theme, 24);
        if (title != null && !title.isEmpty()) {
            TextView t = Ui.display(activity, theme, 24, theme.fg);
            t.setMaxLines(3);
            t.setText(theme.displayText(title));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 10);
            panel.addView(t, lp);
        }
        if (text != null && !text.isEmpty()) {
            TextView body = Ui.text(activity, theme, 16, 400, theme.muted);
            body.setMaxLines(14);
            body.setLineSpacing(0, 1.15f);
            body.setText(text);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 18);
            panel.addView(body, lp);
        }
        if (field != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 18);
            panel.addView(field, lp);
        }
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(buttons, panel);
        panel.addView(buttons);

        final Dialog dialog = baseDialog(activity, wrapScroll(activity, panel), dialogWidthDp(activity, 460));
        PillButton acceptButton = new PillButton(activity, theme, PillButton.Style.PRIMARY, 0, acceptText, 48, 17);
        acceptButton.setOnClickListener(v -> accept.run());
        buttons.addView(acceptButton);
        acceptOut[0] = acceptButton;
        if (rejectText != null) {
            PillButton reject = new PillButton(activity, theme, PillButton.Style.SECONDARY, 0, rejectText, 48, 17);
            reject.setOnClickListener(v -> dialog.cancel());
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMarginStart(Ui.dp(activity, 14));
            buttons.addView(reject, rlp);
        }
        return dialog;
    }

    /** A dialog never wider than the screen minus 16 dp each side (portrait phones) */
    static float dialogWidthDp(android.content.Context context, float wanted) {
        float screen = context.getResources().getConfiguration().screenWidthDp;
        return screen > 0 ? Math.min(wanted, screen - 32) : wanted;
    }
}
