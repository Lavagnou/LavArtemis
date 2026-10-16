package com.limelight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.benchmark.BenchmarkController;
import com.limelight.benchmark.BenchmarkGameRun;
import com.limelight.benchmark.BenchmarkPlanner;
import com.limelight.benchmark.BenchmarkReport;
import com.limelight.benchmark.BenchmarkSettings;
import com.limelight.benchmark.BenchmarkTypes;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.ExternalDisplayControlActivity;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.UiHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Benchmark of one host's streaming settings (docs/benchmark.md in LavApollo), opened from the
 * host's long-press menu. Each run is an ordinary Game activity streaming LavApollo's hidden
 * Benchmark scene with one configuration, in memory only; it measures itself, writes its result
 * to a file and finishes, and this activity starts the next one when it comes back.
 */
public class BenchmarkActivity extends Activity {
    public static final String EXTRA_PC_UUID = "PcUuid";
    public static final String EXTRA_PC_NAME = "PcName";

    private static final int REQUEST_IMPORT = 1;
    private static final int BETTER = Color.rgb(76, 175, 80);
    private static final int WORSE = Color.rgb(244, 67, 54);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pcUuid;
    private String pcName;
    private ComputerDetails computer;
    private ComputerManagerService.ComputerManagerBinder managerBinder;

    private Mode mode = Mode.QUICK;
    private Goal goal = Goal.BALANCED;
    private BenchmarkReport shownReport;   // a finished or opened report, when not setting up
    private String message = "";

