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

    private void showSpecialKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        // Stable only: the Dev retired « Envoyer des touches » (menu workshop, 2026-11-26)
        if(!PreferenceConfiguration.readPreferences(game).disableDefaultExtraKeys){
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

        showMenuDialog(getString(R.string.game_menu_send_keys),
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
        if (BuildConfig.LAV_DEV) {
            // Prototype « cmds »: the host's commands by name, then « Commande envoyée : … »
            List<MenuOption> cmds = new ArrayList<>();
            for (int i = 0; i < serverCmds.size(); i++) {
                final int id = i;
                final String name = serverCmds.get(i);
                cmds.add(new MenuOption(name, true, () -> {
                    game.sendExecServerCmd(id);
                    com.limelight.v2.ui.V2Toast.show(game, V2Theme.load(game), game.getResources().getString(R.string.v2_cmd_sent, name));
                }));
            }
            showOptionsSheet(R.string.v2_menu_server_cmd, getString(R.string.v2_cmds_note), cmds);
            return;
        }
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

    /*
     * LavArtemis Dev: the tiles of the menu workshop (2026-11-26, StreamMenu.tiles): Reprendre,
     * Déconnecter, Quitter {jeu} (a Playnite game only), Quitter la session, mode tactile, Ouvrir le
     * clavier, Micro, Statistiques, Plus. « Envoyer des touches » is retired.
     */
    /** The tile a sub-dialog was opened from (prototype ret): closing the sub-dialog focuses it again */
    private int subTile = -1;
    private GameInputDevice subDevice;

    private void showTileMenu(GameInputDevice device) {
        showTileMenu(device, 0);
    }

    /** A tile that opens a sub-dialog: Annuler, B or a tap outside comes back to this tile */
    private Runnable sub(int tile, Runnable open) {
        return () -> {
            subTile = tile;
            try {
                open.run();
            } finally {
                subTile = -1;
            }
        };
    }

    /** « Annuler » of a sub-dialog: back to the tile it came from (prototype), else just closes */
    private void addCancel(com.limelight.v2.V2Sheet.Spec spec) {
        final int back = subTile;
        final GameInputDevice device = subDevice;
        spec.option(getString(R.string.v2_cancel), false, false, back >= 0 ? () -> showTileMenu(device, back) : null);
    }

    private void showSubSheet(com.limelight.v2.V2Sheet.Spec spec) {
        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        final int back = subTile;
        final GameInputDevice device = subDevice;
        currentDialog = com.limelight.v2.V2Sheet.show(menuActivity(), V2Theme.load(game), spec);
        if (back >= 0) {
            currentDialog.setOnCancelListener(d -> showTileMenu(device, back));
        }
    }

    private void showTileMenu(GameInputDevice device, int focus) {
        subDevice = device;
        V2Theme th = V2Theme.load(game);
        String touch = game.touchModeSwitchLabel();
        com.limelight.v2.V2GameMenu.State st = new com.limelight.v2.V2GameMenu.State();
        st.title = playingTitle();
        st.playniteGame = game.getPlayniteGameName() != null;
        st.toTrackpad = touch != null && touch.equals(getString(R.string.v2_menu_touch_to_trackpad));
        st.micSent = game.isMicrophoneActive();
        st.micMuted = game.isMicrophoneMuted();
        st.hudShown = game.isHudShown();
        List<com.limelight.v2.V2GameMenu.Tile> tiles = new ArrayList<>();
        for (com.limelight.v2.StreamMenu.Tile kind : com.limelight.v2.StreamMenu.tiles(st.playniteGame, touch != null)) {
            int index = tiles.size();
            Runnable action;
            switch (kind) {
                case RESUME:
                    action = this::hideMenu;
                    break;
                case DISCONNECT:
                    action = game::disconnect;
                    break;
                case QUIT_GAME:
                    action = game::quitPlayniteGame;
                    break;
                case QUIT_SESSION:
                    action = game::quit;
                    break;
                case TOUCH_MODE:
                    action = () -> runWithGameFocus(game::switchTouchMode);
                    break;
                case KEYBOARD:
                    action = () -> runWithGameFocus(game::toggleKeyboard);
                    break;
                case MIC:
                    action = st.micSent ? () -> runWithGameFocus(game::toggleMicrophoneMute)
                            : () -> com.limelight.v2.ui.V2Toast.show(game, V2Theme.load(game), getString(R.string.v2_more_mic_not_sent_toast));
                    break;
                case STATS:
                    action = () -> runWithGameFocus(game::toggleHUD);
                    break;
                case MORE:
                default:
                    action = sub(index, () -> showMoreSheet(device));
                    break;
            }
            tiles.add(com.limelight.v2.V2GameMenu.tile(game, kind, st, action));
        }
        if (currentDialog != null) {
            currentDialog.dismiss();
        }
        currentDialog = com.limelight.v2.V2GameMenu.show(menuActivity(), th, playingTitle(),
                game.getPcName() == null ? "" : game.getPcName(), playingVia(), tiles, this::hideMenu, focus);
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
        addCancel(spec);
        showSubSheet(spec);
    }

    /** « Aucune commande de serveur » (prototype cmds with noCmds): the note and OK */
    private void showNoCommandsSheet() {
        com.limelight.v2.V2Sheet.Spec spec = streamSpec(R.string.v2_cmds_none_title);
        spec.note = getString(R.string.v2_cmds_none_note);
        final int back = subTile;
        final GameInputDevice device = subDevice;
        spec.option(getString(R.string.v2_ok), true, false, back >= 0 ? () -> showTileMenu(device, back) : null);
        showSubSheet(spec);
    }

    /** A list of the menu's options in the prototype's dialog (never a main option), « Annuler » last */
    private void showOptionsSheet(int title, String note, List<MenuOption> options) {
        com.limelight.v2.V2Sheet.Spec spec = streamSpec(title);
        spec.note = note;
        for (MenuOption o : options) {
            if (o.runnable == null) {
                continue;
            }
            spec.option(o.label, false, false, () -> run(o));
        }
        addCancel(spec);
        showSubSheet(spec);
    }

    /** The host's commands, or « Aucune commande » */
    private void showServerCommands() {
        ArrayList<String> serverCmds = game.getServerCmds();
        if (serverCmds.isEmpty()) {
            showNoCommandsSheet();
        } else {
            showServerCmd(serverCmds);
        }
    }

    /**
     * Plus (menu workshop, StreamMenu.more): Presse-papiers, Commandes du PC hôte, Zoom/Panoramique,
     * then what only Android has (bouton vocal, le déplacer, pivoter, contrôleur à l’écran, touches
     * spéciales, clavier virtuel complet, sensibilité, la pastille « Menu », the gamepad's entries),
     * each with its icon. Presse-papiers and Commandes open their dialog; closing it comes back to the
     * menu on « Plus ».
     */
    private void showMoreSheet(GameInputDevice device) {
        final int moreTile = subTile;
        com.limelight.v2.V2Sheet.Spec spec = streamSpec(R.string.v2_menu_more);
        // Prototype: « {jeu} · {via} », Affiche « {jeu} — {VIA} »
        boolean af = V2Theme.load(game).isAffiche();
        spec.note = playingTitle() + (af ? " — " + playingVia().toUpperCase(java.util.Locale.FRENCH) : " · " + playingVia());
        boolean canRotate = dialogScreenContext == game && !game.isFollowingDeviceRotation() && !game.isOnExternalDisplay();
        java.util.List<com.limelight.v2.StreamMenu.More> more = com.limelight.v2.StreamMenu.more(game.isVoicePttButtonShown(),
                canRotate, !game.isOnExternalDisplay());
        for (com.limelight.v2.StreamMenu.More m : more) {
            int icon = com.limelight.v2.V2GameMenu.icon(m);
            switch (m) {
                case CLIPBOARD:
                    spec.option(getString(R.string.v2_menu_clipboard), icon, sub(moreTile, this::showClipboardSheet));
                    break;
                case SERVER_COMMANDS:
                    spec.option(getString(R.string.v2_menu_server_cmd), icon, sub(moreTile, this::showServerCommands));
                    break;
                case ZOOM:
                    spec.option(getString(game.isZoomModeEnabled() ? R.string.v2_menu_zoom_off : R.string.v2_menu_zoom_on),
                            icon, () -> runWithGameFocus(game::toggleZoomMode));
                    break;
                case VOICE:
                    spec.option(getString(game.isVoicePttButtonShown() ? R.string.v2_more_voice_hide : R.string.v2_more_voice_show),
                            icon, () -> runWithGameFocus(game::toggleVoicePttButton));
                    break;
                case VOICE_MOVE:
                    spec.option(getString(R.string.v2_more_voice_move), icon, () -> runWithGameFocus(game::startMovingVoicePttButton));
                    break;
                case ROTATE:
                    spec.option(getString(R.string.v2_more_rotate), icon, () -> runWithGameFocus(game::rotateScreen));
                    break;
                case ON_SCREEN_CONTROLLER:
                    spec.option(getString(R.string.v2_more_osc), icon, () -> runWithGameFocus(game::toggleVirtualController));
                    break;
                case SPECIAL_KEYS:
                    spec.option(getString(R.string.v2_more_special_keys), icon, () -> runWithGameFocus(game::toggleKeyboardController));
                    break;
                case FULL_KEYBOARD:
                    spec.option(getString(R.string.v2_more_full_keyboard), icon, () -> runWithGameFocus(game::toggleFullKeyboard));
                    break;
                case TOUCH_SENSITIVITY:
                    spec.option(getString(R.string.v2_more_sensitivity), icon, () -> runWithGameFocus(game::switchTouchSensitivity));
                    break;
                case MENU_PILL:
                    spec.option(getString(game.isMenuPillWanted() ? R.string.v2_more_pill_hide : R.string.v2_more_pill_show),
                            icon, () -> runWithGameFocus(game::toggleFloatingButtonVisibility));
                    break;
                default:
                    break;
            }
        }
        if (device != null) {
            for (MenuOption o : device.getGameMenuOptions()) {
                if (o.runnable != null) {
                    spec.option(o.label, R.drawable.v2_ic_m_custom, () -> run(o));
                }
            }
        }
        addCancel(spec);
        showSubSheet(spec);
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
