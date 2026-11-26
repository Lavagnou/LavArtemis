package com.limelight.v2;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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
import com.limelight.utils.PairingErrorText;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.v2.ui.V2Dialogs;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
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
        android.content.res.Configuration cfg = getResources().getConfiguration();
        screen = new HostsScreen(this, theme, HomeLayout.forSize(cfg.screenWidthDp, cfg.screenHeightDp), new HostsScreen.Actions() {
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

    /** Prototype grid: the cards then « Ajouter un PC », row by row */
    @Override
    protected List<List<View>> focusRows() {
        return screen == null ? null : screen.focusRows();
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
    /**
     * A host whose server software LavArtemis cannot stream from (HostSupport): the message the
     * desktop shows (HostsView.openHost), and false. Opening, launching, benchmarking ask it first.
     */
    boolean checkSupported(ComputerDetails d) {
        if (HostSupport.isSupported(d)) {
            return true;
        }
        error(getString(R.string.v2_host_unsupported, d.name, d.serverAppVersion));
        return false;
    }

    private void openHost(ComputerDetails d) {
        if (!checkSupported(d)) {
            return;
        }
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
            runOnUiThread(() -> toast(getString(ok ? R.string.v2_wake_sent : R.string.v2_wake_failed, name)));
        }).start();
    }

    private void error(String text) {
        if (!isFinishing()) {
            V2Dialogs.message(this, theme, getString(R.string.v2_oops), text, getString(R.string.v2_ok));
        }
    }

    // ---- options -----------------------------------------------------------------------------

    /** The header of the dialogs about a PC (prototype ctx: kind « PC », where = its address) */
    private V2Sheet.Spec pcSpec(ComputerDetails d, String title) {
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.kind = getString(R.string.v2_host_kind);
        spec.where = HostCards.address(d);
        spec.title = title;
        return spec;
    }

    /** The options of a PC (prototype ctxOpts « host »): Ouvrir + Benchmark, or Appairer / Réveiller, then Renommer, Oublier ce PC, Annuler */
    void showHostOptions(String uuid) {
        final ComputerDetails d = find(uuid);
        if (d == null) {
            return;
        }
        V2Sheet.Spec spec = pcSpec(d, d.name);
        spec.info = true;
        spec.note = HostCards.optionsNote(d, labels, getString(R.string.v2_host_online));
        boolean first = true;
        for (HostCards.Option o : HostCards.options(d)) {
            switch (o) {
                case OPEN:
                    spec.option(getString(R.string.v2_host_open), first, false, () -> openHost(d));
                    break;
                case BENCHMARK:
                    spec.option(getString(R.string.v2_host_benchmark), first, false, () -> {
                        if (!checkSupported(d)) {
                            return;
                        }
                        Intent i = new Intent(this, BenchmarkActivity.class);
                        i.putExtra(BenchmarkActivity.EXTRA_PC_UUID, d.uuid);
                        i.putExtra(BenchmarkActivity.EXTRA_PC_NAME, d.name);
                        startActivity(i);
                    });
                    break;
                case PAIR:
                    spec.option(getString(R.string.v2_host_pair), first, false, () -> pair(d, null, null));
                    break;
                case WAKE:
                    spec.option(getString(R.string.v2_wake), first, false, () -> wakeHost(d));
                    break;
                case RENAME:
                    spec.option(getString(R.string.v2_host_rename), first, false, () -> rename(d));
                    break;
                case FORGET:
                    spec.option(getString(R.string.v2_host_forget), first, false, () -> confirmForget(d));
                    break;
                default:
                    break;
            }
            first = false;
        }
        spec.option(getString(R.string.v2_cancel), false, false, null);
        V2Sheet.show(this, theme, spec);
    }

    /** « Renommer X » (prototype rename): the field « Nom », Renommer / Annuler */
    Dialog rename(final ComputerDetails d) {
        final V2Sheet.Spec spec = pcSpec(d, getString(R.string.v2_rename_pc, d.name));
        spec.info = true;
        spec.note = getString(R.string.v2_rename_text);
        spec.inputLabel = getString(R.string.v2_rename_label);
        spec.inputValue = d.name;
        spec.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
        spec.inputMax = 64;
        spec.option(getString(R.string.v2_host_rename), true, false, () -> {
            String name = spec.text();
            final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
            if (binder == null || name.isEmpty()) {
                return;
            }
            // The service waits for a running poll of that PC: off the main thread
            new Thread(() -> {
                binder.renameComputer(d.uuid, name);
                runOnUiThread(this::refresh);
            }).start();
        });
        spec.option(getString(R.string.v2_cancel), false, false, null);
        return V2Sheet.show(this, theme, spec);
    }

    /** « Oublier X ? » (prototype forget): warning, Oublier / Annuler */
    Dialog confirmForget(final ComputerDetails d) {
        V2Sheet.Spec spec = pcSpec(d, getString(R.string.v2_forget_title, d.name));
        spec.warn = true;
        spec.note = getString(R.string.v2_forget_text);
        spec.option(getString(R.string.v2_forget), true, false, () -> forget(d));
        spec.option(getString(R.string.v2_cancel), false, false, null);
        return V2Sheet.show(this, theme, spec);
    }

    /** PcView.removeComputer(), step for step */
    private void forget(ComputerDetails d) {
        if (managerBinder == null) {
            toast(R.string.error_manager_not_running);
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

    /** « Ajouter un PC » (prototype add): the PCs found on the network, « Saisir une adresse… », Annuler */
    void showAddMenu() {
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.kind = getString(R.string.v2_host_kind);
        spec.where = getString(R.string.v2_add_where);
        spec.title = getString(R.string.v2_add_pc);
        spec.info = true;
        List<ComputerDetails> found = HostCards.unpairedOnline(computers.values());
        spec.note = getString(found.isEmpty() ? R.string.v2_add_none_note : R.string.v2_add_found_note);
        for (final ComputerDetails d : found) {
            String address = HostCards.address(d);
            // Prototype isMain: no main row in this list
            spec.option(d.name + (address.isEmpty() ? "" : " · " + address), false, false, () -> pair(d, null, null));
        }
        spec.option(getString(R.string.v2_enter_address), false, false, this::promptAddress);
        spec.option(getString(R.string.v2_cancel), false, false, null);
        V2Sheet.show(this, theme, spec);
    }

    /** « Saisir une adresse » (prototype addr): the field « Adresse », Ajouter / Annuler */
    Dialog promptAddress() {
        final V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.kind = getString(R.string.v2_host_kind);
        spec.where = getString(R.string.v2_add_where);
        spec.title = getString(R.string.v2_addr_title);
        spec.info = true;
        spec.note = getString(R.string.v2_add_pc_text);
        spec.inputLabel = getString(R.string.v2_addr_label);
        spec.inputHint = "192.168.1.24";
        spec.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        spec.option(getString(R.string.v2_add), true, false, () -> {
            String address = spec.text();
            if (!address.isEmpty()) {
                addByAddress(address);
            }
        });
        spec.option(getString(R.string.v2_cancel), false, false, null);
        return V2Sheet.show(this, theme, spec);
    }

    /** AddComputerManually.doAddPc(), with the v2 messages; a new unpaired PC is paired at once */
    private void addByAddress(final String input) {
        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        if (binder == null) {
            toast(R.string.error_manager_not_running);
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
        toast(getString(R.string.v2_searching_address, input));
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
            toast(R.string.v2_already_listed);
        } else {
            toast(getString(R.string.v2_added, d.name));
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
            toast(R.string.error_manager_not_running);
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
            } catch (XmlPullParserException | IOException e) {
                // The desktop's sentences: an expired link code, a short reason (« erreur 500 »),
                // never the exception's English text
                LimeLog.warning("v2: pairing failed: " + e);
                message = PairingErrorText.forException(getResources(), name, e);
            }
            final String error = message;
            final boolean ok = success;
            runOnUiThread(() -> pairFinished(computer, ok, error));
        }).start();
    }

    /**
     * « Appairer X » (prototype pair): the text, the PIN in big letters (none for a pairing link, which
     * brings its own code) and « Annuler », which only closes the window: the PC answers or refuses,
     * and the window closes by itself then.
     */
    Dialog showPairDialog(String name, String pin, boolean fromLink) {
        V2Sheet.Spec spec = new V2Sheet.Spec();
        spec.kind = getString(R.string.v2_host_kind);
        spec.where = name;
        spec.title = getString(R.string.v2_pair_title, name);
        spec.info = true;
        spec.note = fromLink ? getString(R.string.v2_pair_link_text, name) : getString(R.string.v2_pair_text, name, pin);
        if (!fromLink) {
            spec.pin = HostCards.spacedPin(pin);
        }
        spec.option(getString(R.string.v2_cancel), false, false, null);
        return V2Sheet.show(this, theme, spec);
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
            toast(getString(R.string.v2_paired, computer.name));
            openHost(computer);
            return;
        }
        if (message != null) {
            error(message);
        }
        startUpdates();
    }
}
