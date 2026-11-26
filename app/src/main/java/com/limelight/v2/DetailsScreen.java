package com.limelight.v2;

import static com.limelight.v2.HomeParts.argb;
import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The details of a game or an app before launching it (prototype « launch », planches V8-67 / V8-68,
 * design-port-spec §1.7): picture, kind, title, hours or subtitle, the note that says what the launch
 * will do, « Réglages de ce lancement » (Profil, Écran, Codec, Débit) and the launch button. One
 * layout per theme, as the prototype: Braise (picture behind a veil, floating panel), Affiche
 * (picture band, black panel on the right), Ludique (one big card, « Comment tu veux jouer ? »).
 * Built from plain data ({@link State}); the activity does the rest (DetailsActivity).
 */
public final class DetailsScreen {
    public interface Actions {
        void back();

        /** Row 0..3 stepped by d (-1 / +1) */
        void change(int row, int d);

        void go();
    }

    public static final class State {
        public String kind = "", where = "", title = "", sub = "", note = "", goLabel = "";
        public boolean warn;
        public final String[] values = {"", "", "", ""};
        public Bitmap cover, background;
        /** The background is the cover (no background image): blurred, as the prototype */
        public boolean blurBackground;
    }

    private static final int ROWS = 4;

    private final Context ctx;
    private final V2Theme th;
    private final HomeLayout l;
    private final Actions actions;
    private final FrameLayout root;
    private final boolean af, lu;

    private final CoverView background;
    private CoverView cover;
    private TextView kindText, titleText, subText, noteText, goText;
    private final List<HomeParts.Button> rows = new ArrayList<>();
    private final TextView[] valueTexts = new TextView[ROWS];
    private HomeParts.Button go;
    private final HintsBar hints;
    private State state = new State();

    public DetailsScreen(Context ctx, V2Theme th, HomeLayout l, Actions actions) {
        this.ctx = ctx;
        this.th = th;
        this.l = l;
        this.actions = actions;
        af = th.isAffiche();
        lu = th.isLudique();
        root = new FrameLayout(ctx);
        root.setBackgroundColor(th.bg);
        Ui.noClip(root);
        background = new CoverView(ctx);
        if (af) {
            buildAffiche();
        } else if (lu) {
            buildLudique();
        } else {
            buildBraise();
        }
        hints = new HintsBar(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
        root.addView(hints, hints.layoutParams());
        applyInput();
    }

    public View getView() {
        return root;
    }

    private String[] labels() {
        String[] s = {ctx.getString(R.string.v2_details_profile), ctx.getString(R.string.v2_details_screen),
                ctx.getString(R.string.v2_details_codec), ctx.getString(R.string.v2_details_bitrate)};
        if (af) {
            for (int i = 0; i < s.length; i++) {
                s[i] = s[i].toUpperCase(Locale.FRENCH);
            }
        }
        return s;
    }

    // ---- Braise ------------------------------------------------------------------------------------

    private void buildBraise() {
        boolean dark = th.dark;
        background.setSaturation(1.2f);
        root.addView(background, matchParent());
        int veil = dark ? 0xFF0E0D0C : 0xFFF6F1EA;
        root.addView(new HomeParts.GradientView(ctx, 0f,
                new int[]{argb(veil, 0.92f), argb(veil, 0.7f), argb(veil, 0.3f)}, new float[]{0f, 0.5f, 1f}), matchParent());

        LinearLayout across = new LinearLayout(ctx);
        across.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(across);
        root.addView(across, matchParent());

        // Left: cover then kind, title, hours, note (top 120, 600 wide)
        LinearLayout block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(block);
        cover = new CoverView(ctx);
        float cr = pxf(ctx, l, 14);
        cover.setRadii(cr, cr, cr, cr);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            cover.setElevation(pxf(ctx, l, 12));
        }
        block.addView(cover, new LinearLayout.LayoutParams(px(ctx, l, 150), px(ctx, l, 225)));
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(ctx, l, 24));
        block.addView(text, tlp);
        kindText = HomeParts.text(ctx, th, l, 14, 700, dark ? 0xFFFF8A57 : 0xFFC9480F, false);
        text.addView(kindText);
        titleText = HomeParts.text(ctx, th, l, 48, 700, th.fg, true);
        titleText.setLetterSpacing(-0.02f);
        titleText.setMaxLines(2);
        HomeParts.lineHeight(titleText, ctx, l, 48, 1.02f);
        text.addView(titleText, topMargin(10));
        HomeParts.cssLine(titleText, 1.02f);
        subText = HomeParts.text(ctx, th, l, 16, 400, dark ? 0xFFE7E0D8 : 0xFF3E3731, false);
        text.addView(subText, topMargin(10));
        noteText = HomeParts.text(ctx, th, l, 14, 400, th.fg, false);
        noteText.setMaxLines(6);
        HomeParts.lineHeight(noteText, ctx, l, 14, 1.5f);
        noteText.setPadding(px(ctx, l, 16), px(ctx, l, 14), px(ctx, l, 16), px(ctx, l, 14));
        text.addView(noteText, topMargin(20));

