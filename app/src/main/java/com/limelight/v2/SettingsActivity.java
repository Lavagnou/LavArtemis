package com.limelight.v2;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.limelight.DebugInfoActivity;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.audio.MicrophoneStreamer;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.SettingsFileActions;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;
import com.limelight.utils.HelpLauncher;
import com.limelight.v2.settings.Option;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingRowView;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;
import com.limelight.v2.ui.V2Dialogs;
import com.limelight.v2.ui.V2ListDialog;
import com.limelight.utils.PerformanceDataTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Réglages v2 (LavArtemis Dev): replaces the legacy StreamSettings in the v2 interface. Every
 * change is written at once (profile-aware, SettingsStore) with the side effects the legacy screen
 * had: bitrate reset on a new resolution or frame rate, native mode warning, custom values typed
 * in, microphone permission, log clearing, language restart, file imports and exports.
 */
public class SettingsActivity extends V2Activity implements SettingsScreen.Actions {
    public static final String EXTRA_PAGE = "page";

    /** Tests and screenshots: the device description to use instead of reading the device */
    public static SettingsEnv envOverride;

    private static final int REQUEST_IMPORT_KEYS = 1001;
    private static final int REQUEST_IMPORT_SPECIAL = 1002;
    private static final int REQUEST_MIC = 1003;

