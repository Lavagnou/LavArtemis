package com.limelight.v2;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.StreamSettings;
import com.limelight.utils.UiHelper;
import com.limelight.utils.UpdateChecker;
import com.limelight.utils.UpdateDialogHelper;
import com.limelight.utils.UpdateDownloader;
import com.limelight.v2.ui.V2Dialogs;

import java.util.ArrayList;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
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
                startActivity(new Intent(HomeActivity.this, StreamSettings.class));
            }

            @Override
            public void cycleMode() {
                cycleThemeMode();
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
                HomeActivity.this.wake();
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
        if (c != null && s.model != null && HostList.isOnline(c)) {
            long since = SessionClock.observe(this, c.uuid, s.model.runningId, System.currentTimeMillis());
            s.sessionMinutes = SessionClock.minutes(since, System.currentTimeMillis());
        }
        screen.bind(s);
        if (!initialFocusDone && s.model != null && c != null && HostList.isOnline(c)
                && !container.isInTouchMode()) {
            initialFocusDone = screen.requestInitialFocus();
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

    /** Until Réglages v2 (phase 2) has its theme page: Braise, Affiche or Ludique from the home */
    private void showThemePicker() {
        String[] ids = {V2Theme.BRAISE, V2Theme.AFFICHE, V2Theme.LUDIQUE};
        String[] names = {getString(R.string.v2_theme_braise), getString(R.string.v2_theme_affiche), getString(R.string.v2_theme_ludique)};
        List<V2Dialogs.Entry> entries = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            entries.add(new V2Dialogs.Entry(names[i], id.equals(theme.id) ? R.drawable.v2_ic_check : 0, id.equals(theme.id), () -> {
                V2Theme.saveTheme(this, id);
                theme = V2Theme.load(this);
                applyWindowColors();
                rebuild();
            }));
        }
        V2Dialogs.showMenu(this, theme, getString(R.string.v2_theme), null, entries);
    }

    private void showHostPicker() {
        final List<ComputerDetails> hosts = safeHosts();
        if (hosts.size() < 2) {
            startActivity(new Intent(this, HostsActivity.class));
            return;
        }
        List<V2Dialogs.Entry> entries = new ArrayList<>();
        for (final ComputerDetails d : hosts) {
            boolean current = d.uuid.equalsIgnoreCase(currentUuid);
            String status = getString(HostList.isOnline(d) ? R.string.v2_status_online : R.string.v2_status_offline);
            entries.add(new V2Dialogs.Entry(d.name + "  ·  " + status, R.drawable.v2_ic_monitor, current,
                    () -> selectHost(d.uuid, true)));
        }
        V2Dialogs.showMenu(this, theme, getString(R.string.v2_pick_pc), null, entries);
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