        FrameLayout left = new FrameLayout(ctx);
        Ui.noClip(left);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(px(ctx, l, 600), ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = px(ctx, l, 120);
        blp.setMarginStart(px(ctx, l, 40));
        blp.setMarginEnd(px(ctx, l, 40));
        left.addView(block, blp);
        across.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Right: the floating panel (right 40, top 96, 470 wide)
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rect(dark ? 0xF01A1816 : 0xF0FFFFFF, pxf(ctx, l, 26), 0, 0));
        int p22 = px(ctx, l, 22);
        panel.setPadding(p22, p22, p22, p22);
        Ui.noClip(panel);
        TextView head = HomeParts.text(ctx, th, l, 18, 700, th.fg, true);
        head.setText(R.string.v2_details_settings);
        head.setPadding(px(ctx, l, 6), 0, px(ctx, l, 6), px(ctx, l, 6));
        panel.addView(head);
        String[] labels = labels();
        for (int i = 0; i < ROWS; i++) {
            HomeParts.Button row = new HomeParts.Button(ctx, th);
            row.setBackground(rect(dark ? 0xFF24201D : 0xFFEFE7DD, pxf(ctx, l, 16), 0, 0));
            row.radius(l.b(16));
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(px(ctx, l, 16), 0, px(ctx, l, 8), 0);
            TextView lab = HomeParts.text(ctx, th, l, 13, 600, dark ? 0xFFCFC6BC : 0xFF6B6158, false);
            lab.setText(labels[i]);
            line.addView(lab, new LinearLayout.LayoutParams(px(ctx, l, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            int cb = dark ? 0xFF34302C : 0xFFE2D8CC;
            line.addView(stepButton(i, -1, 36, rect(cb, pxf(ctx, l, 12), 0, 0), th.fg, 600, 16), gapStart(36, 8));
            TextView val = HomeParts.text(ctx, th, l, 14, 600, th.fg, false);
            val.setGravity(Gravity.CENTER);
            val.setMaxLines(2);
            HomeParts.lineHeight(val, ctx, l, 14, 1.3f);
            valueTexts[i] = val;
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            vlp.setMarginStart(px(ctx, l, 8));
            line.addView(val, vlp);
            line.addView(stepButton(i, 1, 36, rect(cb, pxf(ctx, l, 12), 0, 0), th.fg, 600, 16), gapStart(36, 8));
            row.addView(line, matchParent());
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 66));
            rlp.topMargin = px(ctx, l, 10);
            panel.addView(row, rlp);
            addRow(row, i);
        }
        go = new HomeParts.Button(ctx, th);
        go.setBackground(rect(0xFFFF6A2B, pxf(ctx, l, 18), 0, 0));
        go.radius(l.b(18));
        goText = HomeParts.text(ctx, th, l, 19, 700, 0xFF0E0D0C, false);
        LinearLayout gl = HomeParts.iconLabel(ctx, l, R.drawable.v2_ic_p_play, 18, 10, goText, 0xFF0E0D0C);
        go.addView(gl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 64));
        glp.topMargin = px(ctx, l, 18);
        panel.addView(go, glp);
        go.setOnClickListener(v -> actions.go());

