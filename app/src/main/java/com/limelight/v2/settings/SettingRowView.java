package com.limelight.v2.settings;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.V2Input;
import com.limelight.v2.V2Theme;
import com.limelight.v2.ui.PillButton;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;
import com.limelight.v2.ui.V2Slider;
import com.limelight.v2.ui.V2Switch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One row of Réglages v2 (SettingRow.qml and its Toggle / Choice / Slider / Action variants): the
 * label with its EXPERT tag, the description, and the control on the right, or under the texts
 * when the row is too narrow for both (piste A of the prototype). The row takes the focus and the
 * keys: A / Entrée flip or open, Left / Right step a choice or a slider; at the end of its range
 * Left goes back to the page list. Values are bound by the screen; changes go to the listener.
 */
public final class SettingRowView extends V2Frame {
    public interface Listener {
        void onToggle(SettingDef def, boolean shown);

        void onChoose(SettingDef def, Option option);

        /** The dropdown: a list dialog */
        void onOpenList(SettingDef def);

        /** Every move of the slider; {@code finished} on release and after a key step */
        void onSlide(SettingDef def, int value, boolean finished);

        void onAction(SettingDef def, int button);

        /** Left at the end of the row: back to the page list */
        boolean onLeaveLeft();
    }

    public final SettingDef def;
    private final V2Theme theme;
    private final boolean compact;
    private final Listener listener;
    private final Body body;
    private final TextView label;
    private TextView tag;
    private final TextView desc;

    private V2Switch toggle;
    private LinearLayout segments;
    private TextView dropdown;
    private V2Slider slider;
    private TextView sliderValue;
    private final List<PillButton> buttons = new ArrayList<>();

    private boolean rowEnabled = true;
    private boolean checked;
    private List<Option> options = Collections.emptyList();
    private String value;
    private int intValue;
    private final float radius;

