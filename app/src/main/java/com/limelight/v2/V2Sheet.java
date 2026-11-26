package com.limelight.v2;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.DialogKeys;
import com.limelight.v2.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

/**
 * The prototype's centred dialog (options of an app or a game, closing warnings, Quitter, Relancer):
 * a backdrop in the theme's colour, a box with the cover, the "{kind} · {PC}" line, the title, the
 * warning note (only when something will be closed), the main action in the accent and the other
 * rows, "Annuler" last. Braise 520 px box r26; Affiche 580 px square, 3 px ink border, rows under a
 * thick rule, main row in ink with "→"; Ludique 560 px r40, lime main pill. On Android every options
 * menu is this dialog (the popover is the PC's, at the mouse). A / B work through DialogKeys; the
 * focus starts on the first row.
 */
public final class V2Sheet {
    public static final class Option {
        final String label;
        final boolean main;
        final boolean play;
        final Runnable action;
        /** An icon before the label (« Plus » of the in-game menu), 0: none; Affiche never draws it */
        final int icon;

        public Option(String label, boolean main, boolean play, Runnable action) {
            this(label, main, play, action, 0);
        }

        public Option(String label, boolean main, boolean play, Runnable action, int icon) {
            this.label = label;
            this.main = main;
            this.play = play;
            this.action = action;
            this.icon = icon;
        }
    }

    public static final class Spec {
        public String kind = "";
        public String where = "";
        public String title = "";
        public Bitmap cover;
        public boolean hasCover;
        public String note;
        public boolean warn;
        /** A calm note, in the muted colour (sub-menus of the stream, Mes PC) */
        public boolean info;
        /** The pairing code in big letters (prototype ctx.hasPin), « 4 8 2 1 »; null: none */
        public String pin;
        /** A text field under the note (prototype ctx.hasInput): its label; null: none */
        public String inputLabel;
        public String inputHint;
        public String inputValue;
        public int inputType = android.text.InputType.TYPE_CLASS_TEXT;
        public int inputMax;
        /** The field once the dialog is built (read it with {@link #text()}) */
        android.widget.EditText field;
        public final List<Option> options = new ArrayList<>();
        /** The option focused first (prototype focus 'c' + n: the current theme, the current PC) */
        public int focus;
        /** A progress bar (prototype ctx.hasProgress): its label, null for none; see {@link #setProgress} */
        public String progressLabel;
        public int progress;
        private TextView progressText, progressPct;
        private View progressFill;

        /** Moves the bar of a shown dialog (on the main thread) */
        public void setProgress(int pct, String label) {
            progress = Math.max(0, Math.min(100, pct));
            if (label != null) {
                progressLabel = label;
            }
            if (progressText != null) {
                progressText.setText(progressLabel);
                progressPct.setText(progress + " %");
                ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
                ViewGroup track = (ViewGroup) progressFill.getParent();
                if (lp instanceof LinearLayout.LayoutParams && track != null && track.getChildCount() > 1) {
                    ((LinearLayout.LayoutParams) lp).weight = progress;
                    progressFill.setLayoutParams(lp);
                    View rest = track.getChildAt(1);
                    LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) rest.getLayoutParams();
                    rp.weight = 100 - progress;
                    rest.setLayoutParams(rp);
                }
            }
        }

        /** What is typed in the field, trimmed ("" without a field) */
        public String text() {
            return field == null ? (inputValue == null ? "" : inputValue.trim()) : field.getText().toString().trim();
        }

        public Spec option(String label, boolean main, boolean play, Runnable action) {
            options.add(new Option(label, main, play, action));
            return this;
        }

