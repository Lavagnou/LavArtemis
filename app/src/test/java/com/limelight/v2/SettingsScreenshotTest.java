package com.limelight.v2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.TestLogSuppressor;
import com.limelight.profiles.ProfilesManager;
import com.limelight.v2.settings.Option;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingRowView;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders Réglages v2 from fake devices at the validated densities (phone 915x412 dp, console
 * 960x540 dp, + portrait), the three themes, expert mode off and on, a choice list and a focused
 * slider, and writes PNGs (prefix settings-) to app/build/v2-shots or $V2_SHOTS_DIR. Also checks
 * that no English is left on screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsScreenshotTest {
    private static final int DENSITY = 2; // xhdpi

    @BeforeClass
    public static void quiet() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        Field f = ProfilesManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        deleteRecursively(new File(app.getFilesDir(), "profiles"));
        ProfilesManager.getInstance().load(app);
        // A setup with a few changes, so the rows do not all show their defaults
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString("list_resolution", "1920x1080")
                .putBoolean("checkbox_mic_passthrough", true)
                .putBoolean("checkbox_vrr_pacing", true)
                .putBoolean("checkbox_enable_perf_overlay", true)
                .putString("mouse_mode_list", "2")
                .putBoolean("checkbox_trackpad_pinch_zoom", true)
                .commit();
    }

    @After
    public void tearDown() {
        V2Input.reset(null);
        SettingsActivity.envOverride = null;
    }

    // ---- rendering --------------------------------------------------------------------------------

    private static File outDir() {
        String env = System.getenv("V2_SHOTS_DIR");
        File dir = env != null && !env.isEmpty() ? new File(env) : new File("build/v2-shots");
        dir.mkdirs();
        return dir;
    }

    private static Bitmap draw(View view, int widthPx, int heightPx) {
        for (int pass = 0; pass < 2; pass++) {
            view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, widthPx, heightPx);
            ShadowLooper.idleMainLooper();
        }
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    private static Bitmap overlay(Bitmap page, Dialog dialog, float dim) {
        ShadowLooper.idleMainLooper();
        View content = dialog.findViewById(android.R.id.content);
        assertNotNull(content);
        int w = page.getWidth(), h = page.getHeight();
        for (int pass = 0; pass < 2; pass++) {
            content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
            content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
            ShadowLooper.idleMainLooper();
        }
        Bitmap out = page.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        c.drawColor(Color.argb(Math.round(255 * dim), 0, 0, 0));
        c.save();
        c.translate((w - content.getMeasuredWidth()) / 2f, (h - content.getMeasuredHeight()) / 2f);
        content.draw(c);
        c.restore();
        return out;
    }

    private static void save(Bitmap bmp, String name) throws Exception {
        File f = new File(outDir(), name + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void qualifiers(int wDp, int hDp) {
        RuntimeEnvironment.setQualifiers("fr-w" + wDp + "dp-h" + hDp + "dp-" + (wDp > hDp ? "land" : "port") + "-xhdpi");
    }

    private static void useTheme(String id, boolean dark) {
        Context app = ApplicationProvider.getApplicationContext();
        V2Theme.saveTheme(app, id);
        V2Theme.saveMode(app, dark ? V2Theme.MODE_DARK : V2Theme.MODE_LIGHT);
    }

    private static void expert(boolean on) {
        new SettingsStore(ApplicationProvider.getApplicationContext()).setExpertMode(on);
    }

    private static SettingsEnv env() {
        SettingsEnv e = SettingsEnv.forTests();
        e.microphones.add(new String[]{"usb:Blue Yeti", "Blue Yeti"});
        return e;
    }

    private SettingsScreen screen(String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input, String page, boolean rows) {
        qualifiers(wDp, hDp);
        V2Input.reset(input);
        useTheme(themeId, dark);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        return new SettingsScreen(ctx, V2Theme.of(themeId, dark), Density.forSize(wDp, hDp), env(),
                new SettingsStore(ctx), new NoActions(), page, rows);
    }

    private static void focus(View v) {
        v.setFocusableInTouchMode(true);
        assertTrue("focus on " + v, v.requestFocus());
    }

    private void shot(String file, String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input, String page,
                      boolean rows, String focusKey) throws Exception {
        SettingsScreen s = screen(themeId, dark, wDp, hDp, input, page, rows);
        View root = s.getView();
        draw(root, wDp * DENSITY, hDp * DENSITY);
        if (focusKey != null) {
            SettingRowView r = s.rowFor(focusKey);
            assertNotNull(focusKey, r);
            focus(r);
        }
        Bitmap bmp = draw(root, wDp * DENSITY, hDp * DENSITY);
        checkFrench(root);
        save(bmp, file);
    }

    // ---- prototype stage (V8-48 to 57: 1280 x 720) ----------------------------------------------------

    private void protoShot(String file, String themeId, boolean dark, V2Input.Kind input, String page, boolean onExpertRow,
                           String focusKey) throws Exception {
        RuntimeEnvironment.setQualifiers("fr-w1280dp-h720dp-land-mdpi");
        V2Input.reset(input);
        useTheme(themeId, dark);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        SettingsScreen s = new SettingsScreen(ctx, V2Theme.of(themeId, dark), Density.forSize(1280, 720), env(),
                new SettingsStore(ctx), new NoActions(), page, true);
        View root = s.getView();
        draw(root, 1280, 720);
        if (input != V2Input.Kind.TOUCH) {
            View target = focusKey != null ? s.rowFor(focusKey) : s.navFor(page);
            assertNotNull(target);
            focus(target);
            s.refreshInput();
        }
        Bitmap bmp = draw(root, 1280, 720);
        checkFrench(root);
        save(bmp, file);
    }

    /** V8-49: Vidéo, expert off (3 hidden rows), focus on the page at the gamepad; the prototype's values */
    @Test
    public void protoVideo() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        for (String t : new String[]{"braise", "affiche", "ludique"}) {
            for (boolean dark : new boolean[]{false, true}) {
                for (V2Input.Kind input : new V2Input.Kind[]{V2Input.Kind.PAD, V2Input.Kind.TOUCH}) {
                    setUp();
                    PreferenceManager.getDefaultSharedPreferences(app).edit()
                            .putBoolean("checkbox_enable_perf_overlay", false).commit();
                    expert(false);
                    protoShot("settings-video-" + t + "-" + (dark ? "sombre" : "clair") + "-"
                            + (input == V2Input.Kind.TOUCH ? "tactile" : "manette"), t, dark, input, SettingsCatalog.VIDEO, false, null);
                }
            }
        }
    }

    /** The other pages of the planches (V8-50 Profil, 53 Apparence, 54 Entrées, 55 Manettes, 56 Contrôles), expert on and off */
    @Test
    public void protoPages() throws Exception {
        String[][] pages = {
                {"profil", SettingsCatalog.PROFIL, "0"}, {"apparence", SettingsCatalog.APPARENCE, "0"},
                {"entrees", SettingsCatalog.ENTREES, "0"}, {"manettes", SettingsCatalog.MANETTES, "1"},
                {"controles", SettingsCatalog.CONTROLES, "0"}, {"video-expert", SettingsCatalog.VIDEO, "1"}};
        for (String[] p : pages) {
            for (String t : new String[]{"braise", "affiche", "ludique"}) {
                for (boolean dark : new boolean[]{false, true}) {
                    setUp();
                    expert("1".equals(p[2]));
                    protoShot("settings-" + p[0] + "-" + t + "-" + (dark ? "sombre" : "clair") + "-manette", t, dark,
                            V2Input.Kind.PAD, p[1], false, null);
                    protoShot("settings-" + p[0] + "-" + t + "-" + (dark ? "sombre" : "clair") + "-tactile", t, dark,
                            V2Input.Kind.TOUCH, p[1], false, null);
                }
            }
        }
    }

    // ---- pages ------------------------------------------------------------------------------------

    @Test
    public void braiseDark() throws Exception {
        expert(false);
        shot("settings-phone-braise-dark-affichage", "braise", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.AFFICHAGE, true, null);
        shot("settings-phone-braise-dark-video", "braise", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.VIDEO, true, null);
        expert(true);
        shot("settings-phone-braise-dark-affichage-expert", "braise", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.AFFICHAGE, true, null);
        // The bitrate slider, focused with the gamepad
        shot("settings-console-braise-dark-video-expert-slider", "braise", true, 960, 540, V2Input.Kind.PAD, SettingsCatalog.VIDEO, true,
                SettingsCatalog.KEY_BITRATE);
        expert(false);
        shot("settings-portrait-braise-dark-list", "braise", true, 412, 915, V2Input.Kind.TOUCH, SettingsCatalog.PROFIL, false, null);
    }

    @Test
    public void afficheLight() throws Exception {
        expert(false);
        shot("settings-phone-affiche-light-audio", "affiche", false, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.AUDIO, true, null);
        shot("settings-console-affiche-light-apparence", "affiche", false, 960, 540, V2Input.Kind.PAD, SettingsCatalog.APPARENCE, true,
                V2Theme.PREF_THEME);
        expert(true);
        shot("settings-console-affiche-light-audio-expert", "affiche", false, 960, 540, V2Input.Kind.KEYS, SettingsCatalog.AUDIO, true,
                "seekbar_max_pending_audio_ms");
        shot("settings-phone-affiche-light-manettes-expert", "affiche", false, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.MANETTES, true, null);
    }

    @Test
    public void ludiqueDark() throws Exception {
        expert(true);
        shot("settings-phone-ludique-dark-entrees-expert", "ludique", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.ENTREES, true, null);
        shot("settings-console-ludique-dark-controles-expert", "ludique", true, 960, 540, V2Input.Kind.PAD, SettingsCatalog.CONTROLES, true,
                "checkbox_show_onscreen_controls");
        expert(false);
        new SettingsStore(ApplicationProvider.getApplicationContext()).createProfile("Salon");
        shot("settings-console-ludique-dark-profil", "ludique", true, 960, 540, V2Input.Kind.KEYS, SettingsCatalog.PROFIL, true, null);
        shot("settings-portrait-ludique-dark-affichage", "ludique", true, 412, 915, V2Input.Kind.TOUCH, SettingsCatalog.AFFICHAGE, true, null);
        shot("settings-phone-ludique-dark-avance", "ludique", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.AVANCE, true, null);
    }

    /** Every page in full, expert mode on: a tall render to review all the rows at once */
    @Test
    public void everyPageInFull() throws Exception {
        expert(true);
        for (SettingsCatalog.Page p : SettingsCatalog.PAGES) {
            SettingsScreen s = screen("braise", true, 915, 412, V2Input.Kind.TOUCH, p.id, true);
            int h = 412;
            View root = s.getView();
            // Grow the page until its rows fit
            for (int i = 0; i < 6; i++) {
                draw(root, 915 * DENSITY, h * DENSITY);
                ViewGroup column = (ViewGroup) s.rows().get(0).getParent();
                int needed = column.getMeasuredHeight() / DENSITY + 90;
                if (needed <= h) {
                    break;
                }
                h = needed;
            }
            Bitmap bmp = draw(root, 915 * DENSITY, h * DENSITY);
            checkFrench(root);
            save(bmp, "settings-full-" + p.id);
        }
    }

    // ---- dialogs ----------------------------------------------------------------------------------

    private SettingsActivity activity(String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input) {
        qualifiers(wDp, hDp);
        useTheme(themeId, dark);
        V2Input.reset(input);
        SettingsActivity.envOverride = env();
        return Robolectric.buildActivity(SettingsActivity.class).setup().get();
    }

    private static void focusFirst(Dialog dialog) {
        View content = dialog.findViewById(android.R.id.content);
        ArrayList<View> focusables = new ArrayList<>();
        content.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
        for (View v : focusables) {
            if (v instanceof android.widget.ScrollView) {
                continue;
            }
            if (v.isSelected() || focusables.indexOf(v) == focusables.size() - 1) {
                focus(v);
                return;
            }
        }
    }

    @Test
    public void choiceDialogs() throws Exception {
        expert(false);
        SettingsActivity a = activity("braise", true, 915, 412, V2Input.Kind.TOUCH);
        Bitmap page = draw(screen("braise", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.AFFICHAGE, true).getView(), 915 * DENSITY, 412 * DENSITY);
        a.onOpenList(SettingsCatalog.find("frame_pacing"));
        Dialog d = ShadowDialog.getLatestDialog();
        checkFrench(d.findViewById(android.R.id.content));
        save(overlay(page, d, 0.5f), "settings-phone-braise-dark-choice-pacing");
        d.dismiss();

        SettingsActivity f = activity("affiche", false, 960, 540, V2Input.Kind.PAD);
        Bitmap fpage = draw(screen("affiche", false, 960, 540, V2Input.Kind.PAD, SettingsCatalog.AFFICHAGE, true).getView(), 960 * DENSITY, 540 * DENSITY);
        f.onOpenList(SettingsCatalog.find("list_resolution"));
        Dialog fd = ShadowDialog.getLatestDialog();
        focusFirst(fd);
        save(overlay(fpage, fd, 0.5f), "settings-console-affiche-light-choice-resolution");
        fd.dismiss();

        // The custom resolution prompt
        f.onChoose(SettingsCatalog.find("list_resolution"), Option.ask("custom", "x"));
        Dialog prompt = ShadowDialog.getLatestDialog();
        save(overlay(fpage, prompt, 0.45f), "settings-console-affiche-light-custom-resolution");
        prompt.dismiss();

        // A new profile
        SettingsActivity l = activity("ludique", true, 915, 412, V2Input.Kind.TOUCH);
        Bitmap lpage = draw(screen("ludique", true, 915, 412, V2Input.Kind.TOUCH, SettingsCatalog.PROFIL, true).getView(), 915 * DENSITY, 412 * DENSITY);
        l.onAction(SettingsCatalog.find(SettingsCatalog.KEY_PROFILES_MANAGE), 0);
        save(overlay(lpage, ShadowDialog.getLatestDialog(), 0.45f), "settings-phone-ludique-dark-new-profile");
    }

    // ---- French only -------------------------------------------------------------------------------

    /** English words that must not show on a French screen (the legacy strings are English by default) */
    private static final Pattern ENGLISH = Pattern.compile(
            "\\b(the|and|with|Enable|Disable|Settings|Show|Hide|Allow|Default|Custom|Prefer|Use|When|Display|Mouse|Touch|Gamepad|Controller|Advanced|Audio Settings)\\b");

    private static void checkFrench(View v) {
        List<String> found = new ArrayList<>();
        collect(v, found);
        assertTrue("English on screen: " + found, found.isEmpty());
    }

    private static void collect(View v, List<String> found) {
        if (v instanceof TextView) {
            String t = ((TextView) v).getText().toString();
            if (ENGLISH.matcher(t).find()) {
                found.add(t);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), found);
            }
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }

    private static final class NoActions implements SettingsScreen.Actions {
        @Override public void back() {}
        @Override public void setExpert(boolean on) {}
        @Override public void onToggle(SettingDef def, boolean shown) {}
        @Override public void onChoose(SettingDef def, Option option) {}
        @Override public void onOpenList(SettingDef def) {}
        @Override public void onSlide(SettingDef def, int value, boolean finished) {}
        @Override public void onAction(SettingDef def, int button) {}
        @Override public boolean onLeaveLeft() { return false; }
    }
}
