package com.limelight.v2;

import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.ui.DotDrawable;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * « Mes PC » (prototype « hosts », planches V8-36 / V8-37; design-port-spec §1.10): back + « Mes PC »
 * + « Appairés avec cet appareil », a card per paired PC (icon of its type, name, state, address,
 * « Tour · réseau filaire · 4 ms », « En cours : … », Ouvrir / Réveiller, ⋯), « Ajouter un PC » in
 * dashes, the help line of the current input and the hints bar. The markup of this page is common
 * to the three themes in the prototype: tokens only. Prototype px through {@link HomeLayout}. Built
 * from plain data ({@link HostCards.Card}): no service, the activity (and the screenshot test) feeds it.
 */
public final class HostsScreen {
    public interface Actions {
        void back();

        /** Tap, A, Entrée */
        void activate(String uuid);

        /** Long press, ⋯, Y / A held, Menu */
        void options(String uuid);

        void add();
    }

    private static final int ONLINE_DOT = Color.parseColor("#3DDC84");
    private static final int OFFLINE_DOT = Color.parseColor("#8A8178");

    private final Context ctx;
    private final V2Theme th;
    private final HomeLayout l;
    private final Actions actions;

    private final FrameLayout root;
    private final LinearLayout grid;
    private final TextView help;
    private final AddCard addCard;
    private final HintsBar hints;
    private final List<HostCard> cards = new ArrayList<>();
    private List<String> shownUuids = Collections.emptyList();
    private int shownColumns = -1;

    public HostsScreen(Context context, V2Theme theme, Density density, Actions actions) {
        this(context, theme, HomeLayout.forSize(density.widthDp, density.heightDp), actions);
    }

    public HostsScreen(Context context, V2Theme theme, HomeLayout layout, Actions actions) {
        this.ctx = context;
        this.th = theme;
        this.l = layout;
        this.actions = actions;

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.bg);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = px(context, l, 48);
        page.setPadding(side, px(context, l, 28), side, 0);
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.noClip(root, scroll, page);

