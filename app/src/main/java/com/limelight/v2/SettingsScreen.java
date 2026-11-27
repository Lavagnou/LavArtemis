package com.limelight.v2;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.v2.settings.Option;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingRowView;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.IconButton;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;
import com.limelight.v2.ui.V2Switch;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages v2 (LavArtemis-Qt gui/v2/SettingsView2.qml): the page list on the left with « Mode
 * expert » on top, the rows of the chosen page on the right. Narrow (a phone in portrait): the
 * page list, then a page's rows on their own (back returns to the list). Built from a
 * {@link SettingsEnv} and a {@link SettingsStore}: the activity and the screenshot tests feed it.
 */
public final class SettingsScreen {
    public interface Actions extends SettingRowView.Listener {
        void back();

        void setExpert(boolean on);
    }

    private final Context ctx;
    private final V2Theme theme;
    private final Density d;
    private final SettingsEnv env;
    private final SettingsStore store;
    private final Actions actions;
    public final boolean narrow;

    private final FrameLayout root;
    private final LinearLayout pageLayout;
    private final TextView title;
    private final ScrollView navScroll;
    private final ScrollView rowsScroll;
    private final LinearLayout rowsColumn;
    private final ExpertCard expertCard;
    private final List<NavItem> navItems = new ArrayList<>();
    private final List<SettingRowView> rows = new ArrayList<>();
    /** « N réglages avancés masqués · activer le mode expert » (prototype smore), null when none */
    private V2Frame moreButton;
    private final HintsBar hints;
    /** dp per prototype px for blocks, and for text (it shrinks less on small screens) */
    final float k, kt;

    private boolean expert;
    private String page;
    /** Narrow layout: the rows of {@link #page} are shown instead of the page list */
    private boolean showingRows;

