package com.limelight.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.v2.ui.AppCard;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.DotDrawable;
import com.limelight.v2.ui.FitGridLayoutManager;
import com.limelight.v2.ui.IconButton;
import com.limelight.v2.ui.PillButton;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The v2 home, built in code from plain data ({@link State}): header (PC switcher, light/dark,
 * Mes PC, Réglages), Scène of the running session, Bureau and Playnite Fullscreen cards, then the
 * "Récents" row of the Playnite library. No service here: HomeActivity feeds it, tests too.
 */
public final class HomeScreen {
    public interface Actions {
        void switchHost(int delta);

        void pickHost();

        void openHosts();

        void openSettings();

        void cycleMode();

        /** Long press (or Y) on the light/dark button: Braise, Affiche, Ludique */
        void pickTheme();

        void launch(NvApp app);

        void options(NvApp app);

        void resumeRunning();

        void quitRunning();

        void openLibrary();

        void wake();
    }

    public static final class State {
        public List<ComputerDetails> hosts = Collections.emptyList();
        public int hostIndex;
        /** null while the app list of the shown PC is not known */
        public HomeModel model;
        /** Minutes of the running session, -1 when unknown or under a minute */
        public long sessionMinutes = -1;

        public ComputerDetails host() {
            return hosts.isEmpty() ? null : hosts.get(Math.min(Math.max(hostIndex, 0), hosts.size() - 1));
        }
    }

    private final Context ctx;
    private final V2Theme theme;
    private final Density d;
    private final Actions actions;
    private CoverSource covers;

    private final FrameLayout root;
    private final CoverView backdrop;
    private final View shade;
    private final ScrollView scroll;
    private final LinearLayout page;

    private final IconButton prevHost, nextHost, modeButton;
    private final V2Frame hostChip;
    private final TextView hostName, hostStatus;
    private final View hostsButton, settingsButton;

    private final LinearLayout empty;
    private final TextView emptyTitle, emptyText;
    private final PillButton wakeButton, emptyHostsButton;

    private final LinearLayout scene;
    private final TextView liveLine, sceneTitle;
    private final PillButton resumeButton, quitButton;

    private final LinearLayout cardsRow;
    private final List<AppCard> cards = new ArrayList<>();

    private final LinearLayout recentsHeader;
    private final TextView recentsLabel;
    private final V2Frame seeAllLink;
    private final RecyclerView recents;
    private final CoverAdapter recentsAdapter;

    private final LinearLayout hints;

    private State state = new State();
    private int backdropAppId = Integer.MIN_VALUE;
    private NvApp focusedApp;

    public HomeScreen(Context context, V2Theme theme, Density density, Actions actions, CoverSource covers) {
        this.ctx = context;
        this.theme = theme;
        this.d = density;
        this.actions = actions;
        this.covers = covers;

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.bg);

