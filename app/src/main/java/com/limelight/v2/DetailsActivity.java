package com.limelight.v2;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * « Afficher les détails » of a game or an app (prototype « launch », V8-67 / V8-68): what the launch
 * will do and « Réglages de ce lancement ». The rows change the real settings (the active profile,
 * the virtual display, the codec, the bitrate), as the desktop's DetailsView: the app has no
 * per-launch settings. B / Retour goes back to the screen it came from.
 */
public class DetailsActivity extends V2HostActivity {
    public static final String EXTRA_APP_ID = "v2AppId";

    private FrameLayout container;
    private DetailsScreen screen;
    private int appId;
    private boolean initialFocusDone;
    /** A launch left for the stream: coming back, the details are closed (the prototype goes home) */
    private boolean launched;

    public static void open(Context ctx, String pcUuid, NvApp app) {
        Intent i = new Intent(ctx, DetailsActivity.class);
        i.putExtra(EXTRA_PC_UUID, pcUuid);
        i.putExtra(EXTRA_APP_ID, app.getAppId());
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appId = getIntent().getIntExtra(EXTRA_APP_ID, 0);
        container = new FrameLayout(this);
        setContentView(container);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launched) {
            finish();
        }
    }

    @Override
    protected void rebuild() {
        if (container == null) {
            return;
        }
        Configuration cfg = getResources().getConfiguration();
        screen = new DetailsScreen(this, theme, HomeLayout.forSize(cfg.screenWidthDp, cfg.screenHeightDp), new DetailsScreen.Actions() {
            @Override
            public void back() {
                finish();
            }

            @Override
            public void change(int row, int d) {
                DetailsActivity.this.change(row, d);
            }

            @Override
            public void go() {
                NvApp app = target();
                if (app != null) {
                    launched = true;
                    startNow(app);
                }
            }
        });
        container.removeAllViews();
        container.addView(screen.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        initialFocusDone = false;
        refresh();
    }

    private NvApp target() {
        if (apps == null) {
            return null;
        }
        for (NvApp a : apps) {
            if (a.getAppId() == appId) {
                return a;
            }
        }
        return null;
    }

    @Override
    protected void onCoversChanged() {
        refresh();
    }

    @Override
    protected void refresh() {
        if (screen == null) {
            return;
        }
        NvApp app = target();
        ComputerDetails c = currentComputer();
        HomeModel m = currentModel();
        if (app == null || c == null || m == null) {
            return;
        }
        DetailsScreen.State s = state(this, app, c, m, theme.isAffiche(), new SettingsStore(this));
        if (covers != null) {
            s.cover = covers.peek(app);
            s.background = covers.peekBackground(app);
        }
        s.blurBackground = s.background == null;
        screen.bind(s);
        if (covers != null) {
            if (covers.peek(app) == null) {
                covers.load(app, (id, bmp) -> {
                    if (bmp != null) {
                        refresh();
                    }
                });
            }
            if (app.hasPlayniteBackground() && covers.peekBackground(app) == null) {
                covers.loadBackground(app, (id, bmp) -> {
                    if (bmp != null) {
                        refresh();
                    }
                });
            }
        }
        if (!initialFocusDone && !container.isInTouchMode()) {
            container.post(() -> initialFocusDone = screen.requestInitialFocus());
        }
    }

    /** What the screen shows (prototype launch view-model) */
    static DetailsScreen.State state(Context ctx, NvApp app, ComputerDetails c, HomeModel m, boolean affiche, SettingsStore store) {
        DetailsScreen.State s = new DetailsScreen.State();
        boolean game = HomeModel.kindOf(app) == HomeModel.Kind.GAME;
        String kind = ctx.getString(game ? R.string.v2_kind_game : R.string.v2_kind_app);
        s.kind = affiche ? kind.toUpperCase(Locale.FRENCH) : kind;
        s.where = c.name;
        s.title = titleOf(ctx, app);
        String sep = affiche ? " — " : " · ";
        if (game) {
            s.sub = HomeParts.hours(ctx, app) + sep + ctx.getString(app.isPlayniteInstalled()
                    ? R.string.v2_details_installed : R.string.v2_details_not_installed);
        } else {
            s.sub = ctx.getString(HomeModel.kindOf(app) == HomeModel.Kind.DESKTOP ? R.string.v2_desktop_subtitle
                    : HomeModel.kindOf(app) == HomeModel.Kind.PLAYNITE ? R.string.v2_playnite_subtitle : R.string.v2_kind_app);
        }
        LaunchRows.Note note = LaunchRows.note(m, app);
        switch (note) {
            case RUNNING:
                // Choice 23: Rejoindre when another device streams this session
                boolean other = otherDeviceConnected(c);
                s.note = ctx.getString(other ? R.string.v2_note_running_join : R.string.v2_note_running_resume, s.title, c.name);
                s.goLabel = ctx.getString(other ? R.string.v2_join : R.string.v2_resume);
                break;
            case INSIDE:
                s.note = m.playniteGame.isEmpty() ? ctx.getString(R.string.v2_note_inside_alone, s.title)
                        : ctx.getString(R.string.v2_note_inside, m.playniteGame, s.title);
                s.goLabel = ctx.getString(R.string.v2_play_inside);
                break;
            case CLOSES:
                s.note = m.runningKind() == HomeModel.Kind.PLAYNITE && !m.playniteGame.isEmpty()
                        ? ctx.getString(R.string.v2_note_closes_two, m.playniteGame, c.name)
                        : ctx.getString(R.string.v2_note_closes_one, sessionNameOf(ctx, m), c.name);
                s.goLabel = ctx.getString(game ? R.string.v2_play : R.string.v2_launch);
                break;
            case GAME:
                s.note = ctx.getString(R.string.v2_note_game_idle, c.name);
                s.goLabel = ctx.getString(R.string.v2_play);
                break;
            default:
                s.note = ctx.getString(R.string.v2_note_app_idle, c.name);
                s.goLabel = ctx.getString(R.string.v2_launch);
                break;
        }
        if (game && !app.isPlayniteInstalled()) {
            s.note += " " + ctx.getString(R.string.v2_note_not_installed);
        }
        s.warn = LaunchRows.warns(m, app);
        s.values[0] = profileName(ctx);
        boolean pcScreen = LaunchRows.joining(m, app) || !store.getBool("checkbox_use_virtual_display");
        s.values[1] = pcScreen ? ctx.getString(R.string.v2_details_display_pc)
                : ctx.getString(R.string.v2_details_display_device, deviceMode(store));
        s.values[2] = codecName(ctx, store.getString("video_format"));
        s.values[3] = ctx.getString(R.string.v2_details_rate, Math.round(store.getInt(SettingsCatalog.KEY_BITRATE) / 1000f));
        return s;
    }

    static String titleOf(Context ctx, NvApp app) {
        switch (HomeModel.kindOf(app)) {
            case DESKTOP:
                return ctx.getString(R.string.v2_desktop);
            case PLAYNITE:
                return ctx.getString(R.string.v2_playnite);
            default:
                return app.getAppName();
        }
    }

    private static String sessionNameOf(Context ctx, HomeModel m) {
        HomeModel.Kind k = m.runningKind();
        if (k == HomeModel.Kind.DESKTOP) {
            return ctx.getString(R.string.v2_session_desktop);
        }
        if (k == HomeModel.Kind.PLAYNITE) {
            return ctx.getString(R.string.v2_playnite);
        }
        NvApp r = m.runningApp();
        return r != null ? r.getAppName() : ctx.getString(R.string.v2_live);
    }

    /** "1920 × 1080 · 120 Hz": the stream size set in the settings (prototype DISPLAYS) */
    static String deviceMode(SettingsStore store) {
        String res = store.getString("list_resolution").replace("x", " × ");
        return res + " · " + store.getString("list_fps") + " Hz";
    }

    static String codecName(Context ctx, String value) {
        switch (value) {
            case "forceav1":
                return "AV1";
            case "forceh265":
                return "HEVC";
            case "neverh265":
                return "H.264";
            case "forcepyrowave":
                return "PyroWave";
            default:
                return ctx.getString(R.string.v2_details_codec_auto);
        }
    }

    private static String profileName(Context ctx) {
        SettingsProfile p = ProfilesManager.getInstance().getActive();
        return p == null ? ctx.getString(R.string.v2_set_profile_none) : p.getName();
    }

    private void change(int row, int d) {
        NvApp app = target();
        HomeModel m = currentModel();
        if (app == null || m == null) {
            return;
        }
        SettingsStore store = new SettingsStore(this);
        switch (row) {
            case 0: {
                // Profiles: "" (global settings) then each saved profile, wrapping
                List<String> ids = new ArrayList<>();
                ids.add("");
                for (SettingsProfile p : ProfilesManager.getInstance().getProfiles()) {
                    ids.add(p.getUuid().toString());
                }
                SettingsProfile active = ProfilesManager.getInstance().getActive();
                int cur = Math.max(0, ids.indexOf(active == null ? "" : active.getUuid().toString()));
                store.setActiveProfile(ids.get(LaunchRows.wrap(cur, ids.size(), d)));
                break;
            }
            case 1:
                if (LaunchRows.joining(m, app)) {
                    toast(R.string.v2_details_display_locked);
                    return;
                }
                store.putBool("checkbox_use_virtual_display", !store.getBool("checkbox_use_virtual_display"));
                break;
            case 2: {
                SettingDef codec = SettingsCatalog.find("video_format");
                if (codec != null) {
                    store.setChoice(codec, LaunchRows.nextCodec(store.getString("video_format"), d));
                }
                break;
            }
            case 3:
                store.putInt(SettingsCatalog.KEY_BITRATE, LaunchRows.nextRate(store.getInt(SettingsCatalog.KEY_BITRATE), d));
                break;
            default:
                return;
        }
        refresh();
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
    }

    @Override
    protected List<List<View>> focusRows() {
        return screen == null ? null : screen.focusRows();
    }

    @Override
    protected void onGridFocus(View target) {
        if (screen != null) {
            screen.onFocusMoved();
        }
    }

    @Override
    protected boolean onV2Horizontal(View focused, int dx) {
        int row = screen == null || focused == null ? -1 : screen.rowOf(focused);
        if (row < 0) {
            return false;
        }
        change(row, dx);
        return true;
    }

    @Override
    protected boolean onV2InitialFocus() {
        return screen != null && screen.requestInitialFocus();
    }
}