        FrameLayout right = new FrameLayout(ctx);
        Ui.noClip(right);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = px(ctx, l, 96);
        right.addView(panel, plp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(px(ctx, l, 470), ViewGroup.LayoutParams.MATCH_PARENT);
        rp.setMarginEnd(px(ctx, l, 40));
        across.addView(right, rp);

        // Back (top 24, left 40, 44 x 44, r14)
        HomeParts.Button back = new HomeParts.Button(ctx, th);
        back.setFocusable(false);
        back.setBackground(rect(dark ? 0xD91A1816 : 0xD9FFFFFF, pxf(ctx, l, 14), 0, 0));
        int is = px(ctx, l, 20);
        back.addView(HomeParts.icon(ctx, R.drawable.v2_ic_p_chevron_left, dark ? 0xFFF3EEE8 : 0xFF1E1A16),
                new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        FrameLayout.LayoutParams bk = new FrameLayout.LayoutParams(Math.max(px(ctx, l, 44), Ui.dp(ctx, 40)), Math.max(px(ctx, l, 44), Ui.dp(ctx, 40)));
        bk.topMargin = px(ctx, l, 24);
        bk.setMarginStart(px(ctx, l, 40));
        back.setContentDescription(ctx.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        root.addView(back, bk);
    }

    // ---- Affiche ----------------------------------------------------------------------------------

    private void buildAffiche() {
        boolean dark = th.dark;
        int ink = dark ? 0xFFEDEDE8 : 0xFF111111;
        LinearLayout across = new LinearLayout(ctx);
        across.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(across, matchParent());

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(left);
        across.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        FrameLayout band = new FrameLayout(ctx);
        band.setBackgroundColor(ink);
        band.addView(background, matchParent());
        HomeParts.Button back = new HomeParts.Button(ctx, th);
        back.setFocusable(false);
        back.setBackgroundColor(dark ? 0xFF141414 : 0xFFECECE8);
        TextView bt = HomeParts.text(ctx, th, l, 14, 800, ink, false);
        bt.setText(ctx.getString(R.string.v2_details_back_arrow));
        bt.setPadding(px(ctx, l, 12), px(ctx, l, 8), px(ctx, l, 12), px(ctx, l, 8));
        back.addView(bt);
        FrameLayout.LayoutParams bk = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bk.topMargin = px(ctx, l, 20);
        bk.setMarginStart(px(ctx, l, 24));
        back.setOnClickListener(v -> actions.back());
        band.addView(back, bk);
        left.addView(band, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 250)));
        View rule = new View(ctx);
        rule.setBackgroundColor(ink);
        left.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));

        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(px(ctx, l, 32), px(ctx, l, 24), px(ctx, l, 32), px(ctx, l, 24));
        left.addView(text);
        kindText = HomeParts.text(ctx, th, l, 14, 800, th.fg, false);
        text.addView(kindText);
        titleText = HomeParts.text(ctx, th, l, 64, 900, th.fg, true);
        titleText.setLetterSpacing(-0.05f);
        titleText.setMaxLines(2);
        HomeParts.lineHeight(titleText, ctx, l, 64, 0.92f);
        text.addView(titleText, topMargin(12));
        HomeParts.cssLine(titleText, 0.92f);
        subText = HomeParts.text(ctx, th, l, 16, 400, dark ? 0xFFA3A39C : 0xFF555550, false);
        text.addView(subText, topMargin(12));
        noteText = HomeParts.text(ctx, th, l, 15, 600, th.fg, false);
        noteText.setMaxLines(6);
        HomeParts.lineHeight(noteText, ctx, l, 15, 1.45f);
        text.addView(noteText, topMargin(18));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pbg = dark ? 0xFFEDEDE8 : 0xFF111111, pfg = dark ? 0xFF141414 : 0xFFECECE8;
        panel.setBackgroundColor(pbg);
        panel.setPadding(0, px(ctx, l, 28), 0, px(ctx, l, 28));
        Ui.noClip(panel);
        across.addView(panel, new LinearLayout.LayoutParams(px(ctx, l, 520), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView head = HomeParts.text(ctx, th, l, 14, 800, dark ? 0xFF7A6000 : 0xFFFFD60A, false);
        head.setText(ctx.getString(R.string.v2_details_settings).toUpperCase(Locale.FRENCH));
        head.setPadding(px(ctx, l, 32), 0, px(ctx, l, 32), px(ctx, l, 14));
        panel.addView(head);
        String[] labels = labels();
        int line = dark ? 0xFFCFCFC9 : 0xFF333330;
        for (int i = 0; i < ROWS; i++) {
            View top = new View(ctx);
            top.setBackgroundColor(line);
            panel.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(ctx, l, 1))));
            HomeParts.Button row = new HomeParts.Button(ctx, th);
            row.setBackgroundColor(pbg);
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(ctx, l, 32), px(ctx, l, 14), px(ctx, l, 32), px(ctx, l, 14));
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView lab = HomeParts.text(ctx, th, l, 12, 800, pfg, false);
            lab.setLetterSpacing(0.06f);
            lab.setText(labels[i]);
            col.addView(lab);
            TextView val = HomeParts.text(ctx, th, l, 19, 800, pfg, false);
            val.setLetterSpacing(-0.01f);
            valueTexts[i] = val;
            col.addView(val, topMargin(4));
            r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(stepButton(i, -1, 36, rect(Color.TRANSPARENT, 0, px(ctx, l, 2), pfg), pfg, 900, 16), gapStart(36, 12));
            r.addView(stepButton(i, 1, 36, rect(Color.TRANSPARENT, 0, px(ctx, l, 2), pfg), pfg, 900, 16), gapStart(36, 12));
            row.addView(r, matchParent());
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addRow(row, i);
        }
        panel.addView(new Space(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        go = new HomeParts.Button(ctx, th);
        go.setBackgroundColor(0xFFFFD60A);
        go.setFocusOutline(true);
        LinearLayout gl = new LinearLayout(ctx);
        gl.setOrientation(LinearLayout.HORIZONTAL);
        gl.setGravity(Gravity.CENTER_VERTICAL);
        gl.setPadding(px(ctx, l, 22), 0, px(ctx, l, 22), 0);
        goText = HomeParts.text(ctx, th, l, 26, 900, 0xFF111111, false);
        gl.addView(goText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = HomeParts.text(ctx, th, l, 26, 900, 0xFF111111, false);
        arrow.setText("→");
        gl.addView(arrow);
        go.addView(gl, matchParent());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 72));
        glp.setMarginStart(px(ctx, l, 32));
        glp.setMarginEnd(px(ctx, l, 32));
        panel.addView(go, glp);
        go.setOnClickListener(v -> actions.go());
    }

    // ---- Ludique ----------------------------------------------------------------------------------

    private void buildLudique() {
        boolean dark = th.dark;
        int ink = dark ? 0xFFF3EEFF : 0xFF1B1530;
        int white = dark ? 0xFF221C3D : 0xFFFFFFFF;
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(rect(white, pxf(ctx, l, 44), 0, 0));
        HomeParts.setClipToOutline21(card, pxf(ctx, l, 44));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.setMargins(px(ctx, l, 48), px(ctx, l, 40), px(ctx, l, 48), px(ctx, l, 90));
        root.addView(card, clp);

        FrameLayout pic = new FrameLayout(ctx);
        pic.setBackgroundColor(0xFF1B1530);
        pic.addView(background, matchParent());
        int veil = dark ? 0xFF000000 : 0xFF1B1530;
        pic.addView(new HomeParts.GradientView(ctx, 180f, new int[]{argb(veil, 0f), argb(veil, 0f), argb(veil, 0.88f)},
                new float[]{0f, 0.4f, 1f}), matchParent());
        HomeParts.Button back = new HomeParts.Button(ctx, th);
        back.setFocusable(false);
        int bs = Math.max(px(ctx, l, 52), Ui.dp(ctx, 40));
        back.setBackground(rect(white, bs / 2f, 0, 0));
        int is = px(ctx, l, 22);
        back.addView(HomeParts.icon(ctx, R.drawable.v2_ic_p_chevron_left, ink), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        FrameLayout.LayoutParams bk = new FrameLayout.LayoutParams(bs, bs);
        bk.topMargin = px(ctx, l, 20);
        bk.setMarginStart(px(ctx, l, 20));
        back.setContentDescription(ctx.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        pic.addView(back, bk);
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        kindText = HomeParts.text(ctx, th, l, 13, 800, 0xFF1B1530, false);
        kindText.setGravity(Gravity.CENTER_VERTICAL);
        kindText.setPadding(px(ctx, l, 12), 0, px(ctx, l, 12), 0);
        kindText.setBackground(rect(0xFFC6F432, pxf(ctx, l, 14), 0, 0));
        text.addView(kindText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 28)));
        titleText = HomeParts.text(ctx, th, l, 40, 800, 0xFFFFFFFF, true);
        titleText.setLetterSpacing(-0.03f);
        titleText.setMaxLines(2);
        HomeParts.lineHeight(titleText, ctx, l, 40, 1f);
        text.addView(titleText, topMargin(6));
        HomeParts.cssLine(titleText, 1f);
        subText = HomeParts.text(ctx, th, l, 15, 400, dark ? 0xFFD9D2F5 : 0xFFE4DEFF, false);
        text.addView(subText, topMargin(6));
        FrameLayout.LayoutParams txp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int m28 = px(ctx, l, 28);
        txp.setMargins(m28, 0, m28, m28);
        pic.addView(text, txp);
        card.addView(pic, new LinearLayout.LayoutParams(px(ctx, l, 480), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout side = new LinearLayout(ctx);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(px(ctx, l, 34), px(ctx, l, 30), px(ctx, l, 34), px(ctx, l, 30));
        Ui.noClip(side);
        card.addView(side, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView head = HomeParts.text(ctx, th, l, 22, 800, th.fg, false);
        head.setLetterSpacing(-0.02f);
        head.setText(R.string.v2_details_how);
        side.addView(head);
        String[] labels = labels();
        for (int i = 0; i < ROWS; i++) {
            HomeParts.Button row = new HomeParts.Button(ctx, th);
            row.setBackground(rect(dark ? 0xFF15112A : 0xFFF3EEFF, pxf(ctx, l, 32), 0, 0));
            row.radius(l.b(32));
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(px(ctx, l, 24), 0, px(ctx, l, 8), 0);
            TextView lab = HomeParts.text(ctx, th, l, 14, 700, dark ? 0xFFB3A9D6 : 0xFF5A527A, false);
            lab.setText(labels[i]);
            line.addView(lab, new LinearLayout.LayoutParams(px(ctx, l, 70), ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(stepButton(i, -1, 48, null, ink, 800, 20), gapStart(48, 10));
            TextView val = HomeParts.text(ctx, th, l, 16, 700, th.fg, false);
            val.setGravity(Gravity.CENTER);
            valueTexts[i] = val;
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            vlp.setMarginStart(px(ctx, l, 10));
            line.addView(val, vlp);
            line.addView(stepButton(i, 1, 48, null, ink, 800, 20), gapStart(48, 10));
            row.addView(line, matchParent());
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 64));
            rlp.topMargin = px(ctx, l, 12);
            side.addView(row, rlp);
            addRow(row, i);
        }
        noteText = HomeParts.text(ctx, th, l, 15, 600, ink, false);
        noteText.setMaxLines(5);
        HomeParts.lineHeight(noteText, ctx, l, 15, 1.4f);
        noteText.setPadding(px(ctx, l, 20), px(ctx, l, 14), px(ctx, l, 20), px(ctx, l, 14));
        side.addView(noteText, topMargin(16));
        side.addView(new Space(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        go = new HomeParts.Button(ctx, th);
        go.setBackground(rect(dark ? 0xFF8C77FF : 0xFF5B3DF5, pxf(ctx, l, 36), 0, 0));
        go.radius(l.b(36));
        LinearLayout gl = new LinearLayout(ctx);
        gl.setOrientation(LinearLayout.HORIZONTAL);
        gl.setGravity(Gravity.CENTER);
        TextView a = HomeParts.text(ctx, th, l, 15, 800, 0xFF1B1530, false);
        a.setText("A");
        a.setGravity(Gravity.CENTER);
        int ds = px(ctx, l, 34);
        a.setBackground(rect(0xFFC6F432, ds / 2f, 0, 0));
        gl.addView(a, new LinearLayout.LayoutParams(ds, ds));
        goText = HomeParts.text(ctx, th, l, 22, 800, dark ? 0xFF221C3D : 0xFFFFFFFF, false);
        LinearLayout.LayoutParams gtl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gtl.setMarginStart(px(ctx, l, 12));
        gl.addView(goText, gtl);
        go.addView(gl, matchParent());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 72));
        glp.topMargin = px(ctx, l, 12);
        side.addView(go, glp);
        go.setOnClickListener(v -> actions.go());
    }

    // ---- pieces -------------------------------------------------------------------------------------

    /** ‹ or › of a row: touch only (the gamepad steps with left / right on the row) */
    private View stepButton(int row, int d, float sizePx, android.graphics.drawable.Drawable bg, int fg, int weight, float textPx) {
        TextView b = HomeParts.text(ctx, th, l, textPx, weight, fg, false);
        b.setText(d < 0 ? "‹" : "›");
        b.setGravity(Gravity.CENTER);
        if (bg == null) {
            int s = px(ctx, l, sizePx);
            bg = rect(th.dark ? 0xFF221C3D : 0xFFFFFFFF, s / 2f, 0, 0);
        }
        b.setBackground(bg);
        b.setClickable(true);
        b.setContentDescription(ctx.getString(d < 0 ? R.string.v2_details_prev : R.string.v2_details_next));
        b.setOnClickListener(v -> actions.change(row, d));
        return b;
    }

    private LinearLayout.LayoutParams gapStart(float sizePx, float gapPx) {
        int s = px(ctx, l, sizePx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.setMarginStart(px(ctx, l, gapPx));
        return lp;
    }

    private void addRow(HomeParts.Button row, int i) {
        row.setOnClickListener(v -> actions.change(i, 1));
        rows.add(row);
    }

    private LinearLayout.LayoutParams topMargin(float protoPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(ctx, l, protoPx);
        return lp;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- data ---------------------------------------------------------------------------------------

    public void bind(State s) {
        state = s;
        String sep = af ? " — " : " · ";
        kindText.setText(lu ? s.kind : s.kind + sep + s.where);
        titleText.setText(s.title);
        subText.setText(s.sub);
        Ui.setVisible(subText, !TextUtils.isEmpty(s.sub));
        noteText.setText(s.note);
        Ui.setVisible(noteText, !TextUtils.isEmpty(s.note));
        styleNote(s.warn);
        for (int i = 0; i < ROWS; i++) {
            valueTexts[i].setText(s.values[i]);
        }
        goText.setText(s.goLabel);
        background.setBlur(s.blurBackground);
        background.setBitmap(s.background != null ? s.background : s.cover);
        if (cover != null) {
            cover.setBitmap(s.cover);
            Ui.setVisible(cover, s.cover != null);
        }
        updateHints();
    }

    private void styleNote(boolean warn) {
        boolean dark = th.dark;
        if (af) {
            if (warn) {
                noteText.setBackgroundColor(0xFFFFD60A);
                noteText.setTextColor(0xFF111111);
                noteText.setTypeface(th.textFont(ctx, 600));
                noteText.setPadding(px(ctx, l, 16), px(ctx, l, 14), px(ctx, l, 16), px(ctx, l, 14));
            } else {
                // A 4 px rule on the left, the text 14 px after it
                noteText.setBackground(new LeftRule(th.fg, px(ctx, l, 4)));
                noteText.setTextColor(th.fg);
                noteText.setTypeface(th.textFont(ctx, 400));
                noteText.setPadding(px(ctx, l, 18), px(ctx, l, 4), 0, px(ctx, l, 4));
            }
        } else if (lu) {
            noteText.setBackground(rect(warn ? 0xFFFF7A59 : (dark ? 0xFF2E2650 : 0xFFE4DEFF), pxf(ctx, l, 24), 0, 0));
            noteText.setTextColor(warn ? 0xFF1B1530 : (dark ? 0xFFF3EEFF : 0xFF1B1530));
            noteText.setTypeface(th.textFont(ctx, warn ? 600 : 400));
        } else {
            if (warn) {
                noteText.setBackground(rect(dark ? 0xE62A1A10 : 0xE6FBE3D4, pxf(ctx, l, 14), Math.max(1, px(ctx, l, 1)), dark ? 0xFFFF6A2B : 0xFFE8541A));
                noteText.setTextColor(dark ? 0xFFF3D9C9 : 0xFF5A2A12);
            } else {
                noteText.setBackground(rect(dark ? 0xD91A1816 : 0xD9FFFFFF, pxf(ctx, l, 14), 0, 0));
                noteText.setTextColor(dark ? 0xFFE7E0D8 : 0xFF3E3731);
            }
        }
    }

    /** Affiche's calm note: a rule down its left side (border-left: 4px solid) */
    private static final class LeftRule extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        private final int width;

        LeftRule(int color, int width) {
            paint.setColor(color);
            this.width = width;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            canvas.drawRect(b.left, b.top, b.left + width, b.bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    // ---- focus and input ----------------------------------------------------------------------------

    /** Prototype grid: [o0], [o1], [o2], [o3], [go] */
    public List<List<View>> focusRows() {
        List<List<View>> out = new ArrayList<>();
        for (View r : rows) {
            List<View> one = new ArrayList<>();
            one.add(r);
            out.add(one);
        }
        List<View> g = new ArrayList<>();
        g.add(go);
        out.add(g);
        return out;
    }

    /** Index of a settings row, -1 for anything else */
    public int rowOf(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            int i = rows.indexOf(p);
            if (i >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** The prototype starts on the launch button */
    public boolean requestInitialFocus() {
        return go.requestFocus();
    }

    public void focusRow(int i) {
        if (i >= 0 && i < rows.size()) {
            rows.get(i).requestFocus();
        }
    }

    public void refreshInput() {
        applyInput();
    }

    private void applyInput() {
        updateHints();
    }

    private void updateHints() {
        if (hints == null) {
            return;
        }
        hints.clear();
        V2Input.Kind k = V2Input.get(ctx);
        if (k == V2Input.Kind.TOUCH) {
            hints.setVisibility(View.GONE);
            return;
        }
        hints.setVisibility(View.VISIBLE);
        boolean pad = k == V2Input.Kind.PAD;
        View f = root.findFocus();
        boolean onRow = f != null && rowOf(f) >= 0;
        hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter),
                onRow ? ctx.getString(R.string.v2_details_change) : state.goLabel, () -> {
                    View cur = root.findFocus();
                    if (cur != null && rowOf(cur) >= 0) {
                        actions.change(rowOf(cur), 1);
                    } else {
                        actions.go();
                    }
                });
        hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_back), actions::back);
    }

    /** The focus moved: the A hint says « Changer » on a row, the launch label on the button */
    public void onFocusMoved() {
        updateHints();
    }
}
