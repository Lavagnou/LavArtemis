package com.limelight.v2;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the v2 home (and the library) from fake data at the validated densities (phone
 * 915x412 dp, console 960x540 dp), in several themes, with and without a running session, and
 * writes PNGs to app/build/v2-shots (or $V2_SHOTS_DIR) to be looked at before a delivery.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeScreenshotTest {
    /** Games, hours and covers: made up, or the prototype's own with $V2_SHOT_COVERS (ShotData) */
    private static final ShotData DATA = ShotData.get();
    private static final String[] GAMES = DATA.games.toArray(new String[0]);

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    private static List<NvApp> apps() {
        return DATA.apps();
    }

    private static ComputerDetails pc(String name, boolean online) {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-" + name;
        d.name = name;
        d.state = online ? ComputerDetails.State.ONLINE : ComputerDetails.State.OFFLINE;
        d.pairState = PairingManager.PairState.PAIRED;
        d.macAddress = "00:11:22:33:44:55";
        return d;
    }

    private static CoverSource covers(List<NvApp> apps) {
        return DATA.covers(apps);
    }

    private static File outDir() {
        String env = System.getenv("V2_SHOTS_DIR");
        File dir = env != null && !env.isEmpty() ? new File(env) : new File("build/v2-shots");
        dir.mkdirs();
        return dir;
    }

    private static Bitmap draw(View view, int widthPx, int heightPx) {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        ShadowLooper.idleMainLooper();
        // Second pass: RecyclerView children and posted span changes
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    private static void save(Bitmap bmp, String name) throws Exception {
        File f = new File(outDir(), name + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        // Not blank: the page has more than one colour
        int first = bmp.getPixel(0, 0);
        boolean varied = false;
        for (int y = 0; y < bmp.getHeight() && !varied; y += 7) {
            for (int x = 0; x < bmp.getWidth(); x += 7) {
                if (bmp.getPixel(x, y) != first) {
                    varied = true;
                    break;
                }
            }
        }
        assertTrue(name + " is blank", varied);
    }

    private enum Scene { IDLE, PLAYNITE_GAME, DESKTOP }

    private void home(String file, String themeId, boolean dark, int wDp, int hDp, Scene scene, V2Input.Kind input) throws Exception {
        RuntimeEnvironment.setQualifiers("w" + wDp + "dp-h" + hDp + "dp-" + (wDp > hDp ? "land" : "port") + (wDp >= 1100 ? "-mdpi" : "-xhdpi"));
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        V2Theme theme = V2Theme.of(themeId, dark);
        List<NvApp> apps = apps();
        CoverSource covers = covers(apps);

        HomeScreen.State s = new HomeScreen.State();
        ComputerDetails atlas = pc("ATLAS-PC", true);
        s.hosts = Arrays.asList(atlas, pc("SALON-HTPC", true), pc("PORTABLE", false));
        s.updateAvailable = true;
        s.hostIndex = 0;
        int running = scene == Scene.PLAYNITE_GAME ? 2 : (scene == Scene.DESKTOP ? 1 : 0);
        String game = scene == Scene.PLAYNITE_GAME ? GAMES[0] : "";
        s.model = HomeModel.build(apps, running, game, scene == Scene.PLAYNITE_GAME, DATA.hidden(apps), false);
        s.sessionMinutes = running != 0 ? 42 : -1;

        HomeScreen screen = new HomeScreen(ctx, theme, HomeLayout.forSize(wDp, hDp), new NoActions(), covers);
        screen.bind(s);
        int density = wDp >= 1100 ? 1 : 2;
        View root = screen.getView();
        Bitmap bmp = draw(root, wDp * density, hDp * density);
        if (input != V2Input.Kind.TOUCH) {
            // Gamepad: the focus starts on Reprendre (or the first card) and its ring shows
            screen.requestInitialFocus();
            bmp = draw(root, wDp * density, hDp * density);
        }
        save(bmp, file);
    }

    private static final String[] THEMES = {"braise", "affiche", "ludique"};

    /** Prototype stage (1280 x 720 dp, V8-05 / V8-06), every theme, light and dark, touch and gamepad */
    @Test
    public void prototypeSize() throws Exception {
        sizes("proto", 1280, 720);
    }

    /** Densite-Tablette (1280 x 800 dp) */
    @Test
    public void tablet() throws Exception {
        sizes("tablet", 1280, 800);
    }

    /** Densite-Telephone (915 x 412 dp) */
    @Test
    public void phone() throws Exception {
        sizes("phone", 915, 412);
    }

    /** Densite-Console (960 x 540 dp) */
    @Test
    public void console() throws Exception {
        sizes("console", 960, 540);
    }

    private void sizes(String prefix, int w, int h) throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                String mode = dark ? "sombre" : "clair";
                home(prefix + "-" + t + "-" + mode + "-tactile", t, dark, w, h, Scene.PLAYNITE_GAME, V2Input.Kind.TOUCH);
                home(prefix + "-" + t + "-" + mode + "-manette", t, dark, w, h, Scene.PLAYNITE_GAME, V2Input.Kind.PAD);
            }
            home(prefix + "-" + t + "-idle-manette", t, !"affiche".equals(t) && !"ludique".equals(t), w, h, Scene.IDLE, V2Input.Kind.PAD);
        }
    }

    /** The options of a game while another one runs inside Playnite (prototype: home, pad, Y on the 2nd cover) */
    @Test
    public void optionsDialog() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
                V2Input.reset(V2Input.Kind.PAD);
                Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
                V2Theme theme = V2Theme.of(t, dark);
                List<NvApp> apps = apps();
                CoverSource covers = covers(apps);
                HomeScreen.State s = new HomeScreen.State();
                s.hosts = Arrays.asList(pc("ATLAS-PC", true), pc("SALON-HTPC", true), pc("PORTABLE", false));
                s.updateAvailable = true;
                s.model = HomeModel.build(apps, 2, GAMES[0], true, DATA.hidden(apps), false);
                s.sessionMinutes = 42;
                HomeLayout l = HomeLayout.forSize(1280, 720);
                HomeScreen screen = new HomeScreen(ctx, theme, l, new NoActions(), covers);
                screen.bind(s);
                screen.setDialogOpen(true);
                Bitmap bmp = draw(screen.getView(), 1280, 720);

                NvApp game = apps.get(4);
                V2Sheet.Spec spec = new V2Sheet.Spec();
                spec.kind = ctx.getString(R.string.v2_kind_game);
                spec.where = "ATLAS-PC";
                spec.title = game.getAppName();
                spec.hasCover = true;
                spec.cover = covers.peek(game);
                spec.warn = true;
                spec.note = ctx.getString(R.string.v2_note_inside, GAMES[0], game.getAppName());
                spec.option(ctx.getString(R.string.v2_close_and_play_inside, GAMES[0]), true, true, null);
                spec.option(ctx.getString(R.string.v2_hide), false, false, null);
                spec.option(ctx.getString(R.string.v2_cancel), false, false, null);
                View sheet = V2Sheet.build(ctx, theme, l, spec, null);
                draw(sheet, 1280, 720);
                View first = firstFocusable(sheet);
                if (first != null) {
                    first.requestFocus();
                }
                Bitmap over = draw(sheet, 1280, 720);
                new Canvas(bmp).drawBitmap(over, 0, 0, null);
                save(bmp, "options-" + t + "-" + (dark ? "sombre" : "clair") + "-manette");
            }
        }
    }

    /** The in-game tile menu over a stream (prototype V8-27: a Playnite game, focus on the 3rd tile) */
    @Test
    public void gameMenu() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
                V2Input.reset(V2Input.Kind.PAD);
                Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
                V2Theme theme = V2Theme.of(t, dark);
                List<NvApp> apps = apps();
                CoverSource covers = covers(apps);
                Bitmap bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                Bitmap stream = covers.peek(apps.get(4));
                if (stream != null) {
                    c.drawBitmap(stream, null, new android.graphics.Rect(0, 0, 1280, 720), new Paint(Paint.FILTER_BITMAP_FLAG));
                }
                java.util.List<V2GameMenu.Tile> tiles = new java.util.ArrayList<>();
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_resume), ctx.getString(R.string.v2_resume), "", "", R.drawable.v2_ic_m_resume, false, 0, null));
                tiles.add(new V2GameMenu.Tile("Afficher le clavier logiciel", ctx.getString(R.string.v2_short_keyboard), "", "", R.drawable.v2_ic_m_keyboard, false, 1, null));
                tiles.add(new V2GameMenu.Tile("Passer en trackpad", ctx.getString(R.string.v2_short_trackpad), "", "", R.drawable.v2_ic_m_touch, false, 1, null));
                tiles.add(new V2GameMenu.Tile("Activer le mode Zoom/Panoramique", ctx.getString(R.string.v2_short_zoom), "", "", R.drawable.v2_ic_m_zoom, false, 1, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_send_keys), ctx.getString(R.string.v2_short_keys), "›", "", R.drawable.v2_ic_m_keys, false, 1, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_clipboard), ctx.getString(R.string.v2_short_clip), "›", "", R.drawable.v2_ic_m_clip, false, 1, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_server_cmd), ctx.getString(R.string.v2_short_cmds), "›", "", R.drawable.v2_ic_m_cmds, false, 1, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_stats), ctx.getString(R.string.v2_short_stats), ctx.getString(R.string.v2_stats_hidden), "", R.drawable.v2_ic_m_stats, false, 1, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_more), ctx.getString(R.string.v2_menu_more), "›", "", R.drawable.v2_ic_m_more, false, 1, null));
                tiles.add(new V2GameMenu.Tile("Déconnecter", "Déconnecter", "", "", R.drawable.v2_ic_m_disconnect, false, 7, null));
                tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_quit, GAMES[0]), ctx.getString(R.string.v2_short_quit), "",
                        ctx.getString(R.string.v2_menu_quit_playnite_sub), R.drawable.v2_ic_m_quit, false, 8, null));
                View menu = V2GameMenu.build(ctx, theme, HomeLayout.forSize(1280, 720), GAMES[0], "ATLAS-PC",
                        ctx.getString(R.string.v2_via_inside), tiles, null);
                draw(menu, 1280, 720);
                View third = nthButton(menu, 2);
                if (third != null) {
                    third.requestFocus();
                }
                c.drawBitmap(draw(menu, 1280, 720), 0, 0, null);
                save(bmp, "menu-" + t + "-" + (dark ? "sombre" : "clair") + "-manette");
            }
        }
    }

    private static View nthButton(View root, int n) {
        java.util.List<View> found = new java.util.ArrayList<>();
        collectButtons(root, found);
        return n < found.size() ? found.get(n) : null;
    }

    private static void collectButtons(View v, java.util.List<View> out) {
        if (v.getClass().getSimpleName().equals("Button")) {
            out.add(v);
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectButtons(g.getChildAt(i), out);
            }
        }
    }

    private static View firstFocusable(View v) {
        if (v.isFocusable() && !(v instanceof android.view.ViewGroup && ((android.view.ViewGroup) v).getChildCount() == 0 && false)) {
            if (v.getClass().getSimpleName().equals("Button")) {
                return v;
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = firstFocusable(g.getChildAt(i));
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    @Test
    public void portraitPhone() throws Exception {
        home("portrait-braise-dark-session", "braise", true, 412, 915, Scene.PLAYNITE_GAME, V2Input.Kind.TOUCH);
    }

    @Test
    public void emptyStates() throws Exception {
        RuntimeEnvironment.setQualifiers("w915dp-h412dp-land-xhdpi");
        V2Input.reset(V2Input.Kind.TOUCH);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        HomeScreen none = new HomeScreen(ctx, V2Theme.of("braise", true), HomeLayout.forSize(915, 412), new NoActions(), null);
        none.bind(new HomeScreen.State());
        save(draw(none.getView(), 1830, 824), "phone-braise-dark-no-pc");

        HomeScreen.State off = new HomeScreen.State();
        off.hosts = Arrays.asList(pc("ATLAS-PC", false));
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
                V2Input.reset(V2Input.Kind.PAD);
                Context c2 = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
                HomeScreen.State o = new HomeScreen.State();
                ComputerDetails portable = pc("PORTABLE", false);
                portable.macAddress = null;
                o.hosts = Arrays.asList(pc("ATLAS-PC", true), portable);
                o.hostIndex = 1;
                o.updateAvailable = true;
                HomeScreen offline = new HomeScreen(c2, V2Theme.of(t, dark), HomeLayout.forSize(1280, 720), new NoActions(), null);
                offline.bind(o);
                draw(offline.getView(), 1280, 720);
                offline.requestInitialFocus();
                save(draw(offline.getView(), 1280, 720), "proto-offline-" + t + "-" + (dark ? "sombre" : "clair") + "-manette");
            }
        }
    }

    @Test
    public void library() throws Exception {
        RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-xhdpi");
        V2Input.reset(V2Input.Kind.TOUCH);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        List<NvApp> apps = apps();
        LibraryScreen lib = new LibraryScreen(ctx, V2Theme.of("braise", true), Density.forSize(960, 540), new LibraryScreen.Actions() {
            @Override
            public void back() {
            }

            @Override
            public void launch(NvApp app) {
            }

            @Override
            public void options(NvApp app) {
            }

            @Override
            public void toggleHidden() {
            }
        }, covers(apps));
        java.util.Set<Integer> hidden = DATA.real ? DATA.hidden(apps) : new java.util.HashSet<>(Arrays.asList(105, 106));
        lib.bind(pc("ATLAS-PC", true), HomeModel.build(apps, 2, GAMES[1], true, hidden, true), true);
        Bitmap bmp = draw(lib.getView(), 1920, 1080);
        save(bmp, "console-braise-dark-library");
        assertNotEquals(0, bmp.getWidth());
    }

    private static final class NoActions implements HomeScreen.Actions {
        @Override public void switchHost(int delta) {}
        @Override public void pickHost() {}
        @Override public void openHosts() {}
        @Override public void openSettings() {}
        @Override public void cycleMode() {}
        @Override public void pickTheme() {}
        @Override public void launch(NvApp app) {}
        @Override public void options(NvApp app) {}
        @Override public void resumeRunning() {}
        @Override public void quitRunning() {}
        @Override public void openLibrary() {}
        @Override public void wake() {}
    }
}