        // ---- header (top 28, left / right 48): back 44, « Mes PC » 40, « Appairés avec cet appareil » ----
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        int bs = Math.max(px(context, l, 44), Ui.dp(context, 40));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bs));
        HomeParts.Button back = new HomeParts.Button(context, theme);
        back.setFocusable(false);
        back.setBackground(rect(theme.surface, pillRadius(bs), Math.max(1, px(context, l, theme.border)), theme.borderColor));
        int cs = px(context, l, 20);
        back.addView(HomeParts.icon(context, R.drawable.v2_ic_p_chevron_left, theme.fg), new FrameLayout.LayoutParams(cs, cs, Gravity.CENTER));
        back.setContentDescription(context.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        header.addView(back, new LinearLayout.LayoutParams(bs, bs));
        TextView title = HomeParts.text(context, theme, l, 40, theme.displayWeight, theme.fg, true);
        title.setLetterSpacing(theme.tracking);
        title.setText(theme.displayText(context.getString(R.string.v2_hosts_title)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(px(context, l, 18));
        header.addView(title, tlp);
        TextView subtitle = HomeParts.text(context, theme, l, 15, 400, theme.muted, false);
        subtitle.setText(R.string.v2_hosts_subtitle);
        header.addView(subtitle);

        // ---- grid (top 130, gap 16) ------------------------------------------------------------
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(grid);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Math.max(Ui.dp(context, 14), px(context, l, 130 - 28) - bs);
        page.addView(grid, glp);

        addCard = new AddCard(context);
        addCard.setOnClickListener(v -> actions.add());

        // ---- help line (left 48, bottom 70, 14 muted) ----------------------------------------------
        page.addView(new Space(context), new LinearLayout.LayoutParams(1, 0, 1f));
        help = HomeParts.text(context, theme, l, 14, 400, theme.muted, false);
        help.setMaxLines(3);
        // WRAP_CONTENT: its max width (below) keeps it beside the hints bar
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = px(context, l, 20);
        hlp.bottomMargin = px(context, l, 70);
        if (l.portrait || theme.isAffiche()) {
            // Portrait, or Affiche's bar on the left: the help line went under the hints bar (beside it
            // when the bar is on the right, in landscape): it stays above the bar
            float hs = l.tablet() ? l.k : Math.max(0.85f, l.k);
            hlp.bottomMargin = Math.max(hlp.bottomMargin, HintsBar.reservedHeight(context, theme, hs) + px(context, l, 12));
        }
        page.addView(help, hlp);

        // ---- hints: A Ouvrir · Y Options · B Retour (gamepad / keyboard) ------------------------------
        hints = new HintsBar(context, theme, l.tablet() ? l.k : Math.max(0.85f, l.k));
        root.addView(hints, hints.layoutParams());
        if (!l.portrait && !theme.isAffiche()) {
            // Landscape: the help line (left) and the hints bar (right) share the bottom; a long help
            // line ran under the bar: it wraps before it
            final int gap = px(context, l, 24);
            root.addOnLayoutChangeListener((v, a, b, c, d, e, f, g, h) -> {
                int room = hints.getVisibility() == View.VISIBLE && hints.getWidth() > 0
                        ? hints.getLeft() - side - gap : Integer.MAX_VALUE;
                if (help.getMaxWidth() != room) {
                    root.post(() -> help.setMaxWidth(room));
                }
            });
        }

        bind(Collections.<HostCards.Card>emptyList());
    }

    private float pillRadius(int size) {
        return Math.min(size / 2f, pxf(ctx, l, th.pill));
    }

    public View getView() {
        return root;
    }

    /** Columns for this width and this many cards (the add card included) */
    int columnsFor(int count) {
        float available = l.widthDp - 2 * l.b(48);
        return HostCards.columns(count + 1, available, l.b(16), l.phone());
    }

    public void bind(List<HostCards.Card> list) {
        List<String> uuids = new ArrayList<>();
        for (HostCards.Card c : list) {
            uuids.add(c.uuid);
        }
        int cols = columnsFor(list.size());
        if (!uuids.equals(shownUuids) || cols != shownColumns) {
            // Another set of PCs: lay the grid out again, keeping the focus on the same PC
            String focusedUuid = null;
            boolean addFocused = addCard.isFocused();
            for (HostCard hc : cards) {
                if (hc.isFocused()) {
                    focusedUuid = hc.uuid;
                }
            }
            layoutGrid(list.size(), cols);
            shownUuids = uuids;
            shownColumns = cols;
            for (int i = 0; i < list.size(); i++) {
                cards.get(i).bind(list.get(i));
            }
            if (focusedUuid != null || addFocused) {
                View target = addCard;
                for (HostCard hc : cards) {
                    if (hc.uuid.equals(focusedUuid)) {
                        target = hc;
                    }
                }
                target.requestFocus();
            }
        } else {
            for (int i = 0; i < list.size(); i++) {
                cards.get(i).bind(list.get(i));
            }
        }
        refreshInput();
    }

    private void layoutGrid(int count, int cols) {
        grid.removeAllViews();
        cards.clear();
        if (addCard.getParent() instanceof ViewGroup) {
            ((ViewGroup) addCard.getParent()).removeView(addCard);
        }
        List<View> cells = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HostCard c = new HostCard(ctx);
            cards.add(c);
            cells.add(c);
        }
        cells.add(addCard);
        int gap = px(ctx, l, 16);
        int cardH = px(ctx, l, 400);
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                Ui.noClip(row);
                // At least the card height; taller when a card needs it (narrow columns), all cards of the row alike
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rlp.topMargin = gap;
                }
                grid.addView(row, rlp);
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i % cols != 0) {
                clp.setMarginStart(gap);
            }
            cells.get(i).setMinimumHeight(cardH);
            row.addView(cells.get(i), clp);
        }
        // The last row keeps the column width
        int filled = cells.size() % cols;
        if (filled != 0) {
            for (int i = filled; i < cols; i++) {
                // A Space: a plain View measured "at most" takes all it is offered (and the row with it),
                // and a fixed height would make the row ignore its match_parent cards (LinearLayout)
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                slp.setMarginStart(gap);
                row.addView(new Space(ctx), slp);
            }
        }
    }

    /** The input changed: the help line and the hints follow (prototype hostsHelp) */
    public void refreshInput() {
        V2Input.Kind k = V2Input.get(ctx);
        help.setText(helpText(k));
        hints.clear();
        if (k == V2Input.Kind.TOUCH) {
            hints.setVisibility(View.GONE);
        } else {
            boolean pad = k == V2Input.Kind.PAD;
            hints.setVisibility(View.VISIBLE);
            hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_open), () -> {
                View f = root.findFocus();
                if (f == addCard) {
                    actions.add();
                } else if (uuidFor(f) != null) {
                    actions.activate(uuidFor(f));
                }
            });
            hints.add(pad ? "Y" : ctx.getString(R.string.v2_key_menu), ctx.getString(R.string.v2_hint_options), () -> {
                String uuid = uuidFor(root.findFocus());
                if (uuid != null) {
                    actions.options(uuid);
                }
            });
            hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_back), actions::back);
        }
        for (HostCard c : cards) {
            c.invalidate();
        }
        root.invalidate();
    }

    static int helpText(V2Input.Kind k) {
        return k == V2Input.Kind.PAD ? R.string.v2_hosts_help_pad
                : (k == V2Input.Kind.KEYS ? R.string.v2_hosts_help_keys : R.string.v2_hosts_help_touch);
    }

    /** Gamepad / keyboard: the first card, else « Ajouter un PC » */
    public boolean requestInitialFocus() {
        return requestInitialFocus(null);
    }

    /** Gamepad / keyboard: the card of the PC the home shows (prototype hostPos), else the first one */
    public boolean requestInitialFocus(String uuid) {
        View shown = uuid == null ? null : cardFor(uuid);
        if (shown != null && shown.requestFocus()) {
            return true;
        }
        if (!cards.isEmpty() && cards.get(0).requestFocus()) {
            return true;
        }
        return addCard.requestFocus();
    }

    /** Prototype grid: one row of cards then « Ajouter un PC » (h0..hn, hadd), as laid out */
    public List<List<View>> focusRows() {
        List<List<View>> out = new ArrayList<>();
        for (int r = 0; r < grid.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) grid.getChildAt(r);
            List<View> items = new ArrayList<>();
            for (int i = 0; i < row.getChildCount(); i++) {
                View v = row.getChildAt(i);
                if (v instanceof HostCard || v == addCard) {
                    items.add(v);
                }
            }
            if (!items.isEmpty()) {
                out.add(items);
            }
        }
        return out;
    }

    /** The PC a focused view stands for (Y / Menu), null for anything else */
    public String uuidFor(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v instanceof HostCard) {
                return ((HostCard) v).uuid;
            }
            if (v == root) {
                break;
            }
        }
        return null;
    }

    public boolean isAddCard(View focused) {
        return focused == addCard;
    }

    /** For the screenshot test: the card of this PC */
    View cardFor(String uuid) {
        for (HostCard c : cards) {
            if (c.uuid.equalsIgnoreCase(uuid)) {
                return c;
            }
        }
        return null;
    }

    // ---- views -------------------------------------------------------------------------------

    private static int iconOf(HostCards.FormFactor f) {
        switch (f) {
            case MINI:
                return R.drawable.v2_ic_p_pc_mini;
            case LAPTOP:
                return R.drawable.v2_ic_p_pc_laptop;
            default:
                return R.drawable.v2_ic_p_pc_tower;
        }
    }

    /** One PC (prototype card h400, pad 26 24, gap 8): icon tile, name, state, detail, meta, « En cours », action, ⋯ */
    private final class HostCard extends V2Frame {
        String uuid = "";
        private final TextView name, status, detail, meta, running, action;
        private final android.widget.ImageView icon, netIcon;
        private final LinearLayout metaRow;

        HostCard(Context context) {
            super(context, th);
            setScaleOnFocus(true);
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            Ui.noClip(this);
            float r = pxf(context, l, th.r);
            setBackground(rect(th.surface, r, th.border > 0 ? Math.max(1, px(context, l, th.border)) : 0, th.borderColor));
            setRing(l.b(th.r), 0, 0);
            // Affiche: the yellow fill keeps the 3 px ink frame of the card (prototype V8-37)
            setFocusFillStroke(th.border > 0 ? Math.max(1, px(context, l, th.border)) : 0, th.borderColor);

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(context, l, 24), px(context, l, 26), px(context, l, 24), px(context, l, 26));
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.TOP));

            FrameLayout tile = new FrameLayout(context);
            tile.setBackground(rect(th.surface2, pxf(context, l, th.rs), 0, 0));
            icon = HomeParts.icon(context, R.drawable.v2_ic_p_pc_tower, th.fg);
            int is = px(context, l, 30);
            tile.addView(icon, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
            int ts = px(context, l, 64);
            col.addView(tile, new LinearLayout.LayoutParams(ts, ts));

            name = HomeParts.text(context, th, l, 26, th.displayWeight, th.fg, true);
            name.setLetterSpacing(th.tracking);
            col.addView(name, top(18));

            status = HomeParts.text(context, th, l, 15, 600, th.fg, false);
            status.setCompoundDrawablePadding(px(context, l, 8));
            col.addView(status, top(8));

            detail = HomeParts.text(context, th, l, 14, 400, th.muted, false);
            detail.setMaxLines(3);
            HomeParts.lineHeight(detail, context, l, 14, 1.45f);
            col.addView(detail, top(8));

            metaRow = new LinearLayout(context);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);
            netIcon = HomeParts.icon(context, R.drawable.v2_ic_p_net_wired, th.muted);
            int ns = px(context, l, 14);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ns, ns);
            nlp.setMarginEnd(px(context, l, 6));
            metaRow.addView(netIcon, nlp);
            meta = HomeParts.text(context, th, l, 13, 400, th.muted, false);
            metaRow.addView(meta);
            col.addView(metaRow, top(8));

            running = HomeParts.text(context, th, l, 13, 700, th.onAccent, false);
            running.setMaxLines(3);
            running.setPadding(px(context, l, 12), px(context, l, 6), px(context, l, 12), px(context, l, 6));
            running.setBackground(rect(th.accent, pxf(context, l, th.pill), 0, 0));
            col.addView(running, top(12));
            // The pill and the button keep their colours on Affiche's yellow fill
            keepColors(running);

            col.addView(new Space(context), new LinearLayout.LayoutParams(1, px(context, l, 12), 1f));
            action = HomeParts.text(context, th, l, 15, 700, th.bg, false);
            action.setGravity(Gravity.CENTER);
            int ah = Math.max(px(context, l, 42), Ui.dp(context, 36));
            action.setPadding(px(context, l, 18), 0, px(context, l, 18), 0);
            action.setBackground(rect(th.fg, Math.min(ah / 2f, pxf(context, l, th.pill)), 0, 0));
            col.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ah));
            keepColors(action);

            // ⋯ (36, radius rs, surface2, top / right 16): always there, as the prototype
            View more = HomeParts.more(context, th, l, 36, 18, th.surface2, th.fg, false, false);
            more.setBackground(rect(th.surface2, pxf(context, l, th.rs), 0, 0));
            int ms = Math.max(px(context, l, 36), Ui.dp(context, 32));
            LayoutParams mlp = new LayoutParams(ms, ms, Gravity.TOP | Gravity.END);
            int mm = px(context, l, 16);
            mlp.setMargins(0, mm, mm, 0);
            addView(more, mlp);

            setOnClickListener(v -> actions.activate(uuid));
            setOnLongClickListener(v -> {
                actions.options(uuid);
                return true;
            });
            more.setOnClickListener(v -> actions.options(uuid));
        }

        private LinearLayout.LayoutParams top(float protoPx) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(getContext(), l, protoPx);
            return lp;
        }

        void bind(HostCards.Card c) {
            uuid = c.uuid;
            name.setText(th.displayText(c.name));
            setContentDescription(c.name);
            icon.setImageDrawable(Ui.icon(getContext(), iconOf(c.formFactor), th.fg));
            status.setText(c.statusText);
            int ds = Math.max(Ui.dp(getContext(), 6), px(getContext(), l, 9));
            status.setCompoundDrawablesRelativeWithIntrinsicBounds(new DotDrawable(c.online ? ONLINE_DOT : OFFLINE_DOT, ds), null, null, null);
            detail.setText(c.detail);
            Ui.setVisible(detail, c.detail != null && !c.detail.isEmpty());
            meta.setText(c.meta);
            Ui.setVisible(metaRow, c.meta != null && !c.meta.isEmpty());
            Ui.setVisible(netIcon, c.link != HostCards.Link.UNKNOWN);
            if (c.link != HostCards.Link.UNKNOWN) {
                netIcon.setImageDrawable(Ui.icon(getContext(),
                        c.link == HostCards.Link.WIRED ? R.drawable.v2_ic_p_net_wired : R.drawable.v2_ic_p_net_wifi, th.muted));
            }
            Ui.setVisible(running, c.running != null);
            if (c.running != null) {
                running.setText(c.running);
            }
            action.setText(c.action);
            Ui.setVisible(action, c.action != null && !c.action.isEmpty());
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            float s = pressed ? 0.98f : 1f;
            setScaleX(s);
            setScaleY(s);
        }
    }

    /** « Ajouter un PC » (h400, 2 px dashes muted, plus 40, title 22, « Trouvé sur le réseau, ou par son adresse ») */
    private final class AddCard extends V2Frame {
        AddCard(Context context) {
            super(context, th);
            setScaleOnFocus(true);
            setFocusable(true);
            setClickable(true);
            Ui.noClip(this);
            GradientDrawable dashed = new GradientDrawable();
            dashed.setShape(GradientDrawable.RECTANGLE);
            dashed.setColor(0);
            dashed.setCornerRadius(pxf(context, l, th.r));
            dashed.setStroke(Math.max(1, px(context, l, 2)), th.muted, pxf(context, l, 6), pxf(context, l, 4));
            setBackground(dashed);
            setRing(l.b(th.r), 0, 0);
            setContentDescription(context.getString(R.string.v2_add_pc));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = px(context, l, 14);
            col.setPadding(pad, 0, pad, 0);
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            int ps = px(context, l, 40);
            col.addView(HomeParts.icon(context, R.drawable.v2_ic_p_plus, th.fg), new LinearLayout.LayoutParams(ps, ps));

            TextView title = HomeParts.text(context, th, l, 22, th.displayWeight, th.fg, true);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setText(context.getString(R.string.v2_add_pc));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(context, l, 12);
            col.addView(title, tlp);

            TextView sub = HomeParts.text(context, th, l, 14, 400, th.muted, false);
            sub.setGravity(Gravity.CENTER);
            sub.setMaxLines(3);
            sub.setText(R.string.v2_add_pc_subtitle);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.max(px(context, l, 200), Ui.dp(context, 120)), ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = px(context, l, 12);
            col.addView(sub, slp);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            float s = pressed ? 0.98f : 1f;
            setScaleX(s);
            setScaleY(s);
        }
    }
}
