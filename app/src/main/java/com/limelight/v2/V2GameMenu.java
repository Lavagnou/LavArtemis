package com.limelight.v2;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.ui.DialogKeys;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

/**
 * The prototype's in-game menu (V8, MENU_AN): Reprendre, the nine most useful entries and « Plus »,
 * as tiles over the stream. Braise: a veil and a 3-column grid of tiles (icon in the accent, label,
 * value), the game's name and "PC · via" above. Affiche: an ink panel on the right, one row per
 * entry, no icons. Ludique: a white pill with the PC, the game and the via line at the top left,
 * and a dock of round-chipped tiles at the bottom. A / B through DialogKeys; Reprendre first.
 */
public final class V2GameMenu {
    public static final class Tile {
        final String label, shortLabel, value, sub;
        final int icon;
        final boolean dim, danger, first, last;
        final Runnable action;

        /**
         * @param chip index of the Ludique chip colour: 0 resume (lime), 7 disconnect, 8 quit, 1 others
         */
        public Tile(String label, String shortLabel, String value, String sub, int icon, boolean dim, int chip, Runnable action) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.value = value == null ? "" : value;
            this.sub = sub == null ? "" : sub;
            this.icon = icon;
            this.dim = dim;
            this.first = chip == 0;
            this.danger = chip == 8;
            this.last = chip == 7;
            this.action = action;
        }
    }

    private V2GameMenu() {}

    public static Dialog show(Activity activity, V2Theme th, String title, String host, String via, List<Tile> tiles,
                              Runnable onCancel) {
        android.content.res.Configuration conf = activity.getResources().getConfiguration();
        HomeLayout l = HomeLayout.forSize(conf.screenWidthDp, conf.screenHeightDp);
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        View content = build(activity, th, l, title, host, via, tiles, dialog);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnCancelListener(d -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        DialogKeys.install(dialog);
        dialog.show();
        View first = content.findViewWithTag(FIRST);
        if (first != null) {
            first.requestFocus();
        }
        return dialog;
    }

    private static final Object FIRST = new Object();

    static View build(Context ctx, V2Theme th, HomeLayout l, String title, String host, String via, List<Tile> tiles,
                      final Dialog dialog) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        FrameLayout root = new FrameLayout(ctx);
        root.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.cancel();
            }
        });
        Ui.noClip(root);
        List<View> items = new ArrayList<>();

        if (af) {
            // Right panel, full height
            LinearLayout panel = new LinearLayout(ctx);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setClickable(true);
            panel.setBackgroundColor(th.lit.band);
            LinearLayout head = new LinearLayout(ctx);
            head.setOrientation(LinearLayout.VERTICAL);
            head.setPadding(px(ctx, l, 28), px(ctx, l, 24), px(ctx, l, 28), px(ctx, l, 16));
            TextView where = HomeParts.text(ctx, th, l, 13, 800, th.lit.bandLive, false);
            where.setText((host + " — " + via).toUpperCase(Locale.FRENCH));
            head.addView(where);
            TextView t = HomeParts.text(ctx, th, l, 30, 900, th.lit.onBand, true);
            t.setText(title);
            t.setMaxLines(2);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(ctx, l, 6);
            head.addView(t, tlp);
            panel.addView(head);
            panel.addView(HomeParts.ruleV(ctx, l, th.lit.bandLive), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
            LinearLayout rows = new LinearLayout(ctx);
            rows.setOrientation(LinearLayout.VERTICAL);
            sv.addView(rows);
            panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            for (Tile tile : tiles) {
                HomeParts.Button b = button(ctx, th, tile, dialog);
                b.setBackground(new RuleBg(th.lit.band, th.dark ? 0xFFCFCFC9 : 0xFF333330, Math.max(1, px(ctx, l, 1))));
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(px(ctx, l, 28), 0, px(ctx, l, 28), 0);
                LinearLayout labels = new LinearLayout(ctx);
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView lab = HomeParts.text(ctx, th, l, 20, 800, th.lit.onBand, false);
                lab.setText(tile.label);
                labels.addView(lab);
                if (!tile.sub.isEmpty()) {
                    TextView sub = HomeParts.text(ctx, th, l, 13, 600, th.lit.onBand, false);
                    sub.setAlpha(0.75f);
                    sub.setText(tile.sub);
                    labels.addView(sub);
                }
                row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView val = HomeParts.text(ctx, th, l, 14, 600, th.lit.onBand, false);
                val.setText(tile.value);
                row.addView(val);
                b.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                b.setMinimumHeight(Math.max(px(ctx, l, 54), Ui.dp(ctx, 44)));
                rows.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                items.add(b);
            }
            root.addView(panel, new FrameLayout.LayoutParams(px(ctx, l, 420), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        } else if (lu) {
            root.addView(new HomeParts.GradientView(ctx, 180f,
                    new int[]{th.dark ? 0x00000000 : 0x001B1530, th.dark ? 0x00000000 : 0x001B1530, th.dark ? 0xBF000000 : 0xBF1B1530},
                    new float[]{0f, 0.45f, 1f}), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout pill = new LinearLayout(ctx);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setBackground(rect(th.lit.card, pxf(ctx, l, 26), 0, 0));
            pill.setPadding(px(ctx, l, 8), 0, px(ctx, l, 22), 0);
            TextView chip = HomeParts.text(ctx, th, l, 13, 800, Color.WHITE, false);
            chip.setText(host);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(px(ctx, l, 14), 0, px(ctx, l, 14), 0);
            chip.setBackground(rect(th.lit.purple, pxf(ctx, l, 18), 0, 0));
            pill.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 36)));
            TextView t = HomeParts.text(ctx, th, l, 17, 800, th.fg, false);
            t.setText(title);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginStart(px(ctx, l, 12));
            pill.addView(t, tlp);
            TextView v = HomeParts.text(ctx, th, l, 14, 600, th.lit.muted2, false);
            v.setText(via);
            pill.addView(v, tlp(ctx, l, 12));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 52), Gravity.TOP | Gravity.START);
            plp.setMargins(px(ctx, l, 48), px(ctx, l, 36), 0, 0);
            root.addView(pill, plp);
            LinearLayout dock = new LinearLayout(ctx);
            dock.setOrientation(LinearLayout.HORIZONTAL);
            dock.setClickable(true);
            dock.setBackground(rect(th.bg, pxf(ctx, l, 40), 0, 0));
            dock.setPadding(px(ctx, l, 16), px(ctx, l, 14), px(ctx, l, 16), px(ctx, l, 14));
            Ui.noClip(dock);
            float tw = Math.min(96, (l.stageWidth() - 48 - 32 - 8 * (tiles.size() - 1)) / Math.max(1, tiles.size()));
            for (int i = 0; i < tiles.size(); i++) {
                Tile tile = tiles.get(i);
                HomeParts.Button b = button(ctx, th, tile, dialog);
                b.setBackground(rect(th.lit.card, pxf(ctx, l, 30), 0, 0));
                b.radius(l.b(30));
                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                FrameLayout disc = new FrameLayout(ctx);
                int chipBg, chipFg = th.fg;
                if (tile.first) {
                    chipBg = th.lit.lime;
                    chipFg = 0xFF1B1530;
                } else if (tile.danger) {
                    chipBg = th.lit.coral;
                    chipFg = 0xFF1B1530;
                } else if (tile.last) {
                    chipBg = th.dark ? 0xFF3A2430 : 0xFFFFE1D8;
                } else {
                    chipBg = th.dark ? 0xFF2E2650 : 0xFFE4DEFF;
                }
                disc.setBackground(rect(chipBg, pxf(ctx, l, 22), 0, 0));
                int is = px(ctx, l, 22);
                disc.addView(HomeParts.icon(ctx, tile.icon, chipFg), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
                col.addView(disc, new LinearLayout.LayoutParams(px(ctx, l, 44), px(ctx, l, 44)));
                TextView lab = HomeParts.text(ctx, th, l, 13, 800, th.fg, false);
                lab.setText(tile.shortLabel);
                lab.setGravity(Gravity.CENTER);
                col.addView(lab, tlpTop(ctx, l, 6));
                TextView val = HomeParts.text(ctx, th, l, 11, 600, th.lit.muted2, false);
                val.setText(tile.value.isEmpty() && tile.danger && !tile.sub.isEmpty() ? ctx.getString(R.string.v2_menu_back_playnite) : tile.value);
                val.setGravity(Gravity.CENTER);
                val.setMaxLines(2);
                col.addView(val, tlpTop(ctx, l, 4));
                b.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(ctx, l, tw), px(ctx, l, 108));
                if (i > 0) {
                    blp.setMarginStart(px(ctx, l, 8));
                }
                dock.addView(b, blp);
                items.add(b);
            }
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dlp.bottomMargin = px(ctx, l, 76);
            root.addView(dock, dlp);
        } else {
            root.setBackgroundColor(Color.argb(158, 8, 7, 6));
            LinearLayout section = new LinearLayout(ctx);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setClickable(true);
            Ui.noClip(section);
            LinearLayout head = new LinearLayout(ctx);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.BOTTOM);
            TextView t = HomeParts.text(ctx, th, l, 26, 700, th.dark ? th.fg : 0xFFF3EEE8, true);
            t.setText(title);
            head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView w = HomeParts.text(ctx, th, l, 14, 400, 0xFFCFC6BC, false);
            w.setText(host + " · " + via);
            head.addView(w);
            section.addView(head);
            int cols = 3;
            LinearLayout row = null;
            for (int i = 0; i < tiles.size(); i++) {
                if (i % cols == 0) {
                    row = new EqualRow(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    Ui.noClip(row);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = px(ctx, l, i == 0 ? 16 : 12);
                    section.addView(row, rlp);
                }
                Tile tile = tiles.get(i);
                HomeParts.Button b = button(ctx, th, tile, dialog);
                b.setBackground(rect(th.dark ? 0xF21A1816 : 0xF2FFFFFF, pxf(ctx, l, 18), 0, 0));
                b.radius(l.b(18));
                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(px(ctx, l, 18), px(ctx, l, 16), px(ctx, l, 18), px(ctx, l, 16));
                int is = px(ctx, l, 24);
                col.addView(HomeParts.icon(ctx, tile.icon, th.lit.accentText), new LinearLayout.LayoutParams(is, is));
                col.addView(new android.widget.Space(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
                LinearLayout line = new LinearLayout(ctx);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.BOTTOM);
                LinearLayout labels = new LinearLayout(ctx);
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView lab = HomeParts.text(ctx, th, l, 16, 600, th.fg, false);
                lab.setMaxLines(2);
                lab.setText(tile.label);
                labels.addView(lab);
                if (!tile.sub.isEmpty()) {
                    TextView sub = HomeParts.text(ctx, th, l, 12, 400, th.lit.muted2, false);
                    sub.setMaxLines(2);
                    sub.setText(tile.sub);
                    labels.addView(sub, tlpTop(ctx, l, 3));
                }
                line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView val = HomeParts.text(ctx, th, l, 13, 400, th.lit.muted2, false);
                val.setText(tile.value);
                line.addView(val, tlp(ctx, l, 8));
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = px(ctx, l, 10);
                col.addView(line, llp);
                b.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                b.setMinimumHeight(px(ctx, l, 96));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i % cols > 0) {
                    blp.setMarginStart(px(ctx, l, 12));
                }
                row.addView(b, blp);
                items.add(b);
            }
            if (row != null) {
                for (int k = tiles.size() % cols; k > 0 && k < cols; k++) {
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1f);
                    sp.setMarginStart(px(ctx, l, 12));
                    row.addView(new android.widget.Space(ctx), sp);
                }
            }
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Math.min(px(ctx, l, 740), Ui.dp(ctx, l.widthDp - 32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            slp.bottomMargin = px(ctx, l, 58);
            root.addView(section, slp);
        }
        if (!items.isEmpty()) {
            items.get(0).setTag(FIRST);
        }

        // Gamepad / keyboard hints: A Valider · B Fermer le menu
        V2Input.Kind k = V2Input.get(ctx);
        if (k != V2Input.Kind.TOUCH) {
            boolean pad = k == V2Input.Kind.PAD;
            HintsBar hints = new HintsBar(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
            hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_validate), null);
            hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_close_menu),
                    dialog == null ? null : dialog::cancel);
            FrameLayout.LayoutParams hlp = hints.layoutParams();
            if (af) {
                // The panel takes the right side: hints stay on the left
                hlp.gravity = Gravity.BOTTOM | Gravity.START;
            }
            root.addView(hints, hlp);
        }
        return root;
    }

    private static HomeParts.Button button(Context ctx, V2Theme th, Tile tile, Dialog dialog) {
        HomeParts.Button b = new HomeParts.Button(ctx, th);
        b.setContentDescription(tile.label);
        if (tile.dim) {
            b.setAlpha(0.45f);
        }
        b.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.dismiss();
            }
            if (tile.action != null) {
                tile.action.run();
            }
        });
        return b;
    }

    private static LinearLayout.LayoutParams tlp(Context ctx, HomeLayout l, float start) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(px(ctx, l, start));
        return lp;
    }

    private static LinearLayout.LayoutParams tlpTop(Context ctx, HomeLayout l, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(ctx, l, top);
        return lp;
    }

    /** A row of tiles that all take the height of the tallest (the prototype's grid rows) */
    private static final class EqualRow extends LinearLayout {
        EqualRow(Context ctx) {
            super(ctx);
            setBaselineAligned(false);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int max = 0;
            for (int i = 0; i < getChildCount(); i++) {
                max = Math.max(max, getChildAt(i).getMeasuredHeight());
            }
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getMeasuredHeight() != max) {
                    c.measure(MeasureSpec.makeMeasureSpec(c.getMeasuredWidth(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY));
                }
            }
            setMeasuredDimension(getMeasuredWidth(), max + getPaddingTop() + getPaddingBottom());
        }
    }

    private static final class RuleBg extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p = new android.graphics.Paint();
        private final int fill, line, width;

        RuleBg(int fill, int line, int width) {
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
