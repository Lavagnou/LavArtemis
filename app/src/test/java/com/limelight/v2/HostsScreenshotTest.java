package com.limelight.v2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders « Mes PC », its dialogs (options, add, pairing) and the themed in-game menu from fake
 * data at the validated densities (phone 915x412 dp, console 960x540 dp, + portrait), and writes
 * PNGs (prefixes hosts-, pair-, gamemenu-) to app/build/v2-shots or $V2_SHOTS_DIR, to be looked at
 * before a delivery.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HostsScreenshotTest {
    private static final int DENSITY = 2; // xhdpi

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    // ---- fake PCs -------------------------------------------------------------------------------

    private static ComputerDetails atlas() {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-atlas";
        d.name = "ATLAS-PC";
        d.state = ComputerDetails.State.ONLINE;
        d.pairState = PairingManager.PairState.PAIRED;
        d.activeAddress = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        d.macAddress = "00:11:22:33:44:55";
        d.apolloVersion = "1.2.0";
        d.runningGameId = 2;
        d.playniteHostRunning = true;
        d.playniteGameName = "Hades II";
        return d;
    }

    private static ComputerDetails salon() {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-salon";
        d.name = "SALON";
        d.state = ComputerDetails.State.OFFLINE;
        d.pairState = PairingManager.PairState.PAIRED;
        d.localAddress = new ComputerDetails.AddressTuple("192.168.1.31", 47989);
        d.macAddress = "00:11:22:33:44:66";
        return d;
    }

    private static ComputerDetails found() {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-bureau";
        d.name = "BUREAU-ETAGE";
        d.state = ComputerDetails.State.ONLINE;
        d.pairState = PairingManager.PairState.NOT_PAIRED;
        d.activeAddress = new ComputerDetails.AddressTuple("192.168.1.40", 47989);
        return d;
    }

    private static List<HostCards.Card> cards(Context ctx, boolean empty) {
        List<HostCards.Card> list = new ArrayList<>();
        if (empty) {
            return list;
        }
        HostCards.Labels l = HostCards.Labels.from(ctx);
        List<NvApp> apps = new ArrayList<>();
        NvApp playnite = new NvApp("Playnite", "u2", 2, false);
        playnite.setPlayniteHost(true);
        apps.add(playnite);
        for (ComputerDetails d : HostCards.pairedByName(java.util.Arrays.asList(salon(), atlas()))) {
            list.add(HostCards.of(d, apps, l));
        }
        return list;
    }

    // ---- rendering ------------------------------------------------------------------------------

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

    /** The dialog's content over the page, dimmed as the window manager dims it */
    private static Bitmap overlay(Bitmap page, Dialog dialog, float dim) {
        ShadowLooper.idleMainLooper();
        View content = dialog.findViewById(android.R.id.content);
        assertNotNull(content);
        int w = page.getWidth(), h = page.getHeight();
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
        ShadowLooper.idleMainLooper();
        content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
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

    private static void qualifiers(int wDp, int hDp, String lang) {
        RuntimeEnvironment.setQualifiers((lang == null ? "" : lang + "-") + "w" + wDp + "dp-h" + hDp + "dp-"
                + (wDp > hDp ? "land" : "port") + "-xhdpi");
    }

    private static void useTheme(String id, boolean dark) {
        Context app = ApplicationProvider.getApplicationContext();
        V2Theme.saveTheme(app, id);
        V2Theme.saveMode(app, dark ? V2Theme.MODE_DARK : V2Theme.MODE_LIGHT);
    }

    private static Bitmap hostsPage(String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input, boolean empty, boolean focus) {
        qualifiers(wDp, hDp, null);
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        HostsScreen screen = new HostsScreen(ctx, V2Theme.of(themeId, dark), Density.forSize(wDp, hDp), new NoActions());
        screen.bind(cards(ctx, empty));
        View root = screen.getView();
        Bitmap bmp = draw(root, wDp * DENSITY, hDp * DENSITY);
        if (focus && input != V2Input.Kind.TOUCH) {
            screen.requestInitialFocus();
            bmp = draw(root, wDp * DENSITY, hDp * DENSITY);
        }
        return bmp;
    }

    private void hosts(String file, String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input, boolean empty) throws Exception {
        save(hostsPage(themeId, dark, wDp, hDp, input, empty, true), file);
    }

    /** A real HostsActivity (no service: the PCs are put in by hand), to open its real dialogs */
    private static HostsActivity activity(String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input) {
        qualifiers(wDp, hDp, null);
        useTheme(themeId, dark);
        V2Input.reset(input);
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        for (ComputerDetails d : new ComputerDetails[]{atlas(), salon(), found()}) {
            a.computers.put(d.uuid, d);
        }
        a.refresh();
        return a;
    }

    /** Focus as a gamepad would put it: the first focusable row of the dialog */
    private static void focusFirst(Dialog dialog) {
        View content = dialog.findViewById(android.R.id.content);
        // Robolectric keeps the window in touch mode, where a plain view is neither listed nor focused
        ArrayList<View> focusables = new ArrayList<>();
        content.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
        for (View v : focusables) {
            if (v instanceof android.widget.ScrollView) {
                continue;
            }
            v.setFocusableInTouchMode(true);
            assertTrue("focus on " + v, v.requestFocus());
            return;
        }
    }

    // ---- Mes PC ---------------------------------------------------------------------------------

    @Test
    public void hostsBraiseDark() throws Exception {
        hosts("hosts-phone-braise-dark", "braise", true, 915, 412, V2Input.Kind.TOUCH, false);
        hosts("hosts-console-braise-dark", "braise", true, 960, 540, V2Input.Kind.PAD, false);
        hosts("hosts-phone-braise-dark-empty", "braise", true, 915, 412, V2Input.Kind.TOUCH, true);
        hosts("hosts-portrait-braise-dark", "braise", true, 412, 915, V2Input.Kind.TOUCH, false);
    }

    @Test
    public void hostsAfficheLight() throws Exception {
        hosts("hosts-phone-affiche-light", "affiche", false, 915, 412, V2Input.Kind.TOUCH, false);
        hosts("hosts-console-affiche-light", "affiche", false, 960, 540, V2Input.Kind.PAD, false);
        hosts("hosts-console-affiche-light-empty", "affiche", false, 960, 540, V2Input.Kind.PAD, true);
    }

    @Test
    public void hostsLudiqueDark() throws Exception {
        hosts("hosts-phone-ludique-dark", "ludique", true, 915, 412, V2Input.Kind.TOUCH, false);
        hosts("hosts-console-ludique-dark", "ludique", true, 960, 540, V2Input.Kind.KEYS, false);
        hosts("hosts-phone-ludique-dark-empty", "ludique", true, 915, 412, V2Input.Kind.TOUCH, true);
    }

    // ---- dialogs --------------------------------------------------------------------------------

    @Test
    public void hostOptionsAndAddMenu() throws Exception {
        HostsActivity a = activity("braise", true, 915, 412, V2Input.Kind.TOUCH);
        Bitmap page = hostsPage("braise", true, 915, 412, V2Input.Kind.TOUCH, false, false);
        a.showHostOptions("uuid-atlas");
        save(overlay(page, ShadowDialog.getLatestDialog(), 0.45f), "hosts-phone-braise-dark-options");
        ShadowDialog.getLatestDialog().dismiss();
        a.showHostOptions("uuid-salon");
        save(overlay(page, ShadowDialog.getLatestDialog(), 0.45f), "hosts-phone-braise-dark-options-offline");
        ShadowDialog.getLatestDialog().dismiss();
        a.showAddMenu();
        save(overlay(page, ShadowDialog.getLatestDialog(), 0.45f), "hosts-phone-braise-dark-add");
        ShadowDialog.getLatestDialog().dismiss();
        // The text dialog of Renommer / Saisir une adresse…
        Dialog rename = a.rename(atlas());
        save(overlay(page, rename, 0f), "hosts-phone-braise-dark-rename");
        rename.dismiss();

        HostsActivity l = activity("ludique", true, 960, 540, V2Input.Kind.PAD);
        Bitmap lpage = hostsPage("ludique", true, 960, 540, V2Input.Kind.PAD, false, false);
        l.showHostOptions("uuid-atlas");
        Dialog d = ShadowDialog.getLatestDialog();
        focusFirst(d);
        save(overlay(lpage, d, 0.45f), "hosts-console-ludique-dark-options");
    }

    @Test
    public void pairingDialog() throws Exception {
        HostsActivity a = activity("braise", true, 915, 412, V2Input.Kind.TOUCH);
        Bitmap page = hostsPage("braise", true, 915, 412, V2Input.Kind.TOUCH, false, false);
        Dialog d = a.showPairDialog("BUREAU-ETAGE", "4827", false);
        save(overlay(page, d, 0.45f), "pair-phone-braise-dark");
        d.dismiss();

        HostsActivity f = activity("affiche", false, 960, 540, V2Input.Kind.PAD);
        Bitmap fpage = hostsPage("affiche", false, 960, 540, V2Input.Kind.PAD, false, false);
        Dialog fd = f.showPairDialog("BUREAU-ETAGE", "4827", false);
        focusFirst(fd);
        save(overlay(fpage, fd, 0.45f), "pair-console-affiche-light");
        fd.dismiss();

        HostsActivity p = activity("ludique", true, 412, 915, V2Input.Kind.TOUCH);
        Bitmap ppage = hostsPage("ludique", true, 412, 915, V2Input.Kind.TOUCH, false, false);
        save(overlay(ppage, p.showPairDialog("BUREAU-ETAGE", "4827", false), 0.45f), "pair-portrait-ludique-dark");
    }

    // ---- in-game menu ---------------------------------------------------------------------------

    /** A stand-in for the stream behind the menu */
    private static Bitmap stream(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF3B1E6B, 0xFF8E2B1C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(Color.argb(70, 255, 255, 255));
        c.drawCircle(w * 0.72f, h * 0.35f, h * 0.3f, p);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(h * 0.12f);
        c.drawText("HADES II", w * 0.06f, h * 0.85f, p);
        return b;
    }

    /** The labels of GameMenu.showMenu(), in its order, for a session inside Playnite */
    private static List<String> quickMenu(Context c) {
        List<String> l = new ArrayList<>();
        l.add(c.getString(R.string.game_menu_quit_playnite_game, "Hades II"));
        l.add(c.getString(R.string.game_menu_disconnect));
        l.add(c.getString(R.string.game_menu_quit_session));
        // Dev: the desktop's order for the common entries (GameMenu.showMenu)
        l.add(c.getString(R.string.v2_menu_server_cmd));
        l.add(c.getString(R.string.v2_menu_send_keys));
        l.add(c.getString(R.string.game_menu_upload_clipboard));
        l.add(c.getString(R.string.game_menu_fetch_clipboard));
        l.add(c.getString(R.string.v2_menu_stats));
        l.add(c.getString(R.string.game_menu_mute_microphone));
        l.add(c.getString(R.string.game_menu_toggle_keyboard));
        l.add(c.getString(R.string.game_menu_show_voice_ptt_button));
        l.add(c.getString(R.string.game_menu_enable_zoom_mode));
        l.add(c.getString(R.string.game_menu_rotate_screen));
        l.add(c.getString(R.string.game_menu_advanced));
        l.add(c.getString(R.string.game_menu_cancel));
        return l;
    }

    private void gameMenu(String file, String themeId, boolean dark, int wDp, int hDp, V2Input.Kind input) throws Exception {
        qualifiers(wDp, hDp, "fr");
        useTheme(themeId, dark);
        V2Input.reset(input);
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        List<String> labels = quickMenu(a);
        Dialog d = V2ListDialog.show(a, V2Theme.load(a), a.getString(R.string.quick_menu_title), labels, labels.size() - 1, null, null);
        if (input != V2Input.Kind.TOUCH) {
            focusFirst(d);
        }
        save(overlay(stream(wDp * DENSITY, hDp * DENSITY), d, 0.5f), file);
        d.dismiss();
    }

    @Test
    public void themedGameMenu() throws Exception {
        gameMenu("gamemenu-phone-braise-dark", "braise", true, 915, 412, V2Input.Kind.TOUCH);
        gameMenu("gamemenu-console-braise-dark", "braise", true, 960, 540, V2Input.Kind.PAD);
        gameMenu("gamemenu-phone-affiche-light", "affiche", false, 915, 412, V2Input.Kind.PAD);
        gameMenu("gamemenu-console-ludique-dark", "ludique", true, 960, 540, V2Input.Kind.KEYS);
        gameMenu("gamemenu-portrait-ludique-dark", "ludique", true, 412, 915, V2Input.Kind.TOUCH);
    }

    @Test
    public void gameMenuMessage() throws Exception {
        qualifiers(915, 412, "fr");
        useTheme("braise", true);
        V2Input.reset(V2Input.Kind.PAD);
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        Dialog d = V2ListDialog.message(a, V2Theme.load(a), a.getString(R.string.game_dialog_title_server_cmd_empty),
                a.getString(R.string.game_dialog_message_server_cmd_empty), a.getString(R.string.v2_ok));
        focusFirst(d);
        save(overlay(stream(915 * DENSITY, 412 * DENSITY), d, 0.5f), "gamemenu-phone-braise-dark-message");
    }

    // ---- prototype size (1280 x 720 dp), the prototype's PCs (Proto HOSTS, V8-37) ----------------------

    private static final String[] THEMES = {"braise", "affiche", "ludique"};

    /** ATLAS-PC tower, wired, 4 ms, RDR2 inside Playnite; SALON-HTPC mini-PC, Wi-Fi, 6 ms; PORTABLE laptop offline */
    private static List<ComputerDetails> protoPcs() {
        ComputerDetails atlas = new ComputerDetails();
        atlas.uuid = "uuid-atlas";
        atlas.name = "ATLAS-PC";
        atlas.state = ComputerDetails.State.ONLINE;
        atlas.pairState = PairingManager.PairState.PAIRED;
        atlas.activeAddress = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        atlas.macAddress = "00:11:22:33:44:55";
        atlas.apolloVersion = "1.2.0";
        atlas.hostFormFactor = "tower";
        atlas.hostLink = "wired";
        atlas.latencyMs = 4;
        atlas.runningGameId = 2;
        atlas.playniteHostRunning = true;
        atlas.playniteGameName = "Red Dead Redemption 2";
        ComputerDetails salon = new ComputerDetails();
        salon.uuid = "uuid-salon";
        salon.name = "SALON-HTPC";
        salon.state = ComputerDetails.State.ONLINE;
        salon.pairState = PairingManager.PairState.PAIRED;
        salon.activeAddress = new ComputerDetails.AddressTuple("192.168.1.31", 47989);
        salon.macAddress = "00:11:22:33:44:66";
        salon.apolloVersion = "1.2.0";
        salon.hostFormFactor = "mini";
        salon.hostLink = "wifi";
        salon.latencyMs = 6;
        ComputerDetails portable = new ComputerDetails();
        portable.uuid = "uuid-portable";
        portable.name = "PORTABLE";
        portable.state = ComputerDetails.State.OFFLINE;
        portable.pairState = PairingManager.PairState.PAIRED;
        portable.localAddress = new ComputerDetails.AddressTuple("192.168.1.40", 47989);
        portable.macAddress = "00:11:22:33:44:77";
        portable.hostFormFactor = "laptop";
        portable.hostLink = "wifi";
        ComputerDetails found = new ComputerDetails();
        found.uuid = "uuid-papa";
        found.name = "BUREAU-PAPA";
        found.state = ComputerDetails.State.ONLINE;
        found.pairState = PairingManager.PairState.NOT_PAIRED;
        found.activeAddress = new ComputerDetails.AddressTuple("192.168.1.57", 47989);
        return java.util.Arrays.asList(atlas, salon, portable, found);
    }

    private static Bitmap protoPage(String themeId, boolean dark, V2Input.Kind input, boolean focus) {
        RuntimeEnvironment.setQualifiers("fr-w1280dp-h720dp-land-mdpi");
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        HostsScreen screen = new HostsScreen(ctx, V2Theme.of(themeId, dark), HomeLayout.forSize(1280, 720), new NoActions());
        HostCards.Labels l = HostCards.Labels.from(ctx);
        List<HostCards.Card> list = new ArrayList<>();
        for (ComputerDetails d : protoPcs()) {
            if (HostList.isPaired(d)) {
                list.add(HostCards.of(d, null, l));
            }
        }
        screen.bind(list);
        View root = screen.getView();
        Bitmap bmp = draw(root, 1280, 720);
        if (focus && input != V2Input.Kind.TOUCH) {
            for (List<View> row : screen.focusRows()) {
                for (View v : row) {
                    v.setFocusableInTouchMode(true);
                }
            }
            screen.requestInitialFocus();
            bmp = draw(root, 1280, 720);
        }
        return bmp;
    }

    private static HostsActivity protoActivity(String themeId, boolean dark, V2Input.Kind input) {
        RuntimeEnvironment.setQualifiers("fr-w1280dp-h720dp-land-mdpi");
        useTheme(themeId, dark);
        V2Input.reset(input);
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        for (ComputerDetails d : protoPcs()) {
            a.computers.put(d.uuid, d);
        }
        a.refresh();
        return a;
    }

    /** V8-37: « Mes PC » in every theme, light and dark, at the gamepad (focus on ATLAS-PC) and on touch */
    @Test
    public void protoHosts() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                String m = dark ? "sombre" : "clair";
                save(protoPage(t, dark, V2Input.Kind.PAD, true), "hosts-" + t + "-" + m + "-manette");
                save(protoPage(t, dark, V2Input.Kind.TOUCH, false), "hosts-" + t + "-" + m + "-tactile");
            }
        }
    }

    /** V8-38 options, V8-41 renommer, V8-39 appairage, V8-65 ajouter, V8-40 adresse, V8-42 oublier, at the gamepad */
    @Test
    public void protoHostDialogs() throws Exception {
        for (String t : THEMES) {
            for (boolean dark : new boolean[]{false, true}) {
                String m = dark ? "sombre" : "clair";
                HostsActivity a = protoActivity(t, dark, V2Input.Kind.PAD);
                // The page under the dialog without its own hints (the dialog shows its own), then the gamepad again
                Bitmap page = protoPage(t, dark, V2Input.Kind.TOUCH, false);
                V2Input.reset(V2Input.Kind.PAD);
                ComputerDetails atlas = protoPcs().get(0);
                a.showHostOptions("uuid-atlas");
                dialogShot(page, ShadowDialog.getLatestDialog(), "hostopts-" + t + "-" + m + "-manette");
                dialogShot(page, a.rename(atlas), "rename-" + t + "-" + m + "-manette");
                dialogShot(page, a.showPairDialog("BUREAU-PAPA", "4821", false), "pair-" + t + "-" + m + "-manette");
                a.showAddMenu();
                dialogShot(page, ShadowDialog.getLatestDialog(), "addpc-" + t + "-" + m + "-manette");
                dialogShot(page, a.promptAddress(), "addr-" + t + "-" + m + "-manette");
                dialogShot(page, a.confirmForget(atlas), "forget-" + t + "-" + m + "-manette");
            }
        }
    }

    /** The V2Sheet of the dialog drawn over the page at full size (its own backdrop), focus on its first row */
    private static void dialogShot(Bitmap page, Dialog d, String name) throws Exception {
        ShadowLooper.idleMainLooper();
        android.view.ViewGroup content = d.findViewById(android.R.id.content);
        View sheet = content.getChildAt(0);
        content.removeView(sheet);
        draw(sheet, page.getWidth(), page.getHeight());
        // The gamepad's focus: the first row of the dialog (not the text field, as V2Sheet.show)
        ArrayList<View> focusables = new ArrayList<>();
        sheet.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL);
        for (View v : focusables) {
            if (!(v instanceof com.limelight.v2.ui.V2Frame)) {
                continue;
            }
            v.setFocusableInTouchMode(true);
            v.requestFocus();
            break;
        }
        Bitmap over = draw(sheet, page.getWidth(), page.getHeight());
        Bitmap out = page.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(out).drawBitmap(over, 0, 0, null);
        save(out, name);
        d.dismiss();
    }

    private static final class NoActions implements HostsScreen.Actions {
        @Override public void back() {}
        @Override public void activate(String uuid) {}
        @Override public void options(String uuid) {}
        @Override public void add() {}
    }
}
