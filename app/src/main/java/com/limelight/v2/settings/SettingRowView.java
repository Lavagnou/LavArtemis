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
    private TextView dot;
    private final TextView desc;

    private V2Switch toggle;
    private LinearLayout segments;
    private TextView dropdown;
    private V2Slider slider;
    private TextView sliderValue;
    private final List<View> buttons = new ArrayList<>();

    private boolean rowEnabled = true;
    private boolean checked;
    private List<Option> options = Collections.emptyList();
    private String value;
    private int intValue;
    // Top of the slider track for the current settings (SettingDef.maxFor), set by bindSlider
    private int sliderMax;
    private final float radius;
    /** dp per prototype px (blocks) and for text (SettingsScreen.k / kt) */
    private final float k, kt;
    /** « ● » after the label: the value follows the profile (SettingsCatalog.followsProfile) */
    private final boolean followsProfile;

    public SettingRowView(Context context, V2Theme theme, boolean compact, SettingDef def, Listener listener) {
        this(context, theme, compact, def, listener, compact ? 0.85f : 1f, compact ? 0.95f : 1f);
    }

    private int px(float protoPx) {
        return Ui.dp(getContext(), protoPx * k);
    }

    private float pxf(float protoPx) {
        return Ui.dpf(getContext(), protoPx * k);
    }

    /**
     * One row of the prototype's Réglages (V8-48 to 57): a card (surface, border, radius rs, padding
     * 14 / 18) with the label 15/700, « ● » when it follows the profile, the EXPERT tag, the
     * description 13 and the control on the right. Sizes are prototype px x {@code k} (text x {@code kt}).
     */
    public SettingRowView(Context context, V2Theme theme, boolean compact, SettingDef def, Listener listener, float k, float kt) {
        super(context, theme);
        this.k = k;
        this.kt = kt;
        this.followsProfile = SettingsCatalog.followsProfile(def);
        this.def = def;
        this.sliderMax = def.max;
        this.theme = theme;
        this.compact = compact;
        this.listener = listener;
        radius = Ui.dpf(context, theme.rs);
        boolean multiButtons = def.type == SettingDef.Type.BUTTONS;
        // The theme row: its cards take the focus, left / right go from one to the next (R10)
        boolean themes = V2Theme.PREF_THEME.equals(def.key);
        setFocusable(!multiButtons && !themes);
        setClickable(!multiButtons && def.type != SettingDef.Type.SLIDER);
        setRing(theme.rs, 5, theme.fg);
        paintBackground();
        if (!multiButtons) {
            setOnClickListener(v -> activate());
        }

        body = new Body(context);
        // CSS border-box: the card's border (Braise 1, Affiche 3) sits inside, before the padding
        int bw = theme.border > 0 ? Ui.dp(context, theme.border) : 0;
        body.setPadding(px(18) + bw, px(14) + bw, px(18) + bw, px(14) + bw);
        addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        label = Ui.text(context, theme, 15 * kt, 700, theme.fg);
        label.setMaxLines(2);
        label.setText(context.getString(def.label));
        // The label takes its own width (capped by Body.onMeasure), « ● » and EXPERT follow it
        labelRow.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (followsProfile) {
            dot = Ui.text(context, theme, 10 * kt, 400, theme.muted);
            dot.setText("●");
            dot.setContentDescription(context.getString(R.string.v2_set_follows_profile));
            LinearLayout.LayoutParams dlp0 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp0.setMarginStart(px(8));
            labelRow.addView(dot, dlp0);
        }
        if (def.expert) {
            // Prototype: 10/800 .04em, padding 1 6, radius 6, surface2 / muted, no border
            tag = Ui.text(context, theme, 10 * kt, 800, theme.muted);
            tag.setText(R.string.v2_set_expert_tag);
            tag.setLetterSpacing(0.04f);
            tag.setGravity(Gravity.CENTER);
            tag.setPadding(px(6), px(1), px(6), px(1));
            tag.setBackground(Ui.roundRect(theme.surface2, pxf(6), 0, 0));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginStart(px(8));
            labelRow.addView(tag, tlp);
        }
        texts.addView(labelRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        desc = Ui.text(context, theme, 13 * kt, 400, theme.muted);
        desc.setMaxLines(4);
        desc.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = px(3);
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
                toggle = new V2Switch(context, theme, 46 * k, 26 * k);
                return toggle;
            case CHOICE:
                return null; // built when the options are known (bindChoice)
            case SLIDER: {
                // Prototype: 330 wide, the value (15/700, accent; Ludique fg) above the track at the right
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.VERTICAL);
                sliderValue = Ui.text(context, theme, 15 * kt, 700, theme.isLudique() ? theme.fg : theme.accent);
                sliderValue.setGravity(Gravity.END);
                row.addView(sliderValue, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                slider = new V2Slider(context, theme, 330 * k, 24 * k, 11 * k);
                slider.setListener((p, finished) -> {
                    if (!rowEnabled) {
                        return;
                    }
                    int v = def.valueAt(p, sliderMax);
                    showSliderValue(v);
                    listener.onSlide(def, v, finished);
                });
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = px(6);
                row.addView(slider, slp);
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
                    // Prototype: an action is a pill h38 in fg on bg; of several buttons the first one is, the
                    // others surface2
                    TextView t = Ui.text(context, theme, 14 * kt, 700, i == 0 ? theme.bg : theme.fg);
                    t.setText(context.getString(def.buttons.get(i)));
                    t.setGravity(Gravity.CENTER);
                    int padH = px(def.type == SettingDef.Type.ACTION ? 18 : 16);
                    t.setPadding(padH, 0, padH, 0);
                    float pr = theme.square() ? 0 : pxf(38) / 2f;
                    V2Frame b = new V2Frame(context, theme);
                    b.setBackground(Ui.roundRect(i == 0 ? theme.fg : theme.surface2, pr,
                            def.type == SettingDef.Type.BUTTONS ? Math.max(1, Ui.dp(context, theme.border)) : 0, theme.borderColor));
                    b.setRing(pr / getResources().getDisplayMetrics().density, 0, 0);
                    b.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    b.setClickable(true);
                    b.setFocusable(def.type == SettingDef.Type.BUTTONS);
                    b.setOnClickListener(v -> {
                        if (rowEnabled && v.isEnabled()) {
                            listener.onAction(def, index);
                        }
                    });
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(38));
                    if (i > 0) {
                        blp.setMarginStart(px(8));
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
        if (V2Theme.PREF_THEME.equals(def.key)) {
            bindThemes(opts, current);
            return;
        }
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

    private LinearLayout themeCards;

    /**
     * Prototype theme picker: one 108 x 74 card per theme in its own colours (Braise dark, Affiche and
     * Ludique light), three dots (bg, accent, fg), its name in its display font 15/800; the chosen one
     * has a 3 px accent border, the others 1 px line
     */
    private void bindThemes(List<Option> opts, String current) {
        Context c = getContext();
        boolean rebuild = themeCards == null || !opts.equals(options);
        options = opts;
        value = current;
        if (rebuild) {
            themeCards = new LinearLayout(c);
            themeCards.setOrientation(LinearLayout.HORIZONTAL);
            Ui.noClip(themeCards);
            for (int i = 0; i < opts.size(); i++) {
                final Option o = opts.get(i);
                V2Theme t = V2Theme.of(o.value, V2Theme.BRAISE.equals(o.value));
                LinearLayout card = new LinearLayout(c);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(px(10), px(9), px(10), px(9));
                // The card the gamepad lands on: its ring, never the Affiche yellow fill (it shows a theme)
                V2Frame frame = new V2Frame(c, theme);
                frame.setFocusable(true);
                frame.setClickable(true);
                frame.setRing(theme.rs, 0, 0);
                frame.setFocusFill(false);
                frame.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout dots = new LinearLayout(c);
                dots.setOrientation(LinearLayout.HORIZONTAL);
                int accent = t.accent == t.fg ? 0xFFFFD60A : t.accent;
                for (int color : new int[]{t.bg, accent, t.fg}) {
                    View dotView = new View(c);
                    dotView.setBackground(Ui.roundRect(color, pxf(7), 0, 0));
                    LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(14), px(14));
                    if (dots.getChildCount() > 0) {
                        dl.setMarginStart(px(4));
                    }
                    dots.addView(dotView, dl);
                }
                card.addView(dots);
                card.addView(new android.widget.Space(c), new LinearLayout.LayoutParams(1, 0, 1f));
                TextView name = Ui.text(c, t, 15 * kt, 800, t.fg);
                name.setTypeface(t.displayFont(c, 800));
                name.setText(o.label);
                card.addView(name);
                frame.setTag(o);
                frame.setContentDescription(o.label);
                frame.setOnClickListener(v -> {
                    if (rowEnabled) {
                        if (V2Input.showFocus(getContext())) {
                            v.requestFocus();
                        }
                        listener.onChoose(def, o);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(108), px(74));
                if (i > 0) {
                    lp.setMarginStart(px(10));
                }
                themeCards.addView(frame, lp);
            }
            body.setControl(themeCards, false);
        }
        for (int i = 0; i < themeCards.getChildCount(); i++) {
            V2Frame card = (V2Frame) themeCards.getChildAt(i);
            Option o = (Option) card.getTag();
            V2Theme t = V2Theme.of(o.value, V2Theme.BRAISE.equals(o.value));
            boolean on = o.value.equals(current);
            card.setBackground(Ui.roundRect(t.bg, Ui.dpf(c, theme.rs), on ? Ui.dp(c, 3) : Math.max(1, Ui.dp(c, 1)),
                    on ? theme.accent : theme.line));
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
        // Prototype: padding 3, radius rs, surface2, segments h34 min 58, 13/700, gap 3
        int pad = px(3);
        segments.setPadding(pad, pad, pad, pad);
        segments.setBackground(Ui.roundRect(theme.surface2, Ui.dpf(c, theme.rs), 0, 0));
        for (int i = 0; i < options.size(); i++) {
            final Option o = options.get(i);
            TextView s = Ui.text(c, theme, 13 * kt, 700, theme.fg);
            s.setText(o.label);
            s.setGravity(Gravity.CENTER);
            s.setMinWidth(px(58));
            s.setPadding(px(12), 0, px(12), 0);
            s.setOnClickListener(v -> {
                if (rowEnabled) {
                    requestFocusIfKeys();
                    listener.onChoose(def, o);
                }
            });
            // A tap picks it; the gamepad stays on the row (a clickable view is focusable on Android 8+)
            s.setFocusable(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(34), 1f);
            if (i > 0) {
                lp.setMarginStart(px(3));
            }
            segments.addView(s, lp);
        }
        return segments;
    }

    private void paintSegment(TextView s, boolean selected) {
        Context c = getContext();
        s.setBackground(selected ? Ui.roundRect(theme.accent, Ui.dpf(c, theme.rs), 0, 0) : null);
        s.setTextColor(selected ? theme.onAccent : theme.fg);
        s.setTypeface(theme.textFont(c, 700));
    }

    private View buildDropdown() {
        Context c = getContext();
        // Prototype: h40, 240 to 330 wide, padding 0 12 0 14, radius rs, tk.border, surface2, 14/700, chevron
        dropdown = Ui.text(c, theme, 14 * kt, 700, theme.fg);
        dropdown.setGravity(Gravity.CENTER_VERTICAL);
        dropdown.setEllipsize(TextUtils.TruncateAt.END);
        dropdown.setPadding(px(14), 0, px(12), 0);
        dropdown.setMinHeight(px(40));
        dropdown.setMinWidth(px(240));
        dropdown.setMaxWidth(px(330));
        int b = theme.border > 0 ? Math.max(1, Ui.dp(c, theme.border)) : 0;
        dropdown.setBackground(Ui.roundRect(theme.surface2, Ui.dpf(c, theme.rs), b, theme.borderColor));
        dropdown.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, sized(Ui.icon(c, R.drawable.v2_ic_chevron_down, theme.fg), 16 * k), null);
        dropdown.setCompoundDrawablePadding(px(10));
        dropdown.setOnClickListener(v -> {
            if (rowEnabled) {
                requestFocusIfKeys();
                listener.onOpenList(def);
            }
        });
        dropdown.setFocusable(false);
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
        bindSlider(v, sliderMax);
    }

    /** max: top of the track for the current settings (SettingDef.maxFor) */
    public void bindSlider(int v, int max) {
        sliderMax = max;
        intValue = v;
        if (slider != null) {
            slider.setPosition(def.position(v, max));
            showSliderValue(v);
        }
    }

    private void showSliderValue(int v) {
        if (sliderValue != null) {
            sliderValue.setText(def.formatValue(getContext(), v) + valueSuffix);
        }
    }

    private String valueSuffix = "";

    /** After the slider's value: « · auto » while the bitrate follows the resolution (prototype) */
    public void setValueSuffix(String suffix) {
        valueSuffix = suffix == null ? "" : suffix;
        showSliderValue(intValue);
    }

    public void setButtonEnabled(int index, boolean enabled) {
        if (index >= 0 && index < buttons.size()) {
            View b = buttons.get(index);
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    public List<View> getButtons() {
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

    /**
     * Where the gamepad stops in this row, left to right: the row itself, else its enabled buttons,
     * else its theme cards (Réglages moves through them, SettingsScreen.move)
     */
    public List<View> focusStops() {
        List<View> out = new ArrayList<>();
        if (isFocusable()) {
            out.add(this);
            return out;
        }
        for (View b : buttons) {
            if (b.isFocusable() && b.isEnabled() && b.getVisibility() == VISIBLE) {
                out.add(b);
            }
        }
        if (themeCards != null) {
            for (int i = 0; i < themeCards.getChildCount(); i++) {
                out.add(themeCards.getChildAt(i));
            }
        }
        return out;
    }

    /** The stop the focus lands on when it comes to this row: the chosen theme card, else the first stop */
    public View entryStop() {
        List<View> stops = focusStops();
        if (stops.isEmpty()) {
            return null;
        }
        if (themeCards != null) {
            for (View card : stops) {
                if (card.getTag() instanceof Option && ((Option) card.getTag()).value.equals(value)) {
                    return card;
                }
            }
        }
        return stops.get(0);
    }

    /** Left / Right on a choice or a slider (the gamepad); false when nothing changed (end of the range) */
    public boolean stepValue(int delta) {
        return step(delta);
    }

    /** Left / Right on a choice or a slider; false when nothing changed (end of the range) */
    boolean step(int delta) {
        if (!rowEnabled || themeCards != null) {
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
            int v = def.stepFrom(intValue, delta, sliderMax);
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
            return Ui.dp(getContext(), compact ? 190 : 240);
        }

        private int gapPx() {
            return px(22);
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
            int extra = 0;
            for (TextView t : new TextView[]{tag, dot}) {
                if (t != null) {
                    t.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    extra += t.getMeasuredWidth() + px(8);
                }
            }
            int max = Math.max(0, textW - extra);
            if (label.getMaxWidth() != max) {
                label.setMaxWidth(max);
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
