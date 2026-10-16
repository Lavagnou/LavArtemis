package com.limelight.benchmark;

import com.limelight.benchmark.BenchmarkPlanner.PlannedRun;
import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A benchmark in progress: the report so far and which run comes next. No Android in here;
 * BenchmarkActivity streams each run through Game and hands the results back.
 *
 * The baseline streams first, with the user's own settings untouched, and the plan is only
 * made from what it really ran as: Auto settles on one codec, and that is the one the other
 * runs are compared on.
 */
public final class BenchmarkController {
    public static final int WARMUP_SECONDS = 5;
    public static final int QUICK_MEASURE_SECONDS = 15;
    public static final int MEASURE_SECONDS = 20;
    public static final int WINDOW_SECONDS = 5;

    // Seconds a run costs beyond its measured time: connecting, warming up, tearing down
    private static final int RUN_OVERHEAD_SECONDS = 12;

    // ServerCodecModeSupport bits (moonlight-common-c's SCM_MASK_*)
    private static final long SCM_MASK_HEVC = 0x00000100L | 0x00000200L | 0x00080000L | 0x00100000L;
    private static final long SCM_MASK_AV1 = 0x00010000L | 0x00020000L | 0x00200000L | 0x00400000L;

    /** The one in progress, which outlives BenchmarkActivity being recreated during a run. */
    private static BenchmarkController current;

    public static synchronized BenchmarkController current() {
        return current;
    }

    public static synchronized void setCurrent(BenchmarkController controller) {
        current = controller;
    }

    /** What the next stream is: the planned run, and whether it uses the user's own settings. */
    public static final class Run {
        public final String id = UUID.randomUUID().toString();
        public final Role role;
        public final RunConfig config;
        public final boolean userSettings;

        Run(Role role, RunConfig config) {
            this.role = role;
            this.config = config;
            this.userSettings = role == Role.BASELINE || role == Role.BASELINE_REPEAT;
        }
    }

    public final BenchmarkReport report;
    private final BenchmarkPlanner.Input input;
    private final RunConfig requestedBaseline;
    private BenchmarkPlanner planner;
    private Run pending;
    private boolean cancelled;

    // The host's state, to rebuild its display when the VRR request changes
    public boolean hostAppRunning;
    public boolean lastRunVrr;

    public BenchmarkController(BenchmarkReport report, BenchmarkPlanner.Input input) {
        this.report = report;
        this.input = input;
        this.requestedBaseline = input.baseline.copy();
    }

    /** The planner's input for this host and device. Codecs the host can't encode are left out. */
    public static BenchmarkPlanner.Input plannerInput(Mode mode, Goal goal, RunConfig baseline,
                                                      long serverCodecModeSupport, int defaultBitrateKbps) {
        BenchmarkPlanner.Input in = new BenchmarkPlanner.Input();
        in.mode = mode;
        in.goal = goal;
        in.baseline = baseline.copy();
        in.codecs.add(Codec.H264);
        if ((serverCodecModeSupport & SCM_MASK_HEVC) != 0) {
            in.codecs.add(Codec.HEVC);
        }
        if ((serverCodecModeSupport & SCM_MASK_AV1) != 0) {
            in.codecs.add(Codec.AV1);
        }
        // PyroWave is decoded natively, out of reach of the Java stats: not measurable here yet
        in.pacingModes = new ArrayList<>(BenchmarkSettings.PACING_MODES);
        in.defaultBitrateKbps = defaultBitrateKbps;
        in.minBitrateKbps = 500;
        in.maxBitrateKbps = 800000;
        return in;
    }

    public static int estimatedRuns(BenchmarkPlanner.Input input) {
        return new BenchmarkPlanner(input).estimatedRuns();
    }

    public static int estimatedMinutes(BenchmarkPlanner.Input input) {
        int measure = input.mode == Mode.QUICK ? QUICK_MEASURE_SECONDS : MEASURE_SECONDS;
        int seconds = estimatedRuns(input) * (WARMUP_SECONDS + measure + RUN_OVERHEAD_SECONDS);
        return Math.max(1, (seconds + 59) / 60);
    }

    public int measureSeconds() {
        return input.mode == Mode.QUICK ? QUICK_MEASURE_SECONDS : MEASURE_SECONDS;
    }

    public int runEstimate() {
        return planner != null ? planner.estimatedRuns() : estimatedRuns(input);
    }

    public Run pending() {
        return pending;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * The next run to stream, or null when the benchmark is over. Runs the planner skips are
     * recorded on the way.
     */
    public Run next() {
        if (cancelled) {
            return null;
        }
        for (;;) {
            if (planner == null) {
                if (report.runs.isEmpty()) {
                    pending = new Run(Role.BASELINE, requestedBaseline);
                    return pending;
                }
                RunResult baseline = report.runs.get(0);
                if (baseline.status != Status.OK) {
                    return null;    // nothing to compare with
                }
                input.baseline = baseline.effective.copy();
                planner = new BenchmarkPlanner(input);
            }

            PlannedRun run = planner.next(report.runs);
            if (run == null) {
                return null;
            }
            if (run.skip) {
                RunResult skipped = new RunResult();
                skipped.role = run.role;
                skipped.status = Status.SKIPPED;
                skipped.note = run.skipReason;
                skipped.requested = run.config.copy();
                skipped.effective = run.config.copy();
                report.runs.add(skipped);
                continue;
            }
            pending = new Run(run.role, run.config);
            return pending;
        }
    }

    /** The pending run's outcome; null when the user left it, which ends the benchmark. */
    public void complete(RunResult result) {
        Run run = pending;
        pending = null;
        if (result == null) {
            cancelled = true;
            return;
        }
        if (run != null) {
            result.role = run.role;
            if (run.userSettings) {
                result.requested = requestedBaseline.copy();
            }
        }
        report.runs.add(result);
        report.baselineDrift = BenchmarkReport.computeBaselineDrift(report.runs);
    }

    /** Whether the benchmark ended because streaming the user's own settings failed. */
    public boolean baselineFailed() {
        return !report.runs.isEmpty() && report.runs.get(0).role == Role.BASELINE && report.runs.get(0).status != Status.OK;
    }

    public void finish() {
        report.cancelled = cancelled;
        report.baselineDrift = BenchmarkReport.computeBaselineDrift(report.runs);
    }
}
