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
    private final TextView title;
    private final ScrollView navScroll;
    private final ScrollView rowsScroll;
    private final LinearLayout rowsColumn;
    private final ExpertCard expertCard;
    private final List<NavItem> navItems = new ArrayList<>();
    private final List<SettingRowView> rows = new ArrayList<>();

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

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.bg);
        LinearLayout pageLayout = new LinearLayout(context);
        pageLayout.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(context, d.side);
        pageLayout.setPadding(side, Ui.dp(context, d.top), side, 0);
        root.addView(pageLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.noClip(root, pageLayout);

        // ---- header: back, « Réglages » (or the page in the narrow layout) --------------------------
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        pageLayout.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, d.headerHeight)));
        IconButton back = new IconButton(context, theme, R.drawable.v2_ic_chevron_left, 48, 44, 20, theme.surface, true);
        back.setContentDescription(context.getString(R.string.v2_back));
        back.setOnClickListener(v -> actions.back());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        title = Ui.display(context, theme, d.compact ? 26 : 32, theme.fg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(context, 14));
        header.addView(title, tlp);

        // ---- body: page list | rows ---------------------------------------------------------------
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.HORIZONTAL);
        // The body clips: the scrolled rows must not draw over the header
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        blp.topMargin = Ui.dp(context, d.compact ? 6 : 14);
        pageLayout.addView(body, blp);

        navScroll = scroll(context);
        LinearLayout navColumn = column(context);
        navScroll.addView(navColumn, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        expertCard = new ExpertCard(context);
        navColumn.addView(expertCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (SettingsCatalog.Page p : SettingsCatalog.PAGES) {
            NavItem item = new NavItem(context, p);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, d.compact ? 48 : 54));
            ilp.topMargin = Ui.dp(context, navItems.isEmpty() ? 14 : 6);
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
            body.addView(navScroll, new LinearLayout.LayoutParams(Ui.dp(context, d.compact ? 250 : 290), ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            rlp.setMarginStart(Ui.dp(context, d.compact ? 6 : 12));
            body.addView(rowsScroll, rlp);
        }

        page = initialPage;
        showingRows = narrow && showRows;
        refreshNav();
        showPage(page, false);
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
        int pad = Ui.dp(context, 10);
        c.setPadding(pad, pad, pad, Ui.dp(context, 24));
        Ui.noClip(c);
        return c;
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
            List<SettingsCatalog.Page> pages = visiblePages();
            page = pages.isEmpty() ? SettingsCatalog.PROFIL : pages.get(0).id;
        }
        for (NavItem item : navItems) {
            Ui.setVisible(item, pageVisible(item.page.id));
            item.setSelected(!narrow && item.page.id.equals(page));
        }
        expertCard.sw.setOn(expert);
        Ui.setVisible(navScroll, !narrow || !showingRows);
        Ui.setVisible(rowsScroll, !narrow || showingRows);
        SettingsCatalog.Page p = SettingsCatalog.page(page);
        String t = narrow && showingRows && p != null ? ctx.getString(p.title) : ctx.getString(R.string.v2_settings);
        title.setText(theme.displayText(t));
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
        String focusKey = focused instanceof SettingRowView ? ((SettingRowView) focused).def.key : null;
        int buttonIndex = -1;
        if (focused != null && !(focused instanceof SettingRowView)) {
            for (SettingRowView r : rows) {
                int i = r.getButtons().indexOf(focused);
                if (i >= 0) {
                    focusKey = r.def.key;
                    buttonIndex = i;
                }
            }
        }
        int scrollY = rowsScroll.getScrollY();
        buildRows(null);
        rowsScroll.scrollTo(0, scrollY);
        if (focusKey != null) {
            SettingRowView r = rowFor(focusKey);
            if (r != null) {
                if (buttonIndex >= 0 && buttonIndex < r.getButtons().size()) {
                    r.getButtons().get(buttonIndex).requestFocus();
                } else {
                    r.requestFocus();
                }
            }
        }
    }

    private void buildRows(String unused) {
        rowsColumn.removeAllViews();
        rows.clear();
        if (!narrow) {
            SettingsCatalog.Page p = SettingsCatalog.page(page);
            TextView pageTitle = Ui.display(ctx, theme, d.compact ? 24 : 30, theme.fg);
            pageTitle.setText(theme.displayText(p == null ? "" : ctx.getString(p.title)));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.bottomMargin = Ui.dp(ctx, d.compact ? 10 : 14);
            plp.topMargin = Ui.dp(ctx, 4);
            rowsColumn.addView(pageTitle, plp);
        }
        for (SettingDef def : SettingsCatalog.visibleRows(page, env, expert)) {
            SettingRowView row = new SettingRowView(ctx, theme, d.compact, def, actions);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (!rows.isEmpty()) {
                lp.topMargin = Ui.dp(ctx, d.compact ? 10 : 12);
            }
            rowsColumn.addView(row, lp);
            rows.add(row);
            bind(row);
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
                break;
            case ACTION:
                if (SettingsCatalog.KEY_BITRATE_DEFAULT.equals(def.key)) {
                    row.setDescription(ctx.getString(R.string.v2_set_bitrate_default_d, SettingsCatalog.mbps(ctx, store.defaultBitrate())));
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
            if (r.isFocusable()) {
                return r.requestFocus();
            }
            if (!r.getButtons().isEmpty()) {
                return r.getButtons().get(0).requestFocus();
            }
        }
        return false;
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
    }

    // ---- views --------------------------------------------------------------------------------------

    /** « Mode expert / Affiche les réglages avancés » with its switch, on top of the page list */
    private final class ExpertCard extends V2Frame {
        final V2Switch sw;

        ExpertCard(Context context) {
            super(context, SettingsScreen.this.theme);
            setFocusable(true);
            setClickable(true);
            setRing(theme.r, 5, theme.fg);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int ph = Ui.dp(context, d.compact ? 16 : 20);
            int pv = Ui.dp(context, d.compact ? 12 : 16);
            row.setPadding(ph, pv, ph, pv);
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(context, theme, d.compact ? 16 : 18, 700, theme.fg);
            t.setText(R.string.v2_set_expert);
            texts.addView(t);
            TextView s = Ui.text(context, theme, d.compact ? 13 : 14, 400, theme.muted);
            s.setMaxLines(2);
            s.setText(R.string.v2_set_expert_d);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(context, 3);
            texts.addView(s, slp);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            sw = new V2Switch(context, theme, d.compact ? 50 : 56, d.compact ? 28 : 32);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.setMarginStart(Ui.dp(context, 12));
            row.addView(sw, wlp);
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setOnClickListener(v -> actions.setExpert(!expert));
            paint();
        }

        void paint() {
            setBackground(Ui.roundRect(isPressed() ? theme.surface2 : theme.surface, Ui.dpf(getContext(), theme.r),
                    Ui.dp(getContext(), Math.max(1, theme.border)), theme.borderColor));
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

    /** A page of the list: accent fill when it is the page shown (wide layout) */
    private final class NavItem extends V2Frame {
        final SettingsCatalog.Page page;
        private final TextView text;
        private final ImageView chevron;
        private boolean selected;

        NavItem(Context context, SettingsCatalog.Page p) {
            super(context, SettingsScreen.this.theme);
            this.page = p;
            setFocusable(true);
            setClickable(true);
            setRing(Math.min(theme.pill, 24), 5, theme.fg);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 14), 0);
            text = Ui.display(context, theme, d.compact ? 17 : 20, theme.fg);
            text.setText(theme.displayText(context.getString(p.title)));
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
            float r = Ui.dpf(getContext(), theme.square() ? 0 : Math.min(theme.pill, 24));
            setBackground(fill == 0 ? null : Ui.roundRect(fill, r, theme.isAffiche() && selected ? Ui.dp(getContext(), 3) : 0, theme.fg));
            int fg = selected ? theme.onAccent : theme.fg;
            text.setTextColor(fg);
            text.setTypeface(theme.displayFont(getContext(), selected ? 700 : 600));
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
