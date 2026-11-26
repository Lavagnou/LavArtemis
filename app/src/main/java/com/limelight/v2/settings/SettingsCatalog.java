package com.limelight.v2.settings;

import android.content.Context;
import android.content.res.Resources;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;
import com.limelight.v2.V2Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every row of Réglages v2, in page order. The keys are those of res/xml/preferences.xml with the
 * defaults PreferenceConfiguration reads (SettingsCatalogTest checks both). Classification: the
 * user's triage of the desktop settings (Normal / Expert / Supprimer) for every setting it covers,
 * and for the Android-only ones: what people change commonly is Normal, tuning and diagnostics are
 * Expert. Removed settings have no row: {@link #REMOVED} lists them, and
 * PreferenceConfiguration.forceRemovedSettings() pins them in the Dev build.
 */
public final class SettingsCatalog {
    private SettingsCatalog() {}

    // ---- pages ---------------------------------------------------------------------------------

    public static final String PROFIL = "profil";
    public static final String AFFICHAGE = "affichage";
    public static final String VIDEO = "video";
    public static final String AUDIO = "audio";
    public static final String ENTREES = "entrees";
    public static final String MANETTES = "manettes";
    public static final String CONTROLES = "controles";
    public static final String APPARENCE = "apparence";
    public static final String PC = "pc";
    public static final String AVANCE = "avance";

    public static final class Page {
        public final String id;
        public final int title;

        Page(String id, int title) {
            this.id = id;
            this.title = title;
        }
    }

    public static final List<Page> PAGES = Collections.unmodifiableList(Arrays.asList(
            new Page(PROFIL, R.string.v2_set_page_profil),
            new Page(AFFICHAGE, R.string.v2_set_page_affichage),
            new Page(VIDEO, R.string.v2_set_page_video),
            new Page(AUDIO, R.string.v2_set_page_audio),
            new Page(ENTREES, R.string.v2_set_page_entrees),
            new Page(MANETTES, R.string.v2_set_page_manettes),
            new Page(CONTROLES, R.string.v2_set_page_controles),
            new Page(APPARENCE, R.string.v2_set_page_apparence),
            new Page(PC, R.string.v2_set_page_pc),
            new Page(AVANCE, R.string.v2_set_page_avance)));

    // ---- keys that are not preferences of the legacy screen ---------------------------------------

    /** Expert mode of the v2 settings, global (base preferences) */
    public static final String PREF_EXPERT = "v2_expert";
    public static final String KEY_PROFILE = "v2_profile";
    public static final String KEY_PROFILES_MANAGE = "v2_profiles_manage";
    public static final String KEY_BITRATE_DEFAULT = "v2_bitrate_default";
    public static final String KEY_BITRATE = "seekbar_bitrate_kbps";
    /** Top of the bitrate track, and with PyroWave (the desktop uses the same two values) */
    public static final int BITRATE_MAX_KBPS = 800000;
    public static final int BITRATE_MAX_PYROWAVE_KBPS = 3000000;

    /**
     * Settings the triage removed that exist on Android, with the value the Dev build forces
     * (PreferenceConfiguration.forceRemovedSettings): HDR, resolution scaling, gamepad mouse.
     */
    public static final Map<String, Object> REMOVED;

    static {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkbox_enable_hdr", false);
        m.put("seekbar_resolution_scale_factor", 100);
        m.put("checkbox_mouse_emulation", false);
        // "Optimiser les réglages du jeu": removed and always on, like the desktop
        m.put("checkbox_enable_sops", true);
        REMOVED = Collections.unmodifiableMap(m);
    }

    /**
     * Preferences of the legacy screen deliberately without a row: settings nothing reads any more
     * (none left), the two options of the legacy app grid, which the v2 interface does not have
     * (small icons, quick resume), and the help links (no help button in the Dev build).
     */
    public static final Set<String> NOT_SHOWN = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "checkbox_small_icon_mode", "checkbox_resume_without_confirm",
            // typed in through the « Personnalisée… » / « Autre… » options of the lists
            "edit_diy_w_h", "custom_refresh_rate",
            // help links (Artemis wiki pages, in English): the user wants no help button left
            "option_help_custom_keys", "option_view_shared_pref_logs",
            // Obtainium: the Dev build updates itself (Avancé › Chercher une mise à jour); the user
            // does not use Obtainium with it. The stable app keeps the link in its legacy screen.
            "option_follow_update")));

    // ---- rows ------------------------------------------------------------------------------------

    private static List<SettingDef> rows;

    public static synchronized List<SettingDef> all() {
        if (rows == null) {
            rows = Collections.unmodifiableList(build());
        }
        return rows;
    }

    public static SettingDef find(String key) {
        for (SettingDef d : all()) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    public static List<SettingDef> rowsOf(String page) {
        List<SettingDef> out = new ArrayList<>();
        for (SettingDef d : all()) {
            if (d.page.equals(page)) {
                out.add(d);
            }
        }
        return out;
    }

    /** The rows of a page this device shows in this mode */
    public static List<SettingDef> visibleRows(String page, SettingsEnv env, boolean expert) {
        List<SettingDef> out = new ArrayList<>();
        for (SettingDef d : rowsOf(page)) {
            if (d.shown(env, expert)) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * Rows of a page hidden by expert mode alone (prototype sHidden): « N réglages avancés masqués
     * · activer le mode expert ». 0 in expert mode, and rows the device does not offer never count.
     */
    public static int hiddenCount(String page, SettingsEnv env, boolean expert) {
        if (expert) {
            return 0;
        }
        return visibleRows(page, env, true).size() - visibleRows(page, env, false).size();
    }

    /** Every row this device shows on the page is an expert one (prototype expertOnly: EXPERT in the page list) */
    public static boolean expertOnly(String page, SettingsEnv env) {
        List<SettingDef> rows = visibleRows(page, env, true);
        if (rows.isEmpty()) {
            return false;
        }
        for (SettingDef d : rows) {
            if (!d.expert) {
                return false;
            }
        }
        return true;
    }

    /**
     * « ● Suit le profil » (prototype p): the row's value goes into a profile. Global rows (theme,
     * update check…), the language, the app-wide keys (micro…) and rows without a value (actions)
     * stay common to every profile (SettingsStore.snapshot).
     */
    public static boolean followsProfile(SettingDef d) {
        return d.kind != SettingDef.Kind.NONE && !d.global && !"list_languages".equals(d.key)
                && !ProfilesManager.isAppWideKey(d.key);
    }

    /** The page after {@code current} in the visible ones, wrapping (LB / RB, prototype shoulder) */
    public static String stepPage(List<Page> visible, String current, int delta) {
        if (visible.isEmpty()) {
            return current;
        }
        int i = 0;
        for (int k = 0; k < visible.size(); k++) {
            if (visible.get(k).id.equals(current)) {
                i = k;
            }
        }
        int n = visible.size();
        return visible.get(((i + delta) % n + n) % n).id;
    }

    /** A page with no row to show disappears (desktop SettingsView2 visiblePages) */
    public static List<Page> visiblePages(SettingsEnv env, boolean expert) {
        List<Page> out = new ArrayList<>();
        for (Page p : PAGES) {
            if (!visibleRows(p.id, env, expert).isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    public static Page page(String id) {
        for (Page p : PAGES) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static SettingDef toggle(List<SettingDef> l, String page, String key, int label, boolean def) {
        SettingDef d = new SettingDef(key, SettingDef.Type.TOGGLE, SettingDef.Kind.BOOL, page, label);
        d.def = def;
        l.add(d);
        return d;
    }

    private static SettingDef choice(List<SettingDef> l, String page, String key, int label, String def) {
        SettingDef d = new SettingDef(key, SettingDef.Type.CHOICE, SettingDef.Kind.STRING, page, label);
        d.def = def;
        l.add(d);
        return d;
    }

    private static SettingDef slider(List<SettingDef> l, String page, String key, int label, int def) {
        SettingDef d = new SettingDef(key, SettingDef.Type.SLIDER, SettingDef.Kind.INT, page, label);
        d.def = def;
        l.add(d);
        return d;
    }

    private static SettingDef action(List<SettingDef> l, String page, String key, int label, int button) {
        SettingDef d = new SettingDef(key, SettingDef.Type.ACTION, SettingDef.Kind.NONE, page, label);
        d.buttons.add(button);
        l.add(d);
        return d;
    }

    private static final SettingDef.Format PERCENT = (c, v) -> c.getString(R.string.v2_set_fmt_percent, v);
    private static final SettingDef.Format SIGNED_PERCENT = (c, v) -> c.getString(R.string.v2_set_fmt_percent, v).replace('-', '−');
    private static final SettingDef.Format MS = (c, v) -> c.getString(R.string.v2_set_fmt_ms, v);
    private static final SettingDef.Format DP = (c, v) -> c.getString(R.string.v2_set_fmt_dp, v);

    /** "40 Mbit/s", "0,5 Mbit/s" */
    public static String mbps(Context c, int kbps) {
        String n = kbps % 1000 == 0 ? String.valueOf(kbps / 1000)
                : String.format(Locale.FRENCH, "%.1f", kbps / 1000f);
        return c.getString(R.string.v2_set_fmt_mbps, n);
    }

    private static boolean touch(SettingsEnv e) {
        return e.touchscreen;
    }

    private static List<SettingDef> build() {
        List<SettingDef> l = new ArrayList<>();

        // ================================ Profil ================================
        SettingDef profile = new SettingDef(KEY_PROFILE, SettingDef.Type.CHOICE, SettingDef.Kind.NONE, PROFIL, R.string.v2_set_profile)
                .desc(R.string.v2_set_profile_d).segments(0)
                .options((c, env, v, expert) -> {
                    List<Option> out = new ArrayList<>();
                    out.add(new Option("", c.getString(R.string.v2_set_profile_none)));
                    for (SettingsProfile p : ProfilesManager.getInstance().getProfiles()) {
                        out.add(new Option(p.getUuid().toString(), p.getName()));
                    }
                    return out;
                });
        profile.def = "";
        l.add(profile);
        SettingDef manage = new SettingDef(KEY_PROFILES_MANAGE, SettingDef.Type.BUTTONS, SettingDef.Kind.NONE, PROFIL, R.string.v2_set_profiles_manage)
                .desc(R.string.v2_set_profiles_manage_d)
                .button(R.string.v2_set_profile_new).button(R.string.v2_set_profile_rename).button(R.string.v2_set_profile_delete);
        l.add(manage);

        // =============================== Affichage ==============================
        choice(l, AFFICHAGE, "list_resolution", R.string.v2_set_resolution, "1280x720")
                .desc(R.string.v2_set_resolution_d).segments(0)
                .options((c, env, v, expert) -> withCurrent(c, env.resolutions, v.getString("list_resolution"),
                        Option.ask("custom", c.getString(R.string.v2_set_res_custom))));
        choice(l, AFFICHAGE, "list_fps", R.string.v2_set_fps, "60")
                .desc(R.string.v2_set_fps_d).segments(0)
                .options((c, env, v, expert) -> withCurrent(c, env.frameRates, v.getString("list_fps"),
                        Option.ask("custom", c.getString(R.string.v2_set_fps_custom))));
        choice(l, AFFICHAGE, "list_video_scale_mode", R.string.v2_set_scale, "fit").desc(R.string.v2_set_scale_d)
                .option("fit", R.string.v2_set_scale_fit).option("fill", R.string.v2_set_scale_fill).option("stretch", R.string.v2_set_scale_stretch);
        choice(l, AFFICHAGE, "frame_pacing", R.string.v2_set_pacing, "latency").desc(R.string.v2_set_pacing_d)
                .option("latency", R.string.v2_set_pacing_latency).option("balanced", R.string.v2_set_pacing_balanced)
                .option("cap-fps", R.string.v2_set_pacing_cap).option("smoothness", R.string.v2_set_pacing_smooth)
                .option("warp", R.string.v2_set_pacing_warp).option("warp2", R.string.v2_set_pacing_warp2);
        toggle(l, AFFICHAGE, "checkbox_vrr_pacing", R.string.v2_set_vrr, false).desc(R.string.v2_set_vrr_d).expert();
        choice(l, AFFICHAGE, "list_vrr_pacing_mode", R.string.v2_set_vrr_mode, "1").desc(R.string.v2_set_vrr_mode_d).expert()
                .dependsOn("checkbox_vrr_pacing")
                .option("0", R.string.v2_set_vrr_low).option("1", R.string.v2_set_vrr_balanced).option("2", R.string.v2_set_vrr_smooth);
        toggle(l, AFFICHAGE, "checkbox_paced_ull", R.string.v2_set_paced_ull, false).desc(R.string.v2_set_paced_ull_d).expert();
        toggle(l, AFFICHAGE, "checkbox_predictive_pacing", R.string.v2_set_predictive, false).desc(R.string.v2_set_predictive_d).expert();
        toggle(l, AFFICHAGE, "pref_low_latency_frame_balance", R.string.v2_set_lfr, false).desc(R.string.v2_set_lfr_d).expert();
        toggle(l, AFFICHAGE, "checkbox_use_virtual_display", R.string.v2_set_vdisplay, false).desc(R.string.v2_set_vdisplay_d);
        toggle(l, AFFICHAGE, "checkbox_auto_orientation", R.string.v2_set_auto_orientation, false).desc(R.string.v2_set_auto_orientation_d);
        toggle(l, AFFICHAGE, "checkbox_auto_invert_video_resolution", R.string.v2_set_invert_res, true).desc(R.string.v2_set_invert_res_d).expert()
                .dependsOn("checkbox_auto_orientation");
        toggle(l, AFFICHAGE, "checkbox_follow_device_rotation", R.string.v2_set_follow_rotation, false).desc(R.string.v2_set_follow_rotation_d);
        toggle(l, AFFICHAGE, "checkbox_full_screen", R.string.v2_set_full_screen, true).desc(R.string.v2_set_full_screen_d).expert();
        toggle(l, AFFICHAGE, "checkbox_enable_view_top_center", R.string.v2_set_top_center, false).desc(R.string.v2_set_top_center_d).expert();
        toggle(l, AFFICHAGE, "checkbox_enforce_display_mode", R.string.v2_set_enforce_mode, false).desc(R.string.v2_set_enforce_mode_d).expert();
        toggle(l, AFFICHAGE, "checkbox_reduce_refresh_rate", R.string.v2_set_reduce_refresh, false).desc(R.string.v2_set_reduce_refresh_d).expert();
        toggle(l, AFFICHAGE, "checkbox_unlock_fps", R.string.v2_set_unlock_fps, false).desc(R.string.v2_set_unlock_fps_d).expert();
        toggle(l, AFFICHAGE, "checkbox_enable_fullexdisplay", R.string.v2_set_external, false).desc(R.string.v2_set_external_d).expert();

        // ================================= Vidéo ================================
        slider(l, VIDEO, KEY_BITRATE, R.string.v2_set_bitrate, 0).desc(R.string.v2_set_bitrate_d)
                .range(500, BITRATE_MAX_KBPS, 500, 1000).curve(3f).format(SettingsCatalog::mbps)
                // PyroWave needs hundreds of Mbit/s: its track goes much higher (same as the desktop)
                .maxFrom(v -> "forcepyrowave".equals(v.getString("video_format")) ? BITRATE_MAX_PYROWAVE_KBPS : BITRATE_MAX_KBPS);
        action(l, VIDEO, KEY_BITRATE_DEFAULT, R.string.v2_set_bitrate_default, R.string.v2_set_bitrate_default_button)
                .desc(R.string.v2_set_bitrate_default_d);
        action(l, VIDEO, "edit_diy_bitrate", R.string.v2_set_bitrate_custom, R.string.v2_set_type_in)
                .desc(R.string.v2_set_bitrate_custom_d).expert();
        slider(l, VIDEO, "seekbar_metered_bitrate_kbps", R.string.v2_set_metered, 0).desc(R.string.v2_set_metered_d).expert()
                .range(0, 300000, 500, 1000).curve(3f)
                .format((c, v) -> v == 0 ? c.getString(R.string.v2_set_metered_auto) : mbps(c, v));
        choice(l, VIDEO, "video_format", R.string.v2_set_codec, "auto").desc(R.string.v2_set_codec_d).segments(0)
                .options((c, env, v, expert) -> {
                    List<Option> out = new ArrayList<>();
                    out.add(new Option("auto", c.getString(R.string.v2_set_codec_auto)));
                    out.add(new Option("neverh265", "H.264"));
                    out.add(new Option("forceh265", "HEVC"));
                    out.add(new Option("forceav1", "AV1"));
                    // PyroWave: expert mode only (triage), on a device that decodes it
                    if (env.pyroWave && (expert || "forcepyrowave".equals(v.getString("video_format")))) {
                        out.add(new Option("forcepyrowave", "PyroWave"));
                    }
                    return out;
                });
        toggle(l, VIDEO, "checkbox_auto_av1", R.string.v2_set_auto_av1, true).desc(R.string.v2_set_auto_av1_d).expert();
        toggle(l, VIDEO, "checkbox_pyrowave_444", R.string.v2_set_444, false).desc(R.string.v2_set_444_d).expert()
                .when(e -> e.pyroWave);
        toggle(l, VIDEO, "checkbox_ultra_low_latency", R.string.v2_set_ull, false).desc(R.string.v2_set_ull_d).expert();
        toggle(l, VIDEO, "checkbox_full_range", R.string.v2_set_full_range, false).desc(R.string.v2_set_full_range_d).expert();
        toggle(l, VIDEO, "checkbox_enable_perf_overlay", R.string.v2_set_overlay, false).desc(R.string.v2_set_overlay_d);
        toggle(l, VIDEO, "checkbox_enable_perf_overlay_lite", R.string.v2_set_overlay_lite, false).desc(R.string.v2_set_overlay_lite_d).expert()
                .dependsOn("checkbox_enable_perf_overlay");
        toggle(l, VIDEO, "checkbox_enable_perf_overlay_lite_dialog", R.string.v2_set_overlay_menu, false).desc(R.string.v2_set_overlay_menu_d).expert()
                .dependsOn(v -> v.getBool("checkbox_enable_perf_overlay") && v.getBool("checkbox_enable_perf_overlay_lite"));
        toggle(l, VIDEO, "checkbox_enable_perf_overlay_bottom", R.string.v2_set_overlay_bottom, false).desc(R.string.v2_set_overlay_bottom_d).expert()
                .dependsOn("checkbox_enable_perf_overlay");
        choice(l, VIDEO, "render_mode_list", R.string.v2_set_3d, "0").desc(R.string.v2_set_3d_d).expert()
                .option("0", R.string.v2_set_3d_off).option("1", R.string.v2_set_3d_sbs).option("2", R.string.v2_set_3d_movie);
        SettingDef.Dep threeD = v -> !"0".equals(v.getString("render_mode_list"));
        slider(l, VIDEO, "parallax_depth", R.string.v2_set_3d_depth, 50).desc(R.string.v2_set_3d_depth_d).expert()
                .range(1, 100, 1, 1).dependsOn(threeD);
        slider(l, VIDEO, "convergence_ratio", R.string.v2_set_3d_convergence, 50).desc(R.string.v2_set_3d_convergence_d).expert()
                .range(0, 100, 1, 1).dependsOn(threeD);
        slider(l, VIDEO, "balance_shift", R.string.v2_set_3d_balance, 50).desc(R.string.v2_set_3d_balance_d).expert()
                .range(0, 100, 1, 1).dependsOn(threeD);

        // ================================= Audio ================================
        choice(l, AUDIO, "list_audio_config", R.string.v2_set_audio, "2").desc(R.string.v2_set_audio_d)
                .option("2", R.string.v2_set_audio_stereo).option("51", R.string.v2_set_audio_51).option("71", R.string.v2_set_audio_71);
        toggle(l, AUDIO, "checkbox_host_audio", R.string.v2_set_mute_host, false).desc(R.string.v2_set_mute_host_d).inverted();
        // The microphone belongs to this device, not to a profile (ProfilesManager.APP_WIDE_KEYS), as on the desktop
        toggle(l, AUDIO, "checkbox_mic_passthrough", R.string.v2_set_mic, false).desc(R.string.v2_set_mic_d).global();
        choice(l, AUDIO, "list_mic_device", R.string.v2_set_mic_device, "").desc(R.string.v2_set_mic_device_d).expert().global().segments(0)
                .dependsOn("checkbox_mic_passthrough").when(e -> e.sdk >= 23)
                .options((c, env, v, expert) -> {
                    List<Option> out = new ArrayList<>();
                    out.add(new Option("", c.getString(R.string.v2_set_mic_default)));
                    for (String[] m : env.microphones) {
                        out.add(new Option(m[0], m[1]));
                    }
                    String current = v.getString("list_mic_device");
                    if (!current.isEmpty() && indexOf(out, current) < 0) {
                        out.add(new Option(current, c.getString(R.string.v2_set_mic_missing)));
                    }
                    return out;
                });
        // Outside the profiles, as the desktop's maxPendingAudioMs (ProfilesManager.APP_WIDE_KEYS)
        slider(l, AUDIO, "seekbar_max_pending_audio_ms", R.string.v2_set_audio_buffer, 40).desc(R.string.v2_set_audio_buffer_d).expert()
                .global().range(10, 100, 5, 5).format(MS);
        toggle(l, AUDIO, "checkbox_enable_audiofx", R.string.v2_set_audiofx, false).desc(R.string.v2_set_audiofx_d).expert();

        // ================================ Entrées ===============================
        // Two modes only, as on the desktop: the others are read as the closest one
        // (PreferenceConfiguration.devTouchMode, applied by SettingsStore.getString)
        choice(l, ENTREES, "mouse_mode_list", R.string.v2_set_touch_mode, "0").desc(R.string.v2_set_touch_mode_d)
                .when(SettingsCatalog::touch)
                .option(String.valueOf(PreferenceConfiguration.TOUCH_MODE_MULTITOUCH), R.string.v2_set_touch_multi)
                .option(String.valueOf(PreferenceConfiguration.TOUCH_MODE_TRACKPAD), R.string.v2_set_touch_trackpad);
        toggle(l, ENTREES, "checkbox_trackpad_pinch_zoom", R.string.v2_set_pinch_zoom, false).desc(R.string.v2_set_pinch_zoom_d)
                .when(SettingsCatalog::touch);
        toggle(l, ENTREES, "checkbox_multi_touch_gestures", R.string.v2_set_gestures, false).desc(R.string.v2_set_gestures_d)
                .when(SettingsCatalog::touch);
        toggle(l, ENTREES, "checkbox_absolute_mouse_mode", R.string.v2_set_absolute_mouse, false).desc(R.string.v2_set_absolute_mouse_d)
                .when(e -> e.sdk >= 26 && !e.shield);
        toggle(l, ENTREES, "checkbox_remember_mouse_mode", R.string.v2_set_remember_touch_mode, false).desc(R.string.v2_set_remember_touch_mode_d).expert()
                .when(SettingsCatalog::touch);
        slider(l, ENTREES, "seekbar_touchpad_sensitivity_opacity", R.string.v2_set_touchpad_x, 100).desc(R.string.v2_set_touchpad_d).expert()
                .range(10, 300, 10, 10).format(PERCENT).when(SettingsCatalog::touch);
        slider(l, ENTREES, "seekbar_touchpad_sensitivity_y_opacity", R.string.v2_set_touchpad_y, 100).desc(R.string.v2_set_touchpad_d).expert()
                .range(10, 300, 10, 10).format(PERCENT).when(SettingsCatalog::touch);
        toggle(l, ENTREES, "checkbox_enable_touch_sensitivity", R.string.v2_set_touch_sens, false).desc(R.string.v2_set_touch_sens_d).expert()
                .when(SettingsCatalog::touch);
        toggle(l, ENTREES, "checkbox_enable_global_touch_sensitivity", R.string.v2_set_touch_sens_global, false).desc(R.string.v2_set_touch_sens_global_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_touch_sensitivity");
        toggle(l, ENTREES, "checkbox_enable_touch_sensitivity_rotation_auto", R.string.v2_set_touch_recenter, true).desc(R.string.v2_set_touch_recenter_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_touch_sensitivity");
        slider(l, ENTREES, "seekbar_touch_sensitivity_opacity_x", R.string.v2_set_touch_sens_x, 100).expert()
                .range(10, 300, 10, 10).format(PERCENT).when(SettingsCatalog::touch).dependsOn("checkbox_enable_touch_sensitivity");
        slider(l, ENTREES, "seekbar_touch_sensitivity_opacity_y", R.string.v2_set_touch_sens_y, 100).expert()
                .range(10, 300, 10, 10).format(PERCENT).when(SettingsCatalog::touch).dependsOn("checkbox_enable_touch_sensitivity");
        toggle(l, ENTREES, "checkbox_show_overlay_zoom_toggle_button", R.string.v2_set_zoom_button, false).desc(R.string.v2_set_zoom_button_d).expert()
                .when(SettingsCatalog::touch);
        toggle(l, ENTREES, "checkbox_remember_zoom_pan", R.string.v2_set_remember_zoom, false).desc(R.string.v2_set_remember_zoom_d).expert();
        toggle(l, ENTREES, "checkbox_enable_commit_text", R.string.v2_set_commit_text, false).desc(R.string.v2_set_commit_text_d).expert();
        toggle(l, ENTREES, "checkbox_force_qwerty", R.string.v2_set_qwerty, true).desc(R.string.v2_set_qwerty_d).expert();
        toggle(l, ENTREES, "checkbox_back_as_meta", R.string.v2_set_back_meta, false).desc(R.string.v2_set_back_meta_d).expert();
        toggle(l, ENTREES, "checkbox_mouse_local_cursor", R.string.v2_set_local_cursor, false).desc(R.string.v2_set_local_cursor_d).expert();
        toggle(l, ENTREES, "checkbox_mouse_nav_buttons", R.string.v2_set_mouse_nav, false).desc(R.string.v2_set_mouse_nav_d).expert();
        toggle(l, ENTREES, "checkbox_ignore_synth_events", R.string.v2_set_synth, false).desc(R.string.v2_set_synth_d).expert();
        slider(l, ENTREES, "seekbar_trackpad_sensitivity_x", R.string.v2_set_trackpad_x, 100).desc(R.string.v2_set_trackpad_d).expert()
                .range(-200, 200, 10, 10).format(SIGNED_PERCENT);
        slider(l, ENTREES, "seekbar_trackpad_sensitivity_y", R.string.v2_set_trackpad_y, 100).desc(R.string.v2_set_trackpad_d).expert()
                .range(-200, 200, 10, 10).format(SIGNED_PERCENT);
        toggle(l, ENTREES, "checkbox_trackpad_swap_axis", R.string.v2_set_trackpad_swap, false).desc(R.string.v2_set_trackpad_swap_d).expert();
        toggle(l, ENTREES, "checkbox_trackpad_drag_drop_vibration", R.string.v2_set_trackpad_vibrate, false).desc(R.string.v2_set_trackpad_vibrate_d).expert();
        slider(l, ENTREES, "seekbar_trackpad_drag_drop_threshold", R.string.v2_set_trackpad_hold, 250).desc(R.string.v2_set_trackpad_hold_d).expert()
                .range(0, 2000, 1, 50).format(MS);

        // ================================ Manettes ==============================
        toggle(l, MANETTES, "checkbox_enable_rumble", R.string.v2_set_rumble, true).desc(R.string.v2_set_rumble_d);
        toggle(l, MANETTES, "checkbox_vibrate_fallback", R.string.v2_set_vibrate_fallback, false).desc(R.string.v2_set_vibrate_fallback_d).expert()
                .when(e -> e.vibrator).dependsOn("checkbox_enable_rumble");
        toggle(l, MANETTES, "checkbox_enable_device_rumble", R.string.v2_set_device_rumble, false).desc(R.string.v2_set_device_rumble_d).expert()
                .when(e -> e.vibrator).dependsOn(v -> v.getBool("checkbox_enable_rumble") && v.getBool("checkbox_vibrate_fallback"));
        slider(l, MANETTES, "seekbar_vibrate_fallback_strength", R.string.v2_set_vibrate_strength, 100).expert()
                .range(0, 200, 1, 10).format(PERCENT)
                .when(e -> e.vibrator && e.amplitudeControl)
                .dependsOn(v -> v.getBool("checkbox_enable_rumble") && v.getBool("checkbox_vibrate_fallback"));
        toggle(l, MANETTES, "checkbox_flip_face_buttons", R.string.v2_set_flip_buttons, false).desc(R.string.v2_set_flip_buttons_d).expert();
        toggle(l, MANETTES, "checkbox_enable_joyconfix", R.string.v2_set_joyconfix, false).desc(R.string.v2_set_joyconfix_d).expert();
        toggle(l, MANETTES, "checkbox_multi_controller", R.string.v2_set_force_pad, true).desc(R.string.v2_set_force_pad_d).expert().inverted();
        slider(l, MANETTES, "seekbar_deadzone", R.string.v2_set_deadzone, 5).desc(R.string.v2_set_deadzone_d).expert()
                .range(-20, 20, 1, 1).format(SIGNED_PERCENT);
        toggle(l, MANETTES, "checkbox_usb_driver", R.string.v2_set_usb_driver, true).desc(R.string.v2_set_usb_driver_d).expert()
                .when(e -> e.usbHost);
        toggle(l, MANETTES, "checkbox_usb_bind_all", R.string.v2_set_usb_all, false).desc(R.string.v2_set_usb_all_d).expert()
                .when(e -> e.usbHost).dependsOn("checkbox_usb_driver");
        choice(l, MANETTES, "analog_scrolling", R.string.v2_set_stick_scroll, "right").desc(R.string.v2_set_stick_scroll_d).expert()
                .option("none", R.string.v2_set_stick_none).option("right", R.string.v2_set_stick_right).option("left", R.string.v2_set_stick_left);
        toggle(l, MANETTES, "checkbox_gamepad_touchpad_as_mouse", R.string.v2_set_pad_touchpad, false).desc(R.string.v2_set_pad_touchpad_d).expert();
        toggle(l, MANETTES, "checkbox_gamepad_motion_sensors", R.string.v2_set_pad_motion, true).desc(R.string.v2_set_pad_motion_d).expert()
                .when(e -> e.sdk >= 31);
        toggle(l, MANETTES, "checkbox_gamepad_motion_fallback", R.string.v2_set_motion_fallback, false).desc(R.string.v2_set_motion_fallback_d).expert()
                .when(e -> e.motionSensors);
        toggle(l, MANETTES, "checkbox_force_device_motion", R.string.v2_set_motion_force, false).desc(R.string.v2_set_motion_force_d).expert()
                .when(e -> e.motionSensors).dependsOn("checkbox_gamepad_motion_fallback");
        toggle(l, MANETTES, "checkbox_gamepad_enable_battery_report", R.string.v2_set_battery, true).desc(R.string.v2_set_battery_d).expert();
        toggle(l, MANETTES, "checkbox_back_as_guide", R.string.v2_set_back_guide, false).desc(R.string.v2_set_back_guide_d).expert();

        // ========================== Contrôles à l'écran =========================
        toggle(l, CONTROLES, "checkbox_show_onscreen_controls", R.string.v2_set_osc, false).desc(R.string.v2_set_osc_d)
                .when(SettingsCatalog::touch);
        toggle(l, CONTROLES, "checkbox_hide_osc_when_has_gamepad", R.string.v2_set_osc_hide, true).desc(R.string.v2_set_osc_hide_d)
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        slider(l, CONTROLES, "seekbar_osc_opacity", R.string.v2_set_osc_opacity, 90).range(0, 100, 1, 5).format(PERCENT)
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        action(l, CONTROLES, "option_reset_osc_preference", R.string.v2_set_osc_reset, R.string.v2_set_reset)
                .desc(R.string.v2_set_osc_reset_d).when(SettingsCatalog::touch);
        toggle(l, CONTROLES, "checkbox_onscreen_style_official", R.string.v2_set_osc_official, false).desc(R.string.v2_set_osc_official_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        toggle(l, CONTROLES, "checkbox_vibrate_osc", R.string.v2_set_osc_vibrate, true).desc(R.string.v2_set_osc_vibrate_d).expert()
                .when(e -> e.touchscreen && e.vibrator).dependsOn("checkbox_show_onscreen_controls");
        toggle(l, CONTROLES, "checkbox_only_show_L3R3", R.string.v2_set_osc_l3r3, false).desc(R.string.v2_set_osc_l3r3_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        toggle(l, CONTROLES, "checkbox_show_guide_button", R.string.v2_set_osc_guide, true).desc(R.string.v2_set_osc_guide_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        toggle(l, CONTROLES, "checkbox_enable_analog_stick_new", R.string.v2_set_osc_free_stick, false).desc(R.string.v2_set_osc_free_stick_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_show_onscreen_controls");
        slider(l, CONTROLES, "seekbar_osc_free_analog_stick_opacity", R.string.v2_set_osc_free_stick_opacity, 20).expert()
                .range(0, 100, 1, 5).format(PERCENT).when(SettingsCatalog::touch)
                .dependsOn(v -> v.getBool("checkbox_show_onscreen_controls") && v.getBool("checkbox_enable_analog_stick_new"));
        toggle(l, CONTROLES, "checkbox_voice_ptt_button", R.string.v2_set_voice_ptt, false).desc(R.string.v2_set_voice_ptt_d)
                .when(SettingsCatalog::touch);
        toggle(l, CONTROLES, "checkbox_enable_floating_button", R.string.v2_set_floating, false).desc(R.string.v2_set_floating_d)
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_quit_dialog");
        toggle(l, CONTROLES, "checkbox_enable_quit_dialog", R.string.v2_set_back_menu, true).desc(R.string.v2_set_back_menu_d).expert();
        toggle(l, CONTROLES, "checkbox_enable_keyboard", R.string.v2_set_keys, false).desc(R.string.v2_set_keys_d).expert()
                .when(SettingsCatalog::touch);
        choice(l, CONTROLES, "keyboard_axi_list", R.string.v2_set_keys_layout, "OSC_Keyboard").desc(R.string.v2_set_keys_layout_d).expert().global()
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_keyboard").segments(5)
                .option("OSC_Keyboard", R.string.v2_set_layout_1).option("OSC_Keyboard_2", R.string.v2_set_layout_2)
                .option("OSC_Keyboard_3", R.string.v2_set_layout_3).option("OSC_Keyboard_4", R.string.v2_set_layout_4)
                .option("OSC_Keyboard_5", R.string.v2_set_layout_5);
        toggle(l, CONTROLES, "checkbox_enable_keyboard_square", R.string.v2_set_keys_square, false).desc(R.string.v2_set_keys_square_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_keyboard");
        toggle(l, CONTROLES, "checkbox_vibrate_keyboard", R.string.v2_set_keys_vibrate, false).desc(R.string.v2_set_keys_vibrate_d).expert()
                .when(e -> e.touchscreen && e.vibrator);
        toggle(l, CONTROLES, "checkbox_enable_clear_default_special_button", R.string.v2_set_keys_no_default, false).desc(R.string.v2_set_keys_no_default_d).expert();
        action(l, CONTROLES, "option_reset_osc", R.string.v2_set_keys_reset, R.string.v2_set_reset).desc(R.string.v2_set_keys_reset_d).expert()
                .when(SettingsCatalog::touch).dependsOn("checkbox_enable_keyboard");
        action(l, CONTROLES, "import_keyboard_file", R.string.v2_set_keys_import, R.string.v2_set_import).desc(R.string.v2_set_keys_import_d).expert()
                .when(SettingsCatalog::touch);
        action(l, CONTROLES, "export_keyboard_file", R.string.v2_set_keys_export, R.string.v2_set_export).desc(R.string.v2_set_keys_export_d).expert()
                .when(SettingsCatalog::touch);
        action(l, CONTROLES, "import_special_button_file", R.string.v2_set_special_import, R.string.v2_set_import).desc(R.string.v2_set_special_import_d).expert();
        slider(l, CONTROLES, "seekbar_keyboard_axi_opacity", R.string.v2_set_kb_opacity, 90).desc(R.string.v2_set_kb_opacity_d).expert()
                .range(0, 100, 1, 5).format(PERCENT);
        toggle(l, CONTROLES, "checkbox_enable_sticky_modifier_key_virtual_keyboard", R.string.v2_set_kb_sticky, true).desc(R.string.v2_set_kb_sticky_d).expert();
        choice(l, CONTROLES, "list_onscreen_keyboard_align_mode", R.string.v2_set_kb_align, "center").desc(R.string.v2_set_kb_align_d).expert()
                .option("left", R.string.v2_set_align_left).option("center", R.string.v2_set_align_center).option("right", R.string.v2_set_align_right);
        toggle(l, CONTROLES, "onscreen_keyboard_autofit", R.string.v2_set_kb_free_size, false).desc(R.string.v2_set_kb_free_size_d).expert();
        slider(l, CONTROLES, "seekbar_onscreen_keyboard_height", R.string.v2_set_kb_height, 200).expert()
                .range(150, 400, 10, 10).format(DP).dependsOn("onscreen_keyboard_autofit");
        slider(l, CONTROLES, "seekbar_onscreen_keyboard_width", R.string.v2_set_kb_width, 1000).expert()
                .range(150, 1000, 10, 10).format(DP).dependsOn("onscreen_keyboard_autofit");

        // =============================== Apparence ==============================
        SettingDef theme = choice(l, APPARENCE, V2Theme.PREF_THEME, R.string.v2_set_theme, V2Theme.BRAISE).desc(R.string.v2_set_theme_d).global()
                .option(V2Theme.BRAISE, R.string.v2_theme_braise).option(V2Theme.AFFICHE, R.string.v2_theme_affiche)
                .option(V2Theme.LUDIQUE, R.string.v2_theme_ludique);
        SettingDef mode = new SettingDef(V2Theme.PREF_MODE, SettingDef.Type.CHOICE, SettingDef.Kind.INT, APPARENCE, R.string.v2_set_theme_mode)
                .desc(R.string.v2_set_theme_mode_d).global()
                .option("0", R.string.v2_mode_system).option("1", R.string.v2_mode_light).option("2", R.string.v2_mode_dark);
        mode.def = V2Theme.MODE_SYSTEM;
        l.add(mode);
        choice(l, APPARENCE, "list_languages", R.string.v2_set_language, "default").desc(R.string.v2_set_language_d).segments(0)
                .options((c, env, v, expert) -> {
                    Resources r = c.getResources();
                    String[] values = r.getStringArray(R.array.language_values);
                    String[] names = r.getStringArray(R.array.language_names);
                    List<Option> out = new ArrayList<>();
                    for (int i = 0; i < values.length && i < names.length; i++) {
                        out.add(new Option(values[i], "default".equals(values[i]) ? c.getString(R.string.v2_set_language_auto) : names[i]));
                    }
                    return out;
                });
        toggle(l, APPARENCE, "checkbox_enable_post_stream_toast", R.string.v2_set_latency_toast, false).desc(R.string.v2_set_latency_toast_d).expert();
        toggle(l, APPARENCE, "checkbox_enable_pip", R.string.v2_set_pip, false).desc(R.string.v2_set_pip_d).expert()
                .when(e -> e.pip);

        // ================================== PC ==================================
        // The page of the desktop that holds the clipboard (Réglages v2 › PC)
        // As on the desktop (quitAppAfter), first row of the page
        toggle(l, PC, PreferenceConfiguration.QUIT_APP_AFTER_PREF_STRING, R.string.v2_set_quit_after, false).desc(R.string.v2_set_quit_after_d);
        // The clipboard is outside the profiles, as the desktop's clipboardSync* (ProfilesManager.APP_WIDE_KEYS)
        toggle(l, PC, "checkbox_smart_clipboard_sync", R.string.v2_set_clipboard, false).desc(R.string.v2_set_clipboard_d).expert().global();
        toggle(l, PC, "checkbox_smart_clipboard_sync_toast", R.string.v2_set_clipboard_toast, true).desc(R.string.v2_set_clipboard_toast_d).expert()
                .global().dependsOn("checkbox_smart_clipboard_sync");
        toggle(l, PC, "checkbox_hide_clipboard_content", R.string.v2_set_clipboard_hide, true).desc(R.string.v2_set_clipboard_hide_d).expert().global();

        // ================================= Avancé ===============================
        toggle(l, AVANCE, "checkbox_auto_update", R.string.v2_set_auto_update, true).desc(R.string.v2_set_auto_update_d).global();
        action(l, AVANCE, "option_check_update", R.string.v2_set_check_update, R.string.v2_set_check).desc(R.string.v2_set_check_update_d);
        toggle(l, AVANCE, "checkbox_disable_warnings", R.string.v2_set_warnings, false).desc(R.string.v2_set_warnings_d).expert().inverted();
        action(l, AVANCE, "option_software_release", R.string.v2_set_releases, R.string.v2_set_open).desc(R.string.v2_set_releases_d).expert();
        toggle(l, AVANCE, "checkbox_enable_adpf", R.string.v2_set_adpf, true).desc(R.string.v2_set_adpf_d).expert();
        toggle(l, AVANCE, "checkbox_sustained_performance", R.string.v2_set_sustained, false).desc(R.string.v2_set_sustained_d).expert();
        toggle(l, AVANCE, "checkbox_prevent_packet_loss", R.string.v2_set_keepalive, false).desc(R.string.v2_set_keepalive_d).expert();
        // Outside the profiles, as the desktop's perfCsvLogging (ProfilesManager.APP_WIDE_KEYS)
        toggle(l, AVANCE, "checkbox_enable_perf_logging", R.string.v2_set_perf_log, false).desc(R.string.v2_set_perf_log_d).expert()
                .global().dependsOn("checkbox_enable_perf_overlay");
        action(l, AVANCE, "share_performance_logs", R.string.v2_set_perf_share, R.string.v2_set_share).desc(R.string.v2_set_perf_share_d).expert()
                .dependsOn(v -> v.getBool("checkbox_enable_perf_overlay") && v.getBool("checkbox_enable_perf_logging"));
        action(l, AVANCE, "pref_debug_info", R.string.v2_set_debug, R.string.v2_set_open).desc(R.string.v2_set_debug_d).expert();

        return l;
    }

    static int indexOf(List<Option> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value.equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /** A detected list, plus the stored value when the list lost it, plus the « ask » entry */
    private static List<Option> withCurrent(Context c, List<Option> detected, String current, Option ask) {
        List<Option> out = new ArrayList<>(detected);
        if (current != null && !current.isEmpty() && indexOf(out, current) < 0) {
            out.add(new Option(current, current));
        }
        out.add(ask);
        return out;
    }
}
