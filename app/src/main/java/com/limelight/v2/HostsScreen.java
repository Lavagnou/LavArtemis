package com.limelight.v2;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.ui.DotDrawable;
import com.limelight.v2.ui.IconButton;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * « Mes PC » (LavArtemis-Qt gui/v2/HostsView.qml, prototype board "hosts"): a card per paired PC,
 * then « Ajouter un PC », in a grid whose columns follow the width; the help line of the current
 * input under it. Built from plain data ({@link HostCards.Card}): no service, the activity (and the
 * screenshot test) feeds it.
 */
public final class HostsScreen {
    public interface Actions {
        void back();

        /** Tap, A, Entrée */
        void activate(String uuid);

        /** Long press, « … », Y / A held, Menu */
        void options(String uuid);

        void add();
    }

    private static final int ONLINE_DOT = Color.parseColor("#3DDC84");
    private static final int OFFLINE_DOT = Color.parseColor("#8A8178");

    private final Context ctx;
    private final V2Theme theme;
    private final Density d;
    private final Actions actions;

    private final FrameLayout root;
    private final LinearLayout grid;
    private final TextView help;
    private final IconButton back;
    private final AddCard addCard;
    private final List<HostCard> cards = new ArrayList<>();
    private List<String> shownUuids = Collections.emptyList();
    private int shownColumns = -1;

    private final float gapDp;
    private final float cardHeightDp;

    public HostsScreen(Context context, V2Theme theme, Density density, Actions actions) {
        this.ctx = context;
        this.theme = theme;
        this.d = density;
        this.actions = actions;
        gapDp = d.compact ? 14 : 16;
        // Prototype cards are 400 px tall at 1280 px: 300 dp on a 960 dp console; a phone in
        // landscape keeps the header, a row of cards and the help line on its 412 dp
        cardHeightDp = d.compact ? 256 : 300;

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.bg);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(context, d.side);
        page.setPadding(side, Ui.dp(context, d.top), side, Ui.dp(context, 24));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.noClip(root, scroll, page);

