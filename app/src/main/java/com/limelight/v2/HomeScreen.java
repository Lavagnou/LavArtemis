package com.limelight.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.v2.HomeParts.AppCard;
import com.limelight.v2.HomeParts.Button;
import com.limelight.v2.HomeParts.CardData;
import com.limelight.v2.HomeParts.Cover;
import com.limelight.v2.HomeParts.LibTile;
import com.limelight.v2.HomeParts.StateCard;
import com.limelight.v2.ui.CoverView;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.AppIconDrawable;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.limelight.v2.HomeParts.argb;
import static com.limelight.v2.HomeParts.px;
import static com.limelight.v2.HomeParts.pxf;
import static com.limelight.v2.HomeParts.rect;

/**
 * The v2 home, a copy of the validated prototype (V8, Proto.dc.html home of each theme): one layout
 * per theme, not one layout recoloured.
 * <ul>
 * <li>Braise: the hero image of what is focused or running under a veil, header (logo, DEV, Mise à
 * jour; PC switcher; light/dark, Mes PC, Réglages), the Scène (live line, title, Reprendre, Quitter),
 * then from the bottom: "Sur PC", the two app cards, "Bibliothèque Playnite · récents", six covers
 * and the "Tout voir" tile.</li>
 * <li>Affiche: flat page; header band with a thick rule, the Scène as an ink band, the cards as two
 * full-width columns with their art column, then the covers and a wide "Tout voir" tile.</li>
 * <li>Ludique: "Salut ! / On joue sur quoi ?", a navy PC switcher, the Scène as a navy bar, colour
 * block cards with a tilted art, round covers.</li>
 * </ul>
 * Sizes come from {@link HomeLayout} (tablet = prototype; console and phone from the density
 * boards). Built from plain data ({@link State}): HomeActivity feeds it, tests too. The gamepad /
 * keyboard focus follows the prototype's grid ({@link #focusRows()}).
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

        /** The PC state card: wake, Mes PC, add a PC... (prototype cardAct) */
        void wake();

        /** The "Mise à jour" pill of the header */
        default void openUpdate() {
        }

        /** A hint of the bar was tapped: A, Y, LB / RB */
        default void hint(String key) {
        }
    }

    public static final class State {
        public List<ComputerDetails> hosts = Collections.emptyList();
        public int hostIndex;
        /** null while the app list of the shown PC is not known */
        public HomeModel model;
        /** Minutes of the running session, -1 when unknown, 0 under a minute */
        public long sessionMinutes = -1;
        /** A newer LavArtemis is available: the header shows "Mise à jour" */
        public boolean updateAvailable;
        /** Another device is connected to the host: "Rejoindre" instead of "Reprendre" (choice 23) */
        public boolean otherDevice;

        public ComputerDetails host() {
            return hosts.isEmpty() ? null : hosts.get(Math.min(Math.max(hostIndex, 0), hosts.size() - 1));
        }
    }

    /** What the PC state card says (prototype homeCard), or null when the PC is ready */
    enum CardState { FIRSTRUN, SEARCHING, LOADING, OFFLINE_WAKE, OFFLINE_NO_MAC }

    static final int RECENTS_SHOWN = 6;

    private final Context ctx;
    private final V2Theme th;
    private final HomeLayout l;
    private final Actions actions;
    private CoverSource covers;

    private final FrameLayout root;
    private final CoverView hero;
    private final LinearLayout page;

    // header
    private final View prevHost, nextHost;
    private final TextView prevLabel, nextLabel, hostName, hostStatus;
    private final View hostBox;
    private final Button modeButton, hostsButton, settingsButton;
    private View updatePill;

    // scène
    private final View scene;
    private final TextView liveLine, sceneTitle, sceneVia;
    private final Button resumeButton, quitButton;
    private final TextView resumeLabel;
    private View heroTitleBlock;
    private TextView heroSub, heroName;

    // content
    private final TextView onHost;
    private final StateCard stateCard;
    private final View stateCardBox;
    private final LinearLayout cardsRow;
    private View cardsRule;
    private final List<AppCard> cards = new ArrayList<>();
    private final View sectionRow;
    private final TextView caption;
    private final View seeAllLink;
    private final View recentsScroll;
    private final LinearLayout recentsRow;
    private final List<Cover> recentTiles = new ArrayList<>();
    private final LibTile libTile;
    private final HintsBar hints;

    private State state = new State();
    private CardState cardState;
    private NvApp focusedRecent;
    private NvApp focusedCard;
    private int heroAppId = Integer.MIN_VALUE;

    public HomeScreen(Context context, V2Theme theme, Density density, Actions actions, CoverSource covers) {
        this(context, theme, HomeLayout.forSize(density.widthDp, density.heightDp), actions, covers);
    }

    public HomeScreen(Context context, V2Theme theme, HomeLayout layout, Actions actions, CoverSource covers) {
        this.ctx = context;
        this.th = theme;
        this.l = layout;
        this.actions = actions;
        this.covers = covers;
        boolean af = th.isAffiche(), lu = th.isLudique(), br = th.isBraise();

        root = new FrameLayout(ctx);
        root.setBackgroundColor(th.bg);

        // ---- Braise hero: an image under a veil that fades into the page ----------------------
        hero = new CoverView(ctx);
        if (br) {
            hero.setSaturation(1.2f);
            root.addView(hero, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(new HomeParts.GradientView(ctx, 180f,
                    new int[]{argb(th.lit.veil, 0.25f), argb(th.lit.veil, 0.6f), th.lit.veil, th.lit.veil},
                    new float[]{0f, 0.38f, 0.74f, 1f}),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.noClip(root, scroll, page);
        float side = af ? 0 : (lu ? 48 : 40);
        if (l.phone()) {
            side = af ? 0 : 20 / l.k;
        }
        // A phone: the header and the covers come close to the edges, the focus ring must still fit
        // (V2Frame.ringRoom: Ludique's 9 dp ring cut 1 dp off the header pills and the covers)
        int coverRing = V2Frame.ringRoom(ctx, th, px(ctx, l, (af ? 198 : (lu ? 186 : 180)) * 1.13f));
        int bottom = l.phone() ? Math.max(Ui.dp(ctx, 8), coverRing) : px(ctx, l, af ? 72 : (lu ? 84 : 64));
        int top = l.phone() ? Math.max(Ui.dp(ctx, 8), V2Frame.ringRoom(ctx, th, 0)) : px(ctx, l, lu ? 26 : 24);
        page.setPadding(px(ctx, l, side), af ? 0 : top, px(ctx, l, side), bottom);
        pageBottom = bottom;

        // ---- header ----------------------------------------------------------------------------
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        int headerH;
        if (l.phone()) {
            headerH = Ui.dp(ctx, 48);
        } else if (af) {
            headerH = px(ctx, l, 60);
            header.setPadding(px(ctx, l, 32), 0, px(ctx, l, 32), 0);
        } else {
            headerH = px(ctx, l, lu ? 58 : 44);
        }
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerH));
        if (af) {
            page.addView(HomeParts.ruleV(ctx, l, th.fg), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
        }

        // left: logo, wordmark, DEV, Mise à jour (phone: the logo alone)
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.HORIZONTAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(left);
        header.addView(left, l.phone() ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        buildBrand(left);

        // centre: the PC switcher
        LinearLayout sw = new LinearLayout(ctx);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(sw);
        hostBox = sw;
        TextView[] lbls = new TextView[2];
        View[] arrows = new View[2];
        TextView[] names = new TextView[2];
        buildSwitcher(sw, arrows, lbls, names);
        prevHost = arrows[0];
        nextHost = arrows[1];
        prevLabel = lbls[0];
        nextLabel = lbls[1];
        hostName = names[0];
        hostStatus = names[1];
        LinearLayout.LayoutParams swlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                af ? ViewGroup.LayoutParams.MATCH_PARENT : (l.phone() ? Ui.dp(ctx, 40) : px(ctx, l, lu ? 58 : 44)));
        if (l.phone()) {
            swlp.setMarginStart(Ui.dp(ctx, 12));
        }
        header.addView(sw, swlp);

        // right: light / dark, Mes PC, Réglages
        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        Ui.noClip(right);
        header.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        modeButton = modeButton();
        right.addView(modeButton, headerButtonLp(true, 0));
        hostsButton = headerPill(R.drawable.v2_ic_p_monitor, ctx.getString(R.string.v2_my_pcs));
        hostsButton.setOnClickListener(v -> actions.openHosts());
        right.addView(hostsButton, headerButtonLp(false, 8));
        settingsButton = headerPill(R.drawable.v2_ic_p_cog, ctx.getString(R.string.v2_settings));
        settingsButton.setOnClickListener(v -> actions.openSettings());
        right.addView(settingsButton, headerButtonLp(false, 8));

        // ---- Scène (or Braise's hero title) ------------------------------------------------------
        TextView[] st = new TextView[4];
        Button[] sb = new Button[2];
        scene = buildScene(st, sb);
        liveLine = st[0];
        sceneTitle = st[1];
        sceneVia = st[2];
        resumeLabel = st[3];
        resumeButton = sb[0];
        quitButton = sb[1];
        resumeButton.setOnClickListener(v -> actions.resumeRunning());
        quitButton.setOnClickListener(v -> actions.quitRunning());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = af ? 0 : (l.phone() ? Ui.dp(ctx, 10) : px(ctx, l, 16));
        page.addView(scene, slp);
        if (br) {
            buildHeroTitle();
        }

        // Braise: the content hangs from the bottom of the screen
        if (br) {
            page.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        }

        onHost = HomeParts.text(ctx, th, l, 14, 600, th.lit.text2, false);
        LinearLayout.LayoutParams ohlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ohlp.bottomMargin = px(ctx, l, 12);
        ohlp.topMargin = px(ctx, l, 12);
        page.addView(onHost, ohlp);
        Ui.setVisible(onHost, br && !l.phone());

        // ---- PC state card -------------------------------------------------------------------------
        stateCard = new StateCard(ctx, th, l);
        stateCard.setOnClickListener(v -> actions.wake());
        FrameLayout scBox = new FrameLayout(ctx);
        Ui.noClip(scBox);
        stateCardBox = scBox;
        if (af) {
            scBox.setPadding(px(ctx, l, 32), px(ctx, l, 40), px(ctx, l, 32), 0);
            scBox.addView(stateCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 260)));
        } else if (lu) {
            scBox.setPadding(0, px(ctx, l, 56), 0, 0);
            scBox.addView(stateCard, new FrameLayout.LayoutParams(px(ctx, l, 600), px(ctx, l, 220)));
        } else {
            scBox.addView(stateCard, new FrameLayout.LayoutParams(px(ctx, l, 640), px(ctx, l, 150)));
        }
        page.addView(scBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- app cards ---------------------------------------------------------------------------
        cardsRow = new LinearLayout(ctx);
        cardsRow.setOrientation(l.portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        Ui.noClip(cardsRow);
        LinearLayout.LayoutParams crlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (lu) {
            crlp.topMargin = px(ctx, l, 16);
        }
        page.addView(cardsRow, crlp);
        if (af) {
            cardsRule = HomeParts.ruleV(ctx, l, th.fg);
            page.addView(cardsRule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, 3)));
        }

        // ---- "Bibliothèque Playnite · récents" ---------------------------------------------------
        LinearLayout section = new LinearLayout(ctx);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(section);
        sectionRow = section;
        TextView label = HomeParts.text(ctx, th, l, af ? 15 : (lu ? 20 : 14), af ? 800 : (lu ? 800 : 600), af ? th.fg : th.lit.text2, false);
        if (lu) {
            label.setLetterSpacing(-0.02f);
        }
        String sectionText = ctx.getString(R.string.v2_recents_section);
        label.setText(af ? sectionText.toUpperCase(Locale.FRENCH) : sectionText);
        section.addView(label);
        caption = HomeParts.text(ctx, th, l, af || lu ? 15 : 14, 500, lu ? th.lit.muted2 : th.muted, false);
        LinearLayout.LayoutParams caplp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        caplp.setMarginStart(px(ctx, l, af ? 18 : 14));
        section.addView(caption, caplp);
        Button link = new Button(ctx, th);
        link.radius(0);
        TextView linkText = HomeParts.text(ctx, th, l, lu ? 15 : 14, af || lu ? 700 : 600, th.muted, false);
        linkText.setText(R.string.v2_see_all_arrow);
        link.addView(linkText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        link.setFocusable(false);
        link.setOnClickListener(v -> actions.openLibrary());
        link.setPadding(Ui.dp(ctx, 8), 0, 0, 0);
        seeAllLink = link;
        section.addView(link);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (af) {
            section.setPadding(px(ctx, l, 32), px(ctx, l, 14), px(ctx, l, 32), px(ctx, l, 12));
        } else if (lu) {
            secLp.topMargin = px(ctx, l, 18);
            secLp.bottomMargin = px(ctx, l, 16);
        } else {
            secLp.topMargin = px(ctx, l, 22);
            secLp.bottomMargin = px(ctx, l, 12);
        }
        page.addView(section, secLp);

        // ---- covers + "Tout voir" ------------------------------------------------------------------
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setFillViewport(false);
        recentsScroll = hs;
        recentsRow = new LinearLayout(ctx);
        recentsRow.setOrientation(LinearLayout.HORIZONTAL);
        recentsRow.setGravity(Gravity.TOP);
        int ring = Math.max(Ui.dp(ctx, 12), coverRing);
        // The row reaches out by the ring into the page's side padding; Affiche has none (its covers
        // start 32 px in): the scroller stays on the screen, or its last cover's ring went past the edge
        int out = af ? 0 : ring;
        recentsRow.setPadding(af ? px(ctx, l, 32) : ring, ring, af ? px(ctx, l, 32) : ring, ring);
        hs.addView(recentsRow);
        Ui.noClip(hs, recentsRow);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(-out, -ring, -out, -ring);
        page.addView(hs, rlp);
        libTile = new LibTile(ctx, th, l);
        libTile.setOnClickListener(v -> actions.openLibrary());

        if (!br) {
            page.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        }

        // ---- hints -----------------------------------------------------------------------------------
        hints = new HintsBar(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k));
        root.addView(hints, hints.layoutParams());

        bind(state);
    }

    // ---- header pieces -------------------------------------------------------------------------------

    private void buildBrand(LinearLayout left) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        View logo = new View(ctx);
        // The Dev keeps its own icon (decision of 2026-11-26), not the theme's logo + DEV chip
        logo.setBackground(new AppIconDrawable(ctx));
        int ls = l.phone() ? Ui.dp(ctx, 30) : px(ctx, l, lu ? 52 : (af ? 30 : 34));
        left.addView(logo, new LinearLayout.LayoutParams(ls, ls));
        if (l.phone()) {
            return;
        }
        if (lu) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView hello = HomeParts.text(ctx, th, l, 16, 600, th.lit.muted2, false);
            hello.setText(R.string.v2_hello);
            line.addView(hello);
            updatePill = updatePill();
            line.addView(updatePill, sized(updatePill, 10));
            col.addView(line);
            TextView what = HomeParts.text(ctx, th, l, 34, 800, th.fg, true);
            what.setLetterSpacing(-0.03f);
            what.setText(R.string.v2_what_play);
            col.addView(what);
            HomeParts.cssLine(what, 1.05f);
            LinearLayout.LayoutParams clp = marginStart(14);
            left.addView(col, clp);
            return;
        }
        TextView word = HomeParts.text(ctx, th, l, af ? 18 : 22, 700, th.fg, false);
        word.setTypeface(spaceGrotesk(700));
        word.setLetterSpacing(af ? 0 : -0.4f / 22f);
        word.setText(R.string.v2_brand);
        left.addView(word, marginStart(10));
        updatePill = updatePill();
        left.addView(updatePill, sized(updatePill, af ? 10 : 14));
    }

    /** The wordmark is Space Grotesk in every theme (prototype headers) */
    private android.graphics.Typeface spaceGrotesk(int weight) {
        return V2Theme.of(V2Theme.BRAISE, th.dark).displayFont(ctx, weight);
    }

    private View updatePill() {
        Button b = new Button(ctx, th);
        TextView t;
        if (th.isLudique()) {
            t = HomeParts.text(ctx, th, l, 12, 800, 0xFF1B1530, false);
            b.setBackground(rect(th.lit.lime, pxf(ctx, l, 12), 0, 0));
            b.radius(l.b(12));
        } else {
            t = HomeParts.text(ctx, th, l, 13, 700, th.fg, false);
            b.setBackground(rect(th.surface, pxf(ctx, l, th.pill), px(ctx, l, th.border), th.borderColor));
            b.radius(l.b(th.pill));
        }
        t.setText(R.string.v2_update_pill);
        LinearLayout row = HomeParts.iconLabel(ctx, l, R.drawable.v2_ic_p_update, th.isLudique() ? 12 : 16, th.isLudique() ? 5 : 7, t,
                th.isLudique() ? 0xFF1B1530 : th.fg);
        row.setPadding(px(ctx, l, th.isLudique() ? 10 : 12), 0, px(ctx, l, th.isLudique() ? 10 : 12), 0);
        b.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ctx, l, th.isLudique() ? 24 : 36)));
        b.setFocusable(false);
        b.setOnClickListener(v -> actions.openUpdate());
        return b;
    }

    /** The view's own height, plus a start margin */
    private LinearLayout.LayoutParams sized(View v, float protoPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((ViewGroup.MarginLayoutParams) v.getLayoutParams());
        lp.setMarginStart(px(ctx, l, protoPx));
        return lp;
    }

    private LinearLayout.LayoutParams marginStart(float protoPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(px(ctx, l, protoPx));
        return lp;
    }

    /** arrows[0..1] = ‹ ›, lbls = their texts, names = name and status */
    private void buildSwitcher(LinearLayout sw, View[] arrows, TextView[] lbls, TextView[] names) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        int fgName, fgStatus;
        if (lu) {
            sw.setPadding(px(ctx, l, 8), 0, px(ctx, l, 8), 0);
            sw.setBackground(rect(th.lit.band, pxf(ctx, l, 29), 0, 0));
            fgName = th.lit.onBand;
            fgStatus = th.lit.onBandChip;
        } else if (af) {
            fgName = th.fg;
            fgStatus = th.lit.muted2;
        } else {
            sw.setPadding(px(ctx, l, 6), 0, px(ctx, l, 6), 0);
            sw.setBackground(rect(argb(th.lit.veil, l.phone() ? 0.75f : 0.7f), pxf(ctx, l, 22), 0, 0));
            fgName = th.fg;
            fgStatus = th.lit.muted2;
        }
        for (int i = 0; i < 2; i++) {
            final int delta = i == 0 ? -1 : 1;
            Button b = new Button(ctx, th);
            b.setFocusable(false);
            TextView t;
            if (lu) {
                t = HomeParts.text(ctx, th, l, 13, 800, th.lit.onBandChip, false);
                b.setBackground(rect(th.lit.bandChip, pxf(ctx, l, 21), 0, 0));
                t.setPadding(px(ctx, l, 14), 0, px(ctx, l, 14), 0);
            } else if (af) {
                t = HomeParts.text(ctx, th, l, 12, 800, th.fg, false);
                b.setBackground(rect(Color.TRANSPARENT, 0, Math.max(1, px(ctx, l, 2)), th.fg));
                t.setPadding(px(ctx, l, 8), px(ctx, l, 3), px(ctx, l, 8), px(ctx, l, 3));
            } else {
                t = HomeParts.text(ctx, th, l, 12, 700, th.lit.muted2, false);
                b.setBackground(rect(Color.TRANSPARENT, pxf(ctx, l, 16), Math.max(1, px(ctx, l, 1)), th.lit.chipBorder));
                t.setPadding(px(ctx, l, 10), 0, px(ctx, l, 10), 0);
            }
            t.setGravity(Gravity.CENTER);
            b.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            b.setContentDescription(ctx.getString(i == 0 ? R.string.v2_prev_pc : R.string.v2_next_pc));
            b.setOnClickListener(v -> actions.switchHost(delta));
            arrows[i] = b;
            lbls[i] = t;
        }
        int armH = af ? ViewGroup.LayoutParams.WRAP_CONTENT : px(ctx, l, lu ? 42 : 32);
        if (!l.phone()) {
            sw.addView(arrows[0], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, armH));
        }
        LinearLayout centre = new LinearLayout(ctx);
        centre.setClickable(true);
        centre.setOnClickListener(v -> actions.pickHost());
        TextView name, status;
        if (af) {
            centre.setOrientation(LinearLayout.HORIZONTAL);
            centre.setGravity(Gravity.CENTER_VERTICAL);
            name = HomeParts.text(ctx, th, l, 20, 900, fgName, false);
            name.setLetterSpacing(-0.02f);
            status = HomeParts.text(ctx, th, l, 14, 600, fgStatus, false);
            centre.addView(name);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.setMarginStart(px(ctx, l, 14));
            centre.addView(status, slp);
        } else if (l.phone()) {
            centre.setOrientation(LinearLayout.HORIZONTAL);
            centre.setGravity(Gravity.CENTER_VERTICAL);
            centre.setPadding(Ui.dp(ctx, 14), 0, Ui.dp(ctx, 14), 0);
            name = HomeParts.text(ctx, th, l, 15, lu ? 800 : 700, fgName, false);
            status = HomeParts.text(ctx, th, l, 13, 500, fgStatus, false);
            centre.addView(name);
            centre.addView(status);
        } else {
            centre.setOrientation(LinearLayout.VERTICAL);
            centre.setGravity(Gravity.CENTER);
            centre.setMinimumWidth(px(ctx, l, lu ? 170 : 190));
            name = HomeParts.text(ctx, th, l, lu ? 17 : 15, lu ? 800 : 700, fgName, false);
            status = HomeParts.text(ctx, th, l, 12, 400, fgStatus, false);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            status.setGravity(Gravity.CENTER_HORIZONTAL);
            centre.addView(name);
            centre.addView(status);
        }
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (!l.phone()) {
            int gap = px(ctx, l, af ? 14 : (lu ? 10 : 8));
            clp.setMarginStart(gap);
            clp.setMarginEnd(gap);
        }
        sw.addView(centre, clp);
        if (!l.phone()) {
            sw.addView(arrows[1], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, armH));
        }
        names[0] = name;
        names[1] = status;
    }

    private Button modeButton() {
        Button b = new Button(ctx, th);
        int size = l.phone() ? Ui.dp(ctx, 48) : px(ctx, l, 40);
        b.setBackground(rect(phoneRound() ? argb(th.lit.veil, 0.75f) : th.surface,
                l.phone() && !th.isAffiche() ? size / 2f : pxf(ctx, l, th.pill), px(ctx, l, th.border), th.borderColor));
        b.radius(l.phone() && !th.isAffiche() ? 24 : l.b(th.pill));
        int is = l.phone() ? Ui.dp(ctx, 22) : px(ctx, l, 18);
        b.addView(HomeParts.icon(ctx, th.mode == V2Theme.MODE_LIGHT ? R.drawable.v2_ic_p_sun
                        : (th.mode == V2Theme.MODE_DARK ? R.drawable.v2_ic_p_moon : (th.dark ? R.drawable.v2_ic_p_moon : R.drawable.v2_ic_p_sun)), th.fg),
                new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        if (th.mode == V2Theme.MODE_SYSTEM) {
            TextView a = HomeParts.text(ctx, th, l, 9, 800, th.muted, false);
            a.setText("A");
            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            alp.setMargins(0, 0, px(ctx, l, 3) + (l.phone() ? Ui.dp(ctx, 6) : 0), px(ctx, l, 2) + (l.phone() ? Ui.dp(ctx, 6) : 0));
            b.addView(a, alp);
        }
        String[] modes = {ctx.getString(R.string.v2_mode_system), ctx.getString(R.string.v2_mode_light), ctx.getString(R.string.v2_mode_dark)};
        b.setContentDescription(ctx.getString(R.string.v2_theme_mode, modes[th.mode]));
        b.setFocusable(false);
        b.setOnClickListener(v -> actions.cycleMode());
        b.setOnLongClickListener(v -> {
            actions.pickTheme();
            return true;
        });
        b.setMinimumWidth(size);
        return b;
    }

    /** Phone header buttons: round and veiled (Densite-Telephone) */
    private boolean phoneRound() {
        return l.phone() && th.isBraise();
    }

    private Button headerPill(int icon, String label) {
        Button b = new Button(ctx, th);
        if (l.phone()) {
            // Densite-Telephone: a round 48 dp icon button, no label
            int size = Ui.dp(ctx, 48);
            b.setBackground(rect(phoneRound() ? argb(th.lit.veil, 0.75f) : th.surface, th.isAffiche() ? 0 : size / 2f,
                    px(ctx, l, th.border), th.borderColor));
            b.radius(th.isAffiche() ? 0 : 24);
            int is = Ui.dp(ctx, 22);
            b.addView(HomeParts.icon(ctx, icon, th.fg), new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
            b.setMinimumWidth(size);
            b.setContentDescription(label);
            return b;
        }
        b.setBackground(rect(th.surface, pxf(ctx, l, th.pill), px(ctx, l, th.border), th.borderColor));
        b.radius(l.b(th.pill));
        TextView t = HomeParts.text(ctx, th, l, 14, 700, th.fg, false);
        t.setText(label);
        LinearLayout row = HomeParts.iconLabel(ctx, l, icon, 18, 8, t, th.fg);
        row.setPadding(px(ctx, l, 14), 0, px(ctx, l, 14), 0);
        b.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return b;
    }

    private LinearLayout.LayoutParams headerButtonLp(boolean square, float gapPx) {
        int h = l.phone() ? Ui.dp(ctx, 48) : px(ctx, l, 40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(square || l.phone() ? h : ViewGroup.LayoutParams.WRAP_CONTENT, h);
        lp.setMarginStart(l.phone() ? Ui.dp(ctx, 10) : px(ctx, l, gapPx));
        return lp;
    }

    // ---- Scène -------------------------------------------------------------------------------------

    /** st = live line, title, via, resume label; sb = Reprendre, Quitter */
    private View buildScene(TextView[] st, Button[] sb) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        float btnMin = l.phone() ? 40 : 0;
        if (af) {
            LinearLayout band = new LinearLayout(ctx);
            band.setOrientation(LinearLayout.HORIZONTAL);
            band.setGravity(Gravity.CENTER_VERTICAL);
            band.setBackgroundColor(th.lit.band);
            band.setPadding(px(ctx, l, 32), 0, px(ctx, l, 32), 0);
            band.setMinimumHeight(px(ctx, l, 90));
            Ui.noClip(band);
            LinearLayout text = new LinearLayout(ctx);
            text.setOrientation(LinearLayout.VERTICAL);
            band.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            st[0] = HomeParts.text(ctx, th, l, 13, 800, th.lit.bandLive, false);
            HomeParts.liveDot(st[0], ctx, l, th.lit.bandLive, 8);
            text.addView(st[0]);
            st[1] = HomeParts.text(ctx, th, l, 40, 900, th.lit.onBand, true);
            st[1].setLetterSpacing(-0.045f);
            HomeParts.lineHeight(st[1], ctx, l, 40, 0.95f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = px(ctx, l, 4);
            text.addView(st[1], tlp);
            HomeParts.cssLine(st[1], 0.95f);
            st[2] = null;
            Button resume = new Button(ctx, th);
            resume.setBackgroundColor(th.lit.onBand);
            st[3] = HomeParts.text(ctx, th, l, 20, 900, th.lit.band, false);
            st[3].setGravity(Gravity.CENTER);
            st[3].setPadding(px(ctx, l, 24), 0, px(ctx, l, 24), 0);
            resume.addView(st[3], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 54), Ui.dp(ctx, btnMin)));
            rlp.setMarginStart(px(ctx, l, 24));
            band.addView(resume, rlp);
            Button quit = new Button(ctx, th);
            quit.setBackground(rect(Color.TRANSPARENT, 0, px(ctx, l, 3), th.lit.onBand));
            TextView q = HomeParts.text(ctx, th, l, 20, 900, th.lit.onBand, false);
            q.setText(ctx.getString(R.string.v2_quit).toUpperCase(Locale.FRENCH));
            q.setGravity(Gravity.CENTER);
            q.setPadding(px(ctx, l, 20), 0, px(ctx, l, 20), 0);
            quit.addView(q, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 54), Ui.dp(ctx, btnMin)));
            qlp.setMarginStart(px(ctx, l, 24));
            band.addView(quit, qlp);
            sb[0] = resume;
            sb[1] = quit;
            return band;
        }
        if (lu) {
            LinearLayout bar = new LinearLayout(ctx);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackground(rect(th.lit.band, pxf(ctx, l, 32), 0, 0));
            bar.setPadding(px(ctx, l, 24), 0, px(ctx, l, 9), 0);
            bar.setMinimumHeight(px(ctx, l, 64));
            Ui.noClip(bar);
            // Portrait: the live line on top, Reprendre / Quitter under it (in one row they were pushed
            // off the right of a 412 dp screen)
            LinearLayout line = bar, buttons = bar;
            if (l.portrait) {
                bar.setOrientation(LinearLayout.VERTICAL);
                bar.setGravity(Gravity.START);
                bar.setPadding(px(ctx, l, 24), px(ctx, l, 14), px(ctx, l, 24), px(ctx, l, 14));
                line = new LinearLayout(ctx);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                Ui.noClip(line);
                bar.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                buttons = new LinearLayout(ctx);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                buttons.setGravity(Gravity.CENTER_VERTICAL);
                Ui.noClip(buttons);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = px(ctx, l, 12);
                bar.addView(buttons, blp);
            }
            st[0] = HomeParts.text(ctx, th, l, 13, 800, th.lit.bandLive, false);
            HomeParts.liveDot(st[0], ctx, l, th.lit.bandLive, 7);
            line.addView(st[0]);
            st[1] = HomeParts.text(ctx, th, l, 24, 800, th.lit.onBand, true);
            st[1].setLetterSpacing(-0.02f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f);
            tlp.setMarginStart(px(ctx, l, 16));
            line.addView(st[1], tlp);
            st[2] = HomeParts.text(ctx, th, l, 14, 600, th.lit.onBandChip, false);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            vlp.setMarginStart(px(ctx, l, 16));
            line.addView(st[2], vlp);
            Button resume = new Button(ctx, th);
            resume.setBackground(rect(th.lit.lime, pxf(ctx, l, 23), 0, 0));
            resume.radius(l.b(23));
            st[3] = HomeParts.text(ctx, th, l, 16, 800, 0xFF1B1530, false);
            LinearLayout rr = HomeParts.iconLabel(ctx, l, R.drawable.v2_ic_p_play, 14, 8, st[3], 0xFF1B1530);
            rr.setPadding(px(ctx, l, 22), 0, px(ctx, l, 22), 0);
            resume.addView(rr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 46), Ui.dp(ctx, btnMin)));
            rlp.setMarginStart(l.portrait ? 0 : px(ctx, l, 16));
            buttons.addView(resume, rlp);
            Button quit = new Button(ctx, th);
            quit.setBackground(rect(Color.TRANSPARENT, pxf(ctx, l, 23), px(ctx, l, 2), th.lit.onBand));
            quit.radius(l.b(23));
            TextView q = HomeParts.text(ctx, th, l, 16, 800, th.lit.onBand, false);
            q.setText(R.string.v2_quit);
            q.setGravity(Gravity.CENTER);
            q.setPadding(px(ctx, l, 18), 0, px(ctx, l, 18), 0);
            quit.addView(q, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 46), Ui.dp(ctx, btnMin)));
            qlp.setMarginStart(px(ctx, l, 16));
            buttons.addView(quit, qlp);
            sb[0] = resume;
            sb[1] = quit;
            return bar;
        }
        // Braise: live line, big title, Reprendre / Quitter and the via line (phone: one row)
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(l.phone() ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        box.setGravity(l.phone() ? Gravity.CENTER_VERTICAL : Gravity.START);
        Ui.noClip(box);
        LinearLayout text = new LinearLayout(ctx);
        text.setOrientation(LinearLayout.VERTICAL);
        box.addView(text, l.phone() ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        st[0] = HomeParts.text(ctx, th, l, 13, 700, th.lit.accentText, false);
        st[0].setLetterSpacing(0.06f);
        HomeParts.liveDot(st[0], ctx, l, th.lit.accentText, 8);
        text.addView(st[0]);
        st[1] = HomeParts.text(ctx, th, l, 42, 700, th.fg, true);
        st[1].setLetterSpacing(-0.02f);
        HomeParts.lineHeight(st[1], ctx, l, 42, 1.02f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = px(ctx, l, 6);
        text.addView(st[1], tlp);
        HomeParts.cssLine(st[1], 1.02f);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(row);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (l.phone()) {
            rowLp.setMarginStart(Ui.dp(ctx, 14));
        } else {
            rowLp.topMargin = px(ctx, l, 10);
        }
        box.addView(row, rowLp);
        Button resume = new Button(ctx, th);
        resume.setBackground(rect(0xFFFF6A2B, pxf(ctx, l, 21), 0, 0));
        resume.radius(l.b(21));
        st[3] = HomeParts.text(ctx, th, l, 16, 700, 0xFF0E0D0C, false);
        LinearLayout rr = HomeParts.iconLabel(ctx, l, R.drawable.v2_ic_p_play, 14, 8, st[3], 0xFF0E0D0C);
        rr.setPadding(px(ctx, l, 20), 0, px(ctx, l, 20), 0);
        resume.addView(rr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(resume, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 42), Ui.dp(ctx, btnMin))));
        Button quit = new Button(ctx, th);
        quit.setBackground(rect(Color.TRANSPARENT, pxf(ctx, l, 21), Math.max(1, px(ctx, l, 1)), th.lit.chipBorder));
        quit.radius(l.b(21));
        TextView q = HomeParts.text(ctx, th, l, 16, 700, th.fg, false);
        q.setText(R.string.v2_quit);
        q.setGravity(Gravity.CENTER);
        q.setPadding(px(ctx, l, 18), 0, px(ctx, l, 18), 0);
        quit.addView(q, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.max(px(ctx, l, 42), Ui.dp(ctx, btnMin)));
        qlp.setMarginStart(px(ctx, l, 14));
        row.addView(quit, qlp);
        st[2] = HomeParts.text(ctx, th, l, 14, 400, th.lit.text2, false);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.setMarginStart(px(ctx, l, 14));
        if (!l.phone()) {
            row.addView(st[2], vlp);
        }
        sb[0] = resume;
        sb[1] = quit;
        return box;
    }

    /** Braise without a session: the focused recent's playtime and name, big, under the header */
    private void buildHeroTitle() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        heroSub = HomeParts.text(ctx, th, l, 14, 600, th.lit.text2, false);
        box.addView(heroSub);
        heroName = HomeParts.text(ctx, th, l, 46, 700, th.fg, true);
        heroName.setLetterSpacing(-0.02f);
        HomeParts.lineHeight(heroName, ctx, l, 46, 1.05f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = px(ctx, l, 6);
        box.addView(heroName, nlp);
        HomeParts.cssLine(heroName, 1.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(ctx, l, 36);
        page.addView(box, lp);
        heroTitleBlock = box;
    }

    public View getView() {
        return root;
    }

    public State getState() {
        return state;
    }

    public void setCoverSource(CoverSource covers) {
        this.covers = covers;
        heroAppId = Integer.MIN_VALUE;
        for (Cover c : recentTiles) {
            c.appId = Integer.MIN_VALUE;
        }
        cards.clear();
        cardsRow.removeAllViews();
        bind(state);
    }

    // ---- binding -------------------------------------------------------------------------------------

    static CardState cardState(ComputerDetails host, HomeModel m) {
        if (host == null) {
            return CardState.FIRSTRUN;
        }
        if (HostList.isOnline(host)) {
            return m == null ? CardState.LOADING : null;
        }
        if (host.state == ComputerDetails.State.OFFLINE) {
            return host.macAddress != null ? CardState.OFFLINE_WAKE : CardState.OFFLINE_NO_MAC;
        }
        return CardState.SEARCHING;
    }

    /** What the state card does when activated (prototype cardAct) */
    public CardState getCardState() {
        return cardState;
    }

    public void bind(State s) {
        this.state = s;
        ComputerDetails host = s.host();
        HomeModel m = s.model;
        cardState = cardState(host, m);
        boolean ready = cardState == null;
        boolean session = ready && m.sessionRunning();

        // Header
        String hostLabel = host == null ? "" : host.name.toUpperCase(Locale.ROOT);
        String status;
        if (host == null) {
            status = ctx.getString(R.string.v2_add_pc);
        } else if (cardState == CardState.SEARCHING) {
            status = ctx.getString(R.string.v2_status_unknown);
        } else if (HostList.isOnline(host)) {
            status = ctx.getString(session ? R.string.v2_status_session : R.string.v2_status_online);
        } else {
            status = ctx.getString(R.string.v2_status_offline);
        }
        boolean caret = host != null;
        hostName.setText(caret ? hostLabel + " ▾" : hostLabel);
        hostStatus.setText(l.phone() && !th.isAffiche() ? "  ·  " + status : status);
        boolean switcher = s.hosts.size() >= 2;
        prevHost.setVisibility(switcher ? View.VISIBLE : View.INVISIBLE);
        nextHost.setVisibility(switcher ? View.VISIBLE : View.INVISIBLE);
        if (updatePill != null) {
            Ui.setVisible(updatePill, s.updateAvailable);
        }

        // Scène
        Ui.setVisible(scene, session);
        String time = s.sessionMinutes > 0 ? (s.sessionMinutes < 60
                ? ctx.getString(R.string.v2_since_min, (int) s.sessionMinutes)
                : ctx.getString(R.string.v2_since_hours, (int) (s.sessionMinutes / 60), (int) (s.sessionMinutes % 60)))
                : ctx.getString(R.string.v2_just_now);
        String resumeText = ctx.getString(s.otherDevice ? R.string.v2_join : R.string.v2_resume);
        if (session) {
            String desktop = ctx.getString(R.string.v2_desktop);
            String playnite = ctx.getString(R.string.v2_playnite);
            String title = m.sceneTitle(desktop, playnite);
            String via = via(m);
            if (th.isAffiche()) {
                liveLine.setText(ctx.getString(R.string.v2_live_dash, hostLabel, time, via).toUpperCase(Locale.FRENCH));
                resumeLabel.setText(ctx.getString(R.string.v2_arrow, resumeText.toUpperCase(Locale.FRENCH)));
            } else if (th.isLudique()) {
                liveLine.setText(ctx.getString(R.string.v2_live_dot, time));
                sceneVia.setText(via);
                resumeLabel.setText(resumeText);
            } else {
                liveLine.setText(l.phone() ? ctx.getString(R.string.v2_live_short, time).toUpperCase(Locale.FRENCH)
                        : ctx.getString(R.string.v2_live_on, hostLabel, time));
                sceneVia.setText(ctx.getString(R.string.v2_scene_hint, via));
                resumeLabel.setText(resumeText);
            }
            sceneTitle.setText(title);
        }

        // State card / content
        Ui.setVisible(stateCardBox, !ready);
        if (!ready) {
            bindStateCard(host);
        }
        onHost.setText(host == null ? "" : ctx.getString(R.string.v2_on_host, hostLabel));
        Ui.setVisible(onHost, th.isBraise() && !l.phone() && cardState != CardState.FIRSTRUN);

        List<NvApp> cardApps = ready ? m.cards : Collections.<NvApp>emptyList();
        Ui.setVisible(cardsRow, !cardApps.isEmpty());
        if (cardsRule != null) {
            Ui.setVisible(cardsRule, !cardApps.isEmpty());
        }
        bindCards(cardApps, m, session, time, resumeText);

        List<NvApp> rec = ready ? m.recents.subList(0, Math.min(RECENTS_SHOWN, m.recents.size())) : Collections.<NvApp>emptyList();
        boolean hasGames = ready && !m.games.isEmpty();
        Ui.setVisible(sectionRow, hasGames);
        Ui.setVisible(recentsScroll, hasGames);
        bindRecents(rec, m, hasGames ? m.library.size() : 0);

        updateFocusTexts();
        applyInput();
    }

    private String via(HomeModel m) {
        HomeModel.Kind k = m.runningKind();
        if (k == HomeModel.Kind.DESKTOP) {
            return ctx.getString(R.string.v2_via_desktop);
        }
        if (k == HomeModel.Kind.PLAYNITE) {
            return ctx.getString(m.playniteGame.isEmpty() ? R.string.v2_via_playnite : R.string.v2_via_inside);
        }
        return ctx.getString(R.string.v2_via_game);
    }

    private void bindStateCard(ComputerDetails host) {
        String name = host == null ? "" : host.name.toUpperCase(Locale.ROOT);
        switch (cardState) {
            case FIRSTRUN:
                stateCard.title.setText(R.string.v2_state_firstrun_title);
                stateCard.action.setText(R.string.v2_state_firstrun_action);
                stateCard.text.setText(R.string.v2_no_pc_text);
                break;
            case SEARCHING:
                stateCard.title.setText(ctx.getString(R.string.v2_state_searching_title, name));
                stateCard.action.setText(ctx.getString(R.string.v2_pc_searching, name));
                stateCard.text.setText(R.string.v2_pc_offline_text);
                break;
            case LOADING:
                stateCard.title.setText(ctx.getString(R.string.v2_state_loading_title, name));
                stateCard.action.setText(ctx.getString(R.string.v2_pc_searching, name));
                stateCard.text.setText(R.string.v2_loading);
                break;
            case OFFLINE_WAKE:
                stateCard.title.setText(ctx.getString(R.string.v2_state_offline_title, name));
                stateCard.action.setText(R.string.v2_state_wake_action);
                stateCard.text.setText(R.string.v2_state_wake_text);
                break;
            default:
                stateCard.title.setText(ctx.getString(R.string.v2_state_offline_title, name));
                stateCard.action.setText(R.string.v2_state_hosts_action);
                stateCard.text.setText(R.string.v2_state_no_mac_text);
                break;
        }
    }

    private void bindCards(List<NvApp> apps, HomeModel m, boolean session, String time, String resumeText) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        if (cards.size() != apps.size()) {
            cardsRow.removeAllViews();
            cards.clear();
            for (int i = 0; i < apps.size(); i++) {
                boolean playnite = HomeModel.kindOf(apps.get(i)) == HomeModel.Kind.PLAYNITE;
                AppCard card = new AppCard(ctx, th, l, playnite);
                float h = af ? (l.tablet() ? 270 : 220) : (lu ? 204 : 170);
                LinearLayout.LayoutParams lp = l.portrait
                        ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ctx, l, h))
                        : new LinearLayout.LayoutParams(0, px(ctx, l, h), 1f);
                if (i > 0 && !af) {
                    int gap = px(ctx, l, lu ? 18 : 16);
                    if (l.portrait) {
                        lp.topMargin = gap;
                    } else {
                        lp.setMarginStart(gap);
                    }
                }
                cardsRow.addView(card, lp);
                cards.add(card);
            }
        }
        for (int i = 0; i < apps.size(); i++) {
            final NvApp app = apps.get(i);
            final AppCard card = cards.get(i);
            boolean playnite = HomeModel.kindOf(app) == HomeModel.Kind.PLAYNITE;
            CardData d = new CardData();
            d.playnite = playnite;
            d.name = ctx.getString(playnite ? R.string.v2_playnite : R.string.v2_desktop);
            d.sub = ctx.getString(playnite ? R.string.v2_playnite_subtitle : R.string.v2_desktop_subtitle);
            d.num = ctx.getString(R.string.v2_card_num, "0" + (i + 1));
            d.running = m.isRunning(app);
            d.dimmed = m.isDimmed(app);
            String run = (playnite && !m.playniteGame.isEmpty() ? m.playniteGame + " · " : "") + time;
            d.run = ctx.getString(af ? R.string.v2_card_live_affiche : (lu ? R.string.v2_card_live_ludique : R.string.v2_card_live_braise), run);
            String act = d.running ? resumeText : ctx.getString(R.string.v2_launch);
            d.action = af ? ctx.getString(R.string.v2_arrow, act) : act;
            card.bind(d);
            Object previous = card.getTag();
            card.setTag(app);
            card.setOnClickListener(v -> actions.launch(app));
            card.setOnLongClickListener(v -> {
                actions.options(app);
                return true;
            });
            card.more.setOnClickListener(v -> actions.options(app));
            card.setOnFocusChangeListener((v, has) -> {
                focusedCard = has ? app : (focusedCard == app ? null : focusedCard);
                updateFocusTexts();
            });
            if (covers != null && (!(previous instanceof NvApp) || ((NvApp) previous).getAppId() != app.getAppId())) {
                Bitmap b = covers.peek(app);
                card.setArt(b);
                if (b == null) {
                    covers.load(app, (id, bmp) -> {
                        if (card.getTag() instanceof NvApp && ((NvApp) card.getTag()).getAppId() == id) {
                            card.setArt(bmp);
                            updateHero();
                        }
                    });
                }
            }
        }
    }

    private void bindRecents(List<NvApp> apps, HomeModel m, int total) {
        boolean af = th.isAffiche(), lu = th.isLudique();
        float w = af ? 132 : (lu ? 124 : 120), h = af ? 198 : (lu ? 186 : 180);
        float grow = l.phone() ? 1.13f : 1f;
        if (recentTiles.size() != apps.size()) {
            recentsRow.removeAllViews();
            recentTiles.clear();
            for (int i = 0; i < apps.size(); i++) {
                Cover c = new Cover(ctx, th, l, w, h);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(ctx, l, w * grow), px(ctx, l, h * grow));
                if (i > 0) {
                    lp.setMarginStart(px(ctx, l, af ? 18 : (lu ? 16 : 14)));
                }
                recentsRow.addView(c, lp);
                recentTiles.add(c);
            }
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(px(ctx, l, (af ? 200 : w) * grow), px(ctx, l, h * grow));
            if (!apps.isEmpty()) {
                tlp.setMarginStart(px(ctx, l, af ? 18 : (lu ? 16 : 14)));
            }
            recentsRow.addView(libTile, tlp);
        }
        String count = total == 0 ? ctx.getString(R.string.v2_games_none)
                : (total == 1 ? ctx.getString(R.string.v2_games_one) : ctx.getString(R.string.v2_games_n, total));
        libTile.count.setText(af ? count.toUpperCase(Locale.FRENCH) : count);
        libTile.setDimmed(m != null && m.sessionRunning());
        for (int i = 0; i < apps.size(); i++) {
            final NvApp app = apps.get(i);
            final Cover c = recentTiles.get(i);
            boolean changed = c.appId != app.getAppId();
            c.bind(app.getAppId(), app.getAppName(), m.isRunning(app), m.isDimmed(app));
            c.setTag(app);
            c.setOnClickListener(v -> actions.launch(app));
            c.setOnLongClickListener(v -> {
                actions.options(app);
                return true;
            });
            c.more.setOnClickListener(v -> actions.options(app));
            c.setOnFocusChangeListener((v, has) -> {
                focusedRecent = has ? app : (focusedRecent == app ? null : focusedRecent);
                updateFocusTexts();
            });
            if (changed && covers != null) {
                Bitmap b = covers.peek(app);
                c.setCover(b);
                if (b == null) {
                    covers.load(app, (id, bmp) -> {
                        if (c.appId == id) {
                            c.setCover(bmp);
                            updateHero();
                        }
                    });
                }
            }
        }
    }

    /** Caption of the section, Braise's hero title and hero image follow the focused item */
    private void updateFocusTexts() {
        boolean focus = V2Input.showFocus(ctx);
        NvApp fr = focus ? focusedRecent : null;
        caption.setText(fr == null ? "" : ctx.getString(R.string.v2_caption, fr.getAppName(), HomeParts.hours(ctx, fr)));
        boolean session = cardState == null && state.model != null && state.model.sessionRunning();
        if (heroTitleBlock != null) {
            boolean show = fr != null && !session;
            Ui.setVisible(heroTitleBlock, show);
            if (show) {
                heroSub.setText(ctx.getString(R.string.v2_library_hours, HomeParts.hours(ctx, fr)));
                heroName.setText(fr.getAppName());
            }
        }
        updateHero();
    }

    /**
     * Braise's hero (prototype hero): the running game when the Scène shows, else the focused app
     * card (blurred), else the focused recent, else the Bureau. The game's background when the host
     * has one, else its cover blurred.
     */
    private void updateHero() {
        if (!th.isBraise() || covers == null) {
            return;
        }
        HomeModel m = state.model;
        NvApp target = null;
        boolean blurIt = true;
        if (cardState == null && m != null) {
            if (m.sessionRunning()) {
                target = m.backdropApp();
            } else if (V2Input.showFocus(ctx) && focusedCard != null) {
                target = focusedCard;
            } else if (V2Input.showFocus(ctx) && focusedRecent != null) {
                target = focusedRecent;
            }
            if (target == null) {
                for (NvApp a : m.cards) {
                    if (HomeModel.kindOf(a) == HomeModel.Kind.DESKTOP) {
                        target = a;
                    }
                }
            }
        }
        if (target == null) {
            heroAppId = Integer.MIN_VALUE;
            hero.setBitmap(null);
            return;
        }
        final NvApp t = target;
        final boolean bg = t.hasPlayniteBackground();
        final int key = t.getAppId() * 2 + (bg ? 1 : 0);
        if (key == heroAppId && hero.getBitmap() != null) {
            return;
        }
        heroAppId = key;
        if (bg) {
            Bitmap b = covers.peekBackground(t);
            if (b != null) {
                hero.setBlur(false);
                hero.setBitmap(b);
                return;
            }
            covers.loadBackground(t, (id, bmp) -> {
                if (heroAppId == key && bmp != null) {
                    hero.setBlur(false);
                    hero.setBitmap(bmp);
                }
            });
        }
        Bitmap c = covers.peek(t);
        hero.setBlur(blurIt);
        hero.setBitmap(c);
    }

    /** The input changed (touch / pad / keys): rings, hints and labels follow */
    public void refreshInput() {
        updateFocusTexts();
        applyInput();
    }

    private boolean dialogOpen;

    /** A dialog is open over the home: its own hints replace the home's (prototype) */
    public void setDialogOpen(boolean open) {
        if (dialogOpen != open) {
            dialogOpen = open;
            applyInput();
        }
    }

    /** Bottom padding of the page without the hints bar */
    private int pageBottom;

    private void applyInput() {
        V2Input.Kind k = V2Input.get(ctx);
        boolean pad = k == V2Input.Kind.PAD, keys = k == V2Input.Kind.KEYS;
        prevLabel.setText(pad ? "LB" : (keys ? "Q" : "‹"));
        nextLabel.setText(pad ? "RB" : (keys ? "E" : "›"));
        hints.clear();
        if ((pad || keys) && !dialogOpen) {
            hints.setVisibility(View.VISIBLE);
            String a = pad ? "A" : ctx.getString(R.string.v2_key_enter);
            String lbrb = pad ? "LB RB" : "Q E";
            if (cardState != null) {
                hints.add(a, ctx.getString(R.string.v2_hint_validate), () -> actions.hint("A"));
            } else {
                hints.add(a, ctx.getString(R.string.v2_hint_launch), () -> actions.hint("A"));
                hints.add(pad ? "Y" : ctx.getString(R.string.v2_key_menu),
                        ctx.getString(pad ? R.string.v2_hint_options_hold_pad : R.string.v2_hint_options_hold_keys), () -> actions.hint("Y"));
            }
            hints.add(lbrb, ctx.getString(R.string.v2_hint_switch_pc), () -> actions.switchHost(1));
        } else {
            hints.setVisibility(View.GONE);
        }
        // A phone: the page reaches the bottom of the screen, where the hints bar is drawn; while it shows,
        // the page keeps its height free (« Tout voir », the last cover, the state card were under it)
        if (l.phone()) {
            int reserve = hints.getVisibility() == View.VISIBLE
                    ? HintsBar.reservedHeight(ctx, th, l.tablet() ? l.k : Math.max(0.85f, l.k)) : 0;
            page.setPadding(page.getPaddingLeft(), page.getPaddingTop(), page.getPaddingRight(), pageBottom + reserve);
        }
        root.invalidate();
    }

    // ---- focus ---------------------------------------------------------------------------------------

    /**
     * The prototype's grid: [Mes PC, Réglages], [Reprendre, Quitter] with a session, [Bureau,
     * Playnite], [6 covers, Tout voir]; with the state card: [Mes PC, Réglages], [card].
     */
    public List<List<View>> focusRows() {
        List<List<View>> rows = new ArrayList<>();
        List<View> head = new ArrayList<>();
        head.add(hostsButton);
        head.add(settingsButton);
        rows.add(head);
        if (cardState != null) {
            rows.add(Collections.<View>singletonList(stateCard));
            return rows;
        }
        if (scene.getVisibility() == View.VISIBLE) {
            List<View> sc = new ArrayList<>();
            sc.add(resumeButton);
            sc.add(quitButton);
            rows.add(sc);
        }
        if (!cards.isEmpty()) {
            rows.add(new ArrayList<View>(cards));
        }
        if (recentsScroll.getVisibility() == View.VISIBLE) {
            List<View> rec = new ArrayList<View>(recentTiles);
            rec.add(libTile);
            rows.add(rec);
        }
        return rows;
    }

    /** A grid item got the focus: keep it on screen */
    public void onGridFocus(View v) {
        if (v.getParent() == recentsRow) {
            v.requestRectangleOnScreen(new android.graphics.Rect(-Ui.dp(ctx, 16), 0, v.getWidth() + Ui.dp(ctx, 16), v.getHeight()));
        }
    }

    /**
     * Where the focus lands first: Reprendre with a session, else the Playnite card (prototype
     * default a1), else the state card.
     */
    public boolean requestInitialFocus() {
        if (cardState != null) {
            return stateCard.requestFocus();
        }
        if (scene.getVisibility() == View.VISIBLE && resumeButton.requestFocus()) {
            return true;
        }
        for (AppCard c : cards) {
            if (c.getTag() instanceof NvApp && HomeModel.kindOf((NvApp) c.getTag()) == HomeModel.Kind.PLAYNITE && c.requestFocus()) {
                return true;
            }
        }
        if (!cards.isEmpty() && cards.get(0).requestFocus()) {
            return true;
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
            if (v == resumeButton && state.model != null) {
                return state.model.runningApp();
            }
            if (v == root) {
                break;
            }
        }
        return null;
    }
}