    private FrameLayout container;
    SettingsScreen screen;
    SettingsEnv env;
    SettingsStore store;
    private String page = SettingsCatalog.PROFIL;
    private boolean showingRows;
    private String languageAtStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SettingsStore(this);
        languageAtStart = store.getString("list_languages");
        if (savedInstanceState != null) {
            page = savedInstanceState.getString(EXTRA_PAGE, page);
            showingRows = savedInstanceState.getBoolean("rows", false);
        } else if (getIntent().getStringExtra(EXTRA_PAGE) != null) {
            page = getIntent().getStringExtra(EXTRA_PAGE);
            showingRows = true;
        }
        readEnv();
        container = new FrameLayout(this);
        setContentView(container);
        rebuild();
    }

    /**
     * Reads the device, and applies what the legacy screen rewrote when it opened: a resolution or
     * frame rate the display cannot take any more, PyroWave chosen on a device without it
     */
    private void readEnv() {
        SharedPreferences prefs = ProfilesManager.getInstance().getOverlayingSharedPreferences(this);
        env = envOverride != null ? envOverride : SettingsEnv.detect(this, prefs);
        if (!env.fixups.isEmpty()) {
            for (java.util.Map.Entry<String, String> e : env.fixups.entrySet()) {
                store.putString(e.getKey(), e.getValue());
            }
            store.resetBitrate();
        }
        if (!env.pyroWave && "forcepyrowave".equals(store.getString("video_format"))) {
            store.putString("video_format", "auto");
        }
    }

    @Override
    protected void onCutoutKnown() {
        // Android 9: the native resolution without the cutout joins the list
        if (envOverride == null && store != null) {
            readEnv();
            rebuild();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (screen != null) {
            outState.putString(EXTRA_PAGE, screen.currentPage());
            outState.putBoolean("rows", screen.narrow && screen.isShowingRows());
        }
    }

    @Override
    protected void rebuild() {
        if (container == null) {
            return;
        }
        boolean hadFocus = screen != null && !container.isInTouchMode() && container.findFocus() != null;
        View focused = container.findFocus();
        // The row, and the button or theme card of it, that had the focus (a theme picked with A)
        SettingRowView focusRow = SettingsScreen.rowOf(focused);
        String focusKey = focusRow == null ? null : focusRow.def.key;
        int focusStop = focusRow == null ? -1 : focusRow.focusStops().indexOf(focused);
        if (screen != null) {
            page = screen.currentPage();
            showingRows = screen.narrow && screen.isShowingRows();
        }
        screen = new SettingsScreen(this, theme, Density.of(getResources().getConfiguration()), env, store, this, page, showingRows);
        container.removeAllViews();
        container.addView(screen.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (focusKey != null && screen.focusRowStop(focusKey, focusStop)) {
            return;
        } else if (hadFocus) {
            screen.requestInitialFocus();
        }
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
    }

    @Override
    protected boolean onV2InitialFocus() {
        return screen != null && screen.requestInitialFocus();
    }

    @Override
    protected void onV2Back() {
        if (screen != null && screen.onBack()) {
            return;
        }
        finish();
    }

    @Override
    public void back() {
        onV2Back();
    }

    @Override
    public void finish() {
        super.finish();
        applyLanguageChange();
    }

    /** The legacy screen's language handling when it closed */
    private void applyLanguageChange() {
        String now = store.getString("list_languages");
        if (now.equals(languageAtStart)) {
            return;
        }
        languageAtStart = now;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Restart the interface so every screen takes the new language
            Intent intent = new Intent(this, PcView.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (PreferenceConfiguration.DEFAULT_LANGUAGE.equals(now)) {
            toast(R.string.v2_set_language_restart);
            System.exit(0);
        }
    }

    // ---- expert mode ------------------------------------------------------------------------------

    @Override
    public void setExpert(boolean on) {
        store.setExpertMode(on);
        if (!on && "forcepyrowave".equals(store.getString("video_format"))) {
            // Prototype setExpert: PyroWave is an expert choice, leaving expert mode leaves it
            SettingDef codec = SettingsCatalog.find("video_format");
            if (codec != null) {
                store.setChoice(codec, "auto");
            }
        }
        screen.setExpert(on);
        toast(on ? R.string.v2_set_expert_on : R.string.v2_set_expert_off);
    }

    /**
     * Réglages move by column (decision of 2026-11-27): up / down stay in the column they are in
     * and stop at its end, left / right change column, a choice or a slider takes left / right first
     */
    @Override
    protected boolean onV2Move(View focused, int dx, int dy) {
        return screen != null && screen.move(focused, dx, dy);
    }

    @Override
    protected boolean shoulderKeysEnabled() {
        return screen != null;
    }

    @Override
    protected boolean onV2Shoulder(int delta) {
        if (screen != null) {
            screen.stepPage(delta);
        }
        return true;
    }

    // ---- rows ---------------------------------------------------------------------------------------

    @Override
    public boolean onLeaveLeft() {
        return screen != null && screen.leaveRows();
    }

    @Override
    public void onToggle(SettingDef def, boolean shown) {
        store.setToggle(def, shown);
        boolean stored = shown != def.inverted;
        switch (def.key) {
            case "checkbox_unlock_fps":
                // The frame rates the display offers change with it
                readEnv();
                screen.refresh();
                screen.rebuildRows();
                return;
            case "checkbox_enable_perf_logging":
                if (!stored) {
                    new PerformanceDataTracker().clearLogs(this);
                }
                break;
            case "checkbox_mic_passthrough":
                // Asked for when the user turns it on, where the reason is obvious, not mid-stream
                if (stored && !MicrophoneStreamer.hasPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
                }
                break;
            default:
                break;
        }
        screen.refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MIC && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            store.putBool("checkbox_mic_passthrough", false);
            toast(R.string.toast_microphone_permission_denied);
            if (screen != null) {
                screen.refresh();
            }
        }
    }

    @Override
    public void onChoose(SettingDef def, Option option) {
        if (option.ask) {
            ask(def);
            return;
        }
        String before = store.choiceValue(def);
        if (option.value.equals(before)) {
            return;
        }
        if (SettingsCatalog.KEY_PROFILE.equals(def.key)) {
            store.setActiveProfile(option.value);
            screen.rebuildRows();
            return;
        }
        store.setChoice(def, option.value);
        if (V2Theme.PREF_THEME.equals(def.key) || V2Theme.PREF_MODE.equals(def.key)) {
            // Live preview: the whole screen in the new theme, focus kept on the row
            theme = V2Theme.load(this);
            applyWindowColors();
            rebuild();
            return;
        }
        screen.refresh();
        if (option.nativeWarn && ("list_resolution".equals(def.key) || "list_fps".equals(def.key))) {
            V2Dialogs.message(this, theme, getString("list_resolution".equals(def.key) ? R.string.v2_set_native_res_title : R.string.v2_set_native_fps_title),
                    getString(R.string.v2_set_native_text), getString(R.string.v2_ok));
        }
        if ("video_format".equals(def.key) || "render_mode_list".equals(def.key)) {
            screen.rebuildRows();
        }
    }

    @Override
    public void onOpenList(SettingDef def) {
        final List<Option> options = def.options(this, env, store, screen.isExpert());
        List<String> labels = new ArrayList<>();
        for (Option o : options) {
            labels.add(o.label);
        }
        int selected = -1;
        String current = store.choiceValue(def);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value.equals(current) && !options.get(i).ask) {
                selected = i;
            }
        }
        labels.add(getString(R.string.v2_cancel));
        V2ListDialog.show(this, theme, getString(def.label), labels, labels.size() - 1, selected,
                index -> onChoose(def, options.get(index)), null);
    }

    @Override
    public void onSlide(SettingDef def, int value, boolean finished) {
        if (!finished) {
            return;
        }
        store.putInt(def.key, value);
        screen.refresh();
    }

    @Override
    public void onAction(SettingDef def, int button) {
        switch (def.key) {
            case SettingsCatalog.KEY_PROFILES_MANAGE:
                manageProfiles(button);
                break;
            case SettingsCatalog.KEY_BITRATE_DEFAULT:
                store.resetBitrate();
                screen.refresh();
                break;
            case "edit_diy_bitrate":
                askBitrate();
                break;
            case "option_reset_osc_preference":
                V2Dialogs.confirm(this, theme, getString(R.string.v2_set_osc_reset), getString(R.string.v2_set_reset_confirm),
                        getString(R.string.v2_set_reset), getString(R.string.v2_cancel), () -> {
                            getSharedPreferences(VirtualControllerConfigurationLoader.OSC_PREFERENCE, Context.MODE_PRIVATE).edit().clear().apply();
                            toast(R.string.toast_reset_osc_success);
                        }, null);
                break;
            case "option_reset_osc":
                V2Dialogs.confirm(this, theme, getString(R.string.v2_set_keys_reset), getString(R.string.v2_set_reset_confirm),
                        getString(R.string.v2_set_reset), getString(R.string.v2_cancel), () -> {
                            String name = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                                    .getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                            if (name.isEmpty()) {
                                name = KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE;
                            }
                            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply();
                            toast(R.string.toast_reset_osc_success);
                        }, null);
                break;
            case "import_keyboard_file":
                startPicker(REQUEST_IMPORT_KEYS);
                break;
            case "import_special_button_file":
                startPicker(REQUEST_IMPORT_SPECIAL);
                break;
            case "export_keyboard_file":
                SettingsFileActions.exportKeyboardLayout(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));
                break;
            case "share_performance_logs":
                SettingsFileActions.sharePerformanceLogs(this);
                break;
            case "pref_debug_info":
                startActivity(new Intent(this, DebugInfoActivity.class));
                break;
            case "option_check_update":
                SettingsFileActions.checkForUpdates(this);
                break;
            case "option_software_release":
                HelpLauncher.launchUrl(this, "https://github.com/Lavagnou/LavArtemis/releases");
                break;
            default:
                break;
        }
    }

    private void startPicker(int request) {
        try {
            startActivityForResult(SettingsFileActions.openJsonIntent(), request);
        } catch (android.content.ActivityNotFoundException e) {
            toast(R.string.pref_error_occurred);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_IMPORT_KEYS) {
            SettingsFileActions.importKeyboardLayout(this, data.getData(), androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));
        } else if (requestCode == REQUEST_IMPORT_SPECIAL) {
            SettingsFileActions.importSpecialButtons(this, data.getData());
        }
    }

    // ---- values typed in ------------------------------------------------------------------------

    /** « Personnalisée… » (resolution) and « Autre… » (frame rate) */
    private void ask(SettingDef def) {
        if ("list_resolution".equals(def.key)) {
            String current = ProfilesManager.getInstance().getOverlayingSharedPreferences(this)
                    .getString(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, "");
            V2Dialogs.prompt(this, theme, getString(R.string.v2_set_res_custom_title), getString(R.string.v2_set_res_custom_text),
                    current, "1920x1080", InputType.TYPE_CLASS_TEXT, 11, getString(R.string.v2_set_apply), getString(R.string.v2_cancel),
                    text -> {
                        String value = text.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
                        String[] seg = value.split("x");
                        int w, h;
                        try {
                            w = seg.length == 2 ? Integer.parseInt(seg[0]) : 0;
                            h = seg.length == 2 ? Integer.parseInt(seg[1]) : 0;
                        } catch (NumberFormatException e) {
                            w = h = 0;
                        }
                        if (w <= 0 || h <= 0) {
                            invalid(getString(R.string.v2_set_res_custom_invalid, text));
                            return;
                        }
                        store.putString(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, w + "x" + h);
                        readEnv();
                        store.setChoice(def, w + "x" + h);
                        screen.rebuildRows();
                        V2Dialogs.message(this, theme, getString(R.string.v2_set_native_res_title), getString(R.string.v2_set_native_text), getString(R.string.v2_ok));
                    });
        } else if ("list_fps".equals(def.key)) {
            String current = ProfilesManager.getInstance().getOverlayingSharedPreferences(this)
                    .getString(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, "");
            V2Dialogs.prompt(this, theme, getString(R.string.v2_set_fps_custom_title), getString(R.string.v2_set_fps_custom_text),
                    current, "59.94", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 7,
                    getString(R.string.v2_set_apply), getString(R.string.v2_cancel), text -> {
                        float hz;
                        try {
                            hz = Float.parseFloat(text.replace(',', '.'));
                        } catch (NumberFormatException e) {
                            hz = 0;
                        }
                        if (hz <= 0) {
                            invalid(getString(R.string.v2_set_fps_custom_invalid, text));
                            return;
                        }
                        String formatted = SettingsEnv.formatRefreshRate(hz);
                        store.putString(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, formatted);
                        readEnv();
                        // The list entry carries the value as Float.toString() writes it
                        store.setChoice(def, Float.toString(Float.parseFloat(formatted)));
                        screen.rebuildRows();
                        V2Dialogs.message(this, theme, getString(R.string.v2_set_native_fps_title), getString(R.string.v2_set_native_text), getString(R.string.v2_ok));
                    });
        }
    }

    /** « Saisir un débit » (edit_diy_bitrate): any value, past the slider's end */
    private void askBitrate() {
        String current = SettingsCatalog.mbps(this, store.getInt(SettingsCatalog.KEY_BITRATE)).replaceAll("[^0-9,]", "");
        V2Dialogs.prompt(this, theme, getString(R.string.v2_set_bitrate_custom), getString(R.string.v2_set_bitrate_custom_text),
                current, "40", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 5,
                getString(R.string.v2_set_apply), getString(R.string.v2_cancel), text -> {
                    float mbps;
                    try {
                        mbps = Float.parseFloat(text.replace(',', '.'));
                    } catch (NumberFormatException e) {
                        mbps = 0;
                    }
                    int kbps = (int) (mbps * 1000);
                    if (kbps <= 0) {
                        invalid(getString(R.string.v2_set_bitrate_custom_invalid, text));
                        return;
                    }
                    store.putString("edit_diy_bitrate", text.replace(',', '.'));
                    store.putInt(SettingsCatalog.KEY_BITRATE, kbps);
                    screen.refresh();
                });
    }

    private void invalid(String text) {
        V2Dialogs.message(this, theme, getString(R.string.v2_oops), text, getString(R.string.v2_ok));
    }

    // ---- profiles ---------------------------------------------------------------------------------

    private void manageProfiles(int button) {
        SettingsProfile active = store.activeProfile();
        switch (button) {
            case 0: // Nouveau
                V2Dialogs.prompt(this, theme, getString(R.string.v2_set_profile_new_title), getString(R.string.v2_set_profile_new_text),
                        store.nextProfileName(getString(R.string.v2_set_profile_default_name)), null, InputType.TYPE_CLASS_TEXT, 40,
                        getString(R.string.v2_set_profile_create), getString(R.string.v2_cancel), name -> {
                            store.createProfile(name);
                            screen.rebuildRows();
                        });
                break;
            case 1: // Renommer
                if (active != null) {
                    V2Dialogs.prompt(this, theme, getString(R.string.v2_set_profile_rename_title), null, active.getName(), null,
                            InputType.TYPE_CLASS_TEXT, 40, getString(R.string.v2_host_rename), getString(R.string.v2_cancel), name -> {
                                store.renameActiveProfile(name);
                                screen.rebuildRows();
                            });
                }
                break;
            case 2: // Supprimer
                if (active != null) {
                    V2Dialogs.confirm(this, theme, getString(R.string.v2_set_profile_delete_title, active.getName()),
                            getString(R.string.v2_set_profile_delete_text), getString(R.string.v2_set_profile_delete),
                            getString(R.string.v2_cancel), () -> {
                                store.deleteActiveProfile();
                                screen.rebuildRows();
                            }, null);
                }
                break;
            default:
                break;
        }
    }
}
