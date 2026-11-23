package com.limelight.v2;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.limelight.AppView;
import com.limelight.BenchmarkActivity;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.v2.ui.V2Dialogs;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * « Mes PC » of the v2 interface (LavArtemis-Qt gui/v2/HostsView.qml): the paired PCs, « Ajouter
 * un PC » (found on the network or by address), PIN pairing, and per PC Ouvrir / Benchmark /
 * Réveiller / Renommer / Oublier. The actions are PcView's, with the v2 dialogs. Also takes the
 * pairing links (art://host?pin=…&passphrase=…) that PcView hands over in the Dev build.
 */
public class HostsActivity extends V2HostActivity {
    /** Pairing link extras, as AddComputerManually sends them to PcView */
    public static final String EXTRA_LINK_HOST = "hostname";
    public static final String EXTRA_LINK_PORT = "port";
    public static final String EXTRA_LINK_PIN = "pin";
    public static final String EXTRA_LINK_PASSPHRASE = "passphrase";

    private FrameLayout container;
    private HostsScreen screen;
    private HostCards.Labels labels;
    private boolean initialFocusDone;

    /** Parsed app lists, by uuid: the name of what runs on each PC */
    private final Map<String, String> appListRaw = new HashMap<>();
    private final Map<String, List<NvApp>> appLists = new HashMap<>();

    private Dialog pairDialog;
    private boolean pairing;
    private String linkHost, linkPin, linkPassphrase;
    private int linkPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        labels = HostCards.Labels.from(this);
        Intent i = getIntent();
        if (i.getStringExtra(EXTRA_LINK_PIN) != null && i.getStringExtra(EXTRA_LINK_PASSPHRASE) != null
                && i.getStringExtra(EXTRA_LINK_HOST) != null) {
            linkHost = i.getStringExtra(EXTRA_LINK_HOST);
            linkPort = i.getIntExtra(EXTRA_LINK_PORT, NvHTTP.DEFAULT_HTTP_PORT);
            linkPin = i.getStringExtra(EXTRA_LINK_PIN);
            linkPassphrase = i.getStringExtra(EXTRA_LINK_PASSPHRASE);
        }
        container = new FrameLayout(this);
        setContentView(container);
        rebuild();
    }

    @Override
    protected void onDestroy() {
        if (pairDialog != null && pairDialog.isShowing()) {
            pairDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void rebuild() {
        if (container == null) {
            return;
        }
        screen = new HostsScreen(this, theme, Density.of(getResources().getConfiguration()), new HostsScreen.Actions() {
            @Override
            public void back() {
                finish();
            }

            @Override
            public void activate(String uuid) {
                HostsActivity.this.activate(uuid);
            }

            @Override
            public void options(String uuid) {
                showHostOptions(uuid);
            }

            @Override
            public void add() {
                showAddMenu();
            }
        });
        container.removeAllViews();
        container.addView(screen.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        initialFocusDone = false;
        refresh();
    }

    @Override
    protected void onCoversChanged() {
    }

    @Override
    protected void refresh() {
        if (screen == null) {
            return;
        }
        List<HostCards.Card> list = new ArrayList<>();
        for (ComputerDetails d : HostCards.pairedByName(computers.values())) {
            list.add(HostCards.of(d, appsOf(d), labels));
        }
        screen.bind(list);
        if (!initialFocusDone && !container.isInTouchMode()) {
            initialFocusDone = screen.requestInitialFocus();
        }
    }

    /** The PC's app list (the poller's, else the cached one), parsed once per change */
    private List<NvApp> appsOf(ComputerDetails d) {
        if (d.runningGameId == 0) {
            return null;
        }
        String raw = d.rawAppList;
        if (raw == null) {
            try {
                raw = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", d.uuid));
            } catch (IOException e) {
                return null;
            }
        }
        if (!raw.equals(appListRaw.get(d.uuid))) {
            try {
                appLists.put(d.uuid, new ArrayList<>(NvHTTP.getAppListByReader(new StringReader(raw))));
                appListRaw.put(d.uuid, raw);
            } catch (Exception e) {
                return null;
            }
        }
        return appLists.get(d.uuid);
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
    }

    @Override
    protected boolean onV2Options(View focused) {
        if (screen == null) {
            return false;
        }
        if (screen.isAddCard(focused)) {
            showAddMenu();
            return true;
        }
        String uuid = screen.uuidFor(focused);
        if (uuid != null) {
            showHostOptions(uuid);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onV2InitialFocus() {
        return screen != null && screen.requestInitialFocus();
    }

    @Override
    protected void onComputerSeen(ComputerDetails details) {
        // A pairing link waits for the PC answering at its address
        if (linkHost != null && details.state == ComputerDetails.State.ONLINE
                && HostCards.matchesLink(details.activeAddress, linkHost, linkPort)) {
            String pin = linkPin, passphrase = linkPassphrase;
            linkHost = null;
            linkPin = null;
            linkPassphrase = null;
            final ComputerDetails target = details;
            handler.post(() -> pair(target, pin, passphrase));
        }
    }

    private ComputerDetails find(String uuid) {
        return uuid == null ? null : computers.get(uuid);
    }

    // ---- tap -----------------------------------------------------------------------------------

    private void activate(String uuid) {
        ComputerDetails d = find(uuid);
        if (d == null) {
            return;
        }
        switch (HostCards.tap(d)) {
            case OPEN:
                openHost(d);
                break;
            case PAIR:
                pair(d, null, null);
                break;
            case WAKE:
                wakeHost(d);
                break;
            default:
                break;
        }
    }

    /** Back to the home, showing this PC (the home reads the remembered PC when it resumes) */
    private void openHost(ComputerDetails d) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_LAST_HOST, d.uuid).apply();
        if (isTaskRoot()) {
            // Opened by a pairing link: there is no home under this page
            Intent home = new Intent(this, HomeActivity.class);
            home.putExtra(EXTRA_PC_UUID, d.uuid);
            startActivity(home);
        }
        finish();
    }

    private void wakeHost(final ComputerDetails d) {
        if (d.macAddress == null) {
            error(getString(R.string.v2_wake_no_mac, d.name));
            return;
        }
        final String name = d.name;
        new Thread(() -> {
            boolean sent;
            try {
                WakeOnLanSender.sendWolPacket(d);
                sent = true;
            } catch (IOException e) {
                sent = false;
            }
            final boolean ok = sent;
            runOnUiThread(() -> Toast.makeText(this, getString(ok ? R.string.v2_wake_sent : R.string.v2_wake_failed, name),
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    private void error(String text) {
        if (!isFinishing()) {
            V2Dialogs.message(this, theme, getString(R.string.v2_oops), text, getString(R.string.v2_ok));
        }
    }

    // ---- options -----------------------------------------------------------------------------

    void showHostOptions(String uuid) {
        final ComputerDetails d = find(uuid);
        if (d == null) {
            return;
        }
        List<V2Dialogs.Entry> entries = new ArrayList<>();
        boolean first = true;
        for (HostCards.Option o : HostCards.options(d)) {
            switch (o) {
                case OPEN:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_host_open), R.drawable.v2_ic_play, first, () -> openHost(d)));
                    break;
                case BENCHMARK:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_host_benchmark), R.drawable.v2_ic_gauge, first, () -> {
                        Intent i = new Intent(this, BenchmarkActivity.class);
                        i.putExtra(BenchmarkActivity.EXTRA_PC_UUID, d.uuid);
                        i.putExtra(BenchmarkActivity.EXTRA_PC_NAME, d.name);
                        startActivity(i);
                    }));
                    break;
                case PAIR:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_host_pair), R.drawable.v2_ic_lock, first, () -> pair(d, null, null)));
                    break;
                case WAKE:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_wake), R.drawable.v2_ic_power, first, () -> wakeHost(d)));
                    break;
                case RENAME:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_host_rename), R.drawable.v2_ic_pencil, first, () -> rename(d)));
                    break;
                case FORGET:
                    entries.add(new V2Dialogs.Entry(getString(R.string.v2_host_forget), R.drawable.v2_ic_trash, first, () -> confirmForget(d)));
                    break;
                default:
                    break;
            }
            first = false;
        }
        V2Dialogs.showMenu(this, theme, d.name, null, entries);
    }

    private void rename(final ComputerDetails d) {
        V2Dialogs.prompt(this, theme, getString(R.string.v2_rename_title), getString(R.string.v2_rename_text), d.name, null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, 64,
                getString(R.string.v2_host_rename), getString(R.string.v2_cancel), name -> {
                    final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
                    if (binder == null) {
                        return;
                    }
                    // The service waits for a running poll of that PC: off the main thread
                    new Thread(() -> {
                        binder.renameComputer(d.uuid, name);
                        runOnUiThread(this::refresh);
                    }).start();
                });
    }

    private void confirmForget(final ComputerDetails d) {
        V2Dialogs.confirm(this, theme, getString(R.string.v2_forget_title, d.name), getString(R.string.v2_forget_text),
                getString(R.string.v2_forget), getString(R.string.v2_cancel), () -> forget(d), null);
    }

    /** PcView.removeComputer(), step for step */
    private void forget(ComputerDetails d) {
        if (managerBinder == null) {
            Toast.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        managerBinder.removeComputer(d);
        new DiskAssetLoader(this).deleteAssetsForComputer(d.uuid);
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE).edit().remove(d.uuid).apply();
        new ShortcutHelper(this).disableComputerShortcut(d, getString(R.string.scut_deleted_pc));
        dropComputer(d.uuid);
        appLists.remove(d.uuid);
        appListRaw.remove(d.uuid);
        refresh();
    }

    // ---- Ajouter un PC -------------------------------------------------------------------------

    void showAddMenu() {
        List<V2Dialogs.Entry> entries = new ArrayList<>();
        for (final ComputerDetails d : HostCards.unpairedOnline(computers.values())) {
            String address = HostCards.address(d);
            entries.add(new V2Dialogs.Entry(d.name + (address.isEmpty() ? "" : " · " + address), R.drawable.v2_ic_monitor,
                    entries.isEmpty(), () -> pair(d, null, null)));
        }
        boolean found = !entries.isEmpty();
        entries.add(new V2Dialogs.Entry(getString(R.string.v2_enter_address), R.drawable.v2_ic_search, !found, this::promptAddress));
        V2Dialogs.showMenu(this, theme, getString(found ? R.string.v2_found_on_network : R.string.v2_none_on_network), null, entries);
    }

    private void promptAddress() {
        V2Dialogs.prompt(this, theme, getString(R.string.v2_add_pc), getString(R.string.v2_add_pc_text), null, "192.168.1.24",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 0,
                getString(R.string.v2_add), getString(R.string.v2_cancel), this::addByAddress);
    }

    /** AddComputerManually.doAddPc(), with the v2 messages; a new unpaired PC is paired at once */
    private void addByAddress(final String input) {
        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        if (binder == null) {
            Toast.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        final Uri uri = AddComputerManually.parseRawUserInputToUri(input);
        if (uri == null || uri.getHost() == null || uri.getHost().isEmpty()) {
            error(getString(R.string.v2_add_invalid, input));
            return;
        }
        final Set<String> pairedBefore = new HashSet<>();
        for (ComputerDetails d : HostCards.pairedByName(computers.values())) {
            pairedBefore.add(d.uuid);
        }
        Toast.makeText(this, getString(R.string.v2_searching_address, input), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? NvHTTP.DEFAULT_HTTP_PORT : uri.getPort();
            ComputerDetails details = new ComputerDetails();
            details.manualAddress = new ComputerDetails.AddressTuple(host, port);
            boolean success;
            boolean invalid = false;
            boolean wrongSubnet = false;
            try {
                success = binder.addComputerBlocking(details);
                if (!success) {
                    wrongSubnet = AddComputerManually.isWrongSubnetSiteLocalAddress(host);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IllegalArgumentException e) {
                // OkHttp refuses a host it cannot canonicalize
                success = false;
                invalid = true;
            }
            boolean blocked = false;
            if (!success && !invalid && !wrongSubnet) {
                int result = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                        MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
                blocked = result != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && result != 0;
            }
            final boolean ok = success, bad = invalid, subnet = wrongSubnet, portsBlocked = blocked;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (bad) {
                    error(getString(R.string.v2_add_invalid, input));
                } else if (subnet) {
                    error(getString(R.string.v2_add_wrong_subnet, input));
                } else if (!ok) {
                    String text = getString(R.string.v2_add_unreachable, input);
                    if (portsBlocked) {
                        text += "\n\n" + getString(R.string.v2_add_blocked);
                    }
                    error(text);
                } else {
                    onAdded(details, pairedBefore);
                }
            });
        }).start();
    }

    private void onAdded(ComputerDetails details, Set<String> pairedBefore) {
        ComputerDetails live = managerBinder != null && details.uuid != null ? managerBinder.getComputer(details.uuid) : null;
        ComputerDetails d = live != null ? live : details;
        if (d.uuid != null) {
            computers.put(d.uuid, d);
        }
        if (d.pairState != PairingManager.PairState.PAIRED) {
            pair(d, null, null);
        } else if (pairedBefore.contains(d.uuid)) {
            Toast.makeText(this, R.string.v2_already_listed, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.v2_added, d.name), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    // ---- appairage -----------------------------------------------------------------------------

    /**
     * PcView.doPair(): the PIN is shown in a themed dialog that closes by itself when the PC
     * answers; on success, the home shows the PC. otp + passphrase come from a pairing link.
     */
    private void pair(final ComputerDetails computer, final String otp, final String passphrase) {
        if (managerBinder == null) {
            Toast.makeText(this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
            return;
        }
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            error(getString(R.string.v2_pair_offline, computer.name));
            return;
        }
        if (pairing) {
            return;
        }
        pairing = true;
        final String name = computer.name;
        final String pin = otp != null ? otp : PairingManager.generatePinString();
        pairDialog = showPairDialog(name, pin, passphrase != null);

        // No poll may run during pairing (PcView stops them the same way)
        stopUpdates();
        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        new Thread(() -> {
            binder.waitForPollingStopped();
            String message = null;
            boolean success = false;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                        binder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(this));
                if (http.getPairState() == PairingManager.PairState.PAIRED) {
                    success = true;
                } else {
                    PairingManager pm = http.getPairingManager();
                    PairingManager.PairState state = pm.pair(http.getServerInfo(true), pin, passphrase);
                    if (state == PairingManager.PairState.PIN_WRONG) {
                        message = getString(R.string.v2_pair_wrong_pin, name);
                    } else if (state == PairingManager.PairState.FAILED) {
                        message = getString(computer.runningGameId != 0 ? R.string.v2_pair_in_game : R.string.v2_pair_failed, name);
                    } else if (state == PairingManager.PairState.ALREADY_IN_PROGRESS) {
                        message = getString(R.string.v2_pair_busy, name);
                    } else if (state == PairingManager.PairState.PAIRED) {
                        success = true;
                        // Pin the certificate for HTTPS, and say it is paired until the next poll
                        // confirms it (the home only shows paired PCs)
                        ComputerDetails live = binder.getComputer(computer.uuid);
                        if (live != null) {
                            live.serverCert = pm.getPairedCert();
                            live.pairState = PairingManager.PairState.PAIRED;
                        }
                        binder.invalidateStateForComputer(computer.uuid);
                    }
                }
            } catch (UnknownHostException e) {
                message = getString(R.string.v2_pair_unknown_host, name);
            } catch (FileNotFoundException e) {
                message = getString(R.string.v2_pair_not_found, name);
            } catch (XmlPullParserException | IOException e) {
                LimeLog.warning("v2: pairing failed: " + e);
                message = getString(R.string.v2_pair_network, name, String.valueOf(e.getMessage()));
            }
            final String error = message;
            final boolean ok = success;
            runOnUiThread(() -> pairFinished(computer, ok, error));
        }).start();
    }

    /** « Appairer X »: the PIN to type on the PC, or (pairing link) a wait; one button that only closes */
    Dialog showPairDialog(String name, String pin, boolean fromLink) {
        String text = fromLink ? getString(R.string.v2_pair_link_text, name)
                : getString(R.string.v2_pair_text, name, HostCards.spacedPin(pin));
        return V2Dialogs.message(this, theme, getString(R.string.v2_pair_title, name), text, getString(R.string.v2_pair_done));
    }

    private void pairFinished(ComputerDetails computer, boolean success, String message) {
        pairing = false;
        if (pairDialog != null && pairDialog.isShowing()) {
            pairDialog.dismiss();
        }
        pairDialog = null;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (success) {
            Toast.makeText(this, getString(R.string.v2_paired, computer.name), Toast.LENGTH_LONG).show();
            openHost(computer);
            return;
        }
        if (message != null) {
            error(message);
        }
        startUpdates();
    }
}
