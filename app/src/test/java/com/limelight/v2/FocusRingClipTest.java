package com.limelight.v2;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.graphics.RectF;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;
import com.limelight.v2.ui.V2Alert;
import com.limelight.v2.ui.V2Frame;
import com.limelight.v2.ui.V2ListDialog;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A focus ring is never cut by an edge (rule of the user, 2026-11-26: the « Ouvrir » button of a
 * card of Mes PC lost its Ludique ring left and right). Every screen of the v2 interface is laid out
 * in the three themes at the sizes Android runs it; every focusable V2Frame takes the focus (as the
 * gamepad gives it, so a ScrollView scrolls) and the box of its ring (V2Frame.focusRingBox: the
 * theme's outer ring, then the 1.04 focus scale of cards and covers) must not be cut by any
 * ancestor: a parent with clipChildren cuts at the child's bounds, clipToPadding at the padding, and
 * the window at the root.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FocusRingClipTest {
    private static final String[] THEMES = {"braise", "affiche", "ludique"};
    /** Phone landscape and portrait, console, tablet (xhdpi) */
    private static final int[][] SIZES = {{915, 412}, {412, 915}, {960, 540}, {1280, 800}};
    private static final int DENSITY = 2;
    private static final ShotData DATA = ShotData.get();

    private final Set<String> cuts = new LinkedHashSet<>();
    private int checked;

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    // ---- screens --------------------------------------------------------------------------------

    @Test
    public void home() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                for (int scene = 0; scene < 3; scene++) {
                    Context ctx = setUp(s);
                    List<NvApp> apps = DATA.apps();
                    HomeScreen.State st = new HomeScreen.State();
                    if (scene == 2) {
                        ComputerDetails off = pc("PORTABLE", false);
                        st.hosts = Arrays.asList(pc("ATLAS-PC", true), off);
                        st.hostIndex = 1;
                    } else {
                        st.hosts = Arrays.asList(pc("ATLAS-PC", true), pc("SALON-HTPC", true), pc("PORTABLE", false));
                        st.model = HomeModel.build(apps, scene == 0 ? 2 : 0, scene == 0 ? DATA.games.get(0) : "", scene == 0,
                                DATA.hidden(apps), false);
                        st.sessionMinutes = scene == 0 ? 42 : -1;
                    }
                    st.updateAvailable = true;
                    HomeScreen screen = new HomeScreen(ctx, V2Theme.of(t, true), HomeLayout.forSize(s[0], s[1]),
                            noop(HomeScreen.Actions.class), DATA.covers(apps));
                    screen.bind(st);
                    check("accueil" + (scene == 0 ? " (session)" : scene == 1 ? "" : " (PC éteint)"), t, s, screen.getView());
                }
            }
        }
        report();
    }

    @Test
    public void library() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                for (int filter : new int[]{0, 3}) {
                    Context ctx = setUp(s);
                    List<NvApp> apps = DATA.apps();
                    HomeModel m = HomeModel.build(apps, 2, DATA.games.get(0), true, DATA.hidden(apps), false);
                    LibraryScreen lib = new LibraryScreen(ctx, V2Theme.of(t, true), HomeLayout.forSize(s[0], s[1]),
                            noop(LibraryScreen.Actions.class), DATA.covers(apps));
                    lib.bind(pc("ATLAS-PC", true), m, ShotData.NOW);
                    if (filter != 0) {
                        lib.setFilter(filter);
                    }
                    check("bibliothèque" + (filter != 0 ? " (Masqués)" : ""), t, s, lib.getView());
                }
            }
        }
        report();
    }

    @Test
    public void details() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                Context ctx = setUp(s);
                V2Theme theme = V2Theme.of(t, true);
                List<NvApp> apps = DATA.apps();
                HomeModel m = HomeModel.build(apps, 2, DATA.games.get(0), true, DATA.hidden(apps), false);
                NvApp game = apps.get(7);
                DetailsScreen.State st = DetailsActivity.state(ctx, game, pc("ATLAS-PC", true), m, theme.isAffiche(),
                        new SettingsStore(ctx));
                st.cover = DATA.covers(apps).peek(game);
                DetailsScreen screen = new DetailsScreen(ctx, theme, HomeLayout.forSize(s[0], s[1]), noop(DetailsScreen.Actions.class));
                screen.bind(st);
                check("détails", t, s, screen.getView());
            }
        }
        report();
    }

    @Test
    public void optionsAndPlus() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                Context ctx = setUp(s);
                V2Theme theme = V2Theme.of(t, true);
                HomeLayout l = HomeLayout.forSize(s[0], s[1]);
                V2Sheet.Spec spec = new V2Sheet.Spec();
                spec.kind = ctx.getString(R.string.v2_kind_game);
                spec.where = "ATLAS-PC";
                spec.title = "Split Fiction";
                spec.hasCover = true;
                spec.warn = true;
                spec.note = ctx.getString(R.string.v2_note_inside, "Hades II", "Split Fiction");
                spec.option(ctx.getString(R.string.v2_close_and_play_inside, "Hades II"), true, true, null);
                spec.option(ctx.getString(R.string.v2_show_details), false, false, null);
                spec.option(ctx.getString(R.string.v2_hide), false, false, null);
                spec.option(ctx.getString(R.string.v2_cancel), false, false, null);
                check("options d'un jeu", t, s, V2Sheet.build(ctx, theme, l, spec, null));

                // « Plus » of the in-game menu: entries with icons, two columns
                V2Sheet.Spec plus = new V2Sheet.Spec();
                plus.kind = "ATLAS-PC";
                plus.title = ctx.getString(R.string.v2_menu_more);
                for (StreamMenu.More e : StreamMenu.More.values()) {
                    plus.option(e.name(), V2GameMenu.icon(e), null);
                }
                check("Plus (menu en jeu)", t, s, V2Sheet.build(ctx, theme, l, plus, null));
            }
        }
        report();
    }

    @Test
    public void gameMenu() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                for (boolean playnite : new boolean[]{true, false}) {
                    Context ctx = setUp(s);
                    String title = playnite ? "Red Dead Redemption 2" : ctx.getString(R.string.v2_desktop);
                    View menu = V2GameMenu.build(ctx, V2Theme.of(t, true), HomeLayout.forSize(s[0], s[1]), title, "ATLAS-PC",
                            ctx.getString(playnite ? R.string.v2_via_inside : R.string.v2_via_desktop),
                            HomeScreenshotTest.menuTiles(ctx, playnite, title), null);
                    check("menu en jeu" + (playnite ? "" : " (Bureau)"), t, s, menu);
                }
            }
        }
        report();
    }

    @Test
    public void hosts() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                Context ctx = setUp(s);
                HostsScreen screen = new HostsScreen(ctx, V2Theme.of(t, true), HomeLayout.forSize(s[0], s[1]),
                        noop(HostsScreen.Actions.class));
                HostCards.Labels lab = HostCards.Labels.from(ctx);
                List<NvApp> apps = new ArrayList<>();
                NvApp playnite = new NvApp("Playnite", "u2", 2, false);
                playnite.setPlayniteHost(true);
                apps.add(playnite);
                List<HostCards.Card> cards = new ArrayList<>();
                ComputerDetails atlas = pc("ATLAS-PC", true);
                atlas.runningGameId = 2;
                atlas.apolloVersion = "1.2.0";
                for (ComputerDetails d : HostCards.pairedByName(Arrays.asList(atlas, pc("SALON", false), pc("PORTABLE", true)))) {
                    cards.add(HostCards.of(d, apps, lab));
                }
                screen.bind(cards);
                check("Mes PC", t, s, screen.getView());
            }
        }
        report();
    }

    @Test
    public void settings() {
        Context app = ApplicationProvider.getApplicationContext();
        new SettingsStore(app).setExpertMode(true);
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                for (SettingsCatalog.Page p : SettingsCatalog.PAGES) {
                    Context ctx = setUp(s);
                    SettingsEnv env = SettingsEnv.forTests();
                    env.microphones.add(new String[]{"usb:Blue Yeti", "Blue Yeti"});
                    SettingsScreen screen = new SettingsScreen(ctx, V2Theme.of(t, true), Density.forSize(s[0], s[1]), env,
                            new SettingsStore(ctx), noop(SettingsScreen.Actions.class), p.id, true);
                    check("Réglages › " + p.id, t, s, screen.getView());
                }
                // A narrow screen shows the list of pages alone first
                Context ctx = setUp(s);
                SettingsScreen list = new SettingsScreen(ctx, V2Theme.of(t, true), Density.forSize(s[0], s[1]),
                        SettingsEnv.forTests(), new SettingsStore(ctx), noop(SettingsScreen.Actions.class), SettingsCatalog.VIDEO, false);
                check("Réglages (pages)", t, s, list.getView());
            }
        }
        report();
    }

    /** The real dialogs of Mes PC, the list dialogs and the alerts, in their own window */
    @Test
    public void dialogs() {
        for (String t : THEMES) {
            for (int[] s : SIZES) {
                HostsActivity a = activity(t, s);
                a.showHostOptions("uuid-ATLAS-PC");
                checkDialog("options d'un PC", t, s, ShadowDialog.getLatestDialog());
                a.showAddMenu();
                checkDialog("Ajouter un PC", t, s, ShadowDialog.getLatestDialog());
                checkDialog("appairage", t, s, a.showPairDialog("BUREAU-ETAGE", "4827", false));
                checkDialog("Renommer", t, s, a.rename(pc("ATLAS-PC", true)));
                V2Theme theme = V2Theme.load(a);
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    labels.add("Entrée " + (i + 1));
                }
                labels.add(a.getString(R.string.v2_cancel));
                checkDialog("liste", t, s, V2ListDialog.show(a, theme, "Liste", labels, labels.size() - 1, 2, i -> {}, null));
                checkDialog("liste à cocher", t, s, V2ListDialog.showMultiChoice(a, theme, "Touches", labels.subList(0, 9),
                        new boolean[9], a.getString(R.string.keyboard_add), a.getString(R.string.cancel), x -> { }));
                checkDialog("message", t, s, V2ListDialog.message(a, theme, "Titre", "Un message.", a.getString(R.string.v2_ok)));
                new V2Alert(a, theme).title("Mise à jour").text("Une version est prête.")
                        .button("Installer", null).button("Plus tard", null).button("Ignorer cette version", null).show();
                checkDialog("alerte", t, s, ShadowDialog.getLatestDialog());
            }
        }
        report();
    }

    // ---- the check ------------------------------------------------------------------------------

    private static Context setUp(int[] s) {
        qualifiers(s);
        V2Input.reset(V2Input.Kind.PAD);
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
    }

    private static void qualifiers(int[] s) {
        RuntimeEnvironment.setQualifiers("fr-w" + s[0] + "dp-h" + s[1] + "dp-" + (s[0] > s[1] ? "land" : "port") + "-xhdpi");
    }

    private static HostsActivity activity(String t, int[] s) {
        qualifiers(s);
        Context app = ApplicationProvider.getApplicationContext();
        V2Theme.saveTheme(app, t);
        V2Theme.saveMode(app, V2Theme.MODE_DARK);
        V2Input.reset(V2Input.Kind.PAD);
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        ComputerDetails atlas = pc("ATLAS-PC", true);
        ComputerDetails found = pc("BUREAU-ETAGE", true);
        found.pairState = PairingManager.PairState.NOT_PAIRED;
        for (ComputerDetails d : new ComputerDetails[]{atlas, pc("SALON", false), found}) {
            a.computers.put(d.uuid, d);
        }
        a.refresh();
        return a;
    }

    private void checkDialog(String what, String theme, int[] s, Dialog d) {
        ShadowLooper.idleMainLooper();
        View content = d.findViewById(android.R.id.content);
        Window w = d.getWindow();
        boolean full = w != null && w.getAttributes().width == WindowManager.LayoutParams.MATCH_PARENT
                && w.getAttributes().height == WindowManager.LayoutParams.MATCH_PARENT;
        int sw = s[0] * DENSITY, sh = s[1] * DENSITY;
        int mode = full ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST;
        for (int pass = 0; pass < 2; pass++) {
            content.measure(View.MeasureSpec.makeMeasureSpec(sw, mode), View.MeasureSpec.makeMeasureSpec(sh, mode));
            content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
            ShadowLooper.idleMainLooper();
        }
        // The window is as big as its content (or the screen): what is drawn outside is lost
        walk(what, theme, s, content, content.getMeasuredWidth(), content.getMeasuredHeight());
        d.dismiss();
    }

    private void check(String what, String theme, int[] s, View root) {
        int w = s[0] * DENSITY, h = s[1] * DENSITY;
        layout(root, w, h);
        walk(what, theme, s, root, w, h);
    }

    private static void layout(View root, int w, int h) {
        for (int pass = 0; pass < 2; pass++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            ShadowLooper.idleMainLooper();
        }
    }

    private void walk(String what, String theme, int[] s, View root, int w, int h) {
        List<V2Frame> frames = new ArrayList<>();
        frames(root, frames);
        for (V2Frame f : frames) {
            f.setFocusableInTouchMode(true);
        }
        for (V2Frame f : frames) {
            if (!visible(f, root) || f.getWidth() == 0 || f.getHeight() == 0 || f.focusRingOutsetPx() <= 0) {
                continue;
            }
            if (!f.requestFocus()) {
                // A view that cannot take the focus shows no ring; say it, so a hole in the check shows
                System.out.println("pas de focus : " + what + " · " + theme + " " + s[0] + "x" + s[1] + " · « " + label(f) + " »");
                continue;
            }
            checked++;
            // Let a smooth scroll (requestRectangleOnScreen of a card row) finish
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            for (View p = f; p != null && p != root.getParent(); p = p.getParent() instanceof View ? (View) p.getParent() : null) {
                p.computeScroll();
            }
            ShadowLooper.idleMainLooper();
            String cut = cut(f, root, w, h);
            if (cut != null) {
                cuts.add(what + " · " + theme + " " + s[0] + "x" + s[1] + " · « " + label(f) + " » (" + f.getClass().getSimpleName()
                        + ") coupé par " + cut);
            }
        }
    }

    /** isShown() wants a window: the screens are laid out without one */
    private static boolean visible(View v, View root) {
        for (View c = v; c != null; c = c.getParent() instanceof View ? (View) c.getParent() : null) {
            if (c.getVisibility() != View.VISIBLE) {
                return false;
            }
            if (c == root) {
                return true;
            }
        }
        return false;
    }

    /** Where the ring of f is cut, or null */
    private static String cut(V2Frame f, View root, int w, int h) {
        RectF box = f.focusRingBox();
        View c = f;
        while (c != root) {
            ViewGroup p = (ViewGroup) c.getParent();
            if (c.getClipToOutline()) {
                // A rounded card that clips its content to its outline (its corners cut even more)
                String side = outside(box, c.getScrollX(), c.getScrollY(), c.getScrollX() + c.getWidth(), c.getScrollY() + c.getHeight());
                if (side != null) {
                    return "le contour de " + name(c) + " (clipToOutline, " + side + ")";
                }
            }
            if (p.getClipChildren()) {
                // A clipping parent cuts the child at its own bounds (in the child's scrolled space)
                String side = outside(box, c.getScrollX(), c.getScrollY(), c.getScrollX() + c.getWidth(), c.getScrollY() + c.getHeight());
                if (side != null) {
                    return "les bords de " + name(c) + " (clipChildren de " + name(p) + ", " + side + ")";
                }
            }
            box.offset(-c.getScrollX(), -c.getScrollY());
            // The focus scale of f is in its ring box already (at its end value, the animation may run)
            if (c != f && (c.getScaleX() != 1f || c.getScaleY() != 1f)) {
                float px = c.getPivotX(), py = c.getPivotY();
                box.set(px + (box.left - px) * c.getScaleX(), py + (box.top - py) * c.getScaleY(),
                        px + (box.right - px) * c.getScaleX(), py + (box.bottom - py) * c.getScaleY());
            }
            box.offset(c.getLeft() + c.getTranslationX(), c.getTop() + c.getTranslationY());
            String hidden = occluded(p, c, box);
            if (hidden != null) {
                return hidden;
            }
            boolean padded = p.getPaddingLeft() != 0 || p.getPaddingTop() != 0 || p.getPaddingRight() != 0 || p.getPaddingBottom() != 0;
            if (p.getClipToPadding() && padded) {
                int sx = p.getScrollX(), sy = p.getScrollY();
                String side = outside(box, sx + p.getPaddingLeft(), sy + p.getPaddingTop(),
                        sx + p.getWidth() - p.getPaddingRight(), sy + p.getHeight() - p.getPaddingBottom());
                if (side != null) {
                    return "le padding de " + name(p) + " (clipToPadding, " + side + ")";
                }
            }
            c = p;
        }
        box.offset(-root.getScrollX(), -root.getScrollY());
        String side = outside(box, 0, 0, w, h);
        return side == null ? null : "la fenêtre (" + side + ")";
    }

    /**
     * A sibling drawn after c (child order, then Z as ViewGroup draws them) that paints over the
     * ring: its own background, or a descendant's, crosses the ring box (box in p's content space)
     * outside c's bounds
     */
    private static String occluded(ViewGroup p, View c, RectF box) {
        List<View> order = new ArrayList<>();
        for (int i = 0; i < p.getChildCount(); i++) {
            order.add(p.getChildAt(i));
        }
        boolean z = false;
        for (View v : order) {
            z |= v.getZ() != 0;
        }
        if (z) {
            order.sort((a, b) -> Float.compare(a.getZ(), b.getZ()));
        }
        RectF own = new RectF(c.getLeft() + c.getTranslationX(), c.getTop() + c.getTranslationY(),
                c.getRight() + c.getTranslationX(), c.getBottom() + c.getTranslationY());
        for (int i = order.indexOf(c) + 1; i < order.size(); i++) {
            View s = order.get(i);
            if (s.getVisibility() != View.VISIBLE) {
                continue;
            }
            RectF hit = paints(s, s.getLeft() + s.getTranslationX(), s.getTop() + s.getTranslationY(), box, own);
            if (hit != null) {
                return "« " + label(s) + " » (" + name(s) + ") dessiné par-dessus, dans " + name(p);
            }
        }
        return null;
    }

    /** The first painted rectangle of v (at x, y in the parent's space) that crosses box outside own */
    private static RectF paints(View v, float x, float y, RectF box, RectF own) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() == 0f) {
            return null;
        }
        RectF r = new RectF(x, y, x + v.getWidth(), y + v.getHeight());
        if (!RectF.intersects(r, box)) {
            return null;
        }
        android.graphics.drawable.Drawable bg = v.getBackground();
        boolean opaque = bg != null && !(bg instanceof android.graphics.drawable.ColorDrawable
                && ((android.graphics.drawable.ColorDrawable) bg).getAlpha() == 0);
        if (opaque || v instanceof V2Frame && bg != null) {
            RectF i = new RectF(r);
            if (i.intersect(box) && !own.contains(i)) {
                return i;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) {
                View ch = g.getChildAt(k);
                RectF hit = paints(ch, x + ch.getLeft() - g.getScrollX(), y + ch.getTop() - g.getScrollY(), box, own);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** The sides of box that pass the clip rectangle (half a pixel of antialiasing allowed) */
    private static String outside(RectF box, float l, float t, float r, float b) {
        final float slack = 0.5f;
        List<String> sides = new ArrayList<>();
        if (box.left < l - slack) sides.add("gauche " + Math.round(l - box.left) + " px");
        if (box.top < t - slack) sides.add("haut " + Math.round(t - box.top) + " px");
        if (box.right > r + slack) sides.add("droite " + Math.round(box.right - r) + " px");
        if (box.bottom > b + slack) sides.add("bas " + Math.round(box.bottom - b) + " px");
        return sides.isEmpty() ? null : String.join(", ", sides);
    }

    private static String name(View v) {
        String n = v.getClass().getSimpleName();
        if (n.isEmpty()) {
            n = v.getClass().getName();
        }
        return n;
    }

    private static String label(View v) {
        if (v.getContentDescription() != null && v.getContentDescription().length() > 0) {
            return v.getContentDescription().toString();
        }
        List<TextView> texts = new ArrayList<>();
        texts(v, texts);
        for (TextView t : texts) {
            if (t.getText().length() > 0) {
                return t.getText().toString();
            }
        }
        return "?";
    }

    private static void texts(View v, List<TextView> out) {
        if (v instanceof TextView) {
            out.add((TextView) v);
        } else if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                texts(((ViewGroup) v).getChildAt(i), out);
            }
        }
    }

    private static void frames(View v, List<V2Frame> out) {
        if (v instanceof V2Frame && v.isFocusable()) {
            out.add((V2Frame) v);
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                frames(((ViewGroup) v).getChildAt(i), out);
            }
        }
    }

    private void report() {
        System.out.println(checked + " anneaux vérifiés");
        assertTrue("no focus ring checked", checked > 0);
        StringBuilder sb = new StringBuilder(cuts.size() + " anneau(x) coupé(s) :");
        for (String c : cuts) {
            sb.append("\n  ").append(c);
        }
        assertTrue(sb.toString(), cuts.isEmpty());
    }

    // ---- data -----------------------------------------------------------------------------------

    private static ComputerDetails pc(String name, boolean online) {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-" + name;
        d.name = name;
        d.state = online ? ComputerDetails.State.ONLINE : ComputerDetails.State.OFFLINE;
        d.pairState = PairingManager.PairState.PAIRED;
        d.macAddress = "00:11:22:33:44:55";
        if (online) {
            d.activeAddress = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        } else {
            d.localAddress = new ComputerDetails.AddressTuple("192.168.1.31", 47989);
        }
        return d;
    }

    /** An implementation of an Actions interface that does nothing */
    @SuppressWarnings("unchecked")
    private static <T> T noop(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, m, args) -> {
            Class<?> r = m.getReturnType();
            if (m.getDeclaringClass() == Object.class) {
                switch (m.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return type.getSimpleName();
                }
            }
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == float.class) return 0f;
            return null;
        });
    }
}