        /**
         * An entry with its icon, as on the tiles (menu workshop, 2026-11-26): the accent's stroke in
         * Braise, a round chip in Ludique, none in Affiche (its menu has no icons)
         */
        public Spec option(String label, int icon, Runnable action) {
            options.add(new Option(label, false, false, action, icon));
            return this;
        }
    }

    private V2Sheet() {}

    /** Columns of the options (prototype ctxCols): 1, 2 past 8 options, 3 past 14 */
    public static int columns(int options) {
        return options > 14 ? 3 : options > 8 ? 2 : 1;
    }

    /** Width of the box in prototype px (prototype ctx.w): the theme's width, 250 more per extra column */
    public static int boxWidthPx(boolean affiche, boolean ludique, int cols) {
        return (affiche ? 580 : (ludique ? 560 : 520)) + (cols - 1) * 250;
    }


    /** Prototype ctxKind 'error' (V8-15): « PC · ATLAS-PC », « Oups », the message, OK */
    public static Dialog oops(Activity activity, V2Theme th, String kind, String where, String text) {
        Spec spec = new Spec();
        spec.kind = kind == null ? "" : kind;
        spec.where = where == null ? "" : where;
        spec.title = activity.getString(com.limelight.R.string.v2_oops);
        spec.info = true;
        spec.note = text;
        spec.option(activity.getString(com.limelight.R.string.v2_ok), true, false, null);
        return show(activity, th, spec);
    }

    public static Dialog show(Activity activity, V2Theme th, Spec spec) {
        android.content.res.Configuration conf = activity.getResources().getConfiguration();
        HomeLayout l = HomeLayout.forSize(conf.screenWidthDp, conf.screenHeightDp);
        return show(activity, th, l, spec);
    }

    static Dialog show(Activity activity, V2Theme th, HomeLayout l, Spec spec) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        View content = build(activity, th, l, spec, dialog);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            // backdrop-filter: blur(3px) behind the dialog, where the system offers it
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                android.view.WindowManager.LayoutParams a = w.getAttributes();
                a.setBlurBehindRadius(Ui.dp(activity, 3));
                w.setAttributes(a);
            }
        }
        dialog.setCanceledOnTouchOutside(true);
        DialogKeys.install(dialog);
        dialog.show();
        View first = content.findViewWithTag(FIRST);
        if (first != null) {
            first.requestFocus();
        }
        return dialog;
    }

    private static final Object FIRST = new Object();

    /** The whole screen: backdrop + centred box (also drawn by the screenshot tests) */
    static View build(android.content.Context ctx, V2Theme th, HomeLayout l, Spec spec, final Dialog dialog) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        FrameLayout root = new FrameLayout(ctx);
        int backdrop;
        if (af) {
            backdrop = th.dark ? Color.argb(140, 0, 0, 0) : Color.argb(102, 17, 17, 17);
        } else if (lu) {
            backdrop = th.dark ? Color.argb(89, 0, 0, 0) : Color.argb(89, 27, 21, 48);
        } else {
            backdrop = th.dark ? Color.argb(97, 8, 7, 6) : Color.argb(56, 60, 45, 30);
        }
        root.setBackgroundColor(backdrop);
        root.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.cancel();
            }
        });

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        Ui.noClip(box);
        int boxBg = af ? th.bg : th.lit.card;
        if (af) {
            box.setBackground(rect(boxBg, 0, px(ctx, l, 3), th.fg));
            box.setPadding(px(ctx, l, 28), px(ctx, l, 26), px(ctx, l, 28), px(ctx, l, 26));
        } else {
            box.setBackground(rect(boxBg, pxf(ctx, l, lu ? 40 : 26), 0, 0));
            int p = px(ctx, l, lu ? 28 : 24);
            box.setPadding(p, p, p, p);
            if (android.os.Build.VERSION.SDK_INT >= 21 && !(lu && th.dark)) {
                box.setElevation(Ui.dpf(ctx, 24));
            }
        }

        // header: cover + "{kind} · {where}" + title
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(af ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);
        if (spec.hasCover) {
            CoverView cv = new CoverView(ctx);
            float r = af ? 0 : pxf(ctx, l, lu ? 18 : 10);
            cv.setRadii(r, r, r, r);
            cv.setBitmap(spec.cover);
            cv.setBackground(rect(th.surface2, r, 0, 0));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(px(ctx, l, af || lu ? 80 : 72), px(ctx, l, af || lu ? 120 : 108));
            clp.setMarginEnd(px(ctx, l, af || lu ? 20 : 18));
            head.addView(cv, clp);
        }
        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        String sep = af ? " — " : " · ";
        String headText = (af ? spec.kind.toUpperCase(Locale.FRENCH) : spec.kind)
                + (spec.where == null || spec.where.isEmpty() ? "" : sep + (af ? spec.where.toUpperCase(Locale.FRENCH) : spec.where));
        TextView kind = HomeParts.text(ctx, th, l, af || lu ? 14 : 13, af ? 800 : 700, af ? th.fg : (lu ? th.lit.muted2 : th.lit.accentText), false);
        kind.setText(headText);
        titles.addView(kind);
        TextView title = HomeParts.text(ctx, th, l, af ? 44 : (lu ? 34 : 28), af ? 900 : (lu ? 800 : 700), th.fg, true);
        title.setMaxLines(3);
        title.setLetterSpacing(af ? -0.045f : (lu ? -0.03f : 0));
        HomeParts.lineHeight(title, ctx, l, af ? 44 : (lu ? 34 : 28), af ? 0.92f : (lu ? 1.02f : 1.1f));
        // A PC name (BUREAU-PAPA) never breaks at its hyphen in a big title
        title.setText(spec.title.replaceAll("-(?=\\S)", "-⁠"));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = px(ctx, l, af ? 8 : (lu ? 4 : 6));
        titles.addView(title, tlp);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = px(ctx, l, af ? 14 : 16);
        box.addView(head, hlp);

        if (spec.warn && spec.note != null && !spec.note.isEmpty()) {
            TextView note = HomeParts.text(ctx, th, l, af || lu ? 15 : 14, af || lu ? 600 : 400,
                    af ? V2Theme.FILL_INK : (lu ? th.fg : (th.dark ? 0xFFF3D9C9 : 0xFF5A2A12)), false);
            note.setMaxLines(6);
            note.setText(spec.note);
            HomeParts.lineHeight(note, ctx, l, af || lu ? 15 : 14, af || lu ? 1.45f : 1.5f);
            if (af) {
                note.setBackgroundColor(V2Theme.YELLOW);
                note.setPadding(px(ctx, l, 14), px(ctx, l, 12), px(ctx, l, 14), px(ctx, l, 12));
            } else if (lu) {
                note.setBackground(rect(th.warnBg, pxf(ctx, l, 22), 0, 0));
                note.setPadding(px(ctx, l, 18), px(ctx, l, 14), px(ctx, l, 18), px(ctx, l, 14));
            } else {
                note.setBackground(rect(th.dark ? 0xE62A1A10 : 0xFFFBE3D4, pxf(ctx, l, 14), Math.max(1, px(ctx, l, 1)), th.warnLine));
                note.setPadding(px(ctx, l, 14), px(ctx, l, 12), px(ctx, l, 14), px(ctx, l, 12));
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.bottomMargin = px(ctx, l, af ? 14 : 12);
            box.addView(note, nlp);
        }

        if (!spec.warn && spec.info && spec.note != null && !spec.note.isEmpty()) {
            TextView note = HomeParts.text(ctx, th, l, 15, 400, th.muted, false);
            note.setMaxLines(6);
            note.setText(spec.note);
            HomeParts.lineHeight(note, ctx, l, 15, 1.5f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.bottomMargin = px(ctx, l, 12);
            box.addView(note, nlp);
        }

        if (spec.progressLabel != null) {
            // Prototype: label and « 42 % » 14/700 on one line, then a 10 px pill track (off) filled with on
            LinearLayout prog = new LinearLayout(ctx);
            prog.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            spec.progressText = HomeParts.text(ctx, th, l, 14, 700, th.fg, false);
            line.addView(spec.progressText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            spec.progressPct = HomeParts.text(ctx, th, l, 14, 700, th.fg, false);
            line.addView(spec.progressPct);
            prog.addView(line);
            LinearLayout track = new LinearLayout(ctx);
            track.setOrientation(LinearLayout.HORIZONTAL);
            int th10 = px(ctx, l, 10);
            track.setBackground(rect(th.off, th10 / 2f, 0, 0));
            HomeParts.setClipToOutline21(track, th10 / 2f);
            spec.progressFill = new View(ctx);
            spec.progressFill.setBackgroundColor(th.on);
            track.addView(spec.progressFill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0));
            track.addView(new android.widget.Space(ctx), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, th10);
            trackLp.topMargin = px(ctx, l, 8);
            prog.addView(track, trackLp);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.bottomMargin = px(ctx, l, 12);
            box.addView(prog, glp);
            spec.setProgress(spec.progress, null);
        }

        if (spec.pin != null) {
            // Prototype: surface2 block, display font 54 with .2em letter spacing
            TextView pin = HomeParts.text(ctx, th, l, 54, th.displayWeight, th.fg, true);
            pin.setGravity(Gravity.CENTER);
            pin.setLetterSpacing(0.2f);
            pin.setText(spec.pin);
            pin.setContentDescription(ctx.getString(R.string.v2_pair_code_desc, spec.pin));
            pin.setPadding(0, px(ctx, l, 14), 0, px(ctx, l, 14));
            pin.setBackground(rect(th.surface2, pxf(ctx, l, th.rs), 0, 0));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.bottomMargin = px(ctx, l, 12);
            box.addView(pin, plp);
        }

        if (spec.inputLabel != null) {
            // Prototype: label 13/700 muted, then the field h52 r(rs) 2px line border, surface2, 18/600
            TextView label = HomeParts.text(ctx, th, l, 13, 700, th.muted, false);
            label.setText(spec.inputLabel);
            box.addView(label);
            final android.widget.EditText field = new android.widget.EditText(ctx);
            field.setTypeface(th.textFont(ctx, 600));
            field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, l.t(18));
            field.setTextColor(th.fg);
            field.setHintTextColor(th.muted);
            field.setSingleLine(true);
            field.setInputType(spec.inputType);
            field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if (spec.inputMax > 0) {
                field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(spec.inputMax)});
            }
            if (spec.inputHint != null) {
                field.setHint(spec.inputHint);
            }
            if (spec.inputValue != null) {
                field.setText(spec.inputValue);
                field.setSelection(field.getText().length());
            }
            field.setPadding(px(ctx, l, 16), 0, px(ctx, l, 16), 0);
            field.setBackground(rect(th.surface2, pxf(ctx, l, th.rs), Math.max(1, px(ctx, l, 2)), th.line));
            field.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE && !spec.options.isEmpty()) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    Runnable first = spec.options.get(0).action;
                    if (first != null) {
                        first.run();
                    }
                    return true;
                }
                return false;
            });
            spec.field = field;
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(px(ctx, l, 52), Ui.dp(ctx, 44)));
            flp.topMargin = px(ctx, l, 6);
            flp.bottomMargin = px(ctx, l, 12);
            box.addView(field, flp);
        }

        LinearLayout rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(rows);
        if (af) {
            box.addView(HomeParts.ruleV(ctx, l, th.fg), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
        }
        int cols = columns(spec.options.size());
        LinearLayout line = null;
        for (int i = 0; i < spec.options.size(); i++) {
            final Option o = spec.options.get(i);
            HomeParts.Button b = new HomeParts.Button(ctx, th);
            TextView t;
            int h;
            if (af) {
                h = o.main ? 60 : 52;
                t = HomeParts.text(ctx, th, l, o.main ? 26 : 20, o.main ? 900 : 800, o.main ? th.lit.onBand : th.fg, false);
                t.setLetterSpacing(o.main ? -0.03f : -0.02f);
                t.setText(o.main ? ctx.getString(R.string.v2_arrow, o.label) : o.label);
                t.setMaxLines(2);
                b.setBackground(new RuleBackground(o.main ? th.lit.band : Color.TRANSPARENT, th.fg, Math.max(1, px(ctx, l, 1))));
                b.radius(0);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(px(ctx, l, 16), px(ctx, l, 8), px(ctx, l, 16), px(ctx, l, 8));
                b.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            } else {
                h = o.main ? (lu ? 62 : 56) : (lu ? 54 : 52);
                float r = lu ? h / 2f : 16;
                int bg = o.main ? (lu ? th.lit.lime : 0xFFFF6A2B) : (lu ? th.bg : th.lit.bandChip);
                int fg = o.main ? (lu ? 0xFF1B1530 : 0xFF0E0D0C) : th.fg;
                b.setBackground(rect(bg, pxf(ctx, l, r), 0, 0));
                b.radius(l.b(r));
                t = HomeParts.text(ctx, th, l, o.main ? (lu ? 19 : 17) : (lu ? 17 : 16), o.main ? (lu ? 800 : 700) : (lu ? 700 : 600), fg, false);
                t.setText(o.label);
                t.setMaxLines(2);
                t.setGravity(Gravity.CENTER);
                View inner;
                if (o.main && o.play) {
                    inner = HomeParts.iconLabel(ctx, l, R.drawable.v2_ic_p_play, 16, 10, t, fg);
                    ((LinearLayout) inner).setGravity(Gravity.CENTER);
                } else if (o.icon != 0 && !o.main) {
                    // Workshop: icon then the label, from the start of the row
                    t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    LinearLayout row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    if (lu) {
                        FrameLayout chip = new FrameLayout(ctx);
                        int cs = px(ctx, l, 32), is = px(ctx, l, 17);
                        chip.setBackground(rect(th.dark ? 0xFF2E2650 : 0xFFE4DEFF, pxf(ctx, l, 16), 0, 0));
                        chip.addView(HomeParts.icon(ctx, o.icon, th.dark ? 0xFFF3EEFF : 0xFF1B1530), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
                        row.addView(chip, new LinearLayout.LayoutParams(cs, cs));
                    } else {
                        int is = px(ctx, l, 20);
                        row.addView(HomeParts.icon(ctx, o.icon, th.lit.accentText), new LinearLayout.LayoutParams(is, is));
                    }
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    tl.setMarginStart(px(ctx, l, 12));
                    row.addView(t, tl);
                    inner = row;
                } else {
                    inner = t;
                }
                inner.setPadding(px(ctx, l, o.main ? (lu ? 22 : 16) : 16), 0, px(ctx, l, o.main ? (lu ? 22 : 16) : 16), 0);
                b.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            }
            b.setContentDescription(o.label);
            b.setOnClickListener(v -> {
                if (dialog != null) {
                    dialog.dismiss();
                }
                if (o.action != null) {
                    o.action.run();
                }
            });
            if (i == Math.max(0, Math.min(spec.focus, spec.options.size() - 1))) {
                b.setTag(FIRST);
            }
            b.setMinimumHeight(Math.max(px(ctx, l, h), Ui.dp(ctx, 44)));
            if (cols == 1) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0 && !af) {
                    lp.topMargin = px(ctx, l, 12);
                }
                rows.addView(b, lp);
            } else {
                // A grid (prototype grid-template-columns): gap 12, Affiche 18 between columns and none between rows
                if (i % cols == 0) {
                    line = new V2GameMenu.EqualRow(ctx);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    Ui.noClip(line);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0 && !af) {
                        rlp.topMargin = px(ctx, l, 12);
                    }
                    rows.addView(line, rlp);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i % cols > 0) {
                    lp.setMarginStart(px(ctx, l, af ? 18 : 12));
                }
                line.addView(b, lp);
            }
        }
        if (line != null) {
            for (int k = spec.options.size() % cols; k > 0 && k < cols; k++) {
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1f);
                sp.setMarginStart(px(ctx, l, af ? 18 : 12));
                line.addView(new android.widget.Space(ctx), sp);
            }
        }
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows);
        Ui.noClip(scroll);
        int ring = af ? 0 : Ui.dp(ctx, 10);
        rows.setPadding(ring, ring, ring, ring);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(-ring, -ring, -ring, -ring);
        box.addView(scroll, slp);

        int width = px(ctx, l, boxWidthPx(af, lu, cols));
        int maxW = Ui.dp(ctx, l.widthDp - 32);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Math.min(width, maxW), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        blp.topMargin = blp.bottomMargin = Ui.dp(ctx, 12);
        root.addView(box, blp);

        // Gamepad / keyboard: "A Valider · B Annuler" (prototype hints with a dialog open)
        V2Input.Kind k = V2Input.get(ctx);
        if (k != V2Input.Kind.TOUCH) {
            boolean pad = k == V2Input.Kind.PAD;
            com.limelight.v2.ui.HintsBar hints = new com.limelight.v2.ui.HintsBar(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
            hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_validate), null);
            hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_cancel), dialog == null ? null : dialog::cancel);
            root.addView(hints, hints.layoutParams());
        }
        return root;
    }

    /** Affiche's rows: a fill and a 1 px ink rule at the bottom */
    private static final class RuleBackground extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p = new android.graphics.Paint();
        private final int fill, line, width;

        RuleBackground(int fill, int line, int width) {
            this.fill = fill;
            this.line = line;
            this.width = width;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            p.setColor(fill);
            c.drawRect(b, p);
            p.setColor(line);
            c.drawRect(b.left, b.bottom - width, b.right, b.bottom, p);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