    public SettingRowView(Context context, V2Theme theme, boolean compact, SettingDef def, Listener listener) {
        super(context, theme);
        this.def = def;
        this.theme = theme;
        this.compact = compact;
        this.listener = listener;
        radius = Ui.dpf(context, theme.rs);
        boolean multiButtons = def.type == SettingDef.Type.BUTTONS;
        setFocusable(!multiButtons);
        setClickable(!multiButtons && def.type != SettingDef.Type.SLIDER);
        setRing(theme.rs, 5, theme.fg);
        paintBackground();
        if (!multiButtons) {
            setOnClickListener(v -> activate());
        }

        body = new Body(context);
        int ph = Ui.dp(context, compact ? 16 : 20);
        int pv = Ui.dp(context, compact ? 12 : 15);
        body.setPadding(ph, pv, ph, pv);
        addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        label = Ui.text(context, theme, compact ? 16 : 18, 700, theme.fg);
        label.setMaxLines(2);
        label.setText(context.getString(def.label));
        // With the tag, the label takes its own width (shrinking if needed) and the tag follows it
        // With the tag, the label takes its own width (capped by Body.onMeasure) and the tag follows
        labelRow.addView(label, def.expert
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (def.expert) {
            tag = Ui.text(context, theme, 10, 700, theme.muted);
            tag.setText(R.string.v2_set_expert_tag);
            tag.setLetterSpacing(0.08f);
            tag.setGravity(Gravity.CENTER);
            int th = Ui.dp(context, 20);
            tag.setPadding(Ui.dp(context, 7), 0, Ui.dp(context, 7), 0);
            tag.setBackground(Ui.roundRect(0, theme.square() ? 0 : th / 2f, Ui.dp(context, 1), theme.muted));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, th);
            tlp.setMarginStart(Ui.dp(context, 10));
            labelRow.addView(tag, tlp);
        }
        texts.addView(labelRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        desc = Ui.text(context, theme, compact ? 13.5f : 15, 400, theme.muted);
        desc.setMaxLines(4);
        desc.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(context, 4);
        texts.addView(desc, dlp);
        setDescription(def.desc != 0 ? context.getString(def.desc) : null);
        body.setTexts(texts);
        body.setControl(buildControl(context), stretchable());
    }

    private boolean stretchable() {
        return def.type == SettingDef.Type.SLIDER || (def.type == SettingDef.Type.CHOICE && dropdown != null);
    }

    private View buildControl(Context context) {
        switch (def.type) {
            case TOGGLE:
                toggle = new V2Switch(context, theme, compact ? 50 : 56, compact ? 28 : 32);
                return toggle;
            case CHOICE:
                return null; // built when the options are known (bindChoice)
            case SLIDER: {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                slider = new V2Slider(context, theme, compact ? 170 : 230);
                slider.setListener((p, finished) -> {
                    if (!rowEnabled) {
                        return;
                    }
                    int v = def.valueAt(p);
                    showSliderValue(v);
                    listener.onSlide(def, v, finished);
                });
                row.addView(slider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                sliderValue = Ui.display(context, theme, compact ? 16 : 18, theme.fg);
                sliderValue.setAllCaps(false);
                sliderValue.setTypeface(theme.displayFont(context, 700));
                sliderValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                sliderValue.setMinWidth(Ui.dp(context, compact ? 92 : 112));
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                vlp.setMarginStart(Ui.dp(context, 10));
                row.addView(sliderValue, vlp);
                return row;
            }
            case ACTION:
            case BUTTONS: {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                Ui.noClip(row);
                for (int i = 0; i < def.buttons.size(); i++) {
                    final int index = i;
                    PillButton b = new PillButton(context, theme, PillButton.Style.SECONDARY, 0,
                            context.getString(def.buttons.get(i)), compact ? 42 : 46, compact ? 15 : 16);
                    b.setFocusable(def.type == SettingDef.Type.BUTTONS);
                    b.setOnClickListener(v -> {
                        if (rowEnabled && v.isEnabled()) {
                            listener.onAction(def, index);
                        }
                    });
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) {
                        blp.setMarginStart(Ui.dp(context, 10));
                    }
                    row.addView(b, blp);
                    buttons.add(b);
                }
                return row;
            }
            default:
                return null;
        }
    }

    // ---- binding ----------------------------------------------------------------------------------

    public void setDescription(String text) {
        boolean has = text != null && !text.isEmpty();
        desc.setText(has ? text : "");
        desc.setVisibility(has ? VISIBLE : GONE);
    }

    public void setRowEnabled(boolean enabled) {
        rowEnabled = enabled;
        body.setAlpha(enabled ? 1f : 0.45f);
        if (slider != null) {
            slider.setEnabled(enabled);
        }
    }

    public boolean isRowEnabled() {
        return rowEnabled;
    }

    public void bindToggle(boolean shown) {
        checked = shown;
        if (toggle != null) {
            toggle.setOn(shown);
        }
    }

    public boolean isChecked() {
        return checked;
    }

    /** Options and current value; segmented for a few short ones, a dropdown otherwise */
    public void bindChoice(List<Option> opts, String current) {
        boolean wantDropdown = useDropdown(opts);
        boolean rebuild = !opts.equals(options) || (wantDropdown != (dropdown != null)) || (segments == null && dropdown == null);
        options = opts;
        value = current;
        if (rebuild) {
            segments = null;
            dropdown = null;
            View control = wantDropdown ? buildDropdown() : buildSegments();
            body.setControl(control, wantDropdown);
        }
        if (dropdown != null) {
            int i = SettingsCatalog.indexOf(options, current);
            dropdown.setText(i >= 0 ? options.get(i).label : current);
        } else if (segments != null) {
            for (int i = 0; i < segments.getChildCount(); i++) {
                paintSegment((TextView) segments.getChildAt(i), options.get(i).value.equals(current));
            }
        }
    }

    private boolean useDropdown(List<Option> opts) {
        if (def.segmentMax <= 0 || opts.size() > def.segmentMax) {
            return true;
        }
        int chars = 0;
        for (Option o : opts) {
            if (o.ask) {
                return true;
            }
            chars += o.label.length();
        }
        return chars > 30;
    }

    private View buildSegments() {
        Context c = getContext();
        segments = new LinearLayout(c);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(c, 4);
        segments.setPadding(pad, pad, pad, pad);
        float r = Ui.dpf(c, theme.square() ? 0 : theme.pill);
        segments.setBackground(Ui.roundRect(theme.surface2, r, theme.isAffiche() ? Ui.dp(c, 2) : 0, theme.fg));
        for (int i = 0; i < options.size(); i++) {
            final Option o = options.get(i);
            TextView s = Ui.text(c, theme, compact ? 14.5f : 16, 600, theme.fg);
            s.setText(o.label);
            s.setGravity(Gravity.CENTER);
            s.setMinWidth(Ui.dp(c, compact ? 52 : 64));
            s.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            s.setOnClickListener(v -> {
                if (rowEnabled) {
                    requestFocusIfKeys();
                    listener.onChoose(def, o);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, compact ? 38 : 42), 1f);
            if (i > 0) {
                lp.setMarginStart(Ui.dp(c, 3));
            }
            segments.addView(s, lp);
        }
        return segments;
    }

    private void paintSegment(TextView s, boolean selected) {
        Context c = getContext();
        float r = Ui.dpf(c, theme.square() ? 0 : Math.max(0, theme.pill - 4));
        s.setBackground(selected ? Ui.roundRect(theme.accent, r, 0, 0) : null);
        s.setTextColor(selected ? theme.onAccent : theme.fg);
        s.setTypeface(theme.textFont(c, selected ? 700 : 600));
    }

    private View buildDropdown() {
        Context c = getContext();
        dropdown = Ui.text(c, theme, compact ? 15 : 16, 600, theme.fg);
        dropdown.setGravity(Gravity.CENTER_VERTICAL);
        dropdown.setEllipsize(TextUtils.TruncateAt.END);
        int ph = Ui.dp(c, 14);
        dropdown.setPadding(ph, 0, Ui.dp(c, 10), 0);
        dropdown.setMinHeight(Ui.dp(c, compact ? 42 : 46));
        dropdown.setMinWidth(Ui.dp(c, compact ? 170 : 220));
        dropdown.setMaxWidth(Ui.dp(c, compact ? 260 : 340));
        float r = Ui.dpf(c, theme.square() ? 0 : Math.min(theme.rs, 12));
        dropdown.setBackground(Ui.roundRect(theme.surface2, r, theme.isAffiche() ? Ui.dp(c, 2) : Math.max(1, Ui.dp(c, theme.border)), theme.isAffiche() ? theme.fg : theme.line));
        dropdown.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, sized(Ui.icon(c, R.drawable.v2_ic_chevron_down, theme.fg), 18), null);
        dropdown.setCompoundDrawablePadding(Ui.dp(c, 8));
        dropdown.setOnClickListener(v -> {
            if (rowEnabled) {
                requestFocusIfKeys();
                listener.onOpenList(def);
            }
        });
        return dropdown;
    }

    private android.graphics.drawable.Drawable sized(android.graphics.drawable.Drawable d, float dp) {
        return d == null ? null : new SizedDrawable(d, Ui.dp(getContext(), dp));
    }

    /** An icon drawn at a fixed size (compound drawables take the intrinsic size otherwise) */
    private static final class SizedDrawable extends android.graphics.drawable.LayerDrawable {
        private final int size;

        SizedDrawable(android.graphics.drawable.Drawable d, int size) {
            super(new android.graphics.drawable.Drawable[]{d});
            this.size = size;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }

    public void bindSlider(int v) {
        intValue = v;
        if (slider != null) {
            slider.setPosition(def.position(v));
            showSliderValue(v);
        }
    }

    private void showSliderValue(int v) {
        if (sliderValue != null) {
            sliderValue.setText(def.formatValue(getContext(), v));
        }
    }

    public void setButtonEnabled(int index, boolean enabled) {
        if (index >= 0 && index < buttons.size()) {
            PillButton b = buttons.get(index);
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    public List<PillButton> getButtons() {
        return buttons;
    }

    // ---- input ------------------------------------------------------------------------------------

    private void requestFocusIfKeys() {
        if (isFocusable() && V2Input.showFocus(getContext())) {
            requestFocus();
        }
    }

    /** A / Entrée / a tap on the row */
    private void activate() {
        if (!rowEnabled) {
            return;
        }
        switch (def.type) {
            case TOGGLE:
                listener.onToggle(def, !checked);
                break;
            case CHOICE:
                if (dropdown != null) {
                    listener.onOpenList(def);
                } else if (!options.isEmpty()) {
                    int i = SettingsCatalog.indexOf(options, value);
                    listener.onChoose(def, options.get((i + 1) % options.size()));
                }
                break;
            case ACTION:
                listener.onAction(def, 0);
                break;
            default:
                break;
        }
    }

    /** Left / Right on a choice or a slider; false when nothing changed (end of the range) */
    boolean step(int delta) {
        if (!rowEnabled) {
            return false;
        }
        if (def.type == SettingDef.Type.CHOICE && !options.isEmpty()) {
            int i = SettingsCatalog.indexOf(options, value);
            int j = i;
            do {
                j += delta;
            } while (j >= 0 && j < options.size() && options.get(j).ask);
            if (j < 0 || j >= options.size() || j == i) {
                return false;
            }
            listener.onChoose(def, options.get(j));
            return true;
        }
        if (def.type == SettingDef.Type.SLIDER) {
            int v = def.stepFrom(intValue, delta);
            if (v == intValue) {
                return false;
            }
            bindSlider(v);
            listener.onSlide(def, v, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int delta = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            if (step(delta)) {
                return true;
            }
            if (delta < 0) {
                listener.onLeaveLeft();
            }
            // Right at the end of a row goes nowhere: the rows are the last column
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        paintBackground();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        paintBackground();
    }

    private void paintBackground() {
        boolean pressed = isPressed() && rowEnabled;
        setBackground(Ui.roundRect(pressed ? theme.surface2 : theme.surface, radius,
                Ui.dp(getContext(), Math.max(1, theme.border)), theme.borderColor));
    }

    // ---- layout -----------------------------------------------------------------------------------

    /**
     * Texts on the left and the control on the right, or the control under the texts when the
     * texts would get less than {@link #minTextPx()} (SettingRow.qml `stacked`); a stretchable
     * control (slider, dropdown) then takes the whole width.
     */
    private final class Body extends ViewGroup {
        private View texts;
        private View control;
        private boolean stretch;
        private boolean stacked;

        Body(Context context) {
            super(context);
            Ui.noClip(this);
        }

        void setTexts(View t) {
            texts = t;
            addView(t);
        }

        void setControl(View c, boolean stretchable) {
            if (control != null) {
                removeView(control);
            }
            control = c;
            stretch = stretchable;
            if (c != null) {
                addView(c);
            }
        }

        private int minTextPx() {
            return Ui.dp(getContext(), compact ? 190 : 260);
        }

        private int gapPx() {
            return Ui.dp(getContext(), compact ? 16 : 24);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int inner = Math.max(0, w - getPaddingLeft() - getPaddingRight());
            int cw = 0, ch = 0;
            if (control != null) {
                control.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                cw = control.getMeasuredWidth();
                ch = control.getMeasuredHeight();
            }
            stacked = control != null && inner - cw - gapPx() < minTextPx();
            int textW = stacked || control == null ? inner : inner - cw - gapPx();
            if (tag != null) {
                tag.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int max = Math.max(0, textW - tag.getMeasuredWidth() - Ui.dp(getContext(), 10));
                if (label.getMaxWidth() != max) {
                    label.setMaxWidth(max);
                }
            }
            texts.measure(MeasureSpec.makeMeasureSpec(textW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h;
            if (stacked) {
                if (stretch) {
                    control.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    ch = control.getMeasuredHeight();
                }
                h = texts.getMeasuredHeight() + Ui.dp(getContext(), 12) + ch;
            } else {
                h = Math.max(texts.getMeasuredHeight(), ch);
            }
            setMeasuredDimension(w, h + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int left = getPaddingLeft(), top = getPaddingTop();
            int inner = getWidth() - left - getPaddingRight();
            int innerH = getHeight() - top - getPaddingBottom();
            int tw = texts.getMeasuredWidth(), th = texts.getMeasuredHeight();
            if (control == null) {
                texts.layout(left, top + (innerH - th) / 2, left + tw, top + (innerH - th) / 2 + th);
                return;
            }
            int cw = control.getMeasuredWidth(), ch = control.getMeasuredHeight();
            if (stacked) {
                texts.layout(left, top, left + tw, top + th);
                int cy = top + th + Ui.dp(getContext(), 12);
                control.layout(left, cy, left + cw, cy + ch);
            } else {
                int ty = top + (innerH - th) / 2;
                texts.layout(left, ty, left + tw, ty + th);
                int cx = left + inner - cw;
                int cy = top + (innerH - ch) / 2;
                control.layout(cx, cy, cx + cw, cy + ch);
            }
        }
    }

    /** The row's label, for tests and accessibility */
    public CharSequence getLabel() {
        return label.getText();
    }
}