        // ---- header: back, « Mes PC », « Appairés avec cet appareil » --------------------------
        boolean narrow = d.widthDp < 600;
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, d.headerHeight)));
        back = new IconButton(context, theme, R.drawable.v2_ic_chevron_left, 48, 44, 20, theme.surface, true);
        back.setContentDescription(context.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        TextView title = Ui.display(context, theme, d.compact ? 26 : 32, theme.fg);
        title.setText(theme.displayText(context.getString(R.string.v2_hosts_title)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(context, 14));
        header.addView(title, tlp);
        TextView subtitle = Ui.text(context, theme, narrow ? 14 : 15, 500, theme.muted);
        subtitle.setText(R.string.v2_hosts_subtitle);
        if (narrow) {
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMarginStart(Ui.dp(context, 62));
            page.addView(subtitle, slp);
        } else {
            header.addView(subtitle);
        }

        // ---- grid ---------------------------------------------------------------------------
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(grid);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Ui.dp(context, d.compact ? 14 : 22);
        page.addView(grid, glp);

        addCard = new AddCard(context, theme, d.compact);
        addCard.setOnClickListener(v -> actions.add());

        // ---- help line -------------------------------------------------------------------------
        help = Ui.text(context, theme, d.compact ? 13 : 15, 400, theme.muted);
        help.setMaxLines(4);
        help.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.dp(context, d.compact ? 14 : 22);
        page.addView(help, hlp);

        bind(Collections.<HostCards.Card>emptyList());
    }

    public View getView() {
        return root;
    }

    /** Columns for this width and this many cards (the add card included) */
    int columnsFor(int count) {
        float available = d.widthDp - 2 * d.side;
        return HostCards.columns(count + 1, available, gapDp, d.compact);
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
            HostCard c = new HostCard(ctx, theme, d.compact, actions);
            cards.add(c);
            cells.add(c);
        }
        cells.add(addCard);
        int gap = Ui.dp(ctx, gapDp);
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
            cells.get(i).setMinimumHeight(Ui.dp(ctx, cardHeightDp));
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
                row.addView(new android.widget.Space(ctx), slp);
            }
        }
    }

    /** The input changed: « … » on touch only, the help line follows */
    public void refreshInput() {
        V2Input.Kind k = V2Input.get(ctx);
        for (HostCard c : cards) {
            c.refreshInput();
        }
        help.setText(k == V2Input.Kind.PAD ? R.string.v2_hosts_help_pad
                : (k == V2Input.Kind.KEYS ? R.string.v2_hosts_help_keys : R.string.v2_hosts_help_touch));
        root.invalidate();
        invalidateAll(root);
    }

    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                invalidateAll(g.getChildAt(i));
            }
        }
    }

    /** Gamepad / keyboard: the first card, else « Ajouter un PC » */
    public boolean requestInitialFocus() {
        if (!cards.isEmpty() && cards.get(0).requestFocus()) {
            return true;
        }
        return addCard.requestFocus();
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
            if (c.uuid.equals(uuid)) {
                return c;
            }
        }
        return null;
    }

    // ---- views -------------------------------------------------------------------------------

    /** One PC: icon tile, name, status, detail, « En cours : … », main action, « … » */
    private static final class HostCard extends V2Frame {
        String uuid = "";
        private final TextView name, status, detail, running, action;
        private final IconButton more;
        private final GradientDrawable actionBg;

        HostCard(Context context, V2Theme theme, boolean compact, Actions actions) {
            super(context, theme);
            setScaleOnFocus(true);
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setBackground(Ui.roundRect(theme.surface, Ui.dpf(context, theme.r), Ui.dp(context, theme.border), theme.borderColor));
            setRing(theme.r, 6, theme.accent);

            int pad = Ui.dp(context, compact ? 16 : 20);
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(pad, pad, pad, pad);
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.TOP));

            FrameLayout tile = new FrameLayout(context);
            tile.setBackground(Ui.roundRect(theme.surface2, Ui.dpf(context, theme.rs), 0, 0));
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(Ui.icon(context, R.drawable.v2_ic_monitor, theme.fg));
            int is = Ui.dp(context, compact ? 22 : 26);
            tile.addView(icon, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
            int ts = Ui.dp(context, compact ? 44 : 52);
            col.addView(tile, new LinearLayout.LayoutParams(ts, ts));

            name = Ui.display(context, theme, compact ? 20 : 24, theme.fg);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.dp(context, compact ? 12 : 16);
            col.addView(name, nlp);

            status = Ui.text(context, theme, compact ? 14 : 15, 600, theme.fg);
            status.setCompoundDrawablePadding(Ui.dp(context, 8));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(context, compact ? 6 : 8);
            col.addView(status, slp);

            detail = Ui.text(context, theme, compact ? 13 : 14, 400, theme.muted);
            detail.setMaxLines(3);
            detail.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = Ui.dp(context, compact ? 6 : 8);
            col.addView(detail, dlp);

            running = Ui.text(context, theme, compact ? 12 : 13, 700, theme.onAccent);
            running.setGravity(Gravity.CENTER_VERTICAL);
            int rh = Ui.dp(context, compact ? 26 : 28);
            running.setMinHeight(rh);
            running.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 12), 0);
            running.setBackground(Ui.roundRect(theme.accent, Ui.dpf(context, Math.min(theme.pill, (compact ? 26 : 28) / 2f)), 0, 0));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(context, compact ? 8 : 10);
            col.addView(running, rlp);

            // Main action: a label of the card (the whole card is the button), at the bottom
            action = Ui.text(context, theme, compact ? 14 : 15, 700, theme.bg);
            action.setGravity(Gravity.CENTER);
            int ah = Ui.dp(context, compact ? 38 : 42);
            action.setMinHeight(ah);
            action.setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), 0);
            actionBg = Ui.roundRect(theme.fg, Ui.dpf(context, Math.min(theme.pill, (compact ? 38 : 42) / 2f)), 0, 0);
            action.setBackground(actionBg);
            // Pushed to the bottom by a spacer, never over the texts
            View spacer = new View(context);
            col.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(context, 12), 1f));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ah);
            col.addView(action, alp);

            more = new IconButton(context, theme, R.drawable.v2_ic_dots, 48, 36, 18, theme.surface2, false);
            more.setFocusable(false);
            LayoutParams mlp = new LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48), Gravity.TOP | Gravity.END);
            int mm = Ui.dp(context, compact ? 8 : 10);
            mlp.setMargins(0, mm, mm, 0);
            addView(more, mlp);

            setOnClickListener(v -> actions.activate(uuid));
            setOnLongClickListener(v -> {
                actions.options(uuid);
                return true;
            });
            more.setOnClickListener(v -> actions.options(uuid));
            refreshInput();
        }

        void bind(HostCards.Card c) {
            uuid = c.uuid;
            name.setText(theme.displayText(c.name));
            setContentDescription(c.name);
            more.setContentDescription(getContext().getString(R.string.v2_host_options, c.name));
            status.setText(c.statusText);
            status.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    new DotDrawable(c.online ? ONLINE_DOT : OFFLINE_DOT, Ui.dp(getContext(), 9)), null, null, null);
            detail.setText(c.detail);
            Ui.setVisible(detail, c.detail != null && !c.detail.isEmpty());
            Ui.setVisible(running, c.running != null);
            if (c.running != null) {
                running.setText(c.running);
            }
            action.setText(c.action);
            Ui.setVisible(action, c.action != null && !c.action.isEmpty());
        }

        void refreshInput() {
            Ui.setVisible(more, V2Input.get(getContext()) == V2Input.Kind.TOUCH);
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            float s = pressed ? 0.98f : 1f;
            setScaleX(s);
            setScaleY(s);
        }
    }

    /** « Ajouter un PC »: dashed frame, plus, title, « Trouvé sur le réseau, ou par son adresse » */
    private static final class AddCard extends V2Frame {
        AddCard(Context context, V2Theme theme, boolean compact) {
            super(context, theme);
            setScaleOnFocus(true);
            setFocusable(true);
            setClickable(true);
            GradientDrawable dashed = new GradientDrawable();
            dashed.setShape(GradientDrawable.RECTANGLE);
            dashed.setColor(0);
            dashed.setCornerRadius(Ui.dpf(context, theme.r));
            dashed.setStroke(Ui.dp(context, 2), theme.muted, Ui.dpf(context, 7), Ui.dpf(context, 5));
            setBackground(dashed);
            setRing(theme.r, 6, theme.accent);
            setContentDescription(context.getString(R.string.v2_add_pc));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = Ui.dp(context, 14);
            col.setPadding(pad, 0, pad, 0);
            addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            ImageView plus = new ImageView(context);
            plus.setImageDrawable(Ui.icon(context, R.drawable.v2_ic_plus, theme.fg));
            int ps = Ui.dp(context, compact ? 34 : 40);
            col.addView(plus, new LinearLayout.LayoutParams(ps, ps));

            TextView title = Ui.display(context, theme, compact ? 19 : 22, theme.fg);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setText(theme.displayText(context.getString(R.string.v2_add_pc)));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(context, 12);
            col.addView(title, tlp);

            TextView sub = Ui.text(context, theme, compact ? 13 : 14, 400, theme.muted);
            sub.setGravity(Gravity.CENTER);
            sub.setMaxLines(3);
            sub.setLineSpacing(0, 1.15f);
            sub.setText(R.string.v2_add_pc_subtitle);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(context, 8);
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
