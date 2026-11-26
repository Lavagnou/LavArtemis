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
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import com.limelight.v2.V2Theme;
import com.limelight.v2.ui.ThemedDialogs;
import com.limelight.v2.ui.V2Page;

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
    /** Label of the run being measured (« HEVC · 80 Mbit/s · Latence la plus basse »), for the Dev screen */
    private String currentRun = "";

    private LinearLayout content;
    // LavArtemis Dev: the screen in the v2 theme (null in the stable, which keeps its widgets)
    private V2Page page;

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

        if (ThemedDialogs.enabled()) {
            page = new V2Page(this, V2Theme.load(this), getString(R.string.benchmark_title, pcName), this::finish);
            content = page.content();
            setContentView(page.getView());
        }
        else {
            ScrollView scroll = new ScrollView(this);
            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(16);
            content.setPadding(pad, pad, pad, pad);
            scroll.addView(content);
            setContentView(scroll);
        }

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

    // The v2 screen shows the focus ring only for a gamepad or a keyboard: it follows the input

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (page != null) {
            V2Page.onTouch(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (page != null) {
            V2Page.onMotion(ev);
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (page != null) {
            V2Page.onKey(event);
        }
        return super.dispatchKeyEvent(event);
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
        currentRun = runLabel(run.role, run.config);
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
        // Shared with the run notes (« Passé en HEVC, … à la place. »)
        return com.limelight.benchmark.BenchmarkNotes.pacingLabel(this, pacing);
    }

    private String modeLabel(Mode m) {
        switch (m) {
            case FULL: return getString(R.string.benchmark_mode_full_short);
            case ADAPTIVE: return getString(R.string.benchmark_mode_adaptive_short);
            default: return getString(R.string.benchmark_mode_quick_short);
        }
    }

    private String goalLabel(Goal g) {
        switch (g) {
            case LATENCY: return getString(R.string.benchmark_goal_latency);
            case SMOOTHNESS: return getString(R.string.benchmark_goal_smoothness);
            default: return getString(R.string.benchmark_goal_balanced);
        }
    }

    private String configLabel(RunConfig c) {
        String codec = com.limelight.benchmark.BenchmarkNotes.codecLabel(c.codec.id);
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
        if (page != null) {
            return page.text(content, s, false);
        }
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(4), 0, dp(4));
        content.addView(t);
        return t;
    }

    private TextView heading(CharSequence s) {
        if (page != null) {
            return page.heading(content, s);
        }
        TextView t = text(s);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private View button(ViewGroup parent, CharSequence label, View.OnClickListener onClick) {
        return button(parent, label, false, onClick);
    }

    /** @param main the screen's main action (« Lancer », « Appliquer »): the accent in the v2 look */
    private View button(ViewGroup parent, CharSequence label, boolean main, View.OnClickListener onClick) {
        if (page != null) {
            return page.button(parent, label.toString(), main, onClick);
        }
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(onClick);
        parent.addView(b);
        return b;
    }

    private LinearLayout row() {
        if (page != null) {
            return page.buttonRow(content);
        }
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

    /** Mode and goal as v2 choices (LavArtemis Dev) */
    private void renderChoices() {
        int[] labels = {R.string.benchmark_mode_quick, R.string.benchmark_mode_full, R.string.benchmark_mode_adaptive};
        Mode[] values = {Mode.QUICK, Mode.FULL, Mode.ADAPTIVE};
        for (int i = 0; i < values.length; i++) {
            final Mode value = values[i];
            page.choice(content, getString(labels[i]), mode == value, () -> {
                mode = value;
                render();
            });
        }
        heading(getString(R.string.benchmark_goal_label));
        int[] goalLabels = {R.string.benchmark_goal_balanced, R.string.benchmark_goal_latency, R.string.benchmark_goal_smoothness};
        Goal[] goalValues = {Goal.BALANCED, Goal.LATENCY, Goal.SMOOTHNESS};
        for (int i = 0; i < goalValues.length; i++) {
            final Goal value = goalValues[i];
            page.choice(content, getString(goalLabels[i]), goal == value, () -> {
                goal = value;
                render();
            });
        }
    }

    private void renderLegacyChoices() {
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
    }

    private void renderSetup() {
        text(getString(R.string.benchmark_intro, pcName));

        heading(getString(R.string.benchmark_mode_label));
        if (page != null) {
            renderChoices();
        }
        else {
            renderLegacyChoices();
        }

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
        View startButton = button(actions, getString(R.string.benchmark_start), true, v -> start());
        if (page != null) {
            V2Page.setUsable(startButton, available);
        }
        else {
            startButton.setEnabled(available);
        }
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
                    format.format(new Date(report.createdAtMs)), modeLabel(report.mode), goalLabel(report.goal)));
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
        if (page != null) {
            renderRunningV8(controller);
            return;
        }
        ProgressBar progress = new ProgressBar(this);
        if (page != null) {
            progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(page.theme.accent));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(40), dp(40));
            plp.topMargin = dp(8);
            content.addView(progress, plp);
        }
        else {
            content.addView(progress);
        }
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

    /**
     * LavArtemis Dev, prototype V8-44 (bench.running): « ● ESSAI 4 SUR 6 », the run in the theme's
     * display font, a 520 x 10 bar on the switch colours, the note, « Arrêter ». The partial report
     * shows at the end, as the prototype.
     */
    private void renderRunningV8(BenchmarkController controller) {
        com.limelight.v2.V2Theme th = page.theme;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        com.limelight.v2.ui.Ui.noClip(box);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(140);
        content.addView(box, blp);

        int done = controller.report.runs.size();
        int estimate = controller.runEstimate();
        TextView step = com.limelight.v2.ui.Ui.text(this, th, 14, 700, th.muted);
        step.setLetterSpacing(0.06f);
        step.setAllCaps(true);
        step.setText(getString(R.string.v2_bench_step, com.limelight.benchmark.BenchmarkProgress.step(done, estimate), Math.max(1, estimate)));
        step.setCompoundDrawablesRelativeWithIntrinsicBounds(new com.limelight.v2.ui.DotDrawable(th.accent, dp(8)), null, null, null);
        step.setCompoundDrawablePadding(dp(8));
        box.addView(step, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView run = com.limelight.v2.ui.Ui.text(this, th, 40, th.displayWeight, th.fg);
        run.setTypeface(th.displayFont(this, th.displayWeight));
        run.setGravity(android.view.Gravity.CENTER);
        run.setMaxLines(3);
        BenchmarkController.Run pending = controller.pending();
        String label = pending != null ? runLabel(pending.role, pending.config) : (!currentRun.isEmpty() ? currentRun : message);
        if (label.isEmpty() && !controller.report.runs.isEmpty()) {
            // Between two runs, before the next one is known: the run just measured
            RunResult last = controller.report.runs.get(controller.report.runs.size() - 1);
            label = runLabel(last.role, last.requested);
        }
        run.setText(th.displayText(label));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Math.min(dp(720), getResources().getDisplayMetrics().widthPixels - dp(80)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(16);
        box.addView(run, rlp);

        int pct = com.limelight.benchmark.BenchmarkProgress.percent(done, estimate);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(com.limelight.v2.ui.Ui.roundRect(th.off, dp(5), 0, 0));
        android.view.View fill = new android.view.View(this);
        fill.setBackground(com.limelight.v2.ui.Ui.roundRect(th.on, dp(5), 0, 0));
        track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
        track.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - pct));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Math.min(dp(520), getResources().getDisplayMetrics().widthPixels - dp(80)), dp(10));
        tlp.topMargin = dp(16);
        box.addView(track, tlp);

        TextView note = com.limelight.v2.ui.Ui.text(this, th, 15, 400, th.muted);
        note.setGravity(android.view.Gravity.CENTER);
        note.setMaxLines(4);
        note.setLineSpacing(0, 1.5f);
        note.setText(getString(R.string.v2_bench_note, pcName == null ? "" : pcName));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Math.min(dp(560), getResources().getDisplayMetrics().widthPixels - dp(80)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(16);
        box.addView(note, nlp);

        View stop = page.button(box, getString(R.string.benchmark_stop), false, v -> {
            controller.cancel();
            if (controller.pending() == null) {
                finishBenchmark(controller);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(24);
        stop.setLayoutParams(slp);
        stop.requestFocus();
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
        heading(report.hostName + " · " + format.format(new Date(report.createdAtMs)) + " · " + modeLabel(report.mode) + ", " + goalLabel(report.goal)
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
                TextView t = text(getString(label) + (r.note.isEmpty() ? "" : ": " + com.limelight.benchmark.BenchmarkNotes.localize(this, r.note)));
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
                button(buttons, getString(R.string.benchmark_apply), true, v -> {
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
