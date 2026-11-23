package com.limelight.v2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.BenchmarkActivity;
import com.limelight.BuildConfig;
import com.limelight.DebugInfoActivity;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.AddComputerManually;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;
import com.limelight.utils.UpdateChecker;
import com.limelight.utils.UpdateDialogHelper;
import com.limelight.v2.ui.ThemedDialogs;
import com.limelight.v2.ui.V2ListDialog;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The screens and dialogs the v2 interface still reached in their legacy look, now themed in
 * LavArtemis Dev: the in-game menu's sub-menus, the stream's dialogs (error, quit, connection),
 * the unsupported host, Benchmark, Infos de débogage, the update and link dialogs, the on-screen
 * controls' dialogs. PNGs (prefix gaps-) to app/build/v2-shots or $V2_SHOTS_DIR, in French, and
 * each one is checked for English left on screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GapsScreenshotTest {
    private static final int DENSITY = 2; // xhdpi

    @Before
    public void devOnly() {
        Assume.assumeTrue("Dev build only", ThemedDialogs.enabled() && BuildConfig.LAV_DEV);
    }

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    // ---- rendering (as HostsScreenshotTest) -----------------------------------------------------

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
        assertNoEnglish(content);
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

    /** Words of the legacy English UI that must not be left on a French screen */
    private static final String[] ENGLISH = {"Cancel", "Connection", "Error", "Failed", "Starting", "Please", "Move",
            "Resize", "Quit", "Settings", "Download", "Later", "Skip", "Proceed", "Help", "Yes", "No ", "Back", "Start",
            "Stop", "Open", "Delete", "Refresh", "Test ", "Vibration Amplitude", "Select", "Your "};

    private static void assertNoEnglish(View root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof TextView && v.getVisibility() == View.VISIBLE) {
                String text = ((TextView) v).getText().toString();
                for (String w : ENGLISH) {
                    assertTrue("English left on screen: « " + text + " »", !text.startsWith(w) && !text.contains(" " + w + " "));
                }
            }
        }
    }

    private static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out);
            }
        }
    }

    private static void setup(int wDp, int hDp, String themeId, boolean dark, V2Input.Kind input) {
        RuntimeEnvironment.setQualifiers("fr-w" + wDp + "dp-h" + hDp + "dp-" + (wDp > hDp ? "land" : "port") + "-xhdpi");
        Context app = ApplicationProvider.getApplicationContext();
        V2Theme.saveTheme(app, themeId);
        V2Theme.saveMode(app, dark ? V2Theme.MODE_DARK : V2Theme.MODE_LIGHT);
        V2Input.reset(input);
    }

    /** A stand-in for the stream behind the in-game dialogs */
    private static Bitmap stream(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF3B1E6B, 0xFF8E2B1C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(h * 0.12f);
        c.drawText("HADES II", w * 0.06f, h * 0.85f, p);
        return b;
    }

    /** The page behind a dialog opened from a v2 screen: the theme's background */
    private static Bitmap plain(Context c, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(V2Theme.load(c).bg);
        return b;
    }

    private static HostsActivity hosts() {
        return Robolectric.buildActivity(HostsActivity.class).setup().get();
    }

    private static void focusFirst(Dialog dialog) {
        View content = dialog.findViewById(android.R.id.content);
        ArrayList<View> focusables = new ArrayList<>();
        content.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
        for (View v : focusables) {
            if (v instanceof android.widget.ScrollView) {
                continue;
            }
            v.setFocusableInTouchMode(true);
            v.requestFocus();
            return;
        }
    }

    // ---- in-game menu: the sub-menus ------------------------------------------------------------

    /** GameMenu.showAdvancedMenu()'s entries in Dev, in its order (multitouch on, a gamepad connected) */
    private static List<String> advanced(Context c) {
        List<String> l = new ArrayList<>();
        l.add(c.getString(R.string.v2_menu_touch_to_trackpad));
        l.add(c.getString(R.string.game_menu_toggle_hud));
        l.add(c.getString(R.string.game_menu_toggle_floating_button));
        l.add(c.getString(R.string.game_menu_toggle_keyboard_model));
        l.add(c.getString(R.string.game_menu_toggle_virtual_model));
        l.add(c.getString(R.string.game_menu_toggle_virtual_keyboard_model));
        l.add(c.getString(R.string.game_menu_task_manager));
        l.add(c.getString(R.string.game_menu_send_keys));
        l.add(c.getString(R.string.game_menu_switch_touch_sensitivity_model));
        l.add(c.getString(R.string.game_menu_toggle_mouse_on));
        l.add(c.getString(R.string.game_menu_cancel));
        return l;
    }

    private static List<String> specialKeys(Context c) {
        int[] ids = {R.string.game_menu_send_keys_esc, R.string.game_menu_send_keys_f11, R.string.game_menu_send_keys_alt_f4,
                R.string.game_menu_send_keys_alt_enter, R.string.game_menu_send_keys_ctrl_v, R.string.game_menu_send_keys_win,
                R.string.game_menu_send_keys_win_d, R.string.game_menu_send_keys_win_g, R.string.game_menu_send_keys_ctrl_alt_tab,
                R.string.game_menu_send_keys_shift_tab, R.string.game_menu_send_keys_win_shift_left,
                R.string.game_menu_send_keys_ctrl_alt_shift_f1, R.string.game_menu_send_keys_ctrl_alt_shift_f12,
                R.string.game_menu_send_keys_alt_b, R.string.game_menu_cancel};
        List<String> l = new ArrayList<>();
        for (int id : ids) {
            l.add(c.getString(id));
        }
        return l;
    }

    @Test
    public void gameMenuSubMenus() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        HostsActivity a = hosts();
        Bitmap s = stream(915 * DENSITY, 412 * DENSITY);
        List<String> adv = advanced(a);
        Dialog d = V2ListDialog.show(a, V2Theme.load(a), a.getString(R.string.game_menu_advanced), adv, adv.size() - 1, null, null);
        save(overlay(s, d, 0.5f), "gaps-gamemenu-advanced");
        d.dismiss();
        List<String> keys = specialKeys(a);
        d = V2ListDialog.show(a, V2Theme.load(a), a.getString(R.string.game_menu_send_keys), keys, keys.size() - 1, null, null);
        save(overlay(s, d, 0.5f), "gaps-gamemenu-send-keys");
        d.dismiss();
        List<String> cmds = new ArrayList<>();
        cmds.add("> Couper l'écran du PC");
        cmds.add("> Mode Jeu Windows");
        cmds.add(a.getString(R.string.game_menu_cancel));
        d = V2ListDialog.show(a, V2Theme.load(a), a.getString(R.string.game_menu_server_cmd), cmds, 2, null, null);
        save(overlay(s, d, 0.5f), "gaps-gamemenu-server-commands");
        d.dismiss();
    }

    // ---- stream dialogs ---------------------------------------------------------------------------

    @Test
    public void streamDialogs() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        HostsActivity a = hosts();
        Bitmap s = stream(915 * DENSITY, 412 * DENSITY);

        // Game.stageFailed(): the real utils/Dialog, with the text Game builds
        String text = a.getString(R.string.conn_error_stage, a.getString(R.string.stage_rtsp_handshake), -408)
                + "\n\n" + a.getString(R.string.error_msg_timeout)
                + "\n\n" + a.getString(R.string.check_ports_msg) + "\nTCP 47984\nTCP 47989";
        com.limelight.utils.Dialog.displayDialog(a, a.getString(R.string.conn_error_title), text, false);
        ShadowLooper.idleMainLooper();
        save(overlay(s, ShadowDialog.getLatestDialog(), 0.5f), "gaps-stream-error");
        com.limelight.utils.Dialog.closeDialogs();

        com.limelight.utils.Dialog.displayDialog(a, a.getString(R.string.conn_terminated_title),
                a.getString(R.string.no_video_received_error), false);
        ShadowLooper.idleMainLooper();
        save(overlay(s, ShadowDialog.getLatestDialog(), 0.5f), "gaps-stream-terminated");
        com.limelight.utils.Dialog.closeDialogs();

        // Game.quit()
        Dialog q = ThemedDialogs.confirm(a, a.getString(R.string.game_dialog_title_quit_confirm),
                a.getString(R.string.game_dialog_message_quit_confirm), a.getString(R.string.yes), a.getString(R.string.no), null, null);
        save(overlay(s, q, 0.5f), "gaps-stream-quit-confirm");
        q.dismiss();

        // The connection spinner (SpinnerDialog), with Game's stage message
        SpinnerDialog spinner = SpinnerDialog.displayDialog(a, a.getString(R.string.conn_establishing_title),
                a.getString(R.string.conn_establishing_msg), true);
        ShadowLooper.idleMainLooper();
        spinner.setMessage(a.getString(R.string.conn_starting_stage, a.getString(R.string.stage_video_start)));
        ShadowLooper.idleMainLooper();
        save(overlay(plain(a, 915 * DENSITY, 412 * DENSITY), ShadowDialog.getLatestDialog(), 0.3f), "gaps-stream-connecting");
        SpinnerDialog.closeDialogs(a);
    }

    // ---- the unsupported host ---------------------------------------------------------------------

    @Test
    public void unsupportedHost() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        HostsActivity a = hosts();
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-old";
        d.name = "VIEUX-PC";
        d.state = ComputerDetails.State.ONLINE;
        d.pairState = PairingManager.PairState.PAIRED;
        d.serverAppVersion = "2.0.0.0";
        assertTrue(!a.checkSupported(d));
        save(overlay(plain(a, 915 * DENSITY, 412 * DENSITY), ShadowDialog.getLatestDialog(), 0.45f), "gaps-host-unsupported");
    }

    // ---- full-screen legacy activities --------------------------------------------------------------

    private static Bitmap screen(Activity a, int wDp, int hDp) {
        ShadowLooper.idleMainLooper();
        View content = a.findViewById(android.R.id.content);
        Bitmap bmp = draw(content, wDp * DENSITY, hDp * DENSITY);
        assertNoEnglish(content);
        return bmp;
    }

    @Test
    public void benchmark() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        Intent i = new Intent(ApplicationProvider.getApplicationContext(), BenchmarkActivity.class);
        i.putExtra(BenchmarkActivity.EXTRA_PC_UUID, "uuid-atlas");
        i.putExtra(BenchmarkActivity.EXTRA_PC_NAME, "ATLAS-PC");
        BenchmarkActivity b = Robolectric.buildActivity(BenchmarkActivity.class, i).setup().get();
        // The PC as the service would give it: online, with LavApollo's benchmark scene
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-atlas";
        d.name = "ATLAS-PC";
        d.state = ComputerDetails.State.ONLINE;
        d.activeAddress = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        d.benchmarkAppId = 9999;
        d.runningGameId = 2;
        java.lang.reflect.Field f = BenchmarkActivity.class.getDeclaredField("computer");
        f.setAccessible(true);
        f.set(b, d);
        java.lang.reflect.Method render = BenchmarkActivity.class.getDeclaredMethod("render");
        render.setAccessible(true);
        render.invoke(b);
        save(screen(b, 915, 412), "gaps-benchmark-phone-braise-dark");
        // The whole setup page, buttons included
        save(screen(b, 915, 1100), "gaps-benchmark-phone-braise-dark-full");

        // A finished report: the baseline, a better candidate (Appliquer), a failed run
        com.limelight.benchmark.BenchmarkReport report = new com.limelight.benchmark.BenchmarkReport();
        report.hostName = "ATLAS-PC";
        report.createdAtMs = 1795000000000L;
        report.platform = "android";
        report.width = 1920;
        report.height = 1080;
        report.fps = 60;
        report.goal = com.limelight.benchmark.BenchmarkTypes.Goal.BALANCED;
        report.runs.add(run(com.limelight.benchmark.BenchmarkTypes.Role.BASELINE, com.limelight.benchmark.BenchmarkTypes.Codec.H264, 20000, 71.2, 80.4));
        report.runs.add(run(com.limelight.benchmark.BenchmarkTypes.Role.CANDIDATE, com.limelight.benchmark.BenchmarkTypes.Codec.HEVC, 40000, 78.9, 84.1));
        com.limelight.benchmark.BenchmarkTypes.RunResult failed = run(com.limelight.benchmark.BenchmarkTypes.Role.CANDIDATE,
                com.limelight.benchmark.BenchmarkTypes.Codec.AV1, 40000, 0, 0);
        failed.status = com.limelight.benchmark.BenchmarkTypes.Status.FAILED;
        report.runs.add(failed);
        java.lang.reflect.Field shown = BenchmarkActivity.class.getDeclaredField("shownReport");
        shown.setAccessible(true);
        shown.set(b, report);
        render.invoke(b);
        save(screen(b, 915, 1100), "gaps-benchmark-phone-braise-dark-report");

        setup(960, 540, "affiche", false, V2Input.Kind.PAD);
        BenchmarkActivity p = Robolectric.buildActivity(BenchmarkActivity.class, i).setup().get();
        f.set(p, d);
        render.invoke(p);
        save(screen(p, 960, 540), "gaps-benchmark-console-affiche-light");
    }

    private static com.limelight.benchmark.BenchmarkTypes.RunResult run(com.limelight.benchmark.BenchmarkTypes.Role role,
            com.limelight.benchmark.BenchmarkTypes.Codec codec, int kbps, double latency, double smoothness) {
        com.limelight.benchmark.BenchmarkTypes.RunResult r = new com.limelight.benchmark.BenchmarkTypes.RunResult();
        r.role = role;
        r.requested.codec = codec;
        r.requested.bitrateKbps = kbps;
        r.requested.pacing = "default";
        r.effective = r.requested.copy();
        r.scores.latency = latency;
        r.scores.smoothness = smoothness;
        r.spreadLatency = 1.2;
        r.spreadSmoothness = 0.8;
        r.metrics.totalMs = 38.5;
        r.metrics.framesExpected = 3600;
        r.metrics.framesRendered = 3590;
        return r;
    }

    @Test
    public void debugInfo() throws Exception {
        setup(915, 412, "ludique", true, V2Input.Kind.TOUCH);
        DebugInfoActivity a = Robolectric.buildActivity(DebugInfoActivity.class).setup().get();
        save(screen(a, 915, 412), "gaps-debug-info");
    }

    // ---- other dialogs reachable from the v2 screens -----------------------------------------------

    @Test
    public void updateAndLinkDialogs() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        HostsActivity a = hosts();
        java.lang.reflect.Constructor<UpdateChecker.UpdateInfo> c = UpdateChecker.UpdateInfo.class
                .getDeclaredConstructor(String.class, String.class, String.class, boolean.class);
        c.setAccessible(true);
        UpdateDialogHelper.showUpdateDialog(a, c.newInstance("21.9.0", "https://example.invalid", null, false));
        save(overlay(plain(a, 915 * DENSITY, 412 * DENSITY), ShadowDialog.getLatestDialog(), 0.45f), "gaps-update-available");
        ShadowDialog.getLatestDialog().dismiss();

        // A virtual display gate of the legacy screens (UiHelper), with its link
        UiHelper.displayVdisplayConfirmationDialog(a, new ComputerDetails(), null, null);
        save(overlay(plain(a, 915 * DENSITY, 412 * DENSITY), ShadowDialog.getLatestDialog(), 0.45f), "gaps-vdisplay-gate");
        ShadowDialog.getLatestDialog().dismiss();

        // art://…?pin=…: the link's question, over the v2 background
        Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse("art://192.168.1.24:47989?pin=4827&passphrase=x&name=ATLAS-PC"));
        AddComputerManually add = Robolectric.buildActivity(AddComputerManually.class, link).setup().get();
        save(overlay(plain(add, 915 * DENSITY, 412 * DENSITY), ShadowDialog.getLatestDialog(), 0.45f), "gaps-link-pair");
    }

    @Test
    public void onScreenControlsDialogs() throws Exception {
        setup(915, 412, "braise", true, V2Input.Kind.TOUCH);
        HostsActivity a = hosts();
        Bitmap s = stream(915 * DENSITY, 412 * DENSITY);
        CharSequence[] items = {a.getString(R.string.osc_config_move), a.getString(R.string.osc_config_resize), a.getString(R.string.v2_cancel)};
        Dialog d = ThemedDialogs.listWithCancel(a, a.getString(R.string.osc_config_title), items, 2, w -> { }, null);
        save(overlay(s, d, 0.5f), "gaps-osc-element");
        d.dismiss();

        List<String> keys = new ArrayList<>();
        for (String k : new String[]{"Échap", "Tab", "Maj", "Ctrl", "Alt", "Espace", "Entrée", "F1", "F2", "F3", "F4", "F5", "Clic gauche", "Clic droit"}) {
            keys.add(k);
        }
        boolean[] checked = new boolean[keys.size()];
        checked[1] = true;
        checked[6] = true;
        d = V2ListDialog.showMultiChoice(a, V2Theme.load(a), a.getString(R.string.keyboard_select_keys), keys, checked,
                a.getString(R.string.keyboard_add), a.getString(R.string.cancel), x -> { });
        save(overlay(s, d, 0.5f), "gaps-osc-add-keys");
        d.dismiss();

        d = ThemedDialogs.confirm(a, a.getString(R.string.keyboard_clear_all_confirm_title),
                a.getString(R.string.keyboard_clear_all_confirm_message), a.getString(R.string.yes), a.getString(R.string.no), null, null);
        save(overlay(s, d, 0.5f), "gaps-osc-clear-all");
    }
}
