package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.BuildConfig;
import com.limelight.TestLogSuppressor;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;
import com.limelight.v2.settings.Option;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * The row table of Réglages v2 against the legacy screen: every key exists in preferences.xml,
 * every preference has a row (or is listed as removed / deliberately not shown), the defaults are
 * those of preferences.xml and of PreferenceConfiguration, the triage classification, the removed
 * settings forced in the Dev build, and the profile-aware writes of the settings store.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class SettingsCatalogTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Rows of Réglages v2 that are not preferences of the legacy screen */
    private static final Set<String> V2_ONLY = new HashSet<>(Arrays.asList(
            SettingsCatalog.KEY_PROFILE, SettingsCatalog.KEY_PROFILES_MANAGE, SettingsCatalog.KEY_BITRATE_DEFAULT,
            V2Theme.PREF_THEME, V2Theme.PREF_MODE,
            // The desktop's « Quitter l'appli sur le PC distant à la fin », new in the Dev
            com.limelight.preferences.PreferenceConfiguration.QUIT_APP_AFTER_PREF_STRING));

    /**
     * Keys whose value PreferenceConfiguration does not show: read elsewhere (the key layout by the
     * on-screen keyboard loader) or turned into MoonBridge constants the test shadow leaves null
     */
    private static final Set<String> NOT_IN_CONFIG = new HashSet<>(Arrays.asList(
            "keyboard_axi_list", "list_audio_config"));

    private Context ctx;
    private File profilesDir;

    @BeforeClass
    public static void quiet() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() throws Exception {
        ctx = ApplicationProvider.getApplicationContext();
        prefs().edit().clear().commit();
        profilesDir = new File(ctx.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        resetProfiles();
    }

    @After
    public void tearDown() throws Exception {
        deleteRecursively(profilesDir);
        resetProfiles();
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    private static void resetProfiles() throws Exception {
        Field f = ProfilesManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    // ---- preferences.xml ------------------------------------------------------------------------

    /** android:key -> android:defaultValue (null when absent) of every preference of the legacy screen */
    private static Map<String, String> xmlPreferences() throws Exception {
        File xml = new File("src/main/res/xml/preferences.xml");
        assertTrue("run from the app module: " + xml.getAbsolutePath(), xml.exists());
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(xml);
        NodeList all = doc.getElementsByTagName("*");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            String key = e.getAttributeNS(ANDROID_NS, "key");
            if (key == null || key.isEmpty() || e.getTagName().endsWith("PreferenceCategory")) {
                continue;
            }
            String def = e.hasAttributeNS(ANDROID_NS, "defaultValue") ? e.getAttributeNS(ANDROID_NS, "defaultValue") : null;
            out.put(key, def);
        }
        return out;
    }

    @Test
    public void everyRowKeyExistsInPreferencesXml() throws Exception {
        Map<String, String> xml = xmlPreferences();
        Set<String> seen = new HashSet<>();
        for (SettingDef d : SettingsCatalog.all()) {
            assertTrue("duplicate row " + d.key, seen.add(d.key));
            if (V2_ONLY.contains(d.key)) {
                continue;
            }
            assertTrue(d.key + " is not a key of preferences.xml", xml.containsKey(d.key));
        }
    }

    @Test
    public void everyPreferenceHasARowOrAReason() throws Exception {
        for (String key : xmlPreferences().keySet()) {
            boolean row = SettingsCatalog.find(key) != null;
            boolean removed = SettingsCatalog.REMOVED.containsKey(key);
            boolean notShown = SettingsCatalog.NOT_SHOWN.contains(key);
            assertTrue(key + ": no row and no reason (REMOVED / NOT_SHOWN)", row || removed || notShown);
            assertFalse(key + " is both a row and removed", row && (removed || notShown));
        }
    }

    @Test
    public void rowDefaultsMatchPreferencesXml() throws Exception {
        Map<String, String> xml = xmlPreferences();
        for (SettingDef d : SettingsCatalog.all()) {
            String def = xml.get(d.key);
            if (def == null || d.kind == SettingDef.Kind.NONE) {
                continue;
            }
            switch (d.kind) {
                case BOOL:
                    assertEquals(d.key, Boolean.parseBoolean(def), d.def);
                    break;
                case INT:
                    assertEquals(d.key, Integer.parseInt(def), d.def);
                    break;
                default:
                    assertEquals(d.key, def, d.def);
                    break;
            }
        }
    }

    // ---- PreferenceConfiguration ---------------------------------------------------------------

    private static Map<String, Object> fields(PreferenceConfiguration c) throws Exception {
        Map<String, Object> out = new HashMap<>();
        for (Field f : PreferenceConfiguration.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                out.put(f.getName(), f.get(c));
            }
        }
        return out;
    }

    private PreferenceConfiguration readWith(String key, Object value) {
        SharedPreferences.Editor e = prefs().edit().clear();
        if (value instanceof Boolean) {
            e.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            e.putInt(key, (Integer) value);
        } else if (value instanceof String) {
            e.putString(key, (String) value);
        }
        e.commit();
        return PreferenceConfiguration.readPreferences(ctx);
    }

    private static Object otherValue(SettingDef d) {
        switch (d.kind) {
            case BOOL:
                return !(Boolean) d.def;
            case INT: {
                int v = (Integer) d.def + Math.max(1, d.step);
                return v > d.max ? (Integer) d.def - Math.max(1, d.step) : v;
            }
            default:
                switch (d.key) {
                    case "list_resolution":
                        return "1920x1080";
                    case "list_fps":
                        return "30";
                    case "video_format":
                        return "forceh265";
                    case "list_mic_device":
                        return "usb:Micro";
                    case "list_languages":
                        return "fr";
                    default:
                        for (String v : d.values) {
                            if (!v.equals(d.def)) {
                                return v;
                            }
                        }
                        fail("no other value for " + d.key);
                        return null;
                }
        }
    }

    /**
     * For every stored row: writing the row's default reads exactly like an empty configuration
     * (the default is PreferenceConfiguration's), and writing another value changes what is read
     * (the key is really used).
     */
    @Test
    public void rowDefaultsAreThoseOfPreferenceConfiguration() throws Exception {
        prefs().edit().clear().commit();
        Map<String, Object> empty = fields(PreferenceConfiguration.readPreferences(ctx));
        List<String> failures = new ArrayList<>();
        for (SettingDef d : SettingsCatalog.all()) {
            if (d.kind == SettingDef.Kind.NONE || d.global || SettingsCatalog.KEY_BITRATE.equals(d.key)) {
                continue;
            }
            Map<String, Object> withDefault = fields(readWith(d.key, d.def));
            if (!withDefault.equals(empty)) {
                failures.add(d.key + ": default " + d.def + " is not PreferenceConfiguration's");
            }
            if (!NOT_IN_CONFIG.contains(d.key)) {
                Map<String, Object> withOther = fields(readWith(d.key, otherValue(d)));
                if (withOther.equals(empty)) {
                    failures.add(d.key + ": another value changes nothing in PreferenceConfiguration");
                }
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void bitrateDefaultFollowsResolutionAndFps() {
        SettingsStore store = new SettingsStore(ctx);
        assertEquals(PreferenceConfiguration.getDefaultBitrate("1280x720", "60"), store.getInt(SettingsCatalog.KEY_BITRATE));
        store.setChoice(SettingsCatalog.find("list_resolution"), "1920x1080");
        // A new resolution resets the bitrate to its default, as the legacy screen did
        assertEquals(PreferenceConfiguration.getDefaultBitrate("1920x1080", "60"), prefs().getInt(SettingsCatalog.KEY_BITRATE, 0));
        store.putInt(SettingsCatalog.KEY_BITRATE, 50000);
        store.setChoice(SettingsCatalog.find("list_fps"), "120");
        assertEquals(PreferenceConfiguration.getDefaultBitrate("1920x1080", "120"), prefs().getInt(SettingsCatalog.KEY_BITRATE, 0));
    }

    // ---- classification -------------------------------------------------------------------------

    private static void assertNormal(String key) {
        SettingDef d = SettingsCatalog.find(key);
        assertNotNull(key, d);
        assertFalse(key + " should be Normal", d.expert);
    }

    private static void assertExpert(String key) {
        SettingDef d = SettingsCatalog.find(key);
        assertNotNull(key, d);
        assertTrue(key + " should be Expert", d.expert);
    }

    @Test
    public void triageClassification() {
        // Normal in the user's triage
        for (String k : new String[]{"list_resolution", "list_fps", SettingsCatalog.KEY_BITRATE, SettingsCatalog.KEY_BITRATE_DEFAULT,
                "list_video_scale_mode", "video_format", "frame_pacing", "checkbox_use_virtual_display", "list_audio_config",
                "checkbox_host_audio", "checkbox_mic_passthrough", "checkbox_absolute_mouse_mode", "list_languages",
                "checkbox_enable_perf_overlay", "checkbox_auto_update", "option_check_update", SettingsCatalog.KEY_PROFILE,
                SettingsCatalog.KEY_PROFILES_MANAGE}) {
            assertNormal(k);
        }
        // Expert in the user's triage
        for (String k : new String[]{"checkbox_vrr_pacing", "list_vrr_pacing_mode", "checkbox_pyrowave_444", "list_mic_device",
                "seekbar_max_pending_audio_ms", "checkbox_flip_face_buttons", "checkbox_multi_controller",
                "checkbox_smart_clipboard_sync", "checkbox_smart_clipboard_sync_toast", "checkbox_disable_warnings",
                "checkbox_enable_perf_logging"}) {
            assertExpert(k);
        }
        // Android-only: common ones Normal, tuning Expert
        for (String k : new String[]{"mouse_mode_list", "checkbox_trackpad_pinch_zoom", "checkbox_follow_device_rotation",
                "checkbox_show_onscreen_controls", "checkbox_voice_ptt_button", "checkbox_enable_rumble"}) {
            assertNormal(k);
        }
        for (String k : new String[]{"checkbox_enable_adpf", "checkbox_paced_ull", "checkbox_predictive_pacing",
                "seekbar_deadzone", "checkbox_force_qwerty", "pref_debug_info"}) {
            assertExpert(k);
        }
        // Inverted rows show the desktop's wording
        assertTrue(SettingsCatalog.find("checkbox_host_audio").inverted);
        assertTrue(SettingsCatalog.find("checkbox_multi_controller").inverted);
        assertTrue(SettingsCatalog.find("checkbox_disable_warnings").inverted);
    }

    @Test
    public void pyroWaveOnlyInExpertModeAndWhenSupported() {
        SettingsStore store = new SettingsStore(ctx);
        SettingDef codec = SettingsCatalog.find("video_format");
        SettingsEnv env = SettingsEnv.forTests();
        env.pyroWave = true;
        assertFalse(values(codec.options(ctx, env, store, false)).contains("forcepyrowave"));
        assertTrue(values(codec.options(ctx, env, store, true)).contains("forcepyrowave"));
        env.pyroWave = false;
        assertFalse(values(codec.options(ctx, env, store, true)).contains("forcepyrowave"));
        assertFalse(SettingsCatalog.find("checkbox_pyrowave_444").shown(env, true));
    }

    private static List<String> values(List<Option> options) {
        List<String> out = new ArrayList<>();
        for (Option o : options) {
            out.add(o.value);
        }
        return out;
    }

    @Test
    public void pagesFollowExpertModeAndDevice() {
        SettingsEnv env = SettingsEnv.forTests();
        List<String> normal = new ArrayList<>();
        for (SettingsCatalog.Page p : SettingsCatalog.visiblePages(env, false)) {
            normal.add(p.id);
        }
        assertEquals(Arrays.asList(SettingsCatalog.PROFIL, SettingsCatalog.AFFICHAGE, SettingsCatalog.VIDEO, SettingsCatalog.AUDIO,
                SettingsCatalog.ENTREES, SettingsCatalog.MANETTES, SettingsCatalog.CONTROLES, SettingsCatalog.APPARENCE,
                SettingsCatalog.PC, SettingsCatalog.AVANCE), normal);
        // No touchscreen (TV): the on-screen controls go, the page stays for the full keyboard in expert mode
        env.touchscreen = false;
        boolean controls = false;
        for (SettingsCatalog.Page p : SettingsCatalog.visiblePages(env, false)) {
            controls |= p.id.equals(SettingsCatalog.CONTROLES);
        }
        assertFalse("no normal row left on a TV", controls);
        assertNull(rowIn(SettingsCatalog.visibleRows(SettingsCatalog.ENTREES, env, true), "mouse_mode_list"));
        assertNotNull(rowIn(SettingsCatalog.visibleRows(SettingsCatalog.CONTROLES, env, true), "seekbar_keyboard_axi_opacity"));
        // Expert rows are hidden in normal mode
        assertNull(rowIn(SettingsCatalog.visibleRows(SettingsCatalog.AFFICHAGE, SettingsEnv.forTests(), false), "checkbox_vrr_pacing"));
        assertNotNull(rowIn(SettingsCatalog.visibleRows(SettingsCatalog.AFFICHAGE, SettingsEnv.forTests(), true), "checkbox_vrr_pacing"));
    }

    private static SettingDef rowIn(List<SettingDef> rows, String key) {
        for (SettingDef d : rows) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    @Test
    public void dependenciesGreyRows() {
        SettingsStore store = new SettingsStore(ctx);
        SettingDef mode = SettingsCatalog.find("list_vrr_pacing_mode");
        assertFalse(mode.enabled(store));
        store.putBool("checkbox_vrr_pacing", true);
        assertTrue(mode.enabled(store));
        SettingDef depth = SettingsCatalog.find("parallax_depth");
        assertFalse(depth.enabled(store));
        store.putString("render_mode_list", "1");
        assertTrue(depth.enabled(store));
    }

    // ---- sliders -----------------------------------------------------------------------------------

    @Test
    public void bitrateSliderIsCurvedAndSteps() {
        SettingDef b = SettingsCatalog.find(SettingsCatalog.KEY_BITRATE);
        for (int v : new int[]{500, 20000, 40000, 150000, 800000}) {
            assertEquals(v, b.valueAt(b.position(v)), 600);
        }
        // 20 Mbit/s sits well into the track, not in its first 3 %
        assertTrue(b.position(20000) > 0.25f);
        assertEquals(21000, b.stepFrom(20000, 1));
        assertEquals(105000, b.stepFrom(100000, 1));
        assertEquals(800000, b.stepFrom(800000, 1));
        assertEquals("40 Mbit/s", SettingsCatalog.mbps(ctx, 40000));
        assertEquals("0,5 Mbit/s", SettingsCatalog.mbps(ctx, 500));
        SettingDef deadzone = SettingsCatalog.find("seekbar_deadzone");
        assertEquals(-20, deadzone.stepFrom(-20, -1));
        assertEquals(6, deadzone.stepFrom(5, 1));
    }

    // ---- removed settings ----------------------------------------------------------------------------

    @Test
    public void removedSettingsHaveNoRowAndAreForcedInDev() {
        for (String key : SettingsCatalog.REMOVED.keySet()) {
            assertNull(key + " is removed", SettingsCatalog.find(key));
        }
        prefs().edit().putBoolean("checkbox_enable_hdr", true).putInt("seekbar_resolution_scale_factor", 150)
                .putBoolean("checkbox_mouse_emulation", true).putBoolean("checkbox_enable_sops", false).commit();
        PreferenceConfiguration c = PreferenceConfiguration.readPreferences(ctx);
        if (BuildConfig.LAV_DEV) {
            assertFalse(c.enableHdr);
            assertEquals(100, c.resolutionScaleFactor);
            assertFalse(c.mouseEmulation);
            assertTrue(c.enableSops);
        } else {
            // The stable build keeps reading them
            assertTrue(c.enableHdr);
            assertEquals(150, c.resolutionScaleFactor);
            assertTrue(c.mouseEmulation);
            assertFalse(c.enableSops);
        }
        PreferenceConfiguration forced = new PreferenceConfiguration();
        forced.enableHdr = true;
        forced.resolutionScaleFactor = 150;
        forced.mouseEmulation = true;
        forced.enableSops = false;
        PreferenceConfiguration.forceRemovedSettings(forced);
        assertEquals(SettingsCatalog.REMOVED.get("checkbox_enable_hdr"), forced.enableHdr);
        assertEquals(SettingsCatalog.REMOVED.get("seekbar_resolution_scale_factor"), forced.resolutionScaleFactor);
        assertEquals(SettingsCatalog.REMOVED.get("checkbox_mouse_emulation"), forced.mouseEmulation);
        assertEquals(SettingsCatalog.REMOVED.get("checkbox_enable_sops"), forced.enableSops);
    }

    // ---- touch modes (Dev: Multitouch / Trackpad only) -------------------------------------------

    @Test
    public void touchModeRowHasExactlyMultitouchAndTrackpad() {
        SettingDef d = SettingsCatalog.find("mouse_mode_list");
        List<Option> options = d.options(ctx, SettingsEnv.forTests(), new SettingsStore(ctx), true);
        assertEquals(Arrays.asList("0", "3"), values(options));
        assertEquals("Multitouch", options.get(0).label);
        assertEquals("Trackpad", options.get(1).label);
        // Two options fit as segments
        assertTrue(options.size() <= d.segmentMax);
        // Both gestures of the trackpad are spelled out
        String desc = ctx.getString(d.desc);
        assertTrue(desc, desc.contains("appui long pour faire glisser"));
        assertTrue(desc, desc.contains("appui à deux doigts pour le clic droit"));
    }

    @Test
    public void removedTouchModesReadAsTheClosestKeptOne() {
        // 2 (trackpad, double tap to drag) -> Trackpad; absolute touch, swapped, disabled -> Multitouch
        int[] expected = {0, 0, 3, 3, 0, 0};
        for (int stored = 0; stored < expected.length; stored++) {
            assertEquals("stored " + stored, expected[stored], PreferenceConfiguration.devTouchMode(stored));
        }
        assertEquals(PreferenceConfiguration.TOUCH_MODE_MULTITOUCH, PreferenceConfiguration.devTouchMode("garbage"));
        assertEquals(PreferenceConfiguration.TOUCH_MODE_MULTITOUCH, PreferenceConfiguration.devTouchMode((String) null));

        // The settings screen shows the mapped value
        SettingsStore store = new SettingsStore(ctx);
        SettingDef row = SettingsCatalog.find("mouse_mode_list");
        prefs().edit().putString("mouse_mode_list", "2").commit();
        assertEquals("3", store.choiceValue(row));
        prefs().edit().putString("mouse_mode_list", "4").commit();
        assertEquals("0", store.choiceValue(row));
        prefs().edit().putString("mouse_mode_list", "5").commit();
        assertEquals("0", store.choiceValue(row));

        // ... and the stream reads the same mode in Dev; the stable build keeps all six
        PreferenceConfiguration c = readWith("mouse_mode_list", "4");
        if (BuildConfig.LAV_DEV) {
            assertTrue("disabled -> multitouch", c.enableMultiTouchScreen);
            assertFalse(c.touchscreenTrackpad);
        } else {
            assertFalse(c.enableMultiTouchScreen);
            assertFalse(c.touchscreenTrackpad);
        }
        c = readWith("mouse_mode_list", "2");
        assertTrue(c.touchscreenTrackpad);
        assertFalse(c.enableMultiTouchScreen);
    }

    @Test
    public void inGameSwitchFlipsMultitouchAndTrackpad() {
        assertEquals(3, PreferenceConfiguration.otherTouchMode(0));
        assertEquals(0, PreferenceConfiguration.otherTouchMode(3));
        // From a removed mode (input-only stream, a value of the stable app): the other of its mapping
        assertEquals(0, PreferenceConfiguration.otherTouchMode(2));
        assertEquals(3, PreferenceConfiguration.otherTouchMode(1));
        assertEquals(3, PreferenceConfiguration.otherTouchMode(4));
        assertEquals("Passer en trackpad", ctx.getString(com.limelight.R.string.v2_menu_touch_to_trackpad));
        assertEquals("Passer en multitouch", ctx.getString(com.limelight.R.string.v2_menu_touch_to_multitouch));
    }

    @Test
    public void noHelpLinkRowsLeft() {
        assertNull(SettingsCatalog.find("option_help_custom_keys"));
        assertNull(SettingsCatalog.find("option_view_shared_pref_logs"));
        // The release page is an update link, not help: it stays. Obtainium is gone from the Dev
        // build (it updates itself), and is listed as deliberately not shown
        assertNotNull(SettingsCatalog.find("option_software_release"));
        assertNull(SettingsCatalog.find("option_follow_update"));
        assertTrue(SettingsCatalog.NOT_SHOWN.contains("option_follow_update"));
    }

    @Test
    public void bitrateTrackGoesHigherWithPyroWave() {
        SettingDef bitrate = SettingsCatalog.find(SettingsCatalog.KEY_BITRATE);
        SettingsStore store = new SettingsStore(ctx);
        prefs().edit().putString("video_format", "forceh265").commit();
        assertEquals(SettingsCatalog.BITRATE_MAX_KBPS, bitrate.maxFor(store));
        prefs().edit().putString("video_format", "forcepyrowave").commit();
        assertEquals(SettingsCatalog.BITRATE_MAX_PYROWAVE_KBPS, bitrate.maxFor(store));
        // The end of the track is the top of the range in use, and a value is snapped to it
        assertEquals(SettingsCatalog.BITRATE_MAX_PYROWAVE_KBPS, bitrate.valueAt(1f, SettingsCatalog.BITRATE_MAX_PYROWAVE_KBPS));
        assertEquals(SettingsCatalog.BITRATE_MAX_KBPS, bitrate.valueAt(1f, SettingsCatalog.BITRATE_MAX_KBPS));
        assertEquals(1f, bitrate.position(2000000, SettingsCatalog.BITRATE_MAX_KBPS), 0.0001f);
    }

    @Test
    public void joyConFixReadsTheKeyTheScreensWrite() {
        // Regression: the option was written as checkbox_enable_joyconfix and read as
        // checkbox_joycon_fix, so it never did anything.
        assertNotNull(SettingsCatalog.find("checkbox_enable_joyconfix"));
        prefs().edit().putBoolean("checkbox_enable_joyconfix", true).commit();
        assertTrue(PreferenceConfiguration.readPreferences(ctx).enableJoyConFix);
        prefs().edit().putBoolean("checkbox_enable_joyconfix", false).commit();
        assertFalse(PreferenceConfiguration.readPreferences(ctx).enableJoyConFix);
        // The old key still counts when the new one was never written
        prefs().edit().remove("checkbox_enable_joyconfix").putBoolean("checkbox_joycon_fix", true).commit();
        assertTrue(PreferenceConfiguration.readPreferences(ctx).enableJoyConFix);
    }

    // ---- profiles ----------------------------------------------------------------------------------

    @Test
    public void newProfileSnapshotsEverySettingAndTakesTheWrites() {
        ProfilesManager.getInstance().load(ctx);
        prefs().edit().putString("list_resolution", "1920x1080").putBoolean("checkbox_enable_rumble", false)
                .putString("list_languages", "de").commit();
        SettingsStore store = new SettingsStore(ctx);
        SettingsProfile p = store.createProfile("Salon");
        assertEquals(p.getUuid(), ProfilesManager.getInstance().getActive().getUuid());
        Map<String, Object> options = p.getOptions();
        assertEquals("1920x1080", options.get("list_resolution"));
        assertEquals(false, options.get("checkbox_enable_rumble"));
        // Defaults are copied too: the profile holds every setting
        assertEquals(true, options.get("checkbox_enable_adpf"));
        assertEquals(40, options.get("seekbar_max_pending_audio_ms"));
        // Language and appearance stay common
        assertFalse(options.containsKey("list_languages"));
        assertFalse(options.containsKey(V2Theme.PREF_THEME));

        // A write lands in the profile, the base preferences do not move
        store.setChoice(SettingsCatalog.find("list_resolution"), "2560x1440");
        store.setToggle(SettingsCatalog.find("checkbox_enable_rumble"), true);
        assertEquals("2560x1440", ProfilesManager.getInstance().getActive().getOptions().get("list_resolution"));
        assertEquals("1920x1080", prefs().getString("list_resolution", null));
        assertFalse(prefs().getBoolean("checkbox_enable_rumble", true));
        assertTrue(store.getBool("checkbox_enable_rumble"));
        // ... and the bitrate reset with it
        assertEquals(PreferenceConfiguration.getDefaultBitrate("2560x1440", "60"),
                ((Number) ProfilesManager.getInstance().getActive().getOptions().get(SettingsCatalog.KEY_BITRATE)).intValue());

        // Back to the global settings: the base values show again
        store.setActiveProfile("");
        assertEquals("1920x1080", store.getString("list_resolution"));
        store.setActiveProfile(p.getUuid().toString());
        store.renameActiveProfile("Dehors");
        assertEquals("Dehors", store.activeProfile().getName());
        store.deleteActiveProfile();
        assertNull(store.activeProfile());
        assertEquals("Profil 1", store.nextProfileName("Profil"));
    }

    @Test
    public void globalRowsBypassProfiles() {
        ProfilesManager.getInstance().load(ctx);
        Map<String, Object> options = new HashMap<>();
        options.put(V2Theme.PREF_THEME, "ludique");
        long now = System.currentTimeMillis();
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "P", now, now, options);
        ProfilesManager.getInstance().add(p);
        ProfilesManager.getInstance().setActive(p.getUuid());
        SettingsStore store = new SettingsStore(ctx);
        store.setChoice(SettingsCatalog.find(V2Theme.PREF_THEME), "affiche");
        assertEquals("affiche", prefs().getString(V2Theme.PREF_THEME, null));
        assertEquals("affiche", store.choiceValue(SettingsCatalog.find(V2Theme.PREF_THEME)));
        assertTrue(Objects.equals("ludique", p.getOptions().get(V2Theme.PREF_THEME)));
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
}
