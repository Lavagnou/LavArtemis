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
 * The in-game menu (prototype V8, MENU_AN, entries of the menu workshop of 2026-11-26: see
 * StreamMenu.tiles()), as tiles over the stream. Braise: a veil and a 3-column grid of tiles (icon in the accent, label,
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

    /** What the labels of the tiles depend on (plain data: GameMenu and the captures fill it) */
    public static final class State {
        /** The game or app the stream shows (prototype playingTitle) */
        public String title = "";
        public boolean playniteGame;
        /** The touch tile switches to Trackpad (else to Multitouch) */
        public boolean toTrackpad;
        public boolean micSent, micMuted;
        public boolean hudShown;
    }

    /**
     * A tile with the labels both clients use (workshop: « Quitter la session » and « Clavier » as
     * the Ludique dock's short labels; « Micro non envoyé », greyed, while the micro is not sent)
     */
    public static Tile tile(Context ctx, StreamMenu.Tile kind, State s, Runnable action) {
        switch (kind) {
            case RESUME:
                return new Tile(ctx.getString(R.string.v2_resume), ctx.getString(R.string.v2_resume), "", "",
                        R.drawable.v2_ic_m_resume, false, 0, action);
            case DISCONNECT:
                return new Tile(ctx.getString(R.string.v2_menu_disconnect), ctx.getString(R.string.v2_menu_disconnect), "", "",
                        R.drawable.v2_ic_m_disconnect, false, 7, action);
            case QUIT_GAME:
                return new Tile(ctx.getString(R.string.v2_menu_quit, s.title), ctx.getString(R.string.v2_short_quit), "",
                        ctx.getString(R.string.v2_menu_quit_playnite_sub), R.drawable.v2_ic_m_quit, false, 8, action);
            case QUIT_SESSION:
                return new Tile(ctx.getString(R.string.v2_more_quit_session), ctx.getString(R.string.v2_more_quit_session), "", "",
                        R.drawable.v2_ic_m_quit_session, false, 1, action);
            case TOUCH_MODE:
                return new Tile(ctx.getString(s.toTrackpad ? R.string.v2_menu_touch_to_trackpad : R.string.v2_menu_touch_to_multitouch),
                        ctx.getString(s.toTrackpad ? R.string.v2_short_trackpad : R.string.v2_short_multitouch), "", "",
                        R.drawable.v2_ic_m_touch, false, 1, action);
            case KEYBOARD:
                return new Tile(ctx.getString(R.string.v2_menu_open_keyboard), ctx.getString(R.string.v2_short_keyboard), "", "",
                        R.drawable.v2_ic_m_keyboard, false, 1, action);
            case MIC:
                if (!s.micSent) {
                    return new Tile(ctx.getString(R.string.v2_more_mic_not_sent), ctx.getString(R.string.v2_more_mic),
                            ctx.getString(R.string.v2_more_mic_not_sent_value), "", R.drawable.v2_ic_m_mic, true, 1, action);
                }
                return new Tile(ctx.getString(R.string.v2_more_mic), ctx.getString(R.string.v2_more_mic),
                        ctx.getString(s.micMuted ? R.string.v2_more_mic_off : R.string.v2_more_mic_on), "",
                        R.drawable.v2_ic_m_mic, false, 1, action);
            case STATS:
                return new Tile(ctx.getString(R.string.v2_menu_stats), ctx.getString(R.string.v2_short_stats),
                        ctx.getString(s.hudShown ? R.string.v2_stats_shown : R.string.v2_stats_hidden), "",
                        R.drawable.v2_ic_m_stats, false, 1, action);
            case MORE:
            default:
                return new Tile(ctx.getString(R.string.v2_menu_more), ctx.getString(R.string.v2_menu_more), "›", "",
                        R.drawable.v2_ic_m_more, false, 1, action);
        }
    }

    /** The icon of an entry of « Plus » (workshop ICON) */
    public static int icon(StreamMenu.More m) {
        switch (m) {
            case CLIPBOARD: return R.drawable.v2_ic_m_clip;
            case SERVER_COMMANDS: return R.drawable.v2_ic_m_cmds;
            case ZOOM: return R.drawable.v2_ic_m_zoom;
            case VOICE: return R.drawable.v2_ic_m_mic;
            case VOICE_MOVE: return R.drawable.v2_ic_m_voice_move;
            case ROTATE: return R.drawable.v2_ic_m_rotate;
            case ON_SCREEN_CONTROLLER: return R.drawable.v2_ic_m_osc;
            case SPECIAL_KEYS: return R.drawable.v2_ic_m_special_keys;
            case FULL_KEYBOARD: return R.drawable.v2_ic_m_keyboard;
            case TOUCH_SENSITIVITY: return R.drawable.v2_ic_m_sens;
            case MENU_PILL: return R.drawable.v2_ic_m_menu;
            default: return R.drawable.v2_ic_m_custom;
        }
    }

    public static Dialog show(Activity activity, V2Theme th, String title, String host, String via, List<Tile> tiles,
                              Runnable onCancel) {
        return show(activity, th, title, host, via, tiles, onCancel, 0);
    }

    /**
     * @param focus the tile that takes the focus: 0 (Reprendre) when the menu opens, the tile a
     *              sub-dialog came from when it is closed (prototype: back returns to the tile)
     */
    public static Dialog show(Activity activity, V2Theme th, String title, String host, String via, List<Tile> tiles,
                              Runnable onCancel, int focus) {
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
        View first = focus > 0 ? tile(content, focus) : null;
        if (first == null) {
            first = content.findViewWithTag(FIRST);
        }
        if (first != null) {
            first.requestFocus();
        }
        return dialog;
    }

    private static final Object FIRST = new Object();
    private static final Object HINTS = new Object();

    /** Ludique dock: room left and right of a tile's text (prototype px) */
    private static final float DOCK_PAD = 6;
    /** Ludique dock: smallest size of a tile's label (dp) when the text shrinks to fit a row */
    private static final float DOCK_MIN_LABEL_DP = 11;

    /** The n-th tile of a built menu */
    static View tile(View v, int n) {
        Object tag = v.getTag(com.limelight.R.id.v2_menu_tile);
        if (tag instanceof Integer && (Integer) tag == n) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = tile(g.getChildAt(i), n);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

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
        // The hints bar (gamepad / keyboard) is drawn over the bottom of the screen: the grid and the
        // dock stay above it, with room for the ring of their bottom tiles (FocusRingClipTest)
        float hintsScale = l.tablet() ? l.k : Math.max(0.85f, l.k);
        int hintsReserve = V2Input.get(ctx) == V2Input.Kind.TOUCH ? 0
                : HintsBar.reservedHeight(ctx, th, hintsScale) + com.limelight.v2.ui.V2Frame.ringRoom(ctx, th, 0);

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
            wrap(where);
            head.addView(where);
            TextView t = HomeParts.text(ctx, th, l, 30, 900, th.lit.onBand, true);
            t.setText(title);
            wrap(t);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(ctx, l, 6);
            head.addView(t, tlp);
            panel.addView(head);
            panel.addView(HomeParts.ruleV(ctx, l, th.lit.bandLive), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
            // Every row as tall as the tallest (« Quitter {jeu} » and its sub-line)
            LinearLayout rows = new EqualColumn(ctx);
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
                // A label is never cut: it takes a second line, as in the prototype
                wrap(lab);
                labels.addView(lab);
                if (!tile.sub.isEmpty()) {
                    TextView sub = HomeParts.text(ctx, th, l, 13, 600, th.lit.onBand, false);
                    sub.setAlpha(0.75f);
                    sub.setText(tile.sub);
                    wrap(sub);
                    labels.addView(sub);
                }
                row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView val = HomeParts.text(ctx, th, l, 14, 600, th.lit.onBand, false);
                val.setText(tile.dim ? "" : tile.value);
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
            // A long name or a narrow screen: the pill takes two lines, nothing is cut
            wrap(t);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMarginStart(px(ctx, l, 12));
            pill.addView(t, tlp);
            TextView v = HomeParts.text(ctx, th, l, 14, 600, th.lit.muted2, false);
            v.setText(via);
            wrap(v);
            pill.addView(v, tlp(ctx, l, 12));
            pill.setMinimumHeight(px(ctx, l, 52));
            pill.setPadding(px(ctx, l, 8), px(ctx, l, 8), px(ctx, l, 22), px(ctx, l, 8));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            plp.setMargins(px(ctx, l, 48), px(ctx, l, 36), px(ctx, l, 48), 0);
            root.addView(pill, plp);
            List<TextView> labs = new ArrayList<>(), vals = new ArrayList<>();
            List<HomeParts.Button> buttons = new ArrayList<>();
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
                // « Quitter la session » (workshop) takes two lines in a 96 px tile; the tile is as
                // wide as its longest word (DockFit), so lines only break between words
                lab.setMaxLines(3);
                col.addView(lab, tlpTop(ctx, l, 6));
                TextView val = HomeParts.text(ctx, th, l, 11, 600, th.lit.muted2, false);
                val.setText(tile.value.isEmpty() && tile.danger && !tile.sub.isEmpty() ? ctx.getString(R.string.v2_menu_back_playnite) : tile.value);
                val.setGravity(Gravity.CENTER);
                val.setMaxLines(3);
                col.addView(val, tlpTop(ctx, l, 4));
                col.setPadding(px(ctx, l, DOCK_PAD), px(ctx, l, 10), px(ctx, l, DOCK_PAD), px(ctx, l, 10));
                b.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                labs.add(lab);
                vals.add(val);
                buttons.add(b);
                items.add(b);
            }
            // One row of 96 x 108 tiles in the prototype; on a narrow screen (a phone in portrait)
            // the tiles widen to their longest word, the text shrinks to a readable minimum, then the
            // dock takes a second row (DockFit: a gap of the prototype, port-notes)
            float word = 0;
            for (TextView tv : labs) {
                word = Math.max(word, longestWord(tv));
            }
            for (TextView tv : vals) {
                word = Math.max(word, longestWord(tv));
            }
            // A pixel or two of slack: a line laid out by StaticLayout can be a hair wider than measureText
            word = (float) Math.ceil(word) + Ui.dp(ctx, 2);
            float avail = Ui.dp(ctx, l.widthDp) - 2 * px(ctx, l, 12) - 2 * px(ctx, l, 16);
            float minScale = Math.min(1f, DOCK_MIN_LABEL_DP / l.t(13));
            DockFit fit = DockFit.of(tiles.size(), word, px(ctx, l, DOCK_PAD), px(ctx, l, 96), px(ctx, l, 8), avail, minScale);
            if (fit.scale < 1f) {
                for (TextView tv : labs) {
                    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, l.t(13) * fit.scale);
                }
                for (TextView tv : vals) {
                    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, l.t(11) * fit.scale);
                }
            }
            Dock dock = new Dock(ctx, fit.rows, (int) Math.ceil(fit.tile), px(ctx, l, 8), px(ctx, l, 108));
            dock.setClickable(true);
            dock.setBackground(rect(th.bg, pxf(ctx, l, 40), 0, 0));
            dock.setPadding(px(ctx, l, 16), px(ctx, l, 14), px(ctx, l, 16), px(ctx, l, 14));
            Ui.noClip(dock);
            for (HomeParts.Button tb : buttons) {
                dock.addView(tb);
            }
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dlp.bottomMargin = Math.max(px(ctx, l, 76), hintsReserve);
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
            // A long name takes a second line rather than an ellipsis
            wrap(t);
            head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView w = HomeParts.text(ctx, th, l, 14, 400, 0xFFCFC6BC, false);
            w.setText(host + " · " + via);
            wrap(w);
            head.addView(w);
            section.addView(head);
            int cols = 3;
            // Room for a tile's label and value (section width as laid out below, 3 columns, padding 18)
            int sectionW = Math.min(px(ctx, l, 740), Ui.dp(ctx, l.widthDp - 32));
            int innerW = (sectionW - 2 * px(ctx, l, 12)) / cols - 2 * px(ctx, l, 18);
            // Every row of the grid as tall as the tallest tile (« Quitter {jeu} » and its sub-line)
            LinearLayout grid = new EqualColumn(ctx);
            Ui.noClip(grid);
            section.addView(grid);
            LinearLayout row = null;
            for (int i = 0; i < tiles.size(); i++) {
                if (i % cols == 0) {
                    row = new EqualRow(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    Ui.noClip(row);
                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rlp.topMargin = px(ctx, l, i == 0 ? 16 : 12);
                    grid.addView(row, rlp);
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
                lab.setText(tile.label);
                // A label is never cut: it takes another line, as in the prototype
                wrap(lab);
                labels.addView(lab);
                if (!tile.sub.isEmpty()) {
                    TextView sub = HomeParts.text(ctx, th, l, 12, 400, th.lit.muted2, false);
                    sub.setText(tile.sub);
                    wrap(sub);
                    labels.addView(sub, tlpTop(ctx, l, 3));
                }
                TextView val = HomeParts.text(ctx, th, l, 13, 400, th.lit.muted2, false);
                // Workshop: a greyed tile (« Micro non envoyé ») says it in its label, no value
                val.setText(tile.dim ? "" : tile.value);
                if (!val.getText().toString().isEmpty()
                        && longestWord(lab) + val.getPaint().measureText(val.getText().toString()) + px(ctx, l, 8) > innerW) {
                    // A phone: « Statistiques » and « Masquées » side by side would cut the word in two,
                    // the value goes under the label
                    line.setOrientation(LinearLayout.VERTICAL);
                    line.setGravity(Gravity.START);
                    line.addView(labels, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    line.addView(val, tlpTop(ctx, l, 4));
                } else {
                    line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    line.addView(val, tlp(ctx, l, 8));
                }
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
            root.addView(section, slp);
            // Prototype: translate(-50%, -54%), i.e. 4 % of its own height above the centre; never so
            // high that the ring of the first row leaves the screen. The hints bar (bottom right) is
            // drawn over the grid where they cross: the grid goes up by as much, the title staying on
            // the screen (a phone in landscape has no room to reserve the bar's height)
            final int ring = com.limelight.v2.ui.V2Frame.ringRoom(ctx, th, 0);
            root.addOnLayoutChangeListener((v, a, b, c, d, e, f, g, h) -> {
                int top = section.getTop(), bottom = section.getBottom();
                float y = -Math.min(0.04f * (bottom - top), Math.max(0, top - ring));
                View bar = root.findViewWithTag(HINTS);
                if (bar != null && bar.getLeft() < section.getRight() + ring && bar.getRight() > section.getLeft() - ring) {
                    float over = bottom + y + ring - bar.getTop();
                    if (over > 0) {
                        y = Math.max(-top, y - over);
                    }
                }
                section.setTranslationY(y);
            });
        }
        if (!items.isEmpty()) {
            items.get(0).setTag(FIRST);
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setTag(com.limelight.R.id.v2_menu_tile, i);
        }

        // Gamepad / keyboard hints: A Valider · B Fermer le menu
        V2Input.Kind k = V2Input.get(ctx);
        if (k != V2Input.Kind.TOUCH) {
            boolean pad = k == V2Input.Kind.PAD;
            HintsBar hints = new HintsBar(ctx, th, hintsScale);
            hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_validate), null);
            hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_close_menu),
                    dialog == null ? null : dialog::cancel);
            FrameLayout.LayoutParams hlp = hints.layoutParams();
            if (af) {
                // The panel takes the right side: hints stay on the left
                hlp.gravity = Gravity.BOTTOM | Gravity.START;
            }
            hints.setTag(HINTS);
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

    /** A text that takes as many lines as it needs, never an ellipsis (a label is never cut) */
    private static void wrap(TextView t) {
        t.setMaxLines(Integer.MAX_VALUE);
        t.setEllipsize(null);
    }
    /** Width of the longest word of a label (a word is never cut in two) */
    private static float longestWord(TextView t) {
        float max = 0;
        for (String w : t.getText().toString().split("\\s+")) {
            max = Math.max(max, t.getPaint().measureText(w));
        }
        return max;
    }

    private static LinearLayout.LayoutParams tlpTop(Context ctx, HomeLayout l, float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(ctx, l, top);
        return lp;
    }

    /**
     * A row whose items all take the height of the tallest (CSS grid rows); V2Sheet uses it too. Given
     * an exact height (EqualColumn: the tallest row of the grid), its items take that height.
     */
    static final class EqualRow extends LinearLayout {
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
            if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
                max = Math.max(max, MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom());
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

    /**
     * A vertical list whose children all take the height of the tallest: the rows of the Braise grid
     * (EqualRow) and the rows of the Affiche list. Rule of the user: every tile of the in-game menu
     * has the same size, a row is never taller than another (GameMenuTileSizeTest).
     */
    static final class EqualColumn extends LinearLayout {
        EqualColumn(Context ctx) {
            super(ctx);
            setOrientation(VERTICAL);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            int max = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() != GONE) {
                    max = Math.max(max, c.getMeasuredHeight());
                }
            }
            int grow = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() != GONE && c.getMeasuredHeight() != max) {
                    grow += max - c.getMeasuredHeight();
                    c.measure(MeasureSpec.makeMeasureSpec(c.getMeasuredWidth(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY));
                }
            }
            if (grow > 0) {
                setMeasuredDimension(getMeasuredWidth(), resolveSize(getMeasuredHeight() + grow, heightSpec));
            }
        }
    }

    /**
     * The Ludique dock: rows of tiles of one width (DockFit), each row centred, every tile as tall as
     * the tallest (at least the prototype's 108 px), so a label or a value is never clipped
     */
    static final class Dock extends ViewGroup {
        private final int[] rows;
        private final int tileW, gap, minH;
        private int tileH;

        Dock(Context ctx, int[] rows, int tileW, int gap, int minH) {
            super(ctx);
            this.rows = rows;
            this.tileW = tileW;
            this.gap = gap;
            this.minH = minH;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY);
            int h = minH;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                c.measure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                h = Math.max(h, c.getMeasuredHeight());
            }
            tileH = h;
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
            int per = 0;
            for (int r : rows) {
                per = Math.max(per, r);
            }
            int width = per * tileW + Math.max(0, per - 1) * gap + getPaddingLeft() + getPaddingRight();
            int height = rows.length * h + Math.max(0, rows.length - 1) * gap + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int inner = getWidth() - getPaddingLeft() - getPaddingRight();
            int i = 0, y = getPaddingTop();
            for (int count : rows) {
                int x = getPaddingLeft() + (inner - (count * tileW + Math.max(0, count - 1) * gap)) / 2;
                for (int k = 0; k < count && i < getChildCount(); k++, i++) {
                    getChildAt(i).layout(x, y, x + tileW, y + tileH);
                    x += tileW + gap;
                }
                y += tileH + gap;
            }
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
