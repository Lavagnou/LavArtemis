package com.limelight.v2;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.ServerHelper;
import com.limelight.v2.ui.V2Dialogs;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the home and the library share: the binding to ComputerManagerService (as PcView /
 * AppView), the shown PC and its app list, and the actions on an app (launch, quit, relaunch,
 * hide), with the same rules as the desktop home (HomeModel.decide()).
 */
public abstract class V2HostActivity extends V2Activity {
    public static final String PREF_LAST_HOST = "v2_last_host";
    public static final String EXTRA_PC_UUID = "v2PcUuid";

    protected ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean bound;
    private boolean inForeground;
    private boolean polling;
    private boolean freezeUpdates;

    /** Every PC the service reported, by uuid (the service's live objects) */
    protected final Map<String, ComputerDetails> computers = new LinkedHashMap<>();
    protected String currentUuid;
    private ComputerManagerService.ApplistPoller poller;
    private String pollerUuid;
    private String lastRawAppList;
    protected List<NvApp> apps;
    protected Set<Integer> hidden;
    protected boolean showHidden;
    protected CoverLoader covers;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder = (ComputerManagerService.ComputerManagerBinder) binder;
            if (localBinder == null) {
                return;
            }
            new Thread(() -> {
                localBinder.waitForReady();
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    managerBinder = localBinder;
                    startUpdates();
                });
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inForeground = true;
        String fromIntent = getIntent().getStringExtra(EXTRA_PC_UUID);
        currentUuid = fromIntent != null ? fromIntent
                : PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_LAST_HOST, null);
        hidden = HiddenApps.read(this, currentUuid);
        loadCachedAppList(currentUuid);
        bound = bindService(new Intent(this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        inForeground = true;
        hidden = HiddenApps.read(this, currentUuid);
        startUpdates();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;
        stopUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (covers != null) {
            covers.close();
            covers = null;
        }
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
    }

    protected void startUpdates() {
        if (managerBinder == null || !inForeground) {
            return;
        }
        if (!polling) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        runOnUiThread(() -> onComputerUpdated(details));
                    }
                }
            });
            polling = true;
        }
        ensurePoller();
    }

    protected void stopUpdates() {
        if (poller != null) {
            poller.stop();
            poller = null;
            pollerUuid = null;
        }
        if (managerBinder != null && polling) {
            freezeUpdates = true;
            managerBinder.stopPolling();
            polling = false;
        }
    }

    /** The app list poller follows the shown PC */
    private void ensurePoller() {
        if (managerBinder == null || !inForeground) {
            return;
        }
        ComputerDetails c = currentComputer();
        String uuid = c == null ? null : c.uuid;
        if (uuid != null && uuid.equals(pollerUuid)) {
            return;
        }
        if (poller != null) {
            poller.stop();
            poller = null;
            pollerUuid = null;
        }
        if (c != null) {
            poller = managerBinder.createAppListPoller(c);
            poller.start();
            pollerUuid = uuid;
        }
    }

    private void onComputerUpdated(ComputerDetails details) {
        if (details.uuid == null) {
            return;
        }
        computers.put(details.uuid, details);
        List<ComputerDetails> hosts = pairedHosts();
        if (currentComputer() == null) {
            // The remembered PC may simply not be reported yet: ask the service before giving up
            ComputerDetails known = currentUuid != null && managerBinder != null ? managerBinder.getComputer(currentUuid) : null;
            if (known != null && HostList.isPaired(known)) {
                computers.put(known.uuid, known);
            } else if (!hosts.isEmpty() && (currentUuid == null || !containsUuid(hosts, currentUuid))) {
                // Nothing chosen yet, or the remembered PC is gone: the first PC of the list
                selectHost(hosts.get(0).uuid, false);
            }
        }
        if (details.uuid.equalsIgnoreCase(currentUuid)) {
            if (details.rawAppList != null && !details.rawAppList.equals(lastRawAppList)) {
                parseAppList(details.rawAppList);
            }
            if (covers == null && managerBinder != null) {
                covers = new CoverLoader(this, details, managerBinder.getUniqueId());
                onCoversChanged();
            }
            ensurePoller();
        }
        onComputerSeen(details);
        refresh();
    }

    /** Every report of the service, paired or not, before refresh() (Mes PC: pairing links) */
    protected void onComputerSeen(ComputerDetails details) {
    }

    /** Forgets a PC the page still shows, before the next refresh */
    protected void dropComputer(String uuid) {
        computers.remove(uuid);
    }

    private static boolean containsUuid(List<ComputerDetails> hosts, String uuid) {
        for (ComputerDetails d : hosts) {
            if (uuid.equalsIgnoreCase(d.uuid)) {
                return true;
            }
        }
        return false;
    }

    private void parseAppList(String raw) {
        try {
            apps = new ArrayList<>(NvHTTP.getAppListByReader(new StringReader(raw)));
            lastRawAppList = raw;
        } catch (Exception e) {
            LimeLog.warning("v2: app list unreadable: " + e.getMessage());
        }
    }

    /** The app list cached by the legacy poller, shown at once while the PC answers */
    private void loadCachedAppList(String uuid) {
        apps = null;
        lastRawAppList = null;
        if (uuid == null) {
            return;
        }
        try {
            String raw = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuid));
            parseAppList(raw);
        } catch (IOException ignored) {
        }
    }

    protected List<ComputerDetails> pairedHosts() {
        return HostList.paired(computers.values());
    }

    protected ComputerDetails currentComputer() {
        if (currentUuid == null) {
            return null;
        }
        for (ComputerDetails d : computers.values()) {
            if (currentUuid.equalsIgnoreCase(d.uuid) && HostList.isPaired(d)) {
                return d;
            }
        }
        return null;
    }

    protected void selectHost(String uuid, boolean refreshNow) {
        if (uuid == null || uuid.equalsIgnoreCase(currentUuid) && apps != null) {
            return;
        }
        currentUuid = uuid;
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_LAST_HOST, uuid).apply();
        hidden = HiddenApps.read(this, uuid);
        showHidden = false;
        if (covers != null) {
            covers.close();
            covers = null;
        }
        ComputerDetails c = currentComputer();
        if (c != null && managerBinder != null) {
            covers = new CoverLoader(this, c, managerBinder.getUniqueId());
        }
        onCoversChanged();
        loadCachedAppList(uuid);
        if (c != null && c.rawAppList != null) {
            parseAppList(c.rawAppList);
        }
        ensurePoller();
        if (refreshNow) {
            refresh();
        }
    }

    /** LB / RB: previous or next paired PC */
    protected void switchHost(int delta) {
        List<ComputerDetails> hosts = pairedHosts();
        if (hosts.size() < 2) {
            return;
        }
        int pos = HostList.indexOf(hosts, currentUuid);
        selectHost(hosts.get(HostList.step(pos, delta, hosts.size())).uuid, true);
    }

    protected HomeModel currentModel() {
        ComputerDetails c = currentComputer();
        if (c == null || apps == null) {
            return null;
        }
        return HomeModel.build(apps, c.runningGameId, c.playniteGameName, c.playniteHostRunning, hidden, showHidden);
    }

    /** Recompute what is shown */
    protected abstract void refresh();

    /** The cover source changed (new PC) */
    protected abstract void onCoversChanged();

    protected void pollNow() {
        if (poller != null) {
            poller.pollNow();
        }
    }

    // ---- actions ---------------------------------------------------------------------------

    /** Tap on an app: launch, join, start inside Playnite, or ask before quitting what runs */
    protected void launch(final NvApp app) {
        final ComputerDetails c = currentComputer();
        final HomeModel m = currentModel();
        if (c == null || m == null || managerBinder == null) {
            return;
        }
        if (!HostSupport.isSupported(c)) {
            // As the desktop: a message instead of a launch that fails half-way
            V2Dialogs.message(this, theme, getString(R.string.v2_oops),
                    getString(R.string.v2_host_unsupported, c.name, c.serverAppVersion), getString(R.string.v2_ok));
            return;
        }
        final PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        switch (m.decide(app)) {
            case RESUME:
            case INSIDE_PLAYNITE:
                ServerHelper.doStart(this, app, c, managerBinder, prefs.useVirtualDisplay);
                break;
            case ASK_QUIT: {
                String running = runningLabel(m);
                V2Dialogs.confirm(this, theme, null,
                        getString(R.string.v2_quit_to_launch, running, labelOf(app)),
                        getString(R.string.v2_yes), getString(R.string.v2_no),
                        () -> startWithDisplayGate(app, c, prefs), null);
                break;
            }
            default:
                startWithDisplayGate(app, c, prefs);
                break;
        }
    }

    /** As AppView: a virtual display the PC cannot give yet is confirmed first */
    private void startWithDisplayGate(NvApp app, ComputerDetails c, PreferenceConfiguration prefs) {
        if (managerBinder == null) {
            return;
        }
        if (prefs.useVirtualDisplay && !(c.vDisplaySupported && c.vDisplayDriverReady)) {
            V2Dialogs.confirm(this, theme, null,
                    getString(c.vDisplaySupported ? R.string.v2_vdisplay_not_ready : R.string.v2_vdisplay_not_supported),
                    getString(R.string.v2_continue), getString(R.string.v2_cancel),
                    () -> {
                        if (managerBinder != null) {
                            ServerHelper.doStart(this, app, c, managerBinder, true);
                        }
                    }, null);
        } else {
            ServerHelper.doStart(this, app, c, managerBinder, prefs.useVirtualDisplay);
        }
    }

    protected void resumeRunning() {
        HomeModel m = currentModel();
        if (m == null || !m.sessionRunning()) {
            return;
        }
        NvApp app = m.runningApp();
        launch(app != null ? app : new NvApp("app", null, m.runningId, false));
    }

    protected void quitRunning() {
        final ComputerDetails c = currentComputer();
        final HomeModel m = currentModel();
        if (c == null || m == null || !m.sessionRunning()) {
            return;
        }
        V2Dialogs.confirm(this, theme, null, getString(R.string.v2_quit_confirm, runningLabel(m)),
                getString(R.string.v2_quit), getString(R.string.v2_cancel),
                () -> doQuit(c, m, null), null);
    }

    /** Quits then starts the same app again, so the host recreates its display for this device */
    protected void relaunch(final NvApp app) {
        final ComputerDetails c = currentComputer();
        final HomeModel m = currentModel();
        if (c == null || m == null) {
            return;
        }
        V2Dialogs.confirm(this, theme, null, getString(R.string.v2_relaunch_confirm, runningLabel(m)),
                getString(R.string.v2_relaunch), getString(R.string.v2_cancel),
                () -> doQuit(c, m, () -> runOnUiThread(() -> {
                    if (!isFinishing()) {
                        startWithDisplayGate(app, c, PreferenceConfiguration.readPreferences(this));
                    }
                })), null);
    }

    private void doQuit(ComputerDetails c, HomeModel m, Runnable then) {
        if (managerBinder == null) {
            return;
        }
        NvApp running = m.runningApp();
        ServerHelper.doQuit(this, c, running != null ? running : new NvApp("app", null, m.runningId, false), managerBinder, () -> {
            pollNow();
            if (then != null) {
                then.run();
            }
        });
    }

    protected void wake() {
        final ComputerDetails c = currentComputer();
        if (c == null) {
            return;
        }
        if (c.macAddress == null) {
            // The v2 messages of Mes PC, which are the desktop's
            Toast.makeText(this, getString(R.string.v2_wake_no_mac, c.name), Toast.LENGTH_LONG).show();
            return;
        }
        new Thread(() -> {
            String message;
            try {
                WakeOnLanSender.sendWolPacket(c);
                message = getString(R.string.v2_wake_sent, c.name);
            } catch (IOException e) {
                message = getString(R.string.v2_wake_failed, c.name);
            }
            final String toast = message;
            runOnUiThread(() -> Toast.makeText(this, toast, Toast.LENGTH_LONG).show());
        }).start();
    }

    protected void setHidden(NvApp app, boolean hide) {
        if (currentUuid == null) {
            return;
        }
        hidden = HiddenApps.setHidden(this, currentUuid, app.getAppId(), hide);
        refresh();
    }

    /** The options menu (long press, "…", Y, Menu) */
    protected void showOptions(final NvApp app) {
        HomeModel m = currentModel();
        if (app == null || m == null) {
            return;
        }
        boolean running = m.isRunning(app) && app.getAppId() == m.runningId;
        List<V2Dialogs.Entry> entries = new ArrayList<>();
        entries.add(new V2Dialogs.Entry(getString(running ? R.string.v2_resume : R.string.v2_launch),
                R.drawable.v2_ic_play, true, () -> launch(app)));
        if (running) {
            entries.add(new V2Dialogs.Entry(getString(R.string.v2_quit), R.drawable.v2_ic_stop, false, this::quitRunning));
            entries.add(new V2Dialogs.Entry(getString(R.string.v2_relaunch), R.drawable.v2_ic_refresh, false, () -> relaunch(app)));
        }
        if (HomeModel.kindOf(app) == HomeModel.Kind.GAME) {
            boolean isHidden = m.isHidden(app);
            entries.add(new V2Dialogs.Entry(getString(isHidden ? R.string.v2_unhide : R.string.v2_hide),
                    isHidden ? R.drawable.v2_ic_eye : R.drawable.v2_ic_eye_off, false, () -> setHidden(app, !isHidden)));
        }
        Bitmap cover = covers != null ? covers.peek(app) : null;
        V2Dialogs.showMenu(this, theme, labelOf(app), cover, entries);
    }

    /** "Desktop" is shown as Bureau, Playnite's own app as Playnite Fullscreen */
    protected String labelOf(NvApp app) {
        switch (HomeModel.kindOf(app)) {
            case DESKTOP:
                return getString(R.string.v2_desktop);
            case PLAYNITE:
                return getString(R.string.v2_playnite);
            default:
                return app.getAppName();
        }
    }

    private String runningLabel(HomeModel m) {
        String title = m.sceneTitle(getString(R.string.v2_desktop), getString(R.string.v2_playnite));
        return title.isEmpty() ? getString(R.string.v2_live) : title;
    }

    protected List<ComputerDetails> safeHosts() {
        List<ComputerDetails> h = pairedHosts();
        return h == null ? Collections.<ComputerDetails>emptyList() : h;
    }
}
