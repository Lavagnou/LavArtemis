package com.limelight.v2;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.preferences.GlPreferences;
import com.limelight.utils.UiHelper;
import com.limelight.utils.UpdateChecker;
import com.limelight.utils.UpdateDialogHelper;
import com.limelight.utils.UpdateDownloader;

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Home of the v2 interface (LavArtemis Dev): one PC at a time, its running session staged at the
 * top, Bureau and Playnite Fullscreen, then the Playnite games played last. PcView redirects here
 * in the Dev build. Behaviour of the desktop home (LavArtemis-Qt gui/v2/HomeView.qml).
 */
public class HomeActivity extends V2HostActivity {
    private FrameLayout container;
    private HomeScreen screen;
    private boolean initialFocusDone;
    /** A newer version was found: the header shows « Mise à jour » */
    private UpdateChecker.UpdateInfo updateInfo;
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = new FrameLayout(this);
        setContentView(container);
        rebuild();
        probeGlRenderer();
        checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // « Mes PC » may have chosen another PC to show
        String last = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_LAST_HOST, null);
        if (last != null && !last.equalsIgnoreCase(currentUuid)) {
            selectHost(last, true);
        }
        UiHelper.showDecoderCrashDialog(this);
        handler.postDelayed(minuteTick, 30_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void rebuild() {
        if (container == null) {
            return;
        }
        screen = new HomeScreen(this, theme, Density.of(getResources().getConfiguration()), new HomeScreen.Actions() {
            @Override
            public void switchHost(int delta) {
                HomeActivity.this.switchHost(delta);
            }

            @Override
            public void pickHost() {
                showHostPicker();
            }

            @Override
            public void openHosts() {
                startActivity(new Intent(HomeActivity.this, HostsActivity.class));
            }

            @Override
            public void openSettings() {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }

            @Override
            public void cycleMode() {
                cycleThemeMode();
                int[] toasts = {R.string.v2_mode_toast_system, R.string.v2_mode_toast_light, R.string.v2_mode_toast_dark};
                toast(toasts[theme.mode]);
            }

            @Override
            public void pickTheme() {
                showThemePicker();
            }

            @Override
            public void launch(NvApp app) {
                HomeActivity.this.launch(app);
            }

            @Override
            public void options(NvApp app) {
                showOptions(app);
            }

            @Override
            public void resumeRunning() {
                HomeActivity.this.resumeRunning();
            }

            @Override
            public void quitRunning() {
                HomeActivity.this.quitRunning();
            }

            @Override
            public void openLibrary() {
                Intent i = new Intent(HomeActivity.this, LibraryActivity.class);
                i.putExtra(EXTRA_PC_UUID, currentUuid);
                startActivity(i);
            }

            @Override
            public void wake() {
                stateCardAction();
            }

            @Override
            public void openUpdate() {
                if (updateInfo != null) {
                    UpdateDialogHelper.showUpdateDialog(HomeActivity.this, updateInfo);
                }
            }

            @Override
            public void hint(String key) {
                View f = getCurrentFocus();
                if ("A".equals(key) && f != null && f.isClickable()) {
                    f.performClick();
                } else if ("Y".equals(key) && f != null) {
                    onV2Options(f);
                }
            }
        }, covers);
        container.removeAllViews();
        container.addView(screen.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        initialFocusDone = false;
        refresh();
    }

    @Override
    protected void onCoversChanged() {
        if (screen != null) {
            screen.setCoverSource(covers);
        }
    }

    @Override
    protected void refresh() {
        if (screen == null) {
            return;
        }
        HomeScreen.State s = new HomeScreen.State();
        s.hosts = safeHosts();
        s.hostIndex = HostList.indexOf(s.hosts, currentUuid);
        ComputerDetails c = currentComputer();
        s.model = currentModel();
        s.updateAvailable = updateInfo != null;
        if (c != null && s.model != null && HostList.isOnline(c)) {
            long since = SessionClock.observe(this, c.uuid, s.model.runningId, System.currentTimeMillis());
            // LavApollo says when the running app started; the device's own clock is for older hosts
            if (s.model.runningId != 0 && c.sessionStartedAt > 0) {
                since = c.sessionStartedAt;
            }
            s.sessionMinutes = SessionClock.minutes(since, System.currentTimeMillis());
        }
        screen.bind(s);
        if (!initialFocusDone && s.model != null && c != null && HostList.isOnline(c)
                && !container.isInTouchMode()) {
            initialFocusDone = screen.requestInitialFocus();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (screen != null) {
            screen.setDialogOpen(!hasFocus);
        }
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
    }

    @Override
    protected boolean onV2Options(View focused) {
        if (screen != null && screen.isThemeButton(focused)) {
            showThemePicker();
            return true;
        }
        NvApp app = screen == null ? null : screen.appFor(focused);
        if (app != null) {
            showOptions(app);
            return true;
        }
        return false;
    }

    @Override
    protected List<List<View>> focusRows() {
        return screen == null ? null : screen.focusRows();
    }

    @Override
    protected void onGridFocus(View target) {
        if (screen != null) {
            screen.onGridFocus(target);
        }
    }

    /** The PC state card (prototype cardAct): add a PC, wait, open Mes PC or wake the PC */
    private void stateCardAction() {
        HomeScreen.CardState cs = screen == null ? null : screen.getCardState();
        ComputerDetails c = currentComputer();
        if (cs == HomeScreen.CardState.FIRSTRUN || cs == HomeScreen.CardState.OFFLINE_NO_MAC) {
            startActivity(new Intent(this, HostsActivity.class));
        } else if (cs == HomeScreen.CardState.SEARCHING || cs == HomeScreen.CardState.LOADING) {
            if (c != null) {
                toast(getString(R.string.v2_pc_searching, c.name));
            }
        } else {
            wake();
        }
    }

    @Override
    protected boolean shoulderKeysEnabled() {
        return true;
    }

    @Override
    protected boolean onV2Shoulder(int delta) {
        switchHost(delta);
        return true;
    }

    @Override
    protected boolean onV2InitialFocus() {
        return screen != null && screen.requestInitialFocus();
    }

    @Override
    protected void onV2Back() {
        // The home is the root of the app: leave it as the launcher's back would
        finish();
    }

    /**
     * Prototype themePick (V8-58): long press on the light / dark button. « ✓ » in front of the
     * current theme, which has the focus; Réglages › Apparence has them too.
     */
    private void showThemePicker() {
        String[] ids = {V2Theme.BRAISE, V2Theme.AFFICHE, V2Theme.LUDIQUE};
        String[] names = {getString(R.string.v2_theme_braise), getString(R.string.v2_theme_affiche), getString(R.string.v2_theme_ludique)};
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.title = getString(R.string.v2_theme);
        spec.kind = getString(R.string.v2_pick_theme_kind);
        spec.info = true;
        spec.note = getString(R.string.v2_pick_theme_note);
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            final String name = names[i];
            boolean current = id.equals(theme.id);
            if (current) {
                spec.focus = i;
            }
            spec.option(Pickers.checked(name, current), false, false, () -> {
                V2Theme.saveTheme(this, id);
                theme = V2Theme.load(this);
                applyWindowColors();
                rebuild();
                toast(getString(R.string.v2_pick_theme_toast, name));
            });
        }
        spec.option(getString(R.string.v2_cancel), false, false, null);
        V2Sheet.show(this, theme, spec);
    }

    /**
     * Prototype hostPick (V8-59): tap on the PC pill. Every paired PC with its state (« ✓ » and the
     * focus on the shown one), « Mes PC… », Annuler.
     */
    private void showHostPicker() {
        final List<ComputerDetails> hosts = safeHosts();
        if (hosts.isEmpty()) {
            startActivity(new Intent(this, HostsActivity.class));
            return;
        }
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.title = getString(R.string.v2_pick_pc_title);
        spec.kind = getString(R.string.v2_pick_pc_kind);
        spec.info = true;
        spec.note = getString(R.string.v2_pick_pc_note);
        for (int i = 0; i < hosts.size(); i++) {
            final ComputerDetails d = hosts.get(i);
            boolean current = d.uuid.equalsIgnoreCase(currentUuid);
            if (current) {
                spec.focus = i;
            }
            boolean online = HostList.isOnline(d);
            String status = getString(online && d.runningGameId != 0 ? R.string.v2_status_session
                    : online ? R.string.v2_status_online : R.string.v2_status_offline);
            spec.option(Pickers.checked(d.name + " · " + status, current), false, false, () -> selectHost(d.uuid, true));
        }
        spec.option(getString(R.string.v2_pick_pc_hosts), false, false,
                () -> startActivity(new Intent(this, HostsActivity.class)));
        spec.option(getString(R.string.v2_cancel), false, false, null);
        V2Sheet.show(this, theme, spec);
    }

    // ---- what PcView did at startup -----------------------------------------------------------

    /**
     * The decoder tuning reads the GL renderer string (Adreno version...), which PcView probes once
     * per device fingerprint. The Dev build skips PcView, so the home probes it the same way.
     */
    private void probeGlRenderer() {
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (glPrefs.savedFingerprint.equals(Build.FINGERPRINT) && !glPrefs.glRenderer.isEmpty()) {
            return;
        }
        final GLSurfaceView probe = new GLSurfaceView(this);
        probe.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                glPrefs.savedFingerprint = Build.FINGERPRINT;
                glPrefs.writePreferences();
                LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);
                runOnUiThread(() -> {
                    if (probe.getParent() instanceof ViewGroup) {
                        ((ViewGroup) probe.getParent()).removeView(probe);
                    }
                });
            }

            @Override
            public void onSurfaceChanged(GL10 gl10, int w, int h) {
            }

            @Override
            public void onDrawFrame(GL10 gl10) {
            }
        });
        // A 1 px surface behind the page, removed as soon as the string is read
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        ((ViewGroup) getWindow().getDecorView()).addView(probe, 0, lp);
    }

    private void checkForUpdates() {
        UpdateDownloader.cleanupOldDownloads(this);
        UpdateDialogHelper.offerDownloadedUpdateOr(this, this::checkForUpdatesOnline);
    }

    private void checkForUpdatesOnline() {
        if (isFinishing() || !UpdateChecker.shouldAutoCheck(this)) {
            return;
        }
        UpdateChecker.recordAutoCheck(this, true);
        UpdateChecker.check(this, new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                updateInfo = info;
                refresh();
                if (!UpdateChecker.isSkipped(HomeActivity.this, info.version)) {
                    UpdateDialogHelper.showUpdateDialog(HomeActivity.this, info);
                }
            }

            @Override
            public void onUpToDate() {
            }

            @Override
            public void onError(Exception e) {
                UpdateChecker.recordAutoCheck(HomeActivity.this, false);
            }
        });
    }
}
