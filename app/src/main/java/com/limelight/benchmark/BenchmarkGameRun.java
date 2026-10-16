package com.limelight.benchmark;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.benchmark.BenchmarkTypes.RawStats;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Scores;
import com.limelight.benchmark.BenchmarkTypes.Status;
import com.limelight.preferences.PreferenceConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One benchmark run, inside Game: what BenchmarkActivity asked for, the measurement schedule
 * and the result.
 *
 * The result goes to a file, files/benchmarks/runs/&lt;id&gt;.json, not through setResult():
 * Game is singleTask and noHistory, which makes startActivityForResult() unreliable, and it
 * survives the benchmark's own activity being killed while the stream runs. No file for a run
 * means the user left it.
 */
public final class BenchmarkGameRun {
    public static final String EXTRA = "BenchmarkRun";

    private final String id;
    private final Role role;
    private final RunConfig override;   // null: the user's own settings
    private final int warmupMs;
    private final int measureMs;
    private final int windowMs;

    private final List<RawStats> snapshots = new ArrayList<>();
    private boolean done;

    private BenchmarkGameRun(String id, Role role, RunConfig override, int warmupMs, int measureMs, int windowMs) {
        this.id = id;
        this.role = role;
        this.override = override;
        this.warmupMs = warmupMs;
        this.measureMs = measureMs;
        this.windowMs = windowMs;
    }

    public static String request(String id, Role role, RunConfig override, int warmupSeconds, int measureSeconds, int windowSeconds) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("role", role.id);
        if (override != null) {
            o.add("config", BenchmarkReport.configToJson(override));
        }
        o.addProperty("warmupMs", warmupSeconds * 1000);
        o.addProperty("measureMs", measureSeconds * 1000);
        o.addProperty("windowMs", windowSeconds * 1000);
        return o.toString();
    }

    /** Null when Game was not started for a benchmark. */
    public static BenchmarkGameRun fromIntent(Intent intent) {
        String json = intent != null ? intent.getStringExtra(EXTRA) : null;
        if (json == null) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Role role = Role.fromId(o.get("role").getAsString());
            RunConfig config = o.has("config") ? BenchmarkReport.configFromJson(o.getAsJsonObject("config")) : null;
            return new BenchmarkGameRun(o.get("id").getAsString(), role != null ? role : Role.CANDIDATE, config,
                    o.get("warmupMs").getAsInt(), o.get("measureMs").getAsInt(), o.get("windowMs").getAsInt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static File resultFile(Context context, String id) {
        return new File(new File(context.getFilesDir(), "benchmarks/runs"), id + ".json");
    }

    public boolean isDone() {
        return done;
    }

    /** In memory, right after Game reads the settings: nothing is saved. */
    public void applyTo(PreferenceConfiguration prefs) {
        if (override != null) {
            BenchmarkSettings.applyTo(prefs, override);
            // On a metered network Game streams at this one instead
            prefs.meteredBitrate = override.bitrateKbps;
        }
        // The benchmark measures the stream, not a voice call
        prefs.enableMicPassthrough = false;
    }

    public interface StatsSource {
        RawStats snapshot();
    }

    /**
     * From the first frame: wait out the warm-up, snapshot the stats every window until the
     * measured time is over, then call onComplete. Snapshots carry their own clock, so a
     * late tick is still exact.
     */
    public void start(Handler handler, StatsSource stats, Runnable onComplete) {
        final long startMs = android.os.SystemClock.uptimeMillis();
        final int windows = measureMs / windowMs;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done) {
                    return;
                }
                RawStats s = stats.snapshot();
                if (s != null && (snapshots.isEmpty() || s.elapsedUs > snapshots.get(snapshots.size() - 1).elapsedUs)) {
                    snapshots.add(s);
                }
                long elapsed = android.os.SystemClock.uptimeMillis() - startMs;
                if (snapshots.size() > windows || elapsed > warmupMs + measureMs + 10_000L) {
                    onComplete.run();
                    return;
                }
                long next = (long) warmupMs + (long) snapshots.size() * windowMs;
                handler.postDelayed(this, Math.max(250, next - elapsed));
            }
        }, warmupMs);
    }

    /** Scores the run from what start() collected and writes the result file. */
    public void complete(Context context, RunConfig effective, double fps) {
        RunResult r = newResult(effective);
        int windows = measureMs / windowMs;
        if (snapshots.size() <= windows) {
            r.status = Status.FAILED;
            r.note = snapshots.size() < 2 ? "No frames arrived to measure." : "The run was cut short.";
            write(context, r);
            return;
        }

        RawStats run = snapshots.get(snapshots.size() - 1).minus(snapshots.get(0));
        r.metrics = BenchmarkScoring.metricsFromStats(run, fps);
        r.scores = BenchmarkScoring.computeScores(r.metrics);
        if (r.metrics.framesRendered == 0) {
            r.status = Status.FAILED;
            r.note = "No frames were shown.";
            write(context, r);
            return;
        }

        List<Double> latencies = new ArrayList<>();
        List<Double> smoothness = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            Scores window = BenchmarkScoring.computeScores(
                    BenchmarkScoring.metricsFromStats(snapshots.get(i).minus(snapshots.get(i - 1)), fps));
            latencies.add(window.latency);
            smoothness.add(window.smoothness);
        }
        r.spreadLatency = BenchmarkScoring.roundScore(BenchmarkScoring.standardDeviation(latencies));
        r.spreadSmoothness = BenchmarkScoring.roundScore(BenchmarkScoring.standardDeviation(smoothness));

        // The reference streams whatever the user streams; anything else must run as asked
        if (override != null && (effective.codec != override.codec || !effective.pacing.equals(override.pacing))) {
            r.status = Status.FALLBACK;
            r.note = "Ran as " + effective.codec.id + ", " + effective.pacing + " instead.";
        }
        write(context, r);
    }

    /** The stream failed before the run could be measured. */
    public void fail(Context context, RunConfig requested, String error) {
        RunResult r = newResult(requested);
        r.status = Status.FAILED;
        r.note = error;
        write(context, r);
    }

    private RunResult newResult(RunConfig effective) {
        RunResult r = new RunResult();
        r.role = role;
        r.requested = override != null ? override.copy() : effective.copy();
        r.effective = effective.copy();
        return r;
    }

    private void write(Context context, RunResult result) {
        if (done) {
            return;
        }
        done = true;
        File file = resultFile(context, id);
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(BenchmarkReport.runToJson(result).toString());
        } catch (IOException ignored) {
            // No file reads as "the user left the run": the benchmark stops, nothing is lost
        }
    }
}
