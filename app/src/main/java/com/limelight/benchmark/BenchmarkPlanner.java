package com.limelight.benchmark;

import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which configuration to stream next (docs/benchmark.md, "Modes"). A port of the
 * desktop client's benchmarkplanner.cpp, run for run: keep them in step.
 *
 * Quick and Full plan everything upfront; Adaptive plans stage by stage from the results so
 * far. The caller hands back one result per run already returned, skipped ones included:
 * results.get(i) answers the i-th run.
 */
public final class BenchmarkPlanner {
    public static final class Input {
        public Mode mode = Mode.QUICK;
        public Goal goal = Goal.BALANCED;
        public RunConfig baseline = new RunConfig();      // the user's own settings
        public List<Codec> codecs = new ArrayList<>();    // usable on this host and device, baseline's included
        public List<String> pacingModes = new ArrayList<>(); // the platform list, in the doc's order
        public int defaultBitrateKbps;
        public int pyroWaveBitrateKbps;
        public int minBitrateKbps = 500;
        public int maxBitrateKbps = 500000;
    }

    public static final class PlannedRun {
        public Role role = Role.CANDIDATE;
        public RunConfig config = new RunConfig();
        public boolean skip;            // record it as Skipped, without streaming
        public String skipReason = "";

        PlannedRun copy() {
            PlannedRun run = new PlannedRun();
            run.role = role;
            run.config = config.copy();
            run.skip = skip;
            run.skipReason = skipReason;
            return run;
        }
    }

    private enum Stage { CODECS, BITRATE_LOW, BITRATE_CLIMB, PACING, CONFIRM, DONE }

    private interface Filter {
        boolean accept(RunResult result, RunConfig reference);
    }

    private static final double[] CLIMB_FACTORS = {1.5, 2.0, 3.0};
    private static final double CLIMB_LOSS_LIMIT_PCT = 1.0;
    private static final int CLIMB_DECLINE_LIMIT = 2;

    private static final Filter ANY = (r, ref) -> true;
    private static final Filter SAME_CODEC_AND_PACING =
            (r, ref) -> r.requested.codec == ref.codec && r.requested.pacing.equals(ref.pacing);

    private final Input input;
    private final List<PlannedRun> plan = new ArrayList<>();

    // Adaptive
    private Stage stage = Stage.CODECS;
    private RunConfig choice = new RunConfig();     // the best configuration so far, stage by stage
    private int climbStep;
    private int climbRunIndex = -1;
    private int declines;

    public BenchmarkPlanner(Input input) {
        this.input = input;
        if (input.mode == Mode.ADAPTIVE) {
            append(Role.BASELINE, input.baseline);
            for (Codec codec : input.codecs) {
                int kbps = codec == Codec.PYROWAVE ? input.pyroWaveBitrateKbps : bitrate(1.0);
                append(Role.CANDIDATE, new RunConfig(codec, kbps, input.baseline.pacing));
            }
        }
        else {
            planFixed();
        }
    }

    public Input input() {
        return input;
    }

    private int bitrate(double factor) {
        long kbps = Math.round(input.defaultBitrateKbps * factor / 500.0) * 500;
        return (int) Math.max(input.minBitrateKbps, Math.min(input.maxBitrateKbps, kbps));
    }

    private boolean isPlanned(RunConfig config) {
        for (PlannedRun run : plan) {
            if (run.config.equals(config)) {
                return true;
            }
        }
        return false;
    }

    private boolean append(Role role, RunConfig config) {
        // Repeats are the only runs allowed to stream a configuration twice
        boolean repeat = role == Role.BASELINE_REPEAT || role == Role.CONFIRM;
        if (!repeat && isPlanned(config)) {
            return false;
        }
        PlannedRun run = new PlannedRun();
        run.role = role;
        run.config = config.copy();
        plan.add(run);
        return true;
    }

    private void planFixed() {
        RunConfig base = input.baseline;
        append(Role.BASELINE, base);

        if (input.mode == Mode.QUICK) {
            for (Codec codec : input.codecs) {
                if (codec != base.codec) {
                    // PyroWave's bitrate means nothing to the other codecs: they get the default then
                    int kbps = codec == Codec.PYROWAVE ? input.pyroWaveBitrateKbps
                            : base.codec == Codec.PYROWAVE ? bitrate(1.0) : base.bitrateKbps;
                    append(Role.CANDIDATE, new RunConfig(codec, kbps, base.pacing));
                }
            }
            int variants = 0;
            for (String pacing : input.pacingModes) {
                if (variants < 2 && !pacing.equals(base.pacing)
                        && append(Role.CANDIDATE, new RunConfig(base.codec, base.bitrateKbps, pacing))) {
                    variants++;
                }
            }
        }
        else {
            for (Codec codec : input.codecs) {
                if (codec == Codec.PYROWAVE) {
                    append(Role.CANDIDATE, new RunConfig(codec, input.pyroWaveBitrateKbps, base.pacing));
                    continue;
                }
                for (double factor : new double[] {0.5, 1.0, 2.0}) {
                    append(Role.CANDIDATE, new RunConfig(codec, bitrate(factor), base.pacing));
                }
            }
            for (String pacing : input.pacingModes) {
                if (!pacing.equals(base.pacing)) {
                    append(Role.CANDIDATE, new RunConfig(base.codec, base.bitrateKbps, pacing));
                }
            }
        }

        // Runs that request VRR differently from the baseline go last, together (stable)
        boolean baseVrr = BenchmarkTypes.isVrrPacing(base.pacing);
        List<PlannedRun> same = new ArrayList<>();
        List<PlannedRun> other = new ArrayList<>();
        for (int i = 1; i < plan.size(); i++) {
            PlannedRun run = plan.get(i);
            (BenchmarkTypes.isVrrPacing(run.config.pacing) == baseVrr ? same : other).add(run);
        }
        PlannedRun first = plan.get(0);
        plan.clear();
        plan.add(first);
        plan.addAll(same);
        plan.addAll(other);

        append(Role.BASELINE_REPEAT, base);
    }

