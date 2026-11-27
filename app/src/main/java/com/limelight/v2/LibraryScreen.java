package com.limelight.v2;

import static com.limelight.v2.HomeParts.argb;
import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Playnite library of one PC (prototype « library », V8-22 / 23 / 60 / 62 / 72; design-port-spec
 * §1.6). One layout per theme: Braise (6-column grid, the picked game aside, its picture faint
 * behind), Affiche (numbered list with a window of 8 rows at the gamepad, black sheet on the right),
 * Ludique (8-column grid, the picked game in a bar at the bottom). Filters Tous / Récents / Installés /
 * Masqués · N (LibraryModel), LB / RB cycle them. The grid scrolls and keeps the focus in view (the
 * prototype's 15 games fit, a real library does not). Covers load as tiles show (RecyclerView).
 */
public final class LibraryScreen {
    public interface Actions {
        void back();

        void launch(NvApp app);

        void options(NvApp app);
    }

    private final Context ctx;
    private final V2Theme th;
    private final HomeLayout l;
    private final Actions actions;
    private final CoverSource covers;
    private final boolean af, lu;
    private final int cols;

    private final FrameLayout root;
    private final RecyclerView grid;
    private final Adapter adapter = new Adapter();
    private final HintsBar hints;
    private final LinearLayout filterRow;
    private TextView countText, lbText, rbText;
    private CoverView backImage, pickArt, pickCover;
    private TextView pickName, pickState, pickHelp;
    private View pickPanel;
    private final View emptyCard;
    private final HomeParts.Button emptyBack;

    private ComputerDetails host;
    private HomeModel model;
    private long nowSeconds = System.currentTimeMillis() / 1000;
    private List<LibraryModel.Filter> filters = new ArrayList<>();
    private int filter;
    private List<NvApp> list = Collections.emptyList();
    /** Index of the picked (focused) game in the list */
    private int focus;
    /** Affiche at the gamepad / keyboard: first row of the window of 8 */
    private int windowStart;

    public LibraryScreen(Context ctx, V2Theme th, HomeLayout l, Actions actions, CoverSource covers) {
        this.ctx = ctx;
        this.th = th;
        this.l = l;
        this.actions = actions;
        this.covers = covers;
        af = th.isAffiche();
        lu = th.isLudique();
        cols = af ? 1 : (lu ? 8 : 6);
        root = new FrameLayout(ctx);
        root.setBackgroundColor(th.bg);

        grid = new RecyclerView(ctx);
        grid.setItemAnimator(null);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        filterRow = new LinearLayout(ctx);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(af ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);

        if (af) {
            buildAffiche();
        } else if (lu) {
            buildLudique();
        } else {
            buildBraise();
        }
        grid.setAdapter(adapter);

        // Empty library (or empty filter): the prototype's dashed card
        LinearLayout empty = new LinearLayout(ctx);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(HomeParts.dashed(th.bg, af ? 0 : Ui.dpf(ctx, th.r), Math.max(1, px(ctx, l, 2)), th.muted,
                pxf(ctx, l, 6), pxf(ctx, l, 5)));
        int p40 = px(ctx, l, 40);
        empty.setPadding(p40, p40, p40, p40);
        Ui.noClip(empty);
        int is = px(ctx, l, 44);
        empty.addView(HomeParts.icon(ctx, R.drawable.v2_ic_grid, th.fg), new LinearLayout.LayoutParams(is, is));
        TextView et = HomeParts.text(ctx, th, l, 30, th.displayWeight, th.fg, true);
        et.setText(th.displayText(ctx.getString(R.string.v2_lib_empty_title)));
        et.setGravity(Gravity.CENTER);
        empty.addView(et, gap(12));
        TextView ed = HomeParts.text(ctx, th, l, 16, 400, th.muted, false);
        ed.setMaxLines(4);
        ed.setGravity(Gravity.CENTER);
        HomeParts.lineHeight(ed, ctx, l, 16, 1.5f);
        ed.setText(R.string.v2_lib_empty_text);
        LinearLayout.LayoutParams edl = new LinearLayout.LayoutParams(Math.min(px(ctx, l, 620), Ui.dp(ctx, l.widthDp - 200)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        edl.topMargin = px(ctx, l, 12);
        empty.addView(ed, edl);
        emptyBack = new HomeParts.Button(ctx, th);
        float pill = af ? 0 : px(ctx, l, 23);
        emptyBack.setBackground(rect(th.fg, pill, 0, 0));
        emptyBack.radius(af ? 0 : l.b(23));
        TextView eb = HomeParts.text(ctx, th, l, 15, 700, th.bg, false);
        eb.setText(R.string.v2_lib_empty_back);
        eb.setGravity(Gravity.CENTER);
        eb.setPadding(px(ctx, l, 22), 0, px(ctx, l, 22), 0);
        emptyBack.addView(eb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyBack.setOnClickListener(v -> actions.back());
        LinearLayout.LayoutParams ebl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 46));
        ebl.topMargin = px(ctx, l, 20);
        empty.addView(emptyBack, ebl);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        elp.setMargins(px(ctx, l, 48), px(ctx, l, 150), px(ctx, l, 48), px(ctx, l, 70));
        root.addView(empty, elp);
        emptyCard = empty;
        empty.setVisibility(View.GONE);

        hints = new HintsBar(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
        root.addView(hints, hints.layoutParams());
    }

    public View getView() {
        return root;
    }

    // ---- Braise ------------------------------------------------------------------------------------

    private void buildBraise() {
        boolean dark = th.dark;
        backImage = new CoverView(ctx);
        backImage.setAlpha(0.22f);
        root.addView(backImage, match());
        int veil = dark ? 0xFF0E0D0C : 0xFFF6F1EA;
        root.addView(new HomeParts.GradientView(ctx, 0f, new int[]{veil, argb(veil, 0.86f), argb(veil, 0.6f)},
                new float[]{0f, 0.62f, 1f}), match());

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(backButton(44, rect(dark ? 0xFF1A1816 : 0xFFFFFFFF, pxf(ctx, l, 14), 0, 0), dark ? 0xFFF3EEE8 : 0xFF1E1A16, 20),
                new LinearLayout.LayoutParams(px(ctx, l, 44), px(ctx, l, 44)));
        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = HomeParts.text(ctx, th, l, 24, 700, th.fg, true);
        title.setText(R.string.v2_library);
        titles.addView(title);
        countText = HomeParts.text(ctx, th, l, 13, 400, dark ? 0xFFCFC6BC : 0xFF6B6158, false);
        titles.addView(countText);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(px(ctx, l, 16));
        header.addView(titles, tl);
        header.addView(filterRow);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMargins(px(ctx, l, 40), px(ctx, l, 24), px(ctx, l, 40), 0);
        root.addView(header, hl);

        int p = ringPad();
        // The last row scrolls clear of the hints bar (a phone: the prototype's 70 px were less than the bar)
        grid.setPadding(p, p, p, Math.max(px(ctx, l, 70), hintsReserve() + p));
        grid.setLayoutManager(new GridLayoutManager(ctx, cols));
        grid.addItemDecoration(new Gaps(px(ctx, l, 16)));
        // Never wider than the screen (a phone in portrait): the last column stayed off the edge
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(Math.min(px(ctx, l, 780) + 2 * p,
                Ui.dp(ctx, l.widthDp) - 2 * (px(ctx, l, 40) - p)), ViewGroup.LayoutParams.MATCH_PARENT);
        gl.setMargins(px(ctx, l, 40) - p, px(ctx, l, 100) - p, 0, 0);
        root.addView(grid, gl);

        LinearLayout aside = new LinearLayout(ctx);
        aside.setOrientation(LinearLayout.VERTICAL);
        FrameLayout art = new FrameLayout(ctx);
        art.setBackground(rect(dark ? 0xFF1A1816 : 0xFFFFFFFF, pxf(ctx, l, 18), 0, 0));
        HomeParts.setClipToOutline21(art, pxf(ctx, l, 18));
        pickArt = new CoverView(ctx);
        float r = pxf(ctx, l, 18);
        pickArt.setRadii(r, r, r, r);
        art.addView(pickArt, match());
        aside.addView(art, new LinearLayout.LayoutParams(px(ctx, l, 380), px(ctx, l, 214)));
        pickName = HomeParts.text(ctx, th, l, 30, 700, th.fg, true);
        pickName.setMaxLines(2);
        HomeParts.lineHeight(pickName, ctx, l, 30, 1.1f);
        aside.addView(pickName, gap(14));
        int muted = dark ? 0xFFCFC6BC : 0xFF6B6158;
        pickState = HomeParts.text(ctx, th, l, 15, 400, muted, false);
        aside.addView(pickState, gap(14));
        pickHelp = HomeParts.text(ctx, th, l, 14, 400, muted, false);
        pickHelp.setMaxLines(4);
        HomeParts.lineHeight(pickHelp, ctx, l, 14, 1.5f);
        aside.addView(pickHelp, gap(14));
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(px(ctx, l, 380), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        al.setMargins(0, px(ctx, l, 100), px(ctx, l, 40), 0);
        root.addView(aside, al);
        pickPanel = aside;
    }

    // ---- Affiche ----------------------------------------------------------------------------------

    private void buildAffiche() {
        boolean dark = th.dark;
        int ink = dark ? 0xFFEDEDE8 : 0xFF111111;
        LinearLayout across = new LinearLayout(ctx);
        across.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(across, match());

        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        across.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        // CSS 18 / 12; Android's Archivo metrics put the title lower in its line box: measured on V8-22
        header.setPadding(px(ctx, l, 32), px(ctx, l, 22), px(ctx, l, 32), px(ctx, l, 25));
        countText = HomeParts.text(ctx, th, l, 14, 800, ink, false);
        countText.setPadding(0, px(ctx, l, 4), 0, px(ctx, l, 4));
        countText.setOnClickListener(v -> actions.back());
        header.addView(countText);
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.BOTTOM);
        TextView title = HomeParts.text(ctx, th, l, 54, 900, th.fg, true);
        title.setLetterSpacing(-0.05f);
        title.setText(R.string.v2_lib_playnite);
        line.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HomeParts.cssLine(title, 0.92f);
        filterRow.setPadding(0, 0, 0, px(ctx, l, 6));
        line.addView(filterRow);
        LinearLayout.LayoutParams linel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linel.topMargin = px(ctx, l, -3);
        header.addView(line, linel);
        // line-height .92: the descender of « Playnite » overflows its line box, as in CSS
        Ui.noClip(line, header, left);
        left.addView(header);
        View rule = new View(ctx);
        rule.setBackgroundColor(ink);
        left.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
        grid.setLayoutManager(new LinearLayoutManager(ctx));
        left.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View foot = new View(ctx);
        left.addView(foot, new LinearLayout.LayoutParams(1, px(ctx, l, 720 - 113 - 548)));

        LinearLayout aside = new LinearLayout(ctx);
        aside.setOrientation(LinearLayout.VERTICAL);
        aside.setBackgroundColor(dark ? 0xFFEDEDE8 : 0xFF111111);
        int p32 = px(ctx, l, 32);
        aside.setPadding(p32, p32, p32, p32);
        pickCover = new CoverView(ctx);
        aside.addView(pickCover, new LinearLayout.LayoutParams(px(ctx, l, 220), px(ctx, l, 330)));
        int sheetFg = dark ? 0xFF141414 : 0xFFECECE8;
        pickName = HomeParts.text(ctx, th, l, 40, 900, sheetFg, true);
        pickName.setLetterSpacing(-0.04f);
        pickName.setMaxLines(3);
        HomeParts.lineHeight(pickName, ctx, l, 40, 0.95f);
        aside.addView(pickName, gap(18));
        int muted = dark ? 0xFF4A4A46 : 0xFFBDBDB6;
        pickState = HomeParts.text(ctx, th, l, 15, 400, muted, false);
        aside.addView(pickState, gap(18));
        View helpRule = new View(ctx);
        helpRule.setBackgroundColor(dark ? 0xFFA3A39C : 0xFF55554F);
        LinearLayout.LayoutParams hr = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(ctx, l, 1)));
        hr.topMargin = px(ctx, l, 18);
        aside.addView(helpRule, hr);
        pickHelp = HomeParts.text(ctx, th, l, 14, 400, muted, false);
        pickHelp.setMaxLines(4);
        HomeParts.lineHeight(pickHelp, ctx, l, 14, 1.5f);
        aside.addView(pickHelp, gap(12));
        across.addView(aside, new LinearLayout.LayoutParams(px(ctx, l, 500), ViewGroup.LayoutParams.MATCH_PARENT));
        pickPanel = aside;
    }

    // ---- Ludique ----------------------------------------------------------------------------------

    private void buildLudique() {
        boolean dark = th.dark;
        int ink = dark ? 0xFFF3EEFF : 0xFF1B1530;
        int card = dark ? 0xFF221C3D : 0xFFFFFFFF;
        int muted = dark ? 0xFFB3A9D6 : 0xFF5A527A;
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int bs = px(ctx, l, 56);
        header.addView(backButton(56, rect(card, bs / 2f, 0, 0), ink, 22), new LinearLayout.LayoutParams(bs, bs));
        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = HomeParts.text(ctx, th, l, 32, 800, th.fg, false);
        title.setLetterSpacing(-0.03f);
        title.setText(R.string.v2_library);
        titles.addView(title);
        HomeParts.cssLine(title, 1f);
        countText = HomeParts.text(ctx, th, l, 14, 600, muted, false);
        titles.addView(countText);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMarginStart(px(ctx, l, 16));
        header.addView(titles, tl);
        lbText = HomeParts.text(ctx, th, l, 12, 800, muted, false);
        header.addView(lbText);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.setMarginStart(px(ctx, l, 16));
        header.addView(filterRow, fl);
        rbText = HomeParts.text(ctx, th, l, 12, 800, muted, false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.setMarginStart(px(ctx, l, 16));
        header.addView(rbText, rl);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMargins(px(ctx, l, 48), px(ctx, l, 26), px(ctx, l, 48), 0);
        root.addView(header, hl);

        int p = ringPad();
        grid.setPadding(p, p, p, p);
        grid.setLayoutManager(new GridLayoutManager(ctx, cols));
        grid.addItemDecoration(new Gaps(px(ctx, l, 18)));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // Portrait: the pick bar (bottom left) and the hints bar (bottom right) do not fit side by side:
        // the pick bar goes above the hints, the grid above both
        int lift = l.portrait ? hintsReserve() : 0;
        gl.setMargins(px(ctx, l, 48) - p, px(ctx, l, 112) - p, px(ctx, l, 48) - p, px(ctx, l, 116) - p + lift);
        root.addView(grid, gl);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(rect(dark ? 0xFFF3EEFF : 0xFF1B1530, pxf(ctx, l, 38), 0, 0));
        bar.setPadding(px(ctx, l, 10), 0, px(ctx, l, 28), 0);
        pickCover = new CoverView(ctx);
        float cr = pxf(ctx, l, 10);
        pickCover.setRadii(cr, cr, cr, cr);
        bar.addView(pickCover, new LinearLayout.LayoutParams(px(ctx, l, 40), px(ctx, l, 60)));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        int barFg = dark ? 0xFF221C3D : 0xFFFFFFFF;
        pickName = HomeParts.text(ctx, th, l, 19, 800, barFg, false);
        texts.addView(pickName);
        pickState = HomeParts.text(ctx, th, l, 13, 400, dark ? 0xFF5A527A : 0xFFD9D2F5, false);
        texts.addView(pickState);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xl.setMarginStart(px(ctx, l, 16));
        bar.addView(texts, xl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 76), Gravity.BOTTOM | Gravity.START);
        bl.setMargins(px(ctx, l, 48), 0, 0, px(ctx, l, 28) + lift);
        root.addView(bar, bl);
        pickPanel = bar;
    }

    // ---- pieces -------------------------------------------------------------------------------------

    private View backButton(float sizePx, android.graphics.drawable.Drawable bg, int fg, float iconPx) {
        HomeParts.Button b = new HomeParts.Button(ctx, th);
        b.setFocusable(false);
        b.setBackground(bg);
        int is = px(ctx, l, iconPx);
        b.addView(HomeParts.icon(ctx, R.drawable.v2_ic_p_chevron_left, fg), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        b.setContentDescription(ctx.getString(R.string.v2_back));
        b.setOnClickListener(v -> actions.back());
        return b;
    }

    /**
     * Padding of the grid, which its parent clips at its bounds: the covers scroll into it
     * (clipToPadding off) and the focus ring of a cover of the first or last row or column fits in
     * it (V2Frame.ringRoom; the prototype's 12 px were too little at 0.6 on a phone). The grid is
     * moved out by as much, so the covers stay where they were.
     */
    /** Height of the hints bar at the bottom of the screen, its margin included */
    private int hintsReserve() {
        return HintsBar.reservedHeight(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
    }

    private int ringPad() {
        return Math.max(px(ctx, l, 12), com.limelight.v2.ui.V2Frame.ringRoom(ctx, th, px(ctx, l, 240)));
    }

    private LinearLayout.LayoutParams gap(float protoPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(ctx, l, protoPx);
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** Grid gaps: equal columns, a gap between rows (CSS grid gap) */
    private final class Gaps extends RecyclerView.ItemDecoration {
        private final int gap;

        Gaps(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            if (pos < 0) {
                return;
            }
            int col = pos % cols;
            out.left = col * gap / cols;
            out.right = gap - (col + 1) * gap / cols;
            out.top = pos >= cols ? gap : 0;
        }
    }

    private void bindFilters() {
        filterRow.removeAllViews();
        boolean dark = th.dark;
        for (int i = 0; i < filters.size(); i++) {
            final int index = i;
            boolean on = i == filter;
            TextView f;
            if (af) {
                f = HomeParts.text(ctx, th, l, 16, on ? 900 : 600, on ? th.fg : (dark ? 0xFFA3A39C : 0xFF555550), false);
                f.setPadding(0, px(ctx, l, 2), 0, px(ctx, l, 2));
                f.setBackground(new Underline(on ? th.fg : Color.TRANSPARENT, px(ctx, l, 4)));
            } else if (lu) {
                f = HomeParts.text(ctx, th, l, 16, on ? 700 : 600, on ? (dark ? 0xFF221C3D : 0xFFFFFFFF) : (dark ? 0xFFF3EEFF : 0xFF1B1530), false);
                f.setGravity(Gravity.CENTER);
                f.setPadding(px(ctx, l, 22), 0, px(ctx, l, 22), 0);
                f.setBackground(rect(on ? (dark ? 0xFFF3EEFF : 0xFF1B1530) : (dark ? 0xFF221C3D : 0xFFFFFFFF), pxf(ctx, l, 23), 0, 0));
            } else {
                f = HomeParts.text(ctx, th, l, 14, on ? 700 : 400, on ? (dark ? 0xFF0E0D0C : 0xFFF6F1EA) : (dark ? 0xFFCFC6BC : 0xFF6B6158), false);
                f.setGravity(Gravity.CENTER);
                f.setPadding(px(ctx, l, 18), 0, px(ctx, l, 18), 0);
                f.setBackground(on ? rect(dark ? 0xFFF3EEE8 : 0xFF1E1A16, pxf(ctx, l, 20), 0, 0)
                        : rect(Color.TRANSPARENT, pxf(ctx, l, 20), Math.max(1, px(ctx, l, 1)), dark ? 0xFF3A342F : 0xFFDCD1C4));
            }
            f.setText(filterLabel(filters.get(i)));
            f.setClickable(true);
            f.setOnClickListener(v -> setFilter(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    af ? ViewGroup.LayoutParams.WRAP_CONTENT : px(ctx, l, lu ? 46 : 40));
            if (i > 0) {
                lp.setMarginStart(px(ctx, l, af ? 18 : (lu ? 16 : 16)));
            }
            filterRow.addView(f, lp);
        }
    }

    private String filterLabel(LibraryModel.Filter f) {
        switch (f) {
            case RECENT:
                return ctx.getString(R.string.v2_lib_filter_recent);
            case INSTALLED:
                return ctx.getString(R.string.v2_lib_filter_installed);
            case HIDDEN:
                return ctx.getString(R.string.v2_lib_filter_hidden, model == null ? 0 : model.hiddenCount);
            default:
                return ctx.getString(R.string.v2_lib_filter_all);
        }
    }

    /** Affiche's tab underline (border-bottom: 4px) */
    private static final class Underline extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        private final int h;

        Underline(int color, int h) {
            paint.setColor(color);
            this.h = h;
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas c) {
            Rect b = getBounds();
            c.drawRect(b.left, b.bottom - h, b.right, b.bottom, paint);
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

    // ---- data ---------------------------------------------------------------------------------------

    /** The library of a PC; nowSeconds dates « joué hier » (the device's clock, tests a fixed one) */
    public void bind(ComputerDetails host, HomeModel model, long nowSeconds) {
        this.host = host;
        this.model = model;
        this.nowSeconds = nowSeconds;
        filters = LibraryModel.filters(model);
        filter = LibraryModel.clamp(filter, filters.size());
        bindFilters();
        reloadList();
    }

    /** The count line, which follows the filter (desktop countLine); Affiche in capitals, behind its arrow */
    private void bindCount() {
        String name = host == null || host.name == null ? "" : host.name;
        LibraryModel.Count c = LibraryModel.count(model,
                filters.isEmpty() ? LibraryModel.Filter.ALL : filters.get(filter), list);
        String line = countLine(ctx.getResources(), name, c);
        countText.setText(af ? ctx.getString(R.string.v2_lib_back, line).toUpperCase(Locale.FRENCH) : line);
    }

    /** « PC · aucun jeu », « PC · N jeu(x) », « PC · N affiché(s) sur M », « PC · N jeu(x) masqué(s) » (LibraryModelTest) */
    static String countLine(android.content.res.Resources res, String name, LibraryModel.Count c) {
        switch (c.kind) {
            case HIDDEN:
                // French plurals take 0 as « one »: « aucun jeu masqué » has its own string
                return c.shown == 0 ? res.getString(R.string.v2_lib_count_hidden_none, name)
                        : res.getQuantityString(R.plurals.v2_lib_count_hidden, c.shown, name, c.shown);
            case NONE:
                return res.getString(R.string.v2_lib_count_none, name);
            case GAMES:
                return res.getQuantityString(R.plurals.v2_lib_count_games, c.shown, name, c.shown);
            default:
                return res.getQuantityString(R.plurals.v2_lib_count_shown, c.shown, name, c.shown, c.total);
        }
    }

    private void reloadList() {
        list = filters.isEmpty() ? Collections.<NvApp>emptyList() : LibraryModel.list(model, filters.get(filter));
        focus = Math.max(0, Math.min(focus, list.size() - 1));
        windowStart = windowed() ? LibraryModel.windowStart(focus, list.size()) : 0;
        adapter.notifyDataSetChanged();
        bindCount();
        boolean empty = list.isEmpty();
        Ui.setVisible(emptyCard, empty && model != null);
        Ui.setVisible(grid, !empty);
        Ui.setVisible(pickPanel, !empty);
        if (backImage != null) {
            Ui.setVisible(backImage, !empty);
        }
        bindPick();
        updateHints();
    }

    private boolean windowed() {
        return af && V2Input.get(ctx) != V2Input.Kind.TOUCH;
    }

    public void setFilter(int i) {
        if (filters.isEmpty()) {
            return;
        }
        filter = LibraryModel.clamp(i, filters.size());
        focus = 0;
        bindFilters();
        reloadList();
        grid.scrollToPosition(0);
        if (V2Input.showFocus(ctx)) {
            focusIndex(0);
        }
    }

    /** LB / RB (Q / E): previous / next filter, focus on the first game */
    public void shiftFilter(int d) {
        setFilter(LibraryModel.step(filter, filters.size(), d));
    }

    /** After a game was hidden or shown again: the focus goes back to the first game (prototype) */
    public void afterHideToggle() {
        focus = 0;
        grid.scrollToPosition(0);
        if (V2Input.showFocus(ctx) && !list.isEmpty()) {
            focusIndex(0);
        }
    }

    private void bindPick() {
        NvApp g = list.isEmpty() ? null : list.get(Math.min(focus, list.size() - 1));
        if (g == null) {
            return;
        }
        String sep = af ? " — " : " · ";
        pickName.setText(g.getAppName());
        StringBuilder st = new StringBuilder(HomeParts.hours(ctx, g)).append(sep);
        if (af || lu) {
            st.append(ctx.getString(g.isPlayniteInstalled() ? R.string.v2_details_installed : R.string.v2_details_not_installed));
        } else {
            st.append(ctx.getString(g.isPlayniteInstalled() ? R.string.v2_lib_state_installed : R.string.v2_lib_state_not_installed));
        }
        String ago = agoText(LibraryModel.ago(nowSeconds, g.getPlayniteLastActivity()));
        if (ago != null) {
            st.append(sep).append(ctx.getString(R.string.v2_lib_played, ago));
        }
        if (lu) {
            st.append(" · ").append(ctx.getString(R.string.v2_lib_via));
        }
        pickState.setText(st.toString());
        if (pickHelp != null) {
            String name = host == null || host.name == null ? "" : host.name;
            V2Input.Kind k = V2Input.get(ctx);
            int res;
            if (k == V2Input.Kind.PAD) {
                res = af ? R.string.v2_lib_help_pad_af : R.string.v2_lib_help_pad;
            } else if (k == V2Input.Kind.KEYS) {
                res = af ? R.string.v2_lib_help_keys_af : R.string.v2_lib_help_keys;
            } else {
                res = af ? R.string.v2_lib_help_touch_af : R.string.v2_lib_help_touch;
            }
            pickHelp.setText(ctx.getString(res, name));
        }
        final int id = g.getAppId();
        Bitmap cover = covers == null ? null : covers.peek(g);
        if (cover == null && covers != null) {
            covers.load(g, (appId, bmp) -> {
                if (bmp != null && current() != null && current().getAppId() == appId) {
                    bindPick();
                }
            });
        }
        Bitmap bg = covers == null ? null : covers.peekBackground(g);
        if (bg == null && covers != null && g.hasPlayniteBackground()) {
            covers.loadBackground(g, (appId, bmp) -> {
                if (bmp != null && current() != null && current().getAppId() == appId) {
                    bindPick();
                }
            });
        }
        if (pickCover != null) {
            pickCover.setBitmap(cover);
        }
        Bitmap wide = bg != null ? bg : cover;
        if (pickArt != null) {
            pickArt.setBlur(bg == null);
            pickArt.setBitmap(wide);
        }
        if (backImage != null) {
            backImage.setBlur(bg == null);
            backImage.setBitmap(wide);
        }
    }

    private String agoText(LibraryModel.Ago a) {
        if (a == null) {
            return null;
        }
        switch (a.kind) {
            case NOW:
                return ctx.getString(R.string.v2_ago_now);
            case TODAY:
                return ctx.getString(R.string.v2_ago_today);
            case YESTERDAY:
                return ctx.getString(R.string.v2_ago_yesterday);
            case DAYS:
                return ctx.getString(R.string.v2_ago_days, a.n);
            case LAST_WEEK:
                return ctx.getString(R.string.v2_ago_last_week);
            case WEEKS:
                return ctx.getString(R.string.v2_ago_weeks, a.n);
            case LAST_MONTH:
                return ctx.getString(R.string.v2_ago_last_month);
            case MONTHS:
                return ctx.getString(R.string.v2_ago_months, a.n);
            case LAST_YEAR:
                return ctx.getString(R.string.v2_ago_last_year);
            default:
                return ctx.getString(R.string.v2_ago_years, a.n);
        }
    }

    private NvApp current() {
        return list.isEmpty() ? null : list.get(Math.min(focus, list.size() - 1));
    }

    // ---- focus ----------------------------------------------------------------------------------------

    /**
     * A D-pad move (prototype grid: rows of {@code cols}, clamped, no wrap; Affiche: one column, left /
     * right do nothing; a single row: up / down do nothing). Returns false when the focus is not on a game.
     */
    public boolean move(View focused, int dx, int dy) {
        if (list.isEmpty()) {
            return false;
        }
        if (focused == null || appFor(focused) == null) {
            return false;
        }
        List<List<Integer>> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i += cols) {
            List<Integer> r = new ArrayList<>();
            for (int j = i; j < Math.min(list.size(), i + cols); j++) {
                r.add(j);
            }
            rows.add(r);
        }
        Integer target = FocusGrid.move(rows, focus, dx, dy);
        if (target != null && target != focus) {
            focusIndex(target);
        }
        return true;
    }

    /** Focus the n-th game of the list (scrolled into view; Affiche: the window follows) */
    public void focusIndex(int index) {
        if (list.isEmpty()) {
            return;
        }
        focus = Math.max(0, Math.min(index, list.size() - 1));
        boolean changed = false;
        if (windowed()) {
            int start = LibraryModel.windowStart(focus, list.size());
            if (start != windowStart) {
                windowStart = start;
                adapter.notifyDataSetChanged();
                changed = true;
            }
        }
        final int pos = focus - windowStart;
        bindPick();
        grid.scrollToPosition(pos);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null && !changed) {
            vh.itemView.requestFocus();
        } else {
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) {
                    h.itemView.requestFocus();
                }
            });
        }
    }

    public boolean requestInitialFocus() {
        if (list.isEmpty()) {
            return emptyCard.getVisibility() == View.VISIBLE && emptyBack.requestFocus();
        }
        focusIndex(focus);
        return true;
    }

    public NvApp appFor(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getTag(R.id.v2_tag_app) instanceof NvApp) {
                return (NvApp) v.getTag(R.id.v2_tag_app);
            }
            if (v == root) {
                break;
            }
        }
        return null;
    }

    public void refreshInput() {
        int start = windowed() ? LibraryModel.windowStart(focus, list.size()) : 0;
        if (start != windowStart) {
            windowStart = start;
        }
        adapter.notifyDataSetChanged();
        bindPick();
        updateHints();
    }

    private void updateHints() {
        V2Input.Kind k = V2Input.get(ctx);
        if (lbText != null) {
            lbText.setText(k == V2Input.Kind.PAD ? "LB" : k == V2Input.Kind.KEYS ? "Q" : "");
            rbText.setText(k == V2Input.Kind.PAD ? "RB" : k == V2Input.Kind.KEYS ? "E" : "");
            Ui.setVisible(lbText, k != V2Input.Kind.TOUCH && !filters.isEmpty());
            Ui.setVisible(rbText, k != V2Input.Kind.TOUCH && !filters.isEmpty());
        }
        hints.clear();
        if (k == V2Input.Kind.TOUCH) {
            hints.setVisibility(View.GONE);
            return;
        }
        hints.setVisibility(View.VISIBLE);
        boolean pad = k == V2Input.Kind.PAD;
        // The prototype keeps the four hints, an empty library included
        {
            hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_play), () -> {
                NvApp g = current();
                if (g != null) {
                    actions.launch(g);
                }
            });
            hints.add(pad ? "Y" : ctx.getString(R.string.v2_key_menu), ctx.getString(R.string.v2_hint_options), () -> {
                NvApp g = current();
                if (g != null) {
                    actions.options(g);
                }
            });
            hints.add(pad ? "LB RB" : "Q E", ctx.getString(R.string.v2_hint_filter), () -> shiftFilter(1));
        }
        hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_back), actions::back);
    }

    // ---- tiles ----------------------------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            if (windowed()) {
                return Math.min(LibraryModel.WINDOW, list.size() - windowStart);
            }
            return list.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = af ? row() : tile();
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int index = position + windowStart;
            final NvApp g = list.get(index);
            View v = holder.itemView;
            v.setTag(R.id.v2_tag_app, g);
            boolean hiddenFilter = !filters.isEmpty() && filters.get(filter) == LibraryModel.Filter.HIDDEN;
            View focusable;
            if (af) {
                LinearLayout item = (LinearLayout) v;
                HomeParts.Button b = (HomeParts.Button) item.getChildAt(0);
                LinearLayout line = (LinearLayout) b.getChildAt(0);
                ((TextView) line.getChildAt(0)).setText(String.format(Locale.ROOT, "%02d", index + 1));
                ((TextView) line.getChildAt(1)).setText(g.getAppName());
                long secs = g.getPlaynitePlaytime();
                PlayTime.Unit unit = PlayTime.unit(secs);
                ((TextView) line.getChildAt(2)).setText(unit == PlayTime.Unit.NEVER ? "—"
                        : ctx.getString(unit == PlayTime.Unit.MINUTES ? R.string.v2_lib_minutes_short : R.string.v2_lib_hours_short,
                        PlayTime.amount(secs)));
                line.getChildAt(3).setOnClickListener(x -> actions.options(g));
                b.setTag(R.id.v2_tag_app, g);
                b.setAlpha(hiddenFilter ? 0.45f : 1f);
                focusable = b;
            } else {
                FrameLayout box = (FrameLayout) v;
                HomeParts.Cover c = (HomeParts.Cover) box.getChildAt(0);
                // No live mark in the library: the prototype frames the running game only in its « frame » live mode
                c.bind(g.getAppId(), g.getAppName(), false, false);
                c.setTag(R.id.v2_tag_app, g);
                c.setAlpha(hiddenFilter ? 0.45f : 1f);
                Ui.setVisible(box.getChildAt(1), g.isPlayniteInstalled());
                c.more.setOnClickListener(x -> actions.options(g));
                Bitmap b = covers == null ? null : covers.peek(g);
                c.setCover(b);
                if (b == null && covers != null) {
                    covers.load(g, (appId, bmp) -> {
                        if (c.appId == appId) {
                            c.setCover(bmp);
                        }
                    });
                }
                focusable = c;
            }
            focusable.setOnClickListener(x -> actions.launch(g));
            focusable.setOnLongClickListener(x -> {
                actions.options(g);
                return true;
            });
            focusable.setOnFocusChangeListener((x, has) -> {
                if (has && focus != index) {
                    focus = index;
                    bindPick();
                }
            });
        }
    }

    /** Braise / Ludique: a cover with « Installé » (top left) and ⋯ (top right) */
    private View tile() {
        FrameLayout box = new FrameLayout(ctx);
        Ui.noClip(box);
        HomeParts.Cover c = new HomeParts.Cover(ctx, th, l, lu ? 140 : 116, lu ? 196 : 174, lu ? 24 : 12);
        box.addView(c, match());
        TextView badge;
        FrameLayout.LayoutParams bl;
        if (lu) {
            badge = HomeParts.text(ctx, th, l, 12, 800, 0xFF1B1530, false);
            badge.setPadding(px(ctx, l, 10), 0, px(ctx, l, 10), 0);
            badge.setBackground(rect(0xFFC6F432, pxf(ctx, l, 12), 0, 0));
            bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 24));
            bl.setMargins(px(ctx, l, 8), px(ctx, l, 8), 0, 0);
        } else {
            boolean dark = th.dark;
            badge = HomeParts.text(ctx, th, l, 11, 700, dark ? 0xFFFF8A57 : 0xFFC9480F, false);
            badge.setPadding(px(ctx, l, 7), 0, px(ctx, l, 7), 0);
            badge.setBackground(rect(dark ? 0xFF0E0D0C : 0xFFF6F1EA, pxf(ctx, l, 10), 0, 0));
            bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, 20));
            bl.setMargins(px(ctx, l, 6), px(ctx, l, 6), 0, 0);
        }
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setText(R.string.v2_lib_badge_installed);
        box.addView(badge, bl);
        box.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, lu ? 196 : 174)));
        return box;
    }

    /** Affiche: « 01  Red Dead Redemption 2  450 h  ⋯ », 56 px, a rule below */
    private View row() {
        boolean dark = th.dark;
        int ink = dark ? 0xFFEDEDE8 : 0xFF111111;
        LinearLayout item = new LinearLayout(ctx);
        item.setOrientation(LinearLayout.VERTICAL);
        HomeParts.Button b = new HomeParts.Button(ctx, th);
        b.setLongClickable(true);
        b.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setBaselineAligned(true);
        line.setPadding(px(ctx, l, 32), px(ctx, l, 12), px(ctx, l, 32), 0);
        TextView num = HomeParts.text(ctx, th, l, 13, 800, ink, false);
        line.addView(num, new LinearLayout.LayoutParams(px(ctx, l, 24), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = HomeParts.text(ctx, th, l, 27, 800, ink, false);
        name.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nl.setMarginStart(px(ctx, l, 18));
        line.addView(name, nl);
        TextView hours = HomeParts.text(ctx, th, l, 13, 600, ink, false);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMarginStart(px(ctx, l, 18));
        line.addView(hours, hl);
        FrameLayout more = new FrameLayout(ctx);
        more.setBackground(rect(Color.TRANSPARENT, 0, Math.max(1, px(ctx, l, 2)), ink));
        int is = px(ctx, l, 16);
        more.addView(HomeParts.icon(ctx, R.drawable.v2_ic_p_dots, ink), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        more.setClickable(true);
        more.setContentDescription(ctx.getString(R.string.v2_options));
        int ms = px(ctx, l, 32);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ms, ms);
        ml.setMarginStart(px(ctx, l, 18));
        ml.gravity = Gravity.TOP;
        line.addView(more, ml);
        b.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        item.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 56)));
        View rule = new View(ctx);
        rule.setBackgroundColor(ink);
        item.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(ctx, l, 1))));
        item.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }
}
