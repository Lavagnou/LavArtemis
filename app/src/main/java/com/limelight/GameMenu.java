package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.KeyConfigHelper;
import com.limelight.utils.KeyMapper;
import com.limelight.v2.V2Theme;
import com.limelight.v2.ui.V2ListDialog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provide options for ongoing Game Stream.
 * <p>
 * Shown on back action in game activity.
 */
public class GameMenu implements Game.GameMenuCallbacks {

    public static final long KEY_UP_DELAY = 25;
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    public static final String PREF_NAME = "specialPrefs"; // SharedPreferences的名称

    public static final String KEY_NAME = "special_key"; // 要保存的键名称

    public static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        public MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        public MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Context dialogScreenContext;

    private Dialog currentDialog;

    public GameMenu(Game game, Context dialogScreenContext) {
        this.game = game;
        this.dialogScreenContext = dialogScreenContext;
    }

    public GameMenu(Game game) {
        this.game = game;
        this.dialogScreenContext = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }


    private void sendKeys(short[] keys) {
        game.sendKeys(keys);
    }

    private void runWithGameFocus(Runnable runnable) {
        // Ensure that the Game activity is still active (not finished)
        if (game.isFinishing()) {
            return;
        }
        // Check if the game window has focus again, if not try again after delay
        if (!game.hasWindowFocus() && dialogScreenContext instanceof Game) {
            new Handler().postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        // Game Activity has focus, run runnable
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    /**
     * LavArtemis Dev: the same options in a dialog of the v2 theme (focusable rows, A / B on a
     * gamepad, « Annuler » as a button). A pick closes the dialog then runs the option exactly as
     * the list below does (runWithGameFocus for the options that need the stream's focus).
     */
    private void showThemedMenuDialog(String title, MenuOption[] options) {
        List<String> labels = new ArrayList<>();
        int cancelIndex = -1;
        String cancel = getString(R.string.game_menu_cancel);
        for (int i = 0; i < options.length; i++) {
            labels.add(options[i].label);
            if (options[i].runnable == null && cancel.equals(options[i].label)) {
                cancelIndex = i;
            }
        }
        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = V2ListDialog.show(dialogScreenContext, V2Theme.load(game), title, labels, cancelIndex,
                index -> run(options[index]), this::hideMenu);
    }

    private void showMenuDialog(String title, MenuOption[] options) {
        if (BuildConfig.LAV_DEV) {
            showThemedMenuDialog(title, options);
            return;
        }
        int themeResId = game.getApplicationInfo().theme;

        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle(title);

        final ArrayAdapter<String> actions = new ArrayAdapter<>(themedContext, android.R.layout.simple_list_item_1);

        builder.setAdapter(actions, (dialog, which) -> {
            String label = actions.getItem(which);
            for (MenuOption option : options) {
                if (label != null && label.equals(option.label)) {
                    run(option);
                    break;
                }
            }
        });

        builder.setOnCancelListener(dialog -> hideMenu());

        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = builder.show();

        Window window = currentDialog.getWindow();

        if (window != null) {
            View decorView = window.getDecorView();
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {

                    decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (MenuOption option : options) {
                            actions.add(option.label);
                        }
                        actions.notifyDataSetChanged();
                    });
                }
            });
        }
    }

    /**
     * LavArtemis Dev: the built-in shortcuts of the desktop client (KeyMacroManager presets), in its
     * order and with its labels, so « Envoyer des touches » is the same list on both clients.
     */
    private static final short VK_LCONTROL = 0xA2, VK_LMENU = 0xA4, VK_LSHIFT = 0xA0, VK_LWIN = 0x5B,
            VK_DELETE = 0x2E;
    private static final Object[][] DEV_SEND_KEYS = {
            {R.string.v2_keys_esc, new short[]{0x1B}},
            {R.string.v2_keys_f11, new short[]{0x7A}},
            {R.string.v2_keys_alt_f4, new short[]{VK_LMENU, 0x73}},
            {R.string.v2_keys_alt_enter, new short[]{VK_LMENU, 0x0D}},
            {R.string.v2_keys_ctrl_v, new short[]{VK_LCONTROL, 0x56}},
            {R.string.v2_keys_win, new short[]{VK_LWIN}},
            {R.string.v2_keys_win_d, new short[]{VK_LWIN, 0x44}},
            {R.string.v2_keys_win_g, new short[]{VK_LWIN, 0x47}},
            {R.string.v2_keys_ctrl_alt_del, new short[]{VK_LCONTROL, VK_LMENU, VK_DELETE}},
            {R.string.v2_keys_ctrl_shift_esc, new short[]{VK_LCONTROL, VK_LSHIFT, 0x1B}},
            {R.string.v2_keys_ctrl_alt_tab, new short[]{VK_LCONTROL, VK_LMENU, 0x09}},
            {R.string.v2_keys_shift_tab, new short[]{VK_LSHIFT, 0x09}},
            {R.string.v2_keys_win_shift_left, new short[]{VK_LWIN, VK_LSHIFT, 0x25}},
            {R.string.v2_keys_ctrl_alt_shift_f1, new short[]{VK_LCONTROL, VK_LMENU, VK_LSHIFT, 0x70}},
            {R.string.v2_keys_ctrl_alt_shift_f12, new short[]{VK_LCONTROL, VK_LMENU, VK_LSHIFT, 0x7B}},
    };

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        if (BuildConfig.LAV_DEV && !PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys) {
            for (Object[] key : DEV_SEND_KEYS) {
                short[] codes = (short[]) key[1];
                options.add(new MenuOption(getString((Integer) key[0]), () -> sendKeys(codes)));
            }
        } else if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
            options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_ESCAPE})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_F11})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_V})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F12})));

            options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_b),
                    () -> sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B})));
        }

        // Import custom shortcuts
        SharedPreferences preferences = game.getSharedPreferences(PREF_NAME, Activity.MODE_PRIVATE);
        String value = preferences.getString(KEY_NAME,"");

        if(!TextUtils.isEmpty(value)){
            try {
                KeyConfigHelper.ShortcutFile shortcutFile = KeyConfigHelper.parseShortcutFile(value);
                if (shortcutFile != null && shortcutFile.data != null && !shortcutFile.data.isEmpty()) {
                    List<KeyConfigHelper.Shortcut> data = shortcutFile.data;
                    for (KeyConfigHelper.Shortcut sc : data) {
                        List<String> keys = sc.keys;
                        short[] keyCodes = new short[keys.size()];

                        for (int i = 0; i < keys.size(); i++) {
                            String code = keys.get(i);
                            int keycode;

                            if (code.startsWith("0x")) {               // literal hex value
                                keycode = Integer.parseInt(code.substring(2), 16);
                            } else if (code.startsWith("VK_")) {       // symbolic constant in KeyMapper
                                Field field = KeyMapper.class.getDeclaredField(code);
                                keycode = field.getInt(null);
                            } else {                                   // unsupported
                                throw new IllegalArgumentException("Unknown key code: " + code);
                            }
                            keyCodes[i] = (short) keycode;
                        }

                        // Whatever MenuOption looks like in your project
                        MenuOption option = new MenuOption(sc.name, () -> sendKeys(keyCodes));
                        options.add(option);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(game,getString(R.string.wrong_import_format),Toast.LENGTH_SHORT).show();
            }
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(BuildConfig.LAV_DEV ? R.string.v2_menu_send_keys : R.string.game_menu_send_keys),
                options.toArray(new MenuOption[options.size()]));
    }

    private void showAdvancedMenu(GameInputDevice device) {
        List<MenuOption> options = new ArrayList<>();
        if (game.allowChangeMouseMode && BuildConfig.LAV_DEV) {
            // LavArtemis Dev: two touch modes, one entry that switches to the other (the old
            // list's "local mouse cursor" entry went with it, at the user's request)
            String touchSwitch = game.touchModeSwitchLabel();
            if (touchSwitch != null) {
                options.add(new MenuOption(touchSwitch, true, game::switchTouchMode));
            }
        } else if (game.allowChangeMouseMode) {
            options.add(new MenuOption(getString(R.string.game_menu_select_mouse_mode), true, () -> game.selectMouseMode(dialogScreenContext)));
        }
        
        if (!BuildConfig.LAV_DEV) {
            // Dev: « Statistiques de performance » is in the main menu, as on the desktop
            options.add(new MenuOption(getString(R.string.game_menu_toggle_hud), true, game::toggleHUD));
        }
        options.add(new MenuOption(getString(R.string.game_menu_toggle_floating_button), true, game::toggleFloatingButtonVisibility));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard_model), true, game::toggleKeyboardController));
        if (!game.isOnExternalDisplay()) {
            options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_model), true, game::toggleVirtualController));
        }
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_keyboard_model), true, game::toggleFullKeyboard));
        if (!BuildConfig.LAV_DEV) {
            // Dev: both in the main menu's « Envoyer des touches » (Ctrl+Maj+Échap is one of its keys)
            options.add(new MenuOption(getString(R.string.game_menu_task_manager), true, () -> sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE})));

            // **FIXED:** This is a UI navigation action, so it should not use withGameFocus.
            options.add(new MenuOption(getString(R.string.game_menu_send_keys), () -> {
                hideMenu();
                showSpecialKeysMenu();
            }));
        }

        options.add(new MenuOption(getString(R.string.game_menu_switch_touch_sensitivity_model), true, game::switchTouchSensitivity));
        if (device != null) {
            options.addAll(device.getGameMenuOptions());
        }
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));
        showMenuDialog(getString(R.string.game_menu_advanced), options.toArray(new MenuOption[options.size()]));
    }

    private void showServerCmd(ArrayList<String> serverCmds) {
        List<MenuOption> options = new ArrayList<>();

        AtomicInteger index = new AtomicInteger(0);
        for (String str : serverCmds) {
            final int finalI = index.getAndIncrement();
            options.add(new MenuOption("> " + str, true, () -> game.sendExecServerCmd(finalI)));
        };

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(BuildConfig.LAV_DEV ? R.string.v2_menu_server_cmd : R.string.game_menu_server_cmd),
                options.toArray(new MenuOption[options.size()]));
    }

    private void addMicrophoneOption(List<MenuOption> options) {
        if (game.isMicrophoneActive()) {
            options.add(new MenuOption(getString(game.isMicrophoneMuted() ?
                    R.string.game_menu_unmute_microphone : R.string.game_menu_mute_microphone), true,
                    game::toggleMicrophoneMute));
        }
    }

    public void showMenu(GameInputDevice device) {
        if (BuildConfig.LAV_DEV) {
            showTileMenu(device);
            return;
        }
        List<MenuOption> options = new ArrayList<>();

        // LavApollo Link: a game runs inside Playnite -> close it, the stream goes back to Playnite
        String playniteGame = game.getPlayniteGameName();
        if (playniteGame != null) {
            options.add(new MenuOption(game.getResources().getString(R.string.game_menu_quit_playnite_game, playniteGame), game::quitPlayniteGame));
        }

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), game::disconnect));

        options.add(new MenuOption(getString(R.string.game_menu_quit_session), game::quit));

        MenuOption uploadClipboard = new MenuOption(getString(R.string.game_menu_upload_clipboard), true,
                () -> game.sendClipboard(true));
        MenuOption fetchClipboard = new MenuOption(getString(R.string.game_menu_fetch_clipboard), true,
                () -> game.getClipboard(0));
        if (!BuildConfig.LAV_DEV) {
            options.add(uploadClipboard);
            options.add(fetchClipboard);
        }

        // Dev: the entries both clients have come first, in the desktop's order (Commandes du PC
        // hôte, Envoyer des touches, presse-papiers, Statistiques, micro), then Android's own
        options.add(new MenuOption(getString(BuildConfig.LAV_DEV ? R.string.v2_menu_server_cmd : R.string.game_menu_server_cmd), true,
                () -> {
                    ArrayList<String> serverCmds = game.getServerCmds();
                    if (serverCmds.isEmpty() && BuildConfig.LAV_DEV) {
                        V2ListDialog.message(dialogScreenContext, V2Theme.load(game),
                                getString(R.string.game_dialog_title_server_cmd_empty),
                                getString(R.string.game_dialog_message_server_cmd_empty), getString(R.string.v2_ok));
                    } else if (serverCmds.isEmpty()) {
                        int themeResId = game.getApplicationInfo().theme;
                        Context themedContext = new ContextThemeWrapper(dialogScreenContext, themeResId);
                        new AlertDialog.Builder(themedContext)
                                .setTitle(R.string.game_dialog_title_server_cmd_empty)
                                .setMessage(R.string.game_dialog_message_server_cmd_empty)
                                .show();
                    } else {
                        hideMenu();
                        this.showServerCmd(serverCmds);
                    }
                }));

        if (BuildConfig.LAV_DEV) {
            options.add(new MenuOption(getString(R.string.v2_menu_send_keys), () -> {
                hideMenu();
                showSpecialKeysMenu();
            }));
            options.add(uploadClipboard);
            options.add(fetchClipboard);
            options.add(new MenuOption(getString(R.string.v2_menu_stats), true, game::toggleHUD));
            addMicrophoneOption(options);
        }

        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true,
                game::toggleKeyboard));

        options.add(new MenuOption(getString(game.isVoicePttButtonShown() ?
                R.string.game_menu_hide_voice_ptt_button : R.string.game_menu_show_voice_ptt_button), true,
                game::toggleVoicePttButton));

        if (game.isVoicePttButtonShown()) {
            options.add(new MenuOption(getString(R.string.game_menu_move_voice_ptt_button), true,
                    game::startMovingVoicePttButton));
        }

        if (!BuildConfig.LAV_DEV) {
            addMicrophoneOption(options);
        }

        options.add(new MenuOption(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode : R.string.game_menu_enable_zoom_mode), true,
                game::toggleZoomMode));

        // Following the phone's rotation, the phone decides the orientation
        if (dialogScreenContext == game && !game.isFollowingDeviceRotation()) {
            options.add(new MenuOption(getString(R.string.game_menu_rotate_screen), true,
                    game::rotateScreen));
        }

        options.add(new MenuOption(getString(R.string.game_menu_advanced), true,
                () -> showAdvancedMenu(device)));

        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        showMenuDialog(getString(R.string.quick_menu_title), options.toArray(new MenuOption[options.size()]));
    }

    // ---- LavArtemis Dev: the prototype's tile menu (V8, MENU_AN) ---------------------------------

    private android.app.Activity menuActivity() {
        return dialogScreenContext instanceof android.app.Activity ? (android.app.Activity) dialogScreenContext : game;
    }

    /** The game or app the stream shows (prototype playingTitle) */
    private String playingTitle() {
        String pg = game.getPlayniteGameName();
        if (pg != null) {
            return pg;
        }
        String app = game.getStreamAppName();
        if (app == null) {
            return "";
        }
        if (app.equalsIgnoreCase("Desktop")) {
            return getString(R.string.v2_desktop);
        }
        return app.toLowerCase(java.util.Locale.ROOT).contains("playnite") ? getString(R.string.v2_playnite) : app;
    }

    /** Prototype sessionVia */
    private String playingVia() {
        if (game.getPlayniteGameName() != null) {
            return getString(R.string.v2_via_inside);
        }
        String app = game.getStreamAppName();
        if (app != null && app.equalsIgnoreCase("Desktop")) {
            return getString(R.string.v2_via_desktop);
        }
        if (app != null && app.toLowerCase(java.util.Locale.ROOT).contains("playnite")) {
            return getString(R.string.v2_via_playnite);
        }
        return getString(R.string.v2_via_game);
    }

    /**
     * Reprendre, the nine most useful entries, « Plus » (decision of 2026-11-26): clavier, mode
     * tactile, zoom, touches, presse-papiers, commandes du PC hôte, statistiques, Plus, Déconnecter,
     * Quitter. The rest (micro, bouton vocal, rotation, menu avancé, Quitter la session) is in « Plus ».
     */
    private void showTileMenu(GameInputDevice device) {
        V2Theme th = V2Theme.load(game);
        boolean lu = th.isLudique();
        List<com.limelight.v2.V2GameMenu.Tile> tiles = new ArrayList<>();
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_resume), getString(R.string.v2_resume), "", "",
                R.drawable.v2_ic_m_resume, false, 0, this::hideMenu));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.game_menu_toggle_keyboard), getString(R.string.v2_short_keyboard), "", "",
                R.drawable.v2_ic_m_keyboard, false, 1, () -> runWithGameFocus(game::toggleKeyboard)));
        String touch = game.touchModeSwitchLabel();
        boolean toTrackpad = touch != null && touch.toLowerCase(java.util.Locale.ROOT).contains("trackpad");
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(touch, getString(toTrackpad ? R.string.v2_short_trackpad : R.string.v2_short_multitouch), "", "",
                R.drawable.v2_ic_m_touch, false, 1, () -> runWithGameFocus(game::switchTouchMode)));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(game.isZoomModeEnabled() ? R.string.game_menu_disable_zoom_mode : R.string.game_menu_enable_zoom_mode),
                getString(R.string.v2_short_zoom), "", "", R.drawable.v2_ic_m_zoom, false, 1, () -> runWithGameFocus(game::toggleZoomMode)));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_menu_send_keys), getString(R.string.v2_short_keys), "›", "",
                R.drawable.v2_ic_m_keys, false, 1, this::showSpecialKeysMenu));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_menu_clipboard), getString(R.string.v2_short_clip), "›", "",
                R.drawable.v2_ic_m_clip, false, 1, this::showClipboardSheet));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_menu_server_cmd), getString(R.string.v2_short_cmds), "›", "",
                R.drawable.v2_ic_m_cmds, false, 1, () -> {
                    ArrayList<String> serverCmds = game.getServerCmds();
                    if (serverCmds.isEmpty()) {
                        V2ListDialog.message(dialogScreenContext, V2Theme.load(game),
                                getString(R.string.game_dialog_title_server_cmd_empty),
                                getString(R.string.game_dialog_message_server_cmd_empty), getString(R.string.v2_ok));
                    } else {
                        showServerCmd(serverCmds);
                    }
                }));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_menu_stats), getString(R.string.v2_short_stats),
                getString(game.isHudShown() ? R.string.v2_stats_shown : R.string.v2_stats_hidden), "",
                R.drawable.v2_ic_m_stats, false, 1, () -> runWithGameFocus(game::toggleHUD)));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.v2_menu_more), getString(R.string.v2_menu_more), "›", "",
                R.drawable.v2_ic_m_more, false, 1, () -> showMoreSheet(device)));
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(getString(R.string.game_menu_disconnect), getString(R.string.game_menu_disconnect), "", "",
                R.drawable.v2_ic_m_disconnect, false, 7, game::disconnect));
        String pg = game.getPlayniteGameName();
        String quit = game.getResources().getString(R.string.v2_menu_quit, playingTitle());
        tiles.add(new com.limelight.v2.V2GameMenu.Tile(quit, getString(R.string.v2_short_quit), "",
                pg != null ? getString(R.string.v2_menu_quit_playnite_sub) : "", R.drawable.v2_ic_m_quit, false, 8,
                pg != null ? game::quitPlayniteGame : game::quit));
        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = com.limelight.v2.V2GameMenu.show(menuActivity(), th, playingTitle(),
                game.getPcName() == null ? "" : game.getPcName(), playingVia(), tiles, this::hideMenu);
    }

    private com.limelight.v2.V2Sheet.Spec streamSpec(int title) {
        com.limelight.v2.V2Sheet.Spec spec = new com.limelight.v2.V2Sheet.Spec();
        spec.kind = getString(R.string.v2_menu_kind);
        spec.where = game.getPcName() == null ? "" : game.getPcName();
        spec.title = getString(title);
        spec.info = true;
        return spec;
    }

    /** Presse-papiers: send what was copied here, or fetch what was copied on the PC */
    private void showClipboardSheet() {
        com.limelight.v2.V2Sheet.Spec spec = streamSpec(R.string.v2_menu_clipboard);
        spec.note = game.getResources().getString(R.string.v2_clip_note, spec.where);
        spec.option(getString(R.string.v2_clip_send), true, false, () -> runWithGameFocus(() -> game.sendClipboard(true)));
        spec.option(getString(R.string.v2_clip_fetch), false, false, () -> runWithGameFocus(() -> game.getClipboard(0)));
        spec.option(getString(R.string.v2_cancel), false, false, null);
        currentDialog = com.limelight.v2.V2Sheet.show(menuActivity(), V2Theme.load(game), spec);
    }

    /** Plus: what did not make the nine tiles (prototype MORE_AN) */
    private void showMoreSheet(GameInputDevice device) {
        com.limelight.v2.V2Sheet.Spec spec = streamSpec(R.string.v2_menu_more);
        spec.note = playingTitle() + " · " + playingVia();
        boolean first = true;
        if (game.isMicrophoneActive()) {
            spec.option(getString(game.isMicrophoneMuted() ? R.string.game_menu_unmute_microphone : R.string.game_menu_mute_microphone),
                    first, false, () -> runWithGameFocus(game::toggleMicrophoneMute));
            first = false;
        }
        spec.option(getString(game.isVoicePttButtonShown() ? R.string.game_menu_hide_voice_ptt_button : R.string.game_menu_show_voice_ptt_button),
                first, false, () -> runWithGameFocus(game::toggleVoicePttButton));
        if (game.isVoicePttButtonShown()) {
            spec.option(getString(R.string.game_menu_move_voice_ptt_button), false, false, () -> runWithGameFocus(game::startMovingVoicePttButton));
        }
        if (dialogScreenContext == game && !game.isFollowingDeviceRotation()) {
            spec.option(getString(R.string.game_menu_rotate_screen), false, false, () -> runWithGameFocus(game::rotateScreen));
        }
        spec.option(getString(R.string.game_menu_advanced), false, false, () -> showAdvancedMenu(device));
        if (game.getPlayniteGameName() != null) {
            spec.option(getString(R.string.game_menu_quit_session), false, false, game::quit);
        }
        spec.option(getString(R.string.v2_cancel), false, false, null);
        currentDialog = com.limelight.v2.V2Sheet.show(menuActivity(), V2Theme.load(game), spec);
    }

    @Override
    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    @Override
    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }
}