    private int bestIndex(List<RunResult> results, Filter filter, RunConfig reference) {
        int best = -1;
        for (int i = 0; i < results.size(); i++) {
            RunResult r = results.get(i);
            if (r.status != Status.OK || !filter.accept(r, reference)) {
                continue;
            }
            if (best < 0 || r.scores.forGoal(input.goal) > results.get(best).scores.forGoal(input.goal)) {
                best = i;
            }
        }
        return best;
    }

    private void advance(List<RunResult> results) {
        switch (stage) {
            case CODECS: {
                int best = bestIndex(results, ANY, input.baseline);
                if (best < 0) {
                    stage = Stage.DONE;
                    return;
                }
                choice = results.get(best).requested.copy();
                if (choice.codec == Codec.PYROWAVE) {
                    stage = Stage.PACING;   // PyroWave's bitrate is what its link allows, not a dial
                    return;
                }
                stage = Stage.BITRATE_LOW;
                append(Role.CANDIDATE, new RunConfig(choice.codec, bitrate(0.5), choice.pacing));
                return;
            }

            case BITRATE_LOW:
                stage = Stage.BITRATE_CLIMB;
                climbStep = 0;
                climbRunIndex = -1;
                declines = 0;
                // fall through

            case BITRATE_CLIMB: {
                boolean climb = true;
                if (climbRunIndex >= 0) {
                    RunResult last = results.get(climbRunIndex);
                    int best = bestIndex(results, SAME_CODEC_AND_PACING, choice);
                    if (last.status != Status.OK || last.metrics.networkLossPct > CLIMB_LOSS_LIMIT_PCT) {
                        climb = false;
                    }
                    else if (best >= 0 && last.scores.forGoal(input.goal) < results.get(best).scores.forGoal(input.goal)) {
                        climb = ++declines < CLIMB_DECLINE_LIMIT;
                    }
                    else {
                        declines = 0;
                    }
                }
                while (climb && climbStep < CLIMB_FACTORS.length) {
                    RunConfig next = new RunConfig(choice.codec, bitrate(CLIMB_FACTORS[climbStep++]), choice.pacing);
                    if (append(Role.CANDIDATE, next)) {
                        climbRunIndex = plan.size() - 1;
                        return;
                    }
                }
                int best = bestIndex(results, SAME_CODEC_AND_PACING, choice);
                if (best >= 0) {
                    choice.bitrateKbps = results.get(best).requested.bitrateKbps;
                }
                stage = Stage.PACING;
                return;
            }

            case PACING:
                stage = Stage.CONFIRM;
                for (String pacing : input.pacingModes) {
                    append(Role.CANDIDATE, new RunConfig(choice.codec, choice.bitrateKbps, pacing));
                }
                return;

            case CONFIRM: {
                stage = Stage.DONE;
                int winner = bestIndex(results, ANY, input.baseline);
                if (winner >= 0 && !results.get(winner).requested.equals(input.baseline)) {
                    append(Role.CONFIRM, results.get(winner).requested);
                }
                append(Role.BASELINE_REPEAT, input.baseline);
                return;
            }

            case DONE:
            default:
        }
    }

    private void markSkips(PlannedRun run, List<RunResult> results) {
        if (run.role == Role.BASELINE || run.role == Role.BASELINE_REPEAT) {
            return;   // the reference streams whatever the user streams
        }
        for (RunResult r : results) {
            if (r.status != Status.FALLBACK) {
                continue;
            }
            if (r.requested.codec == run.config.codec && r.effective.codec != r.requested.codec) {
                run.skip = true;
                run.skipReason = run.config.codec.id + " is not available: the device fell back to " + r.effective.codec.id;
                return;
            }
            if (BenchmarkTypes.isVrrPacing(run.config.pacing) && BenchmarkTypes.isVrrPacing(r.requested.pacing)
                    && !BenchmarkTypes.isVrrPacing(r.effective.pacing)) {
                run.skip = true;
                run.skipReason = "VRR pacing is not available";
                return;
            }
        }
    }

    /** The run after results, or null when the benchmark is over. */
    public PlannedRun next(List<RunResult> results) {
        if (input.mode == Mode.ADAPTIVE) {
            while (results.size() >= plan.size() && stage != Stage.DONE) {
                advance(results);
            }
        }
        if (results.size() >= plan.size()) {
            return null;
        }
        PlannedRun run = plan.get(results.size()).copy();
        markSkips(run, results);
        return run;
    }

    /** How many runs to expect; for Adaptive, the most it can take. */
    public int estimatedRuns() {
        if (input.mode != Mode.ADAPTIVE) {
            return plan.size();
        }
        return plan.size() + 1 + CLIMB_FACTORS.length + input.pacingModes.size() - 1 + 2;
    }
}
