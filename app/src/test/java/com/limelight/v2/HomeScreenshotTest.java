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
                spec.option(ctx.getString(R.string.v2_show_details), false, false, null);
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
                // The stream: the running game's background (prototype), else a cover
                Bitmap stream = covers.peekBackground(apps.get(3));
                if (stream == null) {
                    stream = covers.peek(apps.get(4));
                }
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

    /**
     * The sub-dialogs of the in-game menu over it (prototype V8-29 Plus, V8-30 keys, V8-64 commands,
     * V8-31 no command), focus on the first option at the gamepad. Labels as GameMenu builds them.
     */
    @Test
    public void streamSheets() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                String mode = dark ? "sombre" : "clair";
                RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
                V2Input.reset(V2Input.Kind.PAD);
                Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
                V2Theme theme = V2Theme.of(t, dark);
                HomeLayout l = HomeLayout.forSize(1280, 720);

                V2Sheet.Spec plus = streamSpec(ctx, R.string.v2_menu_more);
                plus.note = theme.isAffiche() ? GAMES[0] + " — " + ctx.getString(R.string.v2_via_inside).toUpperCase(java.util.Locale.FRENCH)
                        : GAMES[0] + " · " + ctx.getString(R.string.v2_via_inside);
                plus.option(StreamMenu.withValue(ctx.getString(R.string.v2_more_mic), ctx.getString(R.string.v2_more_mic_off)), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_voice_hide), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_voice_move), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_rotate), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_osc), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_special_keys), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_full_keyboard), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_sensitivity), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_quit_session), false, false, null);
                plus.option(ctx.getString(R.string.v2_more_pill_hide), false, false, null);
                plus.option(ctx.getString(R.string.v2_cancel), false, false, null);
                sheetShot(ctx, theme, l, plus, "plus-" + t + "-" + mode + "-manette");

                V2Sheet.Spec keys = streamSpec(ctx, R.string.v2_menu_send_keys);
                keys.note = ctx.getString(R.string.v2_keys_note);
                int[] keyLabels = {R.string.v2_keys_esc, R.string.v2_keys_f11, R.string.v2_keys_alt_f4, R.string.v2_keys_alt_enter,
                        R.string.v2_keys_ctrl_v, R.string.v2_keys_win, R.string.v2_keys_win_d, R.string.v2_keys_win_g,
                        R.string.v2_keys_ctrl_alt_del, R.string.v2_keys_ctrl_shift_esc, R.string.v2_keys_ctrl_alt_tab,
                        R.string.v2_keys_shift_tab, R.string.v2_keys_win_shift_left, R.string.v2_keys_ctrl_alt_shift_f1,
                        R.string.v2_keys_ctrl_alt_shift_f12};
                for (int k : keyLabels) {
                    keys.option(ctx.getString(k), false, false, null);
                }
                keys.option(ctx.getString(R.string.v2_cancel), false, false, null);
                sheetShot(ctx, theme, l, keys, "keys-" + t + "-" + mode + "-manette");

                V2Sheet.Spec cmds = streamSpec(ctx, R.string.v2_menu_server_cmd);
                cmds.note = ctx.getString(R.string.v2_cmds_note);
                cmds.option("Verrouiller la session Windows", false, false, null);
                cmds.option("Redémarrer Playnite", false, false, null);
                cmds.option("Éteindre l’écran du PC", false, false, null);
                cmds.option(ctx.getString(R.string.v2_cancel), false, false, null);
                sheetShot(ctx, theme, l, cmds, "cmds-" + t + "-" + mode + "-manette");

                V2Sheet.Spec none = streamSpec(ctx, R.string.v2_cmds_none_title);
                none.note = ctx.getString(R.string.v2_cmds_none_note);
                none.option(ctx.getString(R.string.v2_ok), true, false, null);
                sheetShot(ctx, theme, l, none, "nocmds-" + t + "-" + mode + "-manette");
            }
        }
    }

    private static V2Sheet.Spec streamSpec(Context ctx, int title) {
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.kind = ctx.getString(R.string.v2_menu_kind);
        spec.where = "ATLAS-PC";
        spec.title = ctx.getString(title);
        spec.info = true;
        return spec;
    }

    /** The stream (the running game's background), the tile menu, then the sheet over them */
    private void sheetShot(Context ctx, V2Theme theme, HomeLayout l, V2Sheet.Spec spec, String name) throws Exception {
        List<NvApp> apps = apps();
        CoverSource covers = covers(apps);
        Bitmap bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Bitmap stream = covers.peekBackground(apps.get(3));
        if (stream == null) {
            stream = covers.peek(apps.get(4));
        }
        if (stream != null) {
            c.drawBitmap(stream, null, new android.graphics.Rect(0, 0, 1280, 720), new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        java.util.List<V2GameMenu.Tile> tiles = new java.util.ArrayList<>();
        tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_resume), ctx.getString(R.string.v2_resume), "", "", R.drawable.v2_ic_m_resume, false, 0, null));
        for (int i = 0; i < 8; i++) {
            tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_more), ctx.getString(R.string.v2_menu_more), "›", "", R.drawable.v2_ic_m_more, false, 1, null));
        }
        tiles.add(new V2GameMenu.Tile("Déconnecter", "Déconnecter", "", "", R.drawable.v2_ic_m_disconnect, false, 7, null));
        tiles.add(new V2GameMenu.Tile(ctx.getString(R.string.v2_menu_quit, GAMES[0]), ctx.getString(R.string.v2_short_quit), "",
                ctx.getString(R.string.v2_menu_quit_playnite_sub), R.drawable.v2_ic_m_quit, false, 8, null));
        // The menu under the sheet without its own hints (in the app it closes when a sub-dialog opens)
        V2Input.reset(V2Input.Kind.TOUCH);
        View menu = V2GameMenu.build(ctx, theme, l, GAMES[0], "ATLAS-PC", ctx.getString(R.string.v2_via_inside), tiles, null);
        c.drawBitmap(draw(menu, 1280, 720), 0, 0, null);
        V2Input.reset(V2Input.Kind.PAD);
        View sheet = V2Sheet.build(ctx, theme, l, spec, null);
        draw(sheet, 1280, 720);
        View first = firstFocusable(sheet);
        if (first != null) {
            first.setFocusableInTouchMode(true);
            first.requestFocus();
        }
        c.drawBitmap(draw(sheet, 1280, 720), 0, 0, null);
        save(bmp, name);
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

    /**
     * The details screen (prototype V8-68): Split Fiction picked while Red Dead Redemption 2 runs
     * inside Playnite (warning note), focus on the launch button at the gamepad. The profile and the
     * bitrate show the prototype's values (Wi-Fi maison, 50 Mbit/s) so the captures line up.
     */
    @Test
    public void details() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                for (V2Input.Kind input : new V2Input.Kind[]{V2Input.Kind.PAD, V2Input.Kind.TOUCH}) {
                    RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
                    V2Input.reset(input);
                    Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
                    V2Theme theme = V2Theme.of(t, dark);
                    List<NvApp> apps = apps();
                    CoverSource covers = covers(apps);
                    HomeModel m = HomeModel.build(apps, 2, GAMES[0], true, DATA.hidden(apps), false);
                    NvApp split = apps.get(3 + 4);
                    DetailsScreen.State st = DetailsActivity.state(ctx, split, pc("ATLAS-PC", true), m, theme.isAffiche(),
                            new com.limelight.v2.settings.SettingsStore(ctx));
                    st.values[0] = "Wi-Fi maison";
                    st.values[3] = "50 Mbit/s";
                    st.cover = covers.peek(split);
                    st.background = covers.peekBackground(split);
                    st.blurBackground = st.background == null;
                    DetailsScreen screen = new DetailsScreen(ctx, theme, HomeLayout.forSize(1280, 720), new DetailsScreen.Actions() {
                        @Override public void back() {}
                        @Override public void change(int row, int d) {}
                        @Override public void go() {}
                    });
                    screen.bind(st);
                    View root = screen.getView();
                    draw(root, 1280, 720);
                    if (input != V2Input.Kind.TOUCH) {
                        for (List<View> fr : screen.focusRows()) { for (View fv : fr) { fv.setFocusableInTouchMode(true); } }
                        screen.requestInitialFocus();
                        screen.onFocusMoved();
                    }
                    save(draw(root, 1280, 720), "details-" + t + "-" + (dark ? "sombre" : "clair") + "-"
                            + (input == V2Input.Kind.TOUCH ? "tactile" : "manette"));
                }
            }
        }
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

    /**
     * The library page (prototype V8-22): every theme, light and dark, at the gamepad (focus on the
     * 2nd game, Uncrashed) and at touch; then the Installés and Masqués filters (V8-60 / V8-72) and an
     * empty library (V8-23), Braise dark at the gamepad.
     */
    @Test
    public void library() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                for (V2Input.Kind input : new V2Input.Kind[]{V2Input.Kind.PAD, V2Input.Kind.TOUCH}) {
                    libraryShot("library-" + t + "-" + (dark ? "sombre" : "clair") + "-" + (input == V2Input.Kind.TOUCH ? "tactile" : "manette"),
                            t, dark, input, 0, true);
                }
            }
        }
        libraryShot("library-installes-braise-sombre-manette", "braise", true, V2Input.Kind.PAD, 2, true);
        libraryShot("library-masques-braise-sombre-manette", "braise", true, V2Input.Kind.PAD, 3, true);
        libraryShot("library-masques-affiche-clair-manette", "affiche", false, V2Input.Kind.PAD, 3, true);
        libraryShot("library-vide-braise-sombre-manette", "braise", true, V2Input.Kind.PAD, 0, false);
        libraryShot("library-vide-ludique-clair-manette", "ludique", false, V2Input.Kind.PAD, 0, false);
    }

    private void libraryShot(String file, String t, boolean dark, V2Input.Kind input, int filter, boolean games) throws Exception {
        RuntimeEnvironment.setQualifiers("w1280dp-h720dp-land-mdpi");
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        V2Theme theme = V2Theme.of(t, dark);
        List<NvApp> apps = games ? apps() : new ArrayList<>(apps().subList(0, 3));
        CoverSource covers = covers(apps);
        java.util.Set<Integer> hidden = DATA.real ? DATA.hidden(apps) : new java.util.HashSet<>(Arrays.asList(105));
        HomeModel m = HomeModel.build(apps, 2, GAMES[0], true, hidden, false);
        LibraryScreen lib = new LibraryScreen(ctx, theme, HomeLayout.forSize(1280, 720), new LibraryScreen.Actions() {
            @Override public void back() {}
            @Override public void launch(NvApp app) {}
            @Override public void options(NvApp app) {}
        }, covers);
        lib.bind(pc("ATLAS-PC", true), m, ShotData.NOW);
        if (filter != 0) {
            lib.setFilter(filter);
        }
        View root = lib.getView();
        draw(root, 1280, 720);
        if (input != V2Input.Kind.TOUCH) {
            allowFocus(root);
            if (games) {
                lib.focusIndex(filter == 0 ? 1 : 0);
            } else {
                lib.requestInitialFocus();
            }
            draw(root, 1280, 720);
            allowFocus(root);
            if (games) {
                lib.focusIndex(filter == 0 ? 1 : 0);
            }
        }
        save(draw(root, 1280, 720), file);
    }

    /** Robolectric keeps the window in touch mode: the focusable views take the focus anyway */
    private static void allowFocus(View v) {
        if (v.isFocusable()) {
            v.setFocusableInTouchMode(true);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                allowFocus(g.getChildAt(i));
            }
        }
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