    private LinearLayout content;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder = (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                ComputerDetails details = localBinder.getComputer(pcUuid);
                runOnUiThread(() -> {
                    managerBinder = localBinder;
                    computer = details;
                    render();
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
        UiHelper.setLocale(this);

        pcUuid = getIntent().getStringExtra(EXTRA_PC_UUID);
        pcName = getIntent().getStringExtra(EXTRA_PC_NAME);
        setTitle(getString(R.string.benchmark_title, pcName));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        setContentView(scroll);

        bindService(new Intent(this, ComputerManagerService.class), serviceConnection, BIND_AUTO_CREATE);
        render();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Back from a run: its result file says how it went, its absence that the user left
        BenchmarkController controller = BenchmarkController.current();
        if (controller != null && controller.pending() != null) {
            File file = BenchmarkGameRun.resultFile(this, controller.pending().id);
            RunResult result = readResult(file);
            file.delete();
            controller.complete(result);
            saveProgress(controller);
            render();
            // Let the window settle before the next stream takes the screen
            handler.postDelayed(this::advance, 1500);
        }
        else {
            render();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- Running ------------------------------------------------------------------------------

    private BenchmarkPlanner.Input plannerInput(Mode mode, Goal goal) {
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        return BenchmarkController.plannerInput(mode, goal, BenchmarkSettings.fromPreferences(prefs),
                computer != null ? computer.serverCodecModeSupport : 0,
                PreferenceConfiguration.getDefaultBitrate(this));
    }

    private void start() {
        if (computer == null || managerBinder == null || computer.benchmarkAppId == 0) {
            return;
        }
        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(this);
        BenchmarkReport report = new BenchmarkReport();
        report.id = java.util.UUID.randomUUID().toString();
        report.createdAtMs = System.currentTimeMillis();
        report.mode = mode;
        report.goal = goal;
        report.hostUuid = computer.uuid;
        report.hostName = computer.name;
        report.platform = "android";
        report.device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        report.appVersion = BuildConfig.VERSION_NAME;
        report.width = prefs.width;
        report.height = prefs.height;
        report.fps = prefs.fps;
        report.warmupSeconds = BenchmarkController.WARMUP_SECONDS;

        BenchmarkController controller = new BenchmarkController(report, plannerInput(mode, goal));
        report.measureSeconds = controller.measureSeconds();
        controller.hostAppRunning = computer.runningGameId != 0;
        BenchmarkController.setCurrent(controller);
        shownReport = null;

        // Start from a clean host: another app would refuse the scene, and a scene left by an
        // earlier benchmark may have the wrong display
        if (controller.hostAppRunning) {
            message = getString(R.string.benchmark_quitting);
            render();
            quitHostApp(controller, this::advance);
        }
        else {
            advance();
        }
    }

    private void advance() {
        BenchmarkController controller = BenchmarkController.current();
        if (controller == null || controller.pending() != null || isFinishing()) {
            return;
        }
        BenchmarkController.Run run = controller.next();
        if (run == null) {
            finishBenchmark(controller);
            return;
        }

        boolean vrr = BenchmarkTypes.isVrrPacing(run.config.pacing);
        // The host only builds its 1000 Hz display for VRR at launch: switch through a quit
        if (controller.hostAppRunning && vrr != controller.lastRunVrr) {
            message = getString(R.string.benchmark_restarting);
            render();
            quitHostApp(controller, () -> launch(controller, run, vrr));
            return;
        }
        launch(controller, run, vrr);
    }

    private void launch(BenchmarkController controller, BenchmarkController.Run run, boolean vrr) {
        if (computer == null || managerBinder == null) {
            controller.complete(null);
            finishBenchmark(controller);
            return;
        }
        controller.lastRunVrr = vrr;
        controller.hostAppRunning = true;

        int number = controller.report.runs.size() + 1;
        message = getString(R.string.benchmark_run_progress, number, controller.runEstimate(), runLabel(run.role, run.config));
        render();

        NvApp app = new NvApp("Benchmark", computer.benchmarkAppUuid, computer.benchmarkAppId, false);
        Intent intent = ServerHelper.createStartIntent(this, app, computer, managerBinder, false);
        String request = BenchmarkGameRun.request(run.id, run.role, run.userSettings ? null : run.config,
                BenchmarkController.WARMUP_SECONDS, controller.measureSeconds(), BenchmarkController.WINDOW_SECONDS);
        // On an external display, Game's intent rides inside the touchpad activity's
        Intent inner = intent.getParcelableExtra(ExternalDisplayControlActivity.EXTRA_LAUNCH_INTENT);
        if (inner != null) {
            inner.putExtra(BenchmarkGameRun.EXTRA, request);
            intent.putExtra(ExternalDisplayControlActivity.EXTRA_LAUNCH_INTENT, inner);
        }
        else {
            intent.putExtra(BenchmarkGameRun.EXTRA, request);
        }
        startActivity(intent);
    }

    private void quitHostApp(BenchmarkController controller, Runnable then) {
        final ComputerDetails target = computer;
        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        new Thread(() -> {
            try {
                new NvHTTP(ServerHelper.getCurrentAddressFromComputer(target), target.httpsPort,
                        binder.getUniqueId(), target.serverCert, PlatformBinding.getCryptoProvider(this)).quitApp();
            } catch (Exception ignored) {
                // Nothing running is fine; anything else surfaces as the next launch's error
            }
            runOnUiThread(() -> {
                controller.hostAppRunning = false;
                then.run();
            });
        }).start();
    }

    private void finishBenchmark(BenchmarkController controller) {
        controller.finish();
        boolean measured = false;
        for (RunResult r : controller.report.runs) {
            measured |= r.status == Status.OK;
        }
        if (measured) {
            history().save(controller.report);
        }
        message = controller.baselineFailed() ? getString(R.string.benchmark_baseline_failed)
                : controller.isCancelled() ? getString(R.string.benchmark_stopped) : getString(R.string.benchmark_finished);
        shownReport = controller.report;
        BenchmarkController.setCurrent(null);
        // Leave the host as it was: the scene off and its virtual display gone
        if (controller.hostAppRunning && computer != null && managerBinder != null) {
            quitHostApp(controller, () -> { });
        }
        render();
    }

    private void saveProgress(BenchmarkController controller) {
        // Saved after every run, marked stopped until it ends: a benchmark cut off by the
        // system killing the app is still in the history
        boolean measured = false;
        for (RunResult r : controller.report.runs) {
            measured |= r.status == Status.OK;
        }
        if (measured) {
            controller.report.cancelled = true;
            history().save(controller.report);
            controller.report.cancelled = false;
        }
    }

    private static RunResult readResult(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return BenchmarkReport.readRun(reader);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private BenchmarkReport.History history() {
        return new BenchmarkReport.History(new File(getFilesDir(), "benchmarks"));
    }

    // ---- Labels -------------------------------------------------------------------------------

    private String pacingLabel(String pacing) {
        switch (pacing) {
            case "default": return getString(R.string.benchmark_pacing_default);
            case "ull": return getString(R.string.benchmark_pacing_ull);
            case "paced-ull": return getString(R.string.benchmark_pacing_paced_ull);
            case "predictive": return getString(R.string.benchmark_pacing_predictive);
            case "vrr-low-latency": return getString(R.string.benchmark_pacing_vrr_low_latency);
            case "vrr-balanced": return getString(R.string.benchmark_pacing_vrr_balanced);
            case "vrr-smooth": return getString(R.string.benchmark_pacing_vrr_smooth);
            case "vsync-off": return getString(R.string.benchmark_pacing_vsync_off);
            case "vsync": return getString(R.string.benchmark_pacing_vsync);
            case "vsync-paced": return getString(R.string.benchmark_pacing_vsync_paced);
            default: return pacing;
        }
    }

    private String configLabel(RunConfig c) {
        String codec;
        switch (c.codec) {
            case H264: codec = "H.264"; break;
            case HEVC: codec = "HEVC"; break;
            case AV1: codec = "AV1"; break;
            default: codec = "PyroWave"; break;
        }
        String mbps = c.bitrateKbps % 1000 == 0 ? Integer.toString(c.bitrateKbps / 1000)
                : String.format(Locale.getDefault(), "%.1f", c.bitrateKbps / 1000.0);
        return getString(R.string.benchmark_config, codec, mbps, pacingLabel(c.pacing));
    }

    private String runLabel(Role role, RunConfig c) {
        switch (role) {
            case BASELINE: return getString(R.string.benchmark_role_baseline) + " (" + configLabel(c) + ")";
            case BASELINE_REPEAT: return getString(R.string.benchmark_role_repeat) + " (" + configLabel(c) + ")";
            case CONFIRM: return getString(R.string.benchmark_role_confirm) + " (" + configLabel(c) + ")";
            default: return configLabel(c);
        }
    }

    // ---- Views --------------------------------------------------------------------------------

    private TextView text(CharSequence s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(4), 0, dp(4));
        content.addView(t);
        return t;
    }

    private TextView heading(CharSequence s) {
        TextView t = text(s);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private Button button(ViewGroup parent, CharSequence label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(onClick);
        parent.addView(b);
        return b;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);
        return row;
    }

    private void render() {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        BenchmarkController controller = BenchmarkController.current();
        if (controller != null) {
            renderRunning(controller);
        }
        else if (shownReport != null) {
            renderReport(shownReport, false);
        }
        else {
            renderSetup();
        }
    }

    private void renderSetup() {
        text(getString(R.string.benchmark_intro, pcName));

        heading(getString(R.string.benchmark_mode_label));
        RadioGroup modes = new RadioGroup(this);
        content.addView(modes);
        int[] labels = {R.string.benchmark_mode_quick, R.string.benchmark_mode_full, R.string.benchmark_mode_adaptive};
        Mode[] values = {Mode.QUICK, Mode.FULL, Mode.ADAPTIVE};
        for (int i = 0; i < values.length; i++) {
            final Mode value = values[i];
            RadioButton b = new RadioButton(this);
            b.setId(View.generateViewId());
            b.setText(labels[i]);
            modes.addView(b);
            b.setChecked(mode == value);
            b.setOnClickListener(v -> {
                mode = value;
                render();
            });
        }

        heading(getString(R.string.benchmark_goal_label));
        Spinner goals = new Spinner(this);
        final Goal[] goalValues = {Goal.BALANCED, Goal.LATENCY, Goal.SMOOTHNESS};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[] {
                getString(R.string.benchmark_goal_balanced), getString(R.string.benchmark_goal_latency), getString(R.string.benchmark_goal_smoothness)});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        goals.setAdapter(adapter);
        for (int i = 0; i < goalValues.length; i++) {
            if (goalValues[i] == goal) {
                goals.setSelection(i);
            }
        }
        goals.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (goal != goalValues[position]) {
                    goal = goalValues[position];
                    render();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        content.addView(goals);

        boolean available = false;
        if (computer == null) {
            text("…");
        }
        else if (computer.state != ComputerDetails.State.ONLINE || computer.activeAddress == null) {
            text(getString(R.string.benchmark_offline)).setTextColor(WORSE);
        }
        else if (computer.benchmarkAppId == 0) {
            text(getString(R.string.benchmark_unavailable)).setTextColor(WORSE);
        }
        else {
            available = true;
            BenchmarkPlanner.Input input = plannerInput(mode, goal);
            text(getString(R.string.benchmark_estimate, configLabel(input.baseline),
                    BenchmarkController.estimatedRuns(input), BenchmarkController.estimatedMinutes(input)));
            if (computer.runningGameId != 0 && computer.runningGameId != computer.benchmarkAppId) {
                text(getString(R.string.benchmark_app_running)).setTextColor(WORSE);
            }
        }
        if (!message.isEmpty()) {
            text(message);
        }

        LinearLayout actions = row();
        Button startButton = button(actions, getString(R.string.benchmark_start), v -> start());
        startButton.setEnabled(available);
        button(actions, getString(R.string.benchmark_open_file), v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            try {
                startActivityForResult(pick, REQUEST_IMPORT);
            } catch (android.content.ActivityNotFoundException ignored) {
                // No document picker on this device (some TVs)
            }
        });

        List<BenchmarkReport> reports = history().load(pcUuid);
        if (!reports.isEmpty()) {
            heading(getString(R.string.benchmark_history));
        }
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (BenchmarkReport report : reports) {
            RunResult best = bestRun(report);
            StringBuilder line = new StringBuilder(getString(R.string.benchmark_history_item,
                    format.format(new Date(report.createdAtMs)), report.mode.id, report.goal.id));
            if (!"android".equals(report.platform)) {
                line.append(" (").append(report.platform).append(')');
            }
            if (best != null) {
                line.append(" · ").append(getString(R.string.benchmark_best, configLabel(best.requested)));
            }
            if (report.cancelled) {
                line.append(" · ").append(getString(R.string.benchmark_was_stopped));
            }
            if (report.unstable()) {
                line.append(" · ").append(getString(R.string.benchmark_was_unstable));
            }
            text(line);
            LinearLayout buttons = row();
            button(buttons, getString(R.string.benchmark_open), v -> {
                shownReport = report;
                message = "";
                render();
            });
            button(buttons, getString(R.string.benchmark_delete), v -> {
                history().remove(report.id);
                render();
            });
        }
    }

    private void renderRunning(BenchmarkController controller) {
        ProgressBar progress = new ProgressBar(this);
        content.addView(progress);
        text(message);
        button(row(), getString(R.string.benchmark_stop), v -> {
            controller.cancel();
            if (controller.pending() == null) {
                finishBenchmark(controller);
            }
        });
        if (!controller.report.runs.isEmpty()) {
            renderReport(controller.report, true);
        }
    }

    private static RunResult bestRun(BenchmarkReport report) {
        RunResult best = null;
        for (RunResult r : report.runs) {
            if (r.status == Status.OK && r.role != Role.BASELINE_REPEAT
                    && (best == null || r.scores.forGoal(report.goal) > best.scores.forGoal(report.goal))) {
                best = r;
            }
        }
        return best;
    }

    private static String signed(double value, int digits) {
        return (value > 0 ? "+" : "") + String.format(Locale.getDefault(), "%." + digits + "f", value);
    }

    private void renderReport(BenchmarkReport report, boolean running) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        heading(report.hostName + " · " + format.format(new Date(report.createdAtMs)) + " · " + report.mode.id + ", " + report.goal.id
                + " · " + report.width + "x" + report.height + " " + Math.round(report.fps) + " FPS");
        if (report.unstable()) {
            text(getString(R.string.benchmark_unstable, report.baselineDrift)).setTextColor(WORSE);
        }
        boolean applicable = "android".equals(report.platform);
        if (!applicable) {
            text(getString(R.string.benchmark_desktop_report));
        }
        if (!running && !message.isEmpty()) {
            text(message);
        }
        text(getString(R.string.benchmark_legend)).setAlpha(0.8f);

        RunResult baseline = null;
        for (RunResult r : report.runs) {
            if (r.role == Role.BASELINE && r.status == Status.OK) {
                baseline = r;
                break;
            }
        }
        RunResult best = bestRun(report);

        // Ranked by the goal, failed and skipped runs last
        List<RunResult> ranked = new ArrayList<>(report.runs);
        Collections.sort(ranked, (a, b) -> {
            boolean okA = a.status == Status.OK, okB = b.status == Status.OK;
            if (okA != okB) {
                return okA ? -1 : 1;
            }
            return Double.compare(b.scores.forGoal(report.goal), a.scores.forGoal(report.goal));
        });

        for (RunResult r : ranked) {
            TextView title = heading((r == best ? "★ " : "") + runLabel(r.role, r.requested));
            title.setPadding(0, dp(14), 0, 0);
            if (r.status != Status.OK) {
                int label = r.status == Status.SKIPPED ? R.string.benchmark_status_skipped
                        : r.status == Status.FALLBACK ? R.string.benchmark_status_fallback : R.string.benchmark_status_failed;
                TextView t = text(getString(label) + (r.note.isEmpty() ? "" : ": " + r.note));
                t.setTextColor(r.status == Status.FALLBACK ? Color.rgb(255, 152, 0) : WORSE);
                continue;
            }
            text(getString(R.string.benchmark_scores, r.scores.latency, r.spreadLatency, r.scores.smoothness, r.spreadSmoothness));
            BenchmarkTypes.Metrics m = r.metrics;
            double delivered = m.framesExpected > 0 ? 100.0 * m.framesRendered / m.framesExpected : 0;
            text(getString(R.string.benchmark_details, m.totalMs, m.hostMs, m.networkMs, m.receiveMs, m.decodeMs, m.queueMs,
                    delivered, m.networkLossPct, m.presentP50Ms, m.presentP99Ms, m.stutterPct)).setAlpha(0.85f);
            if (baseline != null && r != baseline) {
                double dl = r.scores.latency - baseline.scores.latency;
                double ds = r.scores.smoothness - baseline.scores.smoothness;
                double dms = m.totalMs - baseline.metrics.totalMs;
                TextView delta = text(getString(R.string.benchmark_vs_baseline,
                        (dl >= 0 ? "▲ " : "▼ ") + signed(dl, 1), (ds >= 0 ? "▲ " : "▼ ") + signed(ds, 1), signed(dms, 1)));
                delta.setTextColor(dl + ds >= 0 ? BETTER : WORSE);
            }
            if (!running && applicable && r.role == Role.CANDIDATE) {
                final RunConfig config = r.effective.copy();
                LinearLayout buttons = row();
                button(buttons, getString(R.string.benchmark_apply), v -> {
                    BenchmarkSettings.applyToSettings(this, config);
                    Toast.makeText(this, getString(R.string.benchmark_applied, configLabel(config)), Toast.LENGTH_LONG).show();
                });
                button(buttons, getString(R.string.benchmark_save_profile), v -> {
                    BenchmarkSettings.applyAsProfile(this, config, getString(R.string.benchmark_profile_name, configLabel(config)));
                    Toast.makeText(this, getString(R.string.benchmark_applied, configLabel(config)), Toast.LENGTH_LONG).show();
                });
            }
        }

        if (!running) {
            button(row(), getString(R.string.benchmark_back), v -> {
                shownReport = null;
                message = "";
                render();
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        BenchmarkReport report = null;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                report = BenchmarkReport.read(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Reported below
        }
        if (report == null) {
            Toast.makeText(this, R.string.benchmark_import_failed, Toast.LENGTH_LONG).show();
            return;
        }
        history().save(report);
        shownReport = report;
        message = "";
        render();
    }
}