    public SettingsScreen(Context context, V2Theme theme, Density density, SettingsEnv env, SettingsStore store,
                          Actions actions, String initialPage, boolean showRows) {
        this.ctx = context;
        this.theme = theme;
        this.d = density;
        this.env = env;
        this.store = store;
        this.actions = actions;
        this.narrow = density.widthDp < 640;
        this.expert = store.expertMode();
        // The prototype's stage is 1280 x 720: a tablet shows it as it is, smaller screens shrink it
        // (text less than blocks), never under three quarters
        float fit = Math.min(density.widthDp / 1280f, density.heightDp / 720f);
        this.k = Math.max(0.75f, Math.min(1.111f, narrow ? 1f : fit));
        this.kt = Math.max(0.9f, k);

        root = new FrameLayout(context) {
            @Override
            public void requestChildFocus(View child, View focused) {
                super.requestChildFocus(child, focused);
                // « A Ouvrir » on the page list, « A Changer » on the rows (R11)
                updateHintsIfColumnChanged();
            }
        };
        root.setBackgroundColor(theme.bg);
        pageLayout = new LinearLayout(context);
        pageLayout.setOrientation(LinearLayout.VERTICAL);
        int side = narrow ? Ui.dp(context, d.side) : px(40);
        // Narrow: the back button sits near the top edge, its focus ring must fit above it (Ludique: 9 dp)
        int top = narrow ? Math.max(Ui.dp(context, d.top), V2Frame.ringRoom(context, theme, 0)
                - Math.max(0, (Ui.dp(context, d.headerHeight) - Ui.dp(context, 48)) / 2)) : px(24);
        pageLayout.setPadding(side, top, side, narrow ? 0 : px(58));
        root.addView(pageLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.noClip(root, pageLayout);

        // ---- header: back, « Réglages » (or the page in the narrow layout) --------------------------
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        pageLayout.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                narrow ? Ui.dp(context, d.headerHeight) : px(48)));
        // Prototype: back 44 (pill, border, surface, arrow 20), « Réglages » display 34, on the right
        // « Thème {nom} · LavArtemis {version} » 14 muted
        float bs = narrow ? 48 : 44 * k;
        IconButton back = new IconButton(context, theme, R.drawable.v2_ic_back, bs, bs, 20 * k, theme.surface, true);
        back.setContentDescription(context.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(context, bs), Ui.dp(context, bs)));
        title = Ui.display(context, theme, narrow ? (d.compact ? 26 : 32) : 34 * kt, theme.fg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(narrow ? Ui.dp(context, 14) : px(16));
        header.addView(title, tlp);
        if (!narrow) {
            TextView about = Ui.text(context, theme, 14 * kt, 400, theme.muted);
            about.setText(context.getString(R.string.v2_set_about, themeName(context, theme),
                    com.limelight.BuildConfig.VERSION_NAME));
            header.addView(about);
        }

        // ---- body: page list | rows ---------------------------------------------------------------
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.HORIZONTAL);
        // The body clips: the scrolled rows must not draw over the header
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = narrow ? Ui.dp(context, d.compact ? 6 : 14) : px(18);
        pageLayout.addView(body, blp);

        navScroll = scroll(context);
        LinearLayout navColumn = column(context);
        navScroll.addView(navColumn, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        expertCard = new ExpertCard(context);
        navColumn.addView(expertCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (SettingsCatalog.Page p : SettingsCatalog.PAGES) {
            NavItem item = new NavItem(context, p);
            // Prototype: pages h40, gap 4, 12 under the Mode expert card
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    narrow ? Ui.dp(context, d.compact ? 48 : 54) : Math.max(px(40), Ui.dp(context, 36)));
            ilp.topMargin = narrow ? Ui.dp(context, navItems.isEmpty() ? 14 : 6) : px(navItems.isEmpty() ? 12 : 4);
            navColumn.addView(item, ilp);
            navItems.add(item);
        }

        rowsScroll = scroll(context);
        rowsColumn = column(context);
        rowsScroll.addView(rowsColumn, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (narrow) {
            FrameLayout stack = new FrameLayout(context);
            body.addView(stack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            stack.addView(navScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            stack.addView(rowsScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            // Prototype: page list 260 wide at left 40, rows from left 330 (30 between, minus the column's padding)
            body.addView(navScroll, new LinearLayout.LayoutParams(px(260) + 2 * colPad(), ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            rlp.setMarginStart(Math.max(0, px(30) - 2 * colPad()));
            body.addView(rowsScroll, rlp);
            // The page list starts at the prototype's left 40: the column's padding is taken back
            LinearLayout.LayoutParams nl = (LinearLayout.LayoutParams) navScroll.getLayoutParams();
            nl.setMarginStart(-colPad());
            navScroll.setLayoutParams(nl);
        }

        hints = new HintsBar(context, theme, hintsScale());
        root.addView(hints, hints.layoutParams());

        page = initialPage;
        showingRows = narrow && showRows;
        refreshNav();
        showPage(page, false);
        updateHints();
    }

    private ScrollView scroll(Context context) {
        ScrollView s = new ScrollView(context);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setVerticalScrollBarEnabled(false);
        s.setFillViewport(true);
        return s;
    }

    /** A column whose children may draw their focus ring outside themselves */
    private LinearLayout column(Context context) {
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = colPad();
        c.setPadding(pad, pad, pad, Ui.dp(context, 24));
        Ui.noClip(c);
        return c;
    }

    /** Room around a column for the focus rings drawn outside its items */
    private int colPad() {
        return Ui.dp(ctx, 10);
    }

    private int px(float protoPx) {
        return Ui.dp(ctx, protoPx * k);
    }

    static String themeName(Context c, V2Theme t) {
        return c.getString(t.isAffiche() ? R.string.v2_theme_affiche : t.isLudique() ? R.string.v2_theme_ludique : R.string.v2_theme_braise);
    }

    public View getView() {
        return root;
    }

    public String currentPage() {
        return page;
    }

    public boolean isShowingRows() {
        return !narrow || showingRows;
    }

    public boolean isExpert() {
        return expert;
    }

    // ---- pages ------------------------------------------------------------------------------------

    private List<SettingsCatalog.Page> visiblePages() {
        return SettingsCatalog.visiblePages(env, expert);
    }

    private boolean pageVisible(String id) {
        for (SettingsCatalog.Page p : visiblePages()) {
            if (p.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void refreshNav() {
        if (!pageVisible(page)) {
            // Prototype setExpert: a page that empties goes to Affichage (else the first page)
            page = pageVisible(SettingsCatalog.AFFICHAGE) ? SettingsCatalog.AFFICHAGE : firstPage();
        }
        for (NavItem item : navItems) {
            Ui.setVisible(item, pageVisible(item.page.id));
            item.setSelected(!narrow && item.page.id.equals(page));
            item.setExpertOnly(expert && SettingsCatalog.expertOnly(item.page.id, env));
        }
        expertCard.sw.setOn(expert);
        Ui.setVisible(navScroll, !narrow || !showingRows);
        Ui.setVisible(rowsScroll, !narrow || showingRows);
        SettingsCatalog.Page p = SettingsCatalog.page(page);
        String t = narrow && showingRows && p != null ? ctx.getString(p.title) : ctx.getString(R.string.v2_settings);
        title.setText(theme.displayText(t));
    }

    private String firstPage() {
        List<SettingsCatalog.Page> pages = visiblePages();
        return pages.isEmpty() ? SettingsCatalog.PROFIL : pages.get(0).id;
    }

    /**
     * LB / RB: the previous / next page, wrapping, focus on it (prototype shoulder). Narrow: the
     * rows of that page are shown (from the list or from another page), focus on the first row (R8).
     */
    public void stepPage(int delta) {
        String next = SettingsCatalog.stepPage(visiblePages(), page, delta);
        if (narrow) {
            showPage(next, false);
            showingRows = true;
            refreshNav();
            rowsScroll.scrollTo(0, 0);
            if (V2Input.showFocus(ctx)) {
                focusRows();
            }
            updateHints();
            return;
        }
        showPage(next, false);
        if (!narrow && V2Input.showFocus(ctx)) {
            NavItem item = navItem(next);
            if (item != null) {
                item.requestFocus();
            }
        }
        updateHints();
    }

    /** Shows a page's rows (and, narrow, switches to them); keeps the scroll on the same page */
    public void showPage(String id, boolean focusRows) {
        boolean samePage = id.equals(page) && !rows.isEmpty();
        int scrollY = samePage ? rowsScroll.getScrollY() : 0;
        page = id;
        if (narrow && focusRows) {
            showingRows = true;
        }
        refreshNav();
        buildRows(null);
        rowsScroll.scrollTo(0, scrollY);
        if (focusRows) {
            focusRows();
        }
    }

    /** Narrow layout: from a page back to the page list */
    public void showList() {
        showingRows = false;
        refreshNav();
        updateHints();
        NavItem item = navItem(page);
        if (item != null && V2Input.showFocus(ctx)) {
            item.requestFocus();
        }
    }

    /**
     * Rebuilds the rows of the page (options, visibility or mode changed); the focus stays on the
     * row it was on when that row is still there
     */
    public void rebuildRows() {
        View focused = root.findFocus();
        SettingRowView at = rowOf(focused);
        String focusKey = at == null ? null : at.def.key;
        int stopIndex = at == null ? -1 : at.focusStops().indexOf(focused);
        int scrollY = rowsScroll.getScrollY();
        buildRows(null);
        rowsScroll.scrollTo(0, scrollY);
        if (focusKey != null) {
            focusRowStop(focusKey, stopIndex);
        }
    }

    /** The row that holds a view (the row itself, one of its buttons or theme cards), or null */
    public static SettingRowView rowOf(View v) {
        for (; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v instanceof SettingRowView) {
                return (SettingRowView) v;
            }
        }
        return null;
    }

    /** Focus a row again after a rebuild: the same button or theme card when it had one */
    public boolean focusRowStop(String key, int stopIndex) {
        SettingRowView r = rowFor(key);
        if (r == null) {
            return false;
        }
        List<View> stops = r.focusStops();
        if (stopIndex >= 0 && stopIndex < stops.size()) {
            return stops.get(stopIndex).requestFocus();
        }
        View entry = r.entryStop();
        return entry != null && entry.requestFocus();
    }

    private void buildRows(String unused) {
        rowsColumn.removeAllViews();
        rows.clear();
        moreButton = null;
        if (!narrow) {
            // Prototype: page title display 26, 4 px in, 4 under (then the 10 px gap)
            SettingsCatalog.Page p = SettingsCatalog.page(page);
            TextView pageTitle = Ui.display(ctx, theme, 26 * kt, theme.fg);
            pageTitle.setText(theme.displayText(p == null ? "" : ctx.getString(p.title)));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.bottomMargin = px(4);
            plp.setMarginStart(px(4));
            rowsColumn.addView(pageTitle, plp);
        }
        for (SettingDef def : SettingsCatalog.visibleRows(page, env, expert)) {
            SettingRowView row = new SettingRowView(ctx, theme, d.compact, def, actions, narrow ? (d.compact ? 0.85f : 1f) : k,
                    narrow ? (d.compact ? 0.95f : 1f) : kt);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!rows.isEmpty() || !narrow) {
                lp.topMargin = narrow ? Ui.dp(ctx, d.compact ? 10 : 12) : px(10);
            }
            rowsColumn.addView(row, lp);
            rows.add(row);
            bind(row);
        }
        int hidden = SettingsCatalog.hiddenCount(page, env, expert);
        if (hidden > 0) {
            moreButton = new MoreButton(ctx, hidden);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(px(46), Ui.dp(ctx, 40)));
            mlp.topMargin = narrow ? Ui.dp(ctx, d.compact ? 10 : 12) : px(10);
            rowsColumn.addView(moreButton, mlp);
        }
    }

    /** Rebinds every row to the stored values (after any write) */
    public void refresh() {
        for (SettingRowView row : rows) {
            bind(row);
        }
    }

    private void bind(SettingRowView row) {
        SettingDef def = row.def;
        row.setRowEnabled(def.enabled(store));
        switch (def.type) {
            case TOGGLE:
                row.bindToggle(store.toggleValue(def));
                break;
            case CHOICE: {
                List<Option> options = def.options(ctx, env, store, expert);
                row.bindChoice(options, store.choiceValue(def));
                if ("list_vrr_pacing_mode".equals(def.key)) {
                    // One description per choice, the desktop's (Marge du VRR)
                    String v = store.choiceValue(def);
                    row.setDescription(ctx.getString("0".equals(v) ? R.string.v2_set_vrr_mode_d_low
                            : "2".equals(v) ? R.string.v2_set_vrr_mode_d_smooth : R.string.v2_set_vrr_mode_d_balanced));
                }
                break;
            }
            case SLIDER:
                row.bindSlider(store.getInt(def.key), def.maxFor(store));
                if (SettingsCatalog.KEY_BITRATE.equals(def.key)) {
                    row.setValueSuffix(store.bitrateIsRecommended() ? ctx.getString(R.string.v2_set_bitrate_auto) : "");
                }
                break;
            case ACTION:
                if (SettingsCatalog.KEY_BITRATE_DEFAULT.equals(def.key)) {
                    // Prototype: already following, or the bitrate it will go back to
                    row.setDescription(store.bitrateIsRecommended() ? ctx.getString(R.string.v2_set_bitrate_default_already)
                            : ctx.getString(R.string.v2_set_bitrate_default_d, SettingsCatalog.mbps(ctx, store.defaultBitrate())));
                } else if ("option_check_update".equals(def.key)) {
                    // As on the desktop: the installed version
                    row.setDescription(ctx.getString(R.string.v2_set_check_update_d, com.limelight.BuildConfig.VERSION_NAME));
                }
                break;
            case BUTTONS:
                if (SettingsCatalog.KEY_PROFILES_MANAGE.equals(def.key)) {
                    boolean active = store.activeProfile() != null;
                    row.setButtonEnabled(1, active);
                    row.setButtonEnabled(2, active);
                }
                break;
            default:
                break;
        }
    }

    public SettingRowView rowFor(String key) {
        for (SettingRowView r : rows) {
            if (r.def.key.equals(key)) {
                return r;
            }
        }
        return null;
    }

    public List<SettingRowView> rows() {
        return rows;
    }

    /** The dashed « N réglages avancés masqués » button, or null */
    public View moreButton() {
        return moreButton;
    }

    /** The page list's item of a page (focus in tests) */
    public View navFor(String id) {
        return navItem(id);
    }

    // ---- expert mode ------------------------------------------------------------------------------

    public void setExpert(boolean on) {
        expert = on;
        String before = page;
        refreshNav();
        if (before.equals(page)) {
            rebuildRows();
        } else {
            buildRows(null);
        }
    }

    // ---- focus --------------------------------------------------------------------------------------

    private NavItem navItem(String id) {
        for (NavItem n : navItems) {
            if (n.page.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    public boolean focusNav() {
        if (narrow && showingRows) {
            return false;
        }
        NavItem item = navItem(page);
        if (item != null && item.getVisibility() == View.VISIBLE) {
            return item.requestFocus();
        }
        return expertCard.requestFocus();
    }

    public boolean focusRows() {
        for (SettingRowView r : rows) {
            View entry = r.entryStop();
            if (entry != null) {
                return entry.requestFocus();
            }
        }
        return moreButton != null && moreButton.requestFocus();
    }

    public boolean requestInitialFocus() {
        if (narrow && showingRows) {
            return focusRows();
        }
        return focusNav();
    }

    private boolean inRows(View v) {
        while (v != null) {
            if (v == rowsScroll) {
                return true;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    private boolean inNav(View v) {
        while (v != null) {
            if (v == navScroll) {
                return true;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    /** The page list as the gamepad goes through it: Mode expert, then the visible pages */
    private List<View> navStops() {
        List<View> out = new ArrayList<>();
        out.add(expertCard);
        for (NavItem n : navItems) {
            if (n.getVisibility() == View.VISIBLE) {
                out.add(n);
            }
        }
        return out;
    }

    /** The rows as the gamepad goes through them: a group per row (the row, its buttons, the theme cards), then « N masqués » */
    private List<List<View>> rowStops() {
        List<List<View>> out = new ArrayList<>();
        for (SettingRowView r : rows) {
            List<View> g = r.focusStops();
            if (!g.isEmpty()) {
                out.add(g);
            }
        }
        if (moreButton != null) {
            List<View> g = new ArrayList<>();
            g.add(moreButton);
            out.add(g);
        }
        return out;
    }

    /**
     * A D-pad move (decision of 2026-11-27): up / down stay in the column the focus is in (the
     * page list or the rows) and stop at its end, never jumping to the other column or the header;
     * right from the page list goes to the rows, left from a row to the page list. On a choice or a
     * slider left / right change the value first, and left at the end of its range goes to the
     * page list. In a row of buttons or theme cards left / right move between them. Always handled.
     */
    public boolean move(View focused, int dx, int dy) {
        boolean rowsShown = rowsScroll.getVisibility() == View.VISIBLE;
        boolean navShown = navScroll.getVisibility() == View.VISIBLE;
        if (focused != null && rowsShown && inRows(focused)) {
            moveInRows(focused, dx, dy);
        } else if (focused != null && navShown && inNav(focused)) {
            moveInNav(focused, dx, dy);
        } else {
            // The header's back button after a tap, or nothing: the screen's starting point
            requestInitialFocus();
        }
        return true;
    }

    private void moveInNav(View focused, int dx, int dy) {
        List<View> stops = navStops();
        int i = -1;
        for (View v = focused; v != null && i < 0; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            i = stops.indexOf(v);
        }
        if (i < 0) {
            focusNav();
            return;
        }
        if (dy != 0) {
            int j = Math.max(0, Math.min(stops.size() - 1, i + dy));
            if (j != i) {
                stops.get(j).requestFocus();
            }
            return;
        }
        if (dx > 0 && !narrow) {
            View item = stops.get(i);
            if (item instanceof NavItem && !((NavItem) item).page.id.equals(page)) {
                showPage(((NavItem) item).page.id, false);
            }
            focusRows();
        }
    }

    private void moveInRows(View focused, int dx, int dy) {
        List<List<View>> groups = rowStops();
        int gi = -1, si = -1;
        for (View v = focused; v != null && gi < 0; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            for (int g = 0; g < groups.size(); g++) {
                int at = groups.get(g).indexOf(v);
                if (at >= 0) {
                    gi = g;
                    si = at;
                    break;
                }
            }
        }
        if (gi < 0) {
            focusRows();
            return;
        }
        List<View> group = groups.get(gi);
        View stop = group.get(si);
        if (dy != 0) {
            int gj = gi + dy;
            if (gj >= 0 && gj < groups.size()) {
                View target = groups.get(gj).get(0);
                SettingRowView r = rowOf(target);
                View entry = r == null ? null : r.entryStop();
                (entry != null ? entry : target).requestFocus();
            }
            return;
        }
        if (dx == 0) {
            return;
        }
        if (stop instanceof SettingRowView) {
            if (((SettingRowView) stop).stepValue(dx)) {
                return;
            }
        } else if (group.size() > 1) {
            int sj = si + dx;
            if (sj >= 0 && sj < group.size()) {
                group.get(sj).requestFocus();
                return;
            }
        }
        if (dx < 0) {
            leaveRows();
        }
    }

    /** B / Back / Échap: rows -> page list -> leave (false) */
    public boolean onBack() {
        if (narrow && showingRows) {
            showList();
            return true;
        }
        View focused = root.findFocus();
        if (!narrow && focused != null && inRows(focused)) {
            focusNav();
            return true;
        }
        return false;
    }

    /** Left at the end of a row: back to the page list (wide layout) */
    public boolean leaveRows() {
        return !narrow && focusNav();
    }

    public void refreshInput() {
        root.invalidate();
        for (NavItem n : navItems) {
            n.paint();
        }
        expertCard.paint();
        updateHints();
    }

    /** The column the hints were made for: the page list (true) or the rows, null when hidden */
    private Boolean hintsOnNav;

    private boolean focusOnNav() {
        if (narrow) {
            return !showingRows;
        }
        View f = root.findFocus();
        return f != null && inNav(f) && f != expertCard;
    }

    private void updateHintsIfColumnChanged() {
        if (hintsOnNav != null && hintsOnNav != focusOnNav()) {
            updateHints();
        }
    }

    /**
     * Prototype hints of the settings: A Ouvrir on the page list, A Changer on the rows (and Mode
     * expert) · LB RB Page · B Retour; not at touch. Narrow too (R8), the page keeping clear of it.
     */
    public void updateHints() {
        if (hints == null) {
            return;
        }
        hints.clear();
        V2Input.Kind kind = V2Input.get(ctx);
        boolean show = kind != V2Input.Kind.TOUCH;
        if (narrow) {
            int bottom = show ? HintsBar.reservedHeight(ctx, theme, hintsScale()) : 0;
            if (pageLayout.getPaddingBottom() != bottom) {
                pageLayout.setPadding(pageLayout.getPaddingLeft(), pageLayout.getPaddingTop(), pageLayout.getPaddingRight(), bottom);
            }
        }
        if (!show) {
            hints.setVisibility(View.GONE);
            hintsOnNav = null;
            return;
        }
        hints.setVisibility(View.VISIBLE);
        hintsOnNav = focusOnNav();
        boolean pad = kind == V2Input.Kind.PAD;
        hints.add(pad ? "A" : ctx.getString(R.string.v2_key_enter),
                ctx.getString(hintsOnNav ? R.string.v2_hint_open : R.string.v2_details_change), null);
        hints.add(pad ? "LB RB" : "Q E", ctx.getString(R.string.v2_hint_page), () -> stepPage(1));
        hints.add(pad ? "B" : ctx.getString(R.string.v2_key_escape), ctx.getString(R.string.v2_hint_back), actions::back);
    }

    private float hintsScale() {
        return Math.max(0.85f, k);
    }

    // ---- views --------------------------------------------------------------------------------------

    /** « Mode expert / Affiche les réglages avancés » with its switch, on top of the page list */
    private final class ExpertCard extends V2Frame {
        final V2Switch sw;

        ExpertCard(Context context) {
            super(context, SettingsScreen.this.theme);
            setFocusable(true);
            setClickable(true);
            setRing(theme.rs, 5, theme.fg);
            // Prototype: min-h 64, radius rs, border, surface, padding 10 16; « Mode expert » 15/700,
            // « Affiche les réglages avancés » 12 muted; switch 46 x 26
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(16), px(10), px(16), px(10));
            row.setMinimumHeight(px(64));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(context, theme, 15 * kt, 700, theme.fg);
            t.setText(R.string.v2_set_expert);
            texts.addView(t);
            TextView s = Ui.text(context, theme, 12 * kt, 400, theme.muted);
            s.setMaxLines(2);
            s.setText(R.string.v2_set_expert_d);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = px(2);
            texts.addView(s, slp);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            sw = new V2Switch(context, theme, 46 * k, 26 * k);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.setMarginStart(px(10));
            row.addView(sw, wlp);
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setOnClickListener(v -> actions.setExpert(!expert));
            paint();
        }

        void paint() {
            int b = theme.border > 0 ? Ui.dp(getContext(), theme.border) : 0;
            setBackground(Ui.roundRect(isPressed() ? theme.surface2 : theme.surface, Ui.dpf(getContext(), theme.rs), b, theme.borderColor));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            paint();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !narrow) {
                focusRows();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * « N réglages avancés masqués · activer le mode expert » (prototype smore): 46 high, radius rs,
     * 2 px dashed muted border, muted 14/600; turns expert mode on and puts the focus on the first row
     */
    private final class MoreButton extends V2Frame {
        MoreButton(Context context, int hidden) {
            super(context, SettingsScreen.this.theme);
            setFocusable(true);
            setClickable(true);
            setRing(theme.rs, 5, theme.fg);
            android.graphics.drawable.GradientDrawable g = Ui.roundRect(0, Ui.dpf(context, theme.rs), 0, 0);
            g.setStroke(Ui.dp(context, 2), theme.muted, Ui.dpf(context, 6), Ui.dpf(context, 4));
            setBackground(g);
            TextView t = Ui.text(context, theme, 14 * kt, 600, theme.muted);
            t.setGravity(Gravity.CENTER);
            t.setText(context.getResources().getQuantityString(R.plurals.v2_set_hidden_rows, hidden, hidden));
            addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setOnClickListener(v -> {
                actions.setExpert(true);
                if (V2Input.showFocus(getContext())) {
                    focusRows();
                }
            });
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return leaveRows() || true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }

    /** A page of the list: accent fill when it is the page shown (wide layout) */
    private final class NavItem extends V2Frame {
        final SettingsCatalog.Page page;
        private final TextView text;
        private final ImageView chevron;
        private final TextView expertTag;
        private boolean selected;

        void setExpertOnly(boolean on) {
            Ui.setVisible(expertTag, on && !narrow);
        }

        NavItem(Context context, SettingsCatalog.Page p) {
            super(context, SettingsScreen.this.theme);
            this.page = p;
            setFocusable(true);
            setClickable(true);
            setRing(narrow ? Math.min(theme.pill, 24) : theme.rs, 5, theme.fg);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(narrow ? Ui.dp(context, 18) : px(16), 0, narrow ? Ui.dp(context, 14) : px(16), 0);
            // Prototype: the display font, 16/700, never upper case (the page names keep their case)
            text = Ui.display(context, theme, narrow ? (d.compact ? 17 : 20) : 16 * kt, theme.fg);
            text.setAllCaps(false);
            text.setText(context.getString(p.title));
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            expertTag = Ui.text(context, theme, 11 * kt, 700, theme.fg);
            expertTag.setText(R.string.v2_set_expert_tag);
            expertTag.setAlpha(0.7f);
            expertTag.setVisibility(GONE);
            row.addView(expertTag);
            chevron = new ImageView(context);
            int cs = Ui.dp(context, 18);
            row.addView(chevron, new LinearLayout.LayoutParams(cs, cs));
            Ui.setVisible(chevron, narrow);
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setContentDescription(context.getString(p.title));
            setOnClickListener(v -> {
                if (narrow) {
                    showPage(page.id, false);
                    showingRows = true;
                    refreshNav();
                    rowsScroll.scrollTo(0, 0);
                    if (V2Input.showFocus(getContext())) {
                        focusRows();
                    }
                } else {
                    showPage(page.id, V2Input.showFocus(getContext()));
                }
            });
            paint();
        }

        @Override
        public void setSelected(boolean s) {
            selected = s;
            paint();
        }

        void paint() {
            boolean pressed = isPressed();
            int fill = selected ? theme.accent : (pressed ? theme.surface2 : 0);
            float r = Ui.dpf(getContext(), narrow ? (theme.square() ? 0 : Math.min(theme.pill, 24)) : theme.rs);
            setBackground(fill == 0 ? null : Ui.roundRect(fill, r, 0, 0));
            int fg = selected ? theme.onAccent : theme.fg;
            text.setTextColor(fg);
            expertTag.setTextColor(fg);
            text.setTypeface(theme.displayFont(getContext(), 700));
            chevron.setImageDrawable(Ui.icon(getContext(), R.drawable.v2_ic_chevron_right, theme.muted));
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            paint();
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            // Moving through the list with the gamepad or the keys opens the page (desktop)
            if (gainFocus && !narrow && V2Input.showFocus(getContext()) && !page.id.equals(SettingsScreen.this.page)) {
                showPage(page.id, false);
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!narrow) {
                    if (!page.id.equals(SettingsScreen.this.page)) {
                        showPage(page.id, false);
                    }
                    focusRows();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }
}