        // Backdrop: the running game's cover, veiled by the page colour (density boards)
        backdrop = new CoverView(context);
        backdrop.setAlpha(theme.dark ? 0.55f : 0.35f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shade = new ShadeView(context, theme.bg);
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(context, d.side);
        page.setPadding(side, Ui.dp(context, d.top), side, Ui.dp(context, 56));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.noClip(root, scroll, page);

        // ---- header ----------------------------------------------------------------------
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, d.headerHeight)));

        View mark = new View(context);
        mark.setBackground(Ui.roundRect(theme.accent, theme.square() ? 0 : Ui.dpf(context, 4), 0, 0));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(context, 12), Ui.dp(context, 12));
        mlp.setMarginStart(Ui.dp(context, 4));
        mlp.setMarginEnd(Ui.dp(context, 12));
        header.addView(mark, mlp);

        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.roundRect(Ui.withAlpha(theme.surface, 0.85f), Ui.dpf(context, Math.min(theme.pill + 10, 24)),
                Ui.dp(context, theme.border), theme.borderColor));
        Ui.noClip(pill);
        // Narrow (portrait phone): the pill takes what the buttons leave and the name shortens
        boolean narrow = d.widthDp < 600;
        header.addView(pill, narrow ? new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 48)));

        prevHost = new IconButton(context, theme, R.drawable.v2_ic_chevron_left, 48, 32, 16, 0, true);
        prevHost.setContentDescription(context.getString(R.string.v2_prev_pc));
        prevHost.setOnClickListener(v -> actions.switchHost(-1));
        pill.addView(prevHost, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));

        hostChip = new V2Frame(context, theme);
        hostChip.setFocusable(true);
        hostChip.setClickable(true);
        hostChip.setRing(theme.square() ? 0 : 20, 2, theme.accent);
        hostChip.setOnClickListener(v -> actions.pickHost());
        hostChip.setContentDescription(context.getString(R.string.v2_pick_pc));
        LinearLayout chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        chipRow.setPadding(Ui.dp(context, 6), 0, Ui.dp(context, 6), 0);
        hostName = Ui.text(context, theme, 15, 700, theme.fg);
        hostName.setMaxWidth(Ui.dp(context, d.compact ? 150 : 240));
        chipRow.addView(hostName);
        hostStatus = Ui.text(context, theme, 13, 500, theme.muted);
        chipRow.addView(hostStatus);
        hostChip.addView(chipRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
        pill.addView(hostChip, narrow ? new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 48)));

        nextHost = new IconButton(context, theme, R.drawable.v2_ic_chevron_right, 48, 32, 16, 0, true);
        nextHost.setContentDescription(context.getString(R.string.v2_next_pc));
        nextHost.setOnClickListener(v -> actions.switchHost(1));
        pill.addView(nextHost, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));

        if (!narrow) {
            header.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        }

        modeButton = new IconButton(context, theme, theme.dark ? R.drawable.v2_ic_moon : R.drawable.v2_ic_sun,
                48, 48, 22, theme.surface, true);
        modeButton.setBadge(theme.mode == V2Theme.MODE_SYSTEM ? "A" : null);
        String[] modes = {context.getString(R.string.v2_mode_system), context.getString(R.string.v2_mode_light), context.getString(R.string.v2_mode_dark)};
        modeButton.setContentDescription(context.getString(R.string.v2_theme_mode, modes[theme.mode]));
        modeButton.setOnClickListener(v -> actions.cycleMode());
        modeButton.setOnLongClickListener(v -> {
            actions.pickTheme();
            return true;
        });
        header.addView(modeButton, headerButtonLp(context));

        hostsButton = headerButton(context, R.drawable.v2_ic_monitor, context.getString(R.string.v2_my_pcs));
        hostsButton.setOnClickListener(v -> actions.openHosts());
        header.addView(hostsButton, headerButtonLp(context));
        settingsButton = headerButton(context, R.drawable.v2_ic_gear, context.getString(R.string.v2_settings));
        settingsButton.setOnClickListener(v -> actions.openSettings());
        header.addView(settingsButton, headerButtonLp(context));

        // ---- empty states ----------------------------------------------------------------
        empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.noClip(empty);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = Ui.dp(context, d.compact ? 36 : 70);
        page.addView(empty, elp);
        emptyTitle = Ui.display(context, theme, d.compact ? 28 : 40, theme.fg);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setMaxLines(2);
        empty.addView(emptyTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyText = Ui.text(context, theme, 16, 400, theme.muted);
        emptyText.setMaxLines(Integer.MAX_VALUE);
        emptyText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etlp.topMargin = Ui.dp(context, 10);
        empty.addView(emptyText, etlp);
        LinearLayout emptyButtons = new LinearLayout(context);
        emptyButtons.setOrientation(LinearLayout.HORIZONTAL);
        Ui.noClip(emptyButtons);
        LinearLayout.LayoutParams eblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        eblp.topMargin = Ui.dp(context, 22);
        empty.addView(emptyButtons, eblp);
        wakeButton = new PillButton(context, theme, PillButton.Style.PRIMARY, R.drawable.v2_ic_power, context.getString(R.string.v2_wake), 52, 18);
        wakeButton.setOnClickListener(v -> actions.wake());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.setMarginEnd(Ui.dp(context, 14));
        emptyButtons.addView(wakeButton, wlp);
        emptyHostsButton = new PillButton(context, theme, PillButton.Style.PRIMARY, R.drawable.v2_ic_monitor, context.getString(R.string.v2_add_pc), 52, 18);
        emptyHostsButton.setOnClickListener(v -> actions.openHosts());
        emptyButtons.addView(emptyHostsButton);

        // ---- Scène ------------------------------------------------------------------------
        scene = new LinearLayout(context);
        scene.setOrientation(d.stackScene ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        scene.setGravity(d.stackScene ? Gravity.START : Gravity.CENTER_VERTICAL);
        Ui.noClip(scene);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(context, d.compact ? 10 : 12);
        page.addView(scene, slp);
        LinearLayout sceneText = new LinearLayout(context);
        sceneText.setOrientation(LinearLayout.VERTICAL);
        scene.addView(sceneText, d.stackScene
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int liveColor = theme.isAffiche() ? theme.fg : (theme.isLudique() && !theme.dark ? theme.fg : theme.accent);
        liveLine = Ui.text(context, theme, 13, 700, liveColor);
        liveLine.setMaxLines(d.stackScene ? 2 : 1);
        liveLine.setCompoundDrawablesRelativeWithIntrinsicBounds(new DotDrawable(theme.isAffiche() ? theme.live : liveColor, Ui.dp(context, 8)), null, null, null);
        liveLine.setCompoundDrawablePadding(Ui.dp(context, 7));
        sceneText.addView(liveLine);
        sceneTitle = Ui.display(context, theme, d.sceneTitle, theme.fg);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = Ui.dp(context, 4);
        sceneText.addView(sceneTitle, stlp);

        LinearLayout sceneButtons = new LinearLayout(context);
        sceneButtons.setOrientation(LinearLayout.HORIZONTAL);
        sceneButtons.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(sceneButtons);
        LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (d.stackScene) {
            sblp.topMargin = Ui.dp(context, 10);
        } else {
            sblp.setMarginStart(Ui.dp(context, 14));
        }
        scene.addView(sceneButtons, sblp);
        quitButton = new PillButton(context, theme, PillButton.Style.SECONDARY, R.drawable.v2_ic_stop, context.getString(R.string.v2_quit), 52, 17);
        quitButton.setOnClickListener(v -> actions.quitRunning());
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.setMarginEnd(Ui.dp(context, 12));
        sceneButtons.addView(quitButton, qlp);
        resumeButton = new PillButton(context, theme, PillButton.Style.PRIMARY, R.drawable.v2_ic_play, context.getString(R.string.v2_resume), 52, 17);
        resumeButton.setHints("A", context.getString(R.string.v2_key_enter));
        resumeButton.setOnClickListener(v -> actions.resumeRunning());
        sceneButtons.addView(resumeButton);

        // ---- Bureau + Playnite Fullscreen ----------------------------------------------------
        cardsRow = new LinearLayout(context);
        cardsRow.setOrientation(d.stackCards ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        Ui.noClip(cardsRow);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(context, d.sectionGap);
        page.addView(cardsRow, clp);

        // ---- Récents ----------------------------------------------------------------------
        recentsHeader = new LinearLayout(context);
        recentsHeader.setOrientation(LinearLayout.HORIZONTAL);
        recentsHeader.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(recentsHeader);
        LinearLayout.LayoutParams rhlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 24));
        rhlp.topMargin = Ui.dp(context, d.sectionGap);
        page.addView(recentsHeader, rhlp);
        recentsLabel = Ui.text(context, theme, d.sectionLabel, 700, theme.fg);
        recentsLabel.setAllCaps(theme.upper);
        recentsHeader.addView(recentsLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        seeAllLink = new V2Frame(context, theme);
        seeAllLink.setFocusable(true);
        seeAllLink.setClickable(true);
        seeAllLink.setRing(theme.square() ? 0 : 10, 0, theme.accent);
        seeAllLink.setContentDescription(context.getString(R.string.v2_see_all));
        TextView seeAllText = Ui.text(context, theme, d.sectionLabel, 700, liveColor);
        seeAllText.setText(R.string.v2_see_all_arrow);
        seeAllText.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 4), 0);
        seeAllLink.addView(seeAllText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        seeAllLink.setOnClickListener(v -> actions.openLibrary());
        // 48 dp touch target over a 24 dp line
        LinearLayout.LayoutParams salp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 48));
        salp.topMargin = -Ui.dp(context, 12);
        salp.bottomMargin = -Ui.dp(context, 12);
        recentsHeader.addView(seeAllLink, salp);

        recents = new RecyclerView(context);
        recents.setItemAnimator(null);
        recents.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recents.setNestedScrollingEnabled(false);
        Ui.noClip(recents);
        final int gap = Ui.dp(context, d.coverGap);
        // Landscape: one row that scrolls sideways. Portrait: the covers wrap into a grid, the page
        // has the height for it
        final boolean portraitGrid = d.heightDp > d.widthDp;
        if (portraitGrid) {
            FitGridLayoutManager glm = new FitGridLayoutManager(context, Ui.dp(context, d.coverWidth), gap, null);
            recents.setLayoutManager(glm);
            recents.addItemDecoration(glm.decoration(null));
        } else {
            recents.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            recents.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State s) {
                    if (parent.getChildAdapterPosition(view) > 0) {
                        outRect.left = gap;
                    }
                }
            });
        }
        recentsAdapter = new CoverAdapter(context, theme, d.coverWidth, d.coverHeight, covers, new CoverAdapter.Listener() {
            @Override
            public void onLaunch(NvApp app) {
                actions.launch(app);
            }

            @Override
            public void onOptions(NvApp app) {
                actions.options(app);
            }

            @Override
            public void onSeeAll() {
                actions.openLibrary();
            }

            @Override
            public void onFocusedApp(NvApp app) {
                focusedApp = app;
                updateRecentsLabel();
            }
        });
        recents.setAdapter(recentsAdapter);
        // Room for the halo and the focus ring above and below the covers
        int ringRoom = Ui.dp(context, 10);
        recents.setPadding(0, ringRoom, 0, ringRoom);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                portraitGrid ? ViewGroup.LayoutParams.WRAP_CONTENT : Ui.dp(context, d.coverHeight) + 2 * ringRoom);
        rlp.topMargin = Ui.dp(context, 8) - ringRoom;
        page.addView(recents, rlp);

        // ---- hints (gamepad / keyboard) -----------------------------------------------------
        hints = new LinearLayout(context);
        hints.setOrientation(LinearLayout.HORIZONTAL);
        hints.setGravity(Gravity.CENTER_VERTICAL);
        hints.setBackground(Ui.roundRect(Ui.withAlpha(theme.bg, 0.85f), Ui.dpf(context, theme.square() ? 0 : 14), 0, 0));
        hints.setPadding(Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 4));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hlp.setMargins(0, 0, Ui.dp(context, d.side), Ui.dp(context, 8));
        root.addView(hints, hlp);

        bind(state);
    }

    private View headerButton(Context context, int icon, String label) {
        if (d.headerLabels) {
            return new PillButton(context, theme, PillButton.Style.HEADER, icon, label, 48, 15);
        }
        IconButton b = new IconButton(context, theme, icon, 48, 48, 22, theme.surface, true);
        b.setContentDescription(label);
        return b;
    }

    private static LinearLayout.LayoutParams headerButtonLp(Context context) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 48));
        lp.setMarginStart(Ui.dp(context, 10));
        return lp;
    }

    public View getView() {
        return root;
    }

    public State getState() {
        return state;
    }

    public void setCoverSource(CoverSource covers) {
        this.covers = covers;
        backdropAppId = Integer.MIN_VALUE;
        recentsAdapter.setCoverSource(covers);
        bind(state);
    }

    // ---- binding ---------------------------------------------------------------------------

    public void bind(State s) {
        this.state = s;
        ComputerDetails host = s.host();
        HomeModel m = s.model;
        boolean online = host != null && HostList.isOnline(host);
        boolean ready = online && m != null;
        boolean session = ready && m.sessionRunning();

        // Header
        hostName.setText(host == null ? "" : host.name.toUpperCase(Locale.ROOT));
        String status;
        if (host == null) {
            status = "";
        } else if (online) {
            status = ctx.getString(session ? R.string.v2_status_session : R.string.v2_status_online);
        } else if (host.state == ComputerDetails.State.OFFLINE) {
            status = ctx.getString(R.string.v2_status_offline);
        } else {
            status = ctx.getString(R.string.v2_status_unknown);
        }
        hostStatus.setText(status.isEmpty() ? "" : "  ·  " + status);
        // Arrows when there is room; a narrow screen keeps the name, a tap on it lists the PCs
        boolean arrows = s.hosts.size() > 1 && d.widthDp >= 600;
        Ui.setVisible(prevHost, arrows);
        Ui.setVisible(nextHost, arrows);
        ((View) hostChip.getParent()).setVisibility(host == null ? View.GONE : View.VISIBLE);
        hostChip.setPadding(arrows ? 0 : Ui.dp(ctx, 10), 0, arrows ? 0 : Ui.dp(ctx, 10), 0);

        // Empty states
        boolean showEmpty = !ready;
        Ui.setVisible(empty, showEmpty);
        if (showEmpty) {
            if (host == null) {
                emptyTitle.setText(theme.displayText(ctx.getString(R.string.v2_no_pc)));
                emptyText.setText(R.string.v2_no_pc_text);
                emptyHostsButton.setText(ctx.getString(R.string.v2_add_pc));
                Ui.setVisible(wakeButton, false);
            } else if (online) {
                emptyTitle.setText("");
                emptyText.setText(R.string.v2_loading);
                emptyHostsButton.setText(ctx.getString(R.string.v2_my_pcs));
                Ui.setVisible(wakeButton, false);
            } else if (host.state == ComputerDetails.State.OFFLINE) {
                emptyTitle.setText(theme.displayText(ctx.getString(R.string.v2_pc_offline, host.name)));
                emptyText.setText(R.string.v2_pc_offline_text);
                emptyHostsButton.setText(ctx.getString(R.string.v2_my_pcs));
                Ui.setVisible(wakeButton, host.macAddress != null);
            } else {
                emptyTitle.setText(theme.displayText(ctx.getString(R.string.v2_pc_searching, host.name)));
                emptyText.setText("");
                emptyHostsButton.setText(ctx.getString(R.string.v2_my_pcs));
                Ui.setVisible(wakeButton, false);
            }
            Ui.setVisible(emptyTitle, emptyTitle.getText().length() > 0);
            Ui.setVisible(emptyText, emptyText.getText().length() > 0);
        }

        // Scène
        Ui.setVisible(scene, session);
        if (session) {
            String desktop = ctx.getString(R.string.v2_desktop);
            String playnite = ctx.getString(R.string.v2_playnite);
            StringBuilder live = new StringBuilder(ctx.getString(R.string.v2_live));
            if (!m.playniteGame.isEmpty()) {
                live.append(" · ").append(ctx.getString(R.string.v2_live_inside, m.runningTitle(desktop, playnite)));
            }
            if (s.sessionMinutes >= 0) {
                live.append(" · ").append(s.sessionMinutes < 60
                        ? ctx.getString(R.string.v2_since_min, (int) s.sessionMinutes)
                        : ctx.getString(R.string.v2_since_hours, (int) (s.sessionMinutes / 60), (int) (s.sessionMinutes % 60)));
            }
            liveLine.setText(live.toString().toUpperCase(Locale.FRENCH));
            String title = m.sceneTitle(desktop, playnite);
            sceneTitle.setText(theme.displayText(title.isEmpty() ? ctx.getString(R.string.v2_live) : title));
        }
        bindBackdrop(session ? m.backdropApp() : null);

        // Cards
        List<NvApp> cardApps = ready ? m.cards : Collections.<NvApp>emptyList();
        Ui.setVisible(cardsRow, !cardApps.isEmpty());
        bindCards(cardApps, m);

        // Récents
        boolean hasGames = ready && !m.recents.isEmpty();
        Ui.setVisible(recentsHeader, hasGames);
        Ui.setVisible(recents, hasGames);
        if (ready) {
            recentsAdapter.submit(m, m.recents, m.games.size() > 0);
        }
        updateRecentsLabel();
        applyInput(false);
    }

    private void bindBackdrop(NvApp app) {
        int id = app == null ? Integer.MIN_VALUE : app.getAppId();
        if (id == backdropAppId) {
            return;
        }
        backdropAppId = id;
        backdrop.setBitmap(null);
        if (app != null && covers != null) {
            Bitmap b = covers.peek(app);
            if (b != null) {
                backdrop.setBitmap(b);
            } else {
                covers.load(app, (appId, bmp) -> {
                    if (appId == backdropAppId) {
                        backdrop.setBitmap(bmp);
                    }
                });
            }
        }
    }

    private void bindCards(List<NvApp> apps, HomeModel m) {
        if (cards.size() != apps.size()) {
            cardsRow.removeAllViews();
            cards.clear();
            for (int i = 0; i < apps.size(); i++) {
                AppCard card = new AppCard(ctx, theme, d.cardHeight, d.compact);
                LinearLayout.LayoutParams lp = d.stackCards
                        ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, d.cardHeight))
                        : new LinearLayout.LayoutParams(0, Ui.dp(ctx, d.cardHeight), 1f);
                if (i > 0) {
                    if (d.stackCards) {
                        lp.topMargin = Ui.dp(ctx, 12);
                    } else {
                        lp.setMarginStart(Ui.dp(ctx, 12));
                    }
                }
                cardsRow.addView(card, lp);
                cards.add(card);
            }
        }
        String desktop = ctx.getString(R.string.v2_desktop);
        String playnite = ctx.getString(R.string.v2_playnite);
        for (int i = 0; i < apps.size(); i++) {
            final NvApp app = apps.get(i);
            final AppCard card = cards.get(i);
            boolean isPlaynite = HomeModel.kindOf(app) == HomeModel.Kind.PLAYNITE;
            boolean running = m.isRunning(app);
            String live = isPlaynite && !m.playniteGame.isEmpty()
                    ? ctx.getString(R.string.v2_live_game, m.playniteGame) : ctx.getString(R.string.v2_live);
            card.bind(isPlaynite ? playnite : desktop,
                    ctx.getString(isPlaynite ? R.string.v2_playnite_subtitle : R.string.v2_desktop_subtitle),
                    live, running, m.isDimmed(app));
            Object previous = card.getTag();
            card.setTag(app);
            card.setOnClickListener(v -> actions.launch(app));
            card.setOnLongClickListener(v -> {
                actions.options(app);
                return true;
            });
            card.setOnMoreClickListener(v -> actions.options(app));
            if (covers != null && (!(previous instanceof NvApp) || ((NvApp) previous).getAppId() != app.getAppId() || card.getTag(R.id.v2_cover_loaded) == null)) {
                Bitmap b = covers.peek(app);
                card.setCover(b);
                card.setTag(R.id.v2_cover_loaded, b != null ? Boolean.TRUE : null);
                if (b == null) {
                    covers.load(app, (id, bmp) -> {
                        if (card.getTag() instanceof NvApp && ((NvApp) card.getTag()).getAppId() == id) {
                            card.setCover(bmp);
                            card.setTag(R.id.v2_cover_loaded, Boolean.TRUE);
                        }
                    });
                }
            }
        }
    }

    private void updateRecentsLabel() {
        String label = ctx.getString(R.string.v2_recents);
        if (focusedApp != null && V2Input.showFocus(ctx)) {
            label += "  —  " + focusedApp.getAppName();
        }
        recentsLabel.setText(label);
    }

    /** The input changed (touch / pad / keys): rings, "…" buttons and hints follow */
    public void refreshInput() {
        applyInput(true);
    }

    private void applyInput(boolean changed) {
        V2Input.Kind k = V2Input.get(ctx);
        boolean pad = k == V2Input.Kind.PAD;
        boolean keys = k == V2Input.Kind.KEYS;
        for (AppCard c : cards) {
            c.refreshInput();
        }
        if (changed) {
            recentsAdapter.notifyItemRangeChanged(0, recentsAdapter.getItemCount());
        }
        resumeButton.refreshHint();
        prevHost.setLabel(pad ? "LB" : (keys ? "Q" : null));
        nextHost.setLabel(pad ? "RB" : (keys ? "E" : null));
        buildHints(k);
        updateRecentsLabel();
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

    private void buildHints(V2Input.Kind k) {
        hints.removeAllViews();
        if (k == V2Input.Kind.TOUCH || state.host() == null) {
            hints.setVisibility(View.GONE);
            return;
        }
        hints.setVisibility(View.VISIBLE);
        boolean pad = k == V2Input.Kind.PAD;
        addHint(pad ? "A" : ctx.getString(R.string.v2_key_enter), ctx.getString(R.string.v2_hint_launch));
        addHint(pad ? "Y" : ctx.getString(R.string.v2_key_menu), ctx.getString(R.string.v2_hint_options));
        if (state.hosts.size() > 1) {
            addHint(pad ? "LB RB" : "Q E", ctx.getString(R.string.v2_hint_switch_pc));
        }
    }

    private void addHint(String key, String label) {
        TextView k = Ui.text(ctx, theme, 11, 800, theme.bg);
        k.setText(key);
        k.setGravity(Gravity.CENTER);
        int h = Ui.dp(ctx, 22);
        k.setMinWidth(h);
        k.setPadding(Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6), 0);
        k.setBackground(Ui.roundRect(theme.fg, theme.square() ? 0 : h / 2f, 0, 0));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h);
        if (hints.getChildCount() > 0) {
            klp.setMarginStart(Ui.dp(ctx, 14));
        }
        hints.addView(k, klp);
        TextView l = Ui.text(ctx, theme, 13, 500, theme.fg);
        l.setText(label);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.setMarginStart(Ui.dp(ctx, 7));
        hints.addView(l, llp);
    }

    // ---- focus -----------------------------------------------------------------------------

    /** Gamepad / keyboard: where the focus lands first (Reprendre, else the first card or cover) */
    public boolean requestInitialFocus() {
        if (scene.getVisibility() == View.VISIBLE && resumeButton.requestFocus()) {
            return true;
        }
        if (!cards.isEmpty() && cardsRow.getVisibility() == View.VISIBLE && cards.get(0).requestFocus()) {
            return true;
        }
        if (recents.getVisibility() == View.VISIBLE && recents.getChildCount() > 0 && recents.getChildAt(0).requestFocus()) {
            return true;
        }
        if (empty.getVisibility() == View.VISIBLE) {
            if (wakeButton.getVisibility() == View.VISIBLE && wakeButton.requestFocus()) {
                return true;
            }
            if (emptyHostsButton.requestFocus()) {
                return true;
            }
        }
        return settingsButton.requestFocus();
    }

    public boolean isThemeButton(View v) {
        return v == modeButton;
    }

    /** The app a focused view stands for (Y / Menu opens its options); Reprendre stands for the running app */
    public NvApp appFor(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getTag() instanceof NvApp) {
                return (NvApp) v.getTag();
            }
            if (v == scene && state.model != null) {
                return state.model.runningApp();
            }
            if (v == root) {
                break;
            }
        }
        return null;
    }

    /** Page veil over the backdrop: transparent at the top, the page colour from 70 % down */
    private static final class ShadeView extends View {
        private final Paint paint = new Paint();
        private final int color;

        ShadeView(Context context, int color) {
            super(context);
            this.color = color;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Ui.withAlpha(color, 0.55f), Ui.withAlpha(color, 0.85f), color, color},
                    new float[]{0f, 0.4f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}
