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
    private static final String[] GAMES = {
            "Red Dead Redemption 2", "Hades II", "Cyberpunk 2077", "Elden Ring", "Celeste",
            "Forza Horizon 5", "Baldur's Gate 3", "Hollow Knight", "Stardew Valley", "Portal 2", "Outer Wilds", "Tunic"};

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    private static NvApp app(String name, int id) {
        return new NvApp(name, "uuid-" + id, id, false);
    }

    private static List<NvApp> apps() {
        List<NvApp> list = new ArrayList<>();
        list.add(app("Desktop", 1));
        NvApp playnite = app("Playnite", 2);
        playnite.setPlayniteHost(true);
        list.add(playnite);
        list.add(app("Steam Big Picture", 3));
        for (int i = 0; i < GAMES.length; i++) {
            NvApp g = app(GAMES[i], 100 + i);
            g.setPlayniteGame(true);
            list.add(g);
        }
        return list;
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

    /** Box-art-like images: a gradient per game, its title in big letters. Two games have none. */
    private static final class FakeCovers implements CoverSource {
        private final Map<Integer, Bitmap> bitmaps = new HashMap<>();

        FakeCovers(List<NvApp> apps) {
            int[][] palette = {{0xFF8E2B1C, 0xFF2A0F0A}, {0xFF3B1E6B, 0xFF0E0A1F}, {0xFFE0C43A, 0xFF4A2A06},
                    {0xFF5B6B3A, 0xFF101A0C}, {0xFF2F7FB5, 0xFF0B1E33}, {0xFFB53A6B, 0xFF2A0A18}};
            int i = 0;
            for (NvApp a : apps) {
                if (a.getAppId() == 100 || a.getAppId() == 104 || a.getAppId() == 109) {
                    continue; // no cover on the host: the name shows (a long one: Red Dead Redemption 2)
                }
                int[] c = palette[i++ % palette.length];
                bitmaps.put(a.getAppId(), cover(a.getAppName(), c[0], c[1], a.getAppId() < 100));
            }
        }

        private static Bitmap cover(String title, int top, int bottom, boolean landscapeArt) {
            Bitmap b = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, 450, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 300, 450, p);
            p.setShader(null);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawCircle(220, 140, 110, p);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            // The title fits the art: one word per line from the bottom up, smaller when it is long
            String[] words = title.split(" ");
            float size = landscapeArt ? 44 : (words.length > 3 ? 32 : 38);
            p.setTextSize(size);
            for (String w : words) {
                while (p.measureText(w.toUpperCase()) > 256 && size > 18) {
                    size -= 2;
                    p.setTextSize(size);
                }
            }
            float line = size * 1.15f;
            // Wide art (Bureau, Playnite) is cropped to its middle on the cards: the title stays there
            float y = landscapeArt ? 330 : 450 - 24 - (words.length - 1) * line;
            for (String word : words) {
                c.drawText(word.toUpperCase(), 22, y, p);
                y += line;
            }
            return b;
        }

        @Override
        public void load(NvApp app, Sink sink) {
            sink.onCover(app.getAppId(), bitmaps.get(app.getAppId()));
        }

        @Override
        public Bitmap peek(NvApp app) {
            return bitmaps.get(app.getAppId());
        }
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
        RuntimeEnvironment.setQualifiers("w" + wDp + "dp-h" + hDp + "dp-" + (wDp > hDp ? "land" : "port") + "-xhdpi");
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        V2Theme theme = V2Theme.of(themeId, dark);
        List<NvApp> apps = apps();
        FakeCovers covers = new FakeCovers(apps);

        HomeScreen.State s = new HomeScreen.State();
        ComputerDetails atlas = pc("ATLAS-PC", true);
        s.hosts = Arrays.asList(atlas, pc("SALON", false));
        s.hostIndex = 0;
        int running = scene == Scene.PLAYNITE_GAME ? 2 : (scene == Scene.DESKTOP ? 1 : 0);
        String game = scene == Scene.PLAYNITE_GAME ? GAMES[1] : "";
        s.model = HomeModel.build(apps, running, game, scene == Scene.PLAYNITE_GAME, null, false);
        s.sessionMinutes = running != 0 ? 42 : -1;

        HomeScreen screen = new HomeScreen(ctx, theme, Density.forSize(wDp, hDp), new NoActions(), covers);
        screen.bind(s);
        int density = 2;
        View root = screen.getView();
        Bitmap bmp = draw(root, wDp * density, hDp * density);
        if (input != V2Input.Kind.TOUCH) {
            // Gamepad: the focus starts on Reprendre (or the first card) and its ring shows
            screen.requestInitialFocus();
            bmp = draw(root, wDp * density, hDp * density);
        }
        save(bmp, file);
    }

    @Test
    public void phoneBraiseDark() throws Exception {
        home("phone-braise-dark-idle", "braise", true, 915, 412, Scene.IDLE, V2Input.Kind.TOUCH);
        home("phone-braise-dark-session", "braise", true, 915, 412, Scene.PLAYNITE_GAME, V2Input.Kind.TOUCH);
    }

    @Test
    public void consoleBraiseDark() throws Exception {
        home("console-braise-dark-idle", "braise", true, 960, 540, Scene.IDLE, V2Input.Kind.PAD);
        home("console-braise-dark-session", "braise", true, 960, 540, Scene.PLAYNITE_GAME, V2Input.Kind.PAD);
    }

    @Test
    public void afficheLight() throws Exception {
        home("phone-affiche-light-idle", "affiche", false, 915, 412, Scene.IDLE, V2Input.Kind.TOUCH);
        home("phone-affiche-light-session", "affiche", false, 915, 412, Scene.DESKTOP, V2Input.Kind.TOUCH);
        home("console-affiche-light-idle", "affiche", false, 960, 540, Scene.IDLE, V2Input.Kind.PAD);
        home("console-affiche-light-session", "affiche", false, 960, 540, Scene.PLAYNITE_GAME, V2Input.Kind.PAD);
    }

    @Test
    public void ludiqueDark() throws Exception {
        home("phone-ludique-dark-idle", "ludique", true, 915, 412, Scene.IDLE, V2Input.Kind.TOUCH);
        home("phone-ludique-dark-session", "ludique", true, 915, 412, Scene.PLAYNITE_GAME, V2Input.Kind.TOUCH);
        home("console-ludique-dark-idle", "ludique", true, 960, 540, Scene.IDLE, V2Input.Kind.PAD);
        home("console-ludique-dark-session", "ludique", true, 960, 540, Scene.DESKTOP, V2Input.Kind.KEYS);
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
        HomeScreen none = new HomeScreen(ctx, V2Theme.of("braise", true), Density.forSize(915, 412), new NoActions(), null);
        none.bind(new HomeScreen.State());
        save(draw(none.getView(), 1830, 824), "phone-braise-dark-no-pc");

        HomeScreen.State off = new HomeScreen.State();
        off.hosts = Arrays.asList(pc("ATLAS-PC", false));
        HomeScreen offline = new HomeScreen(ctx, V2Theme.of("ludique", false), Density.forSize(915, 412), new NoActions(), null);
        offline.bind(off);
        save(draw(offline.getView(), 1830, 824), "phone-ludique-light-offline");
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
        }, new FakeCovers(apps));
        java.util.Set<Integer> hidden = new java.util.HashSet<>(Arrays.asList(105, 106));
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
