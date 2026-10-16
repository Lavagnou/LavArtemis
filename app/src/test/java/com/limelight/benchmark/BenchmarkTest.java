package com.limelight.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limelight.benchmark.BenchmarkPlanner.PlannedRun;
import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Metrics;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.RawStats;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Benchmark planning, scoring and reports. The reference values come from LavApollo's
 * docs/benchmark.md; the desktop client's tests/benchmark/tst_benchmark.cpp checks the same
 * ones, so both clients score and plan a run identically.
 */
public class BenchmarkTest {
    private static final double EPS = 0.0001;
    private static final List<String> ANDROID_PACING =
            Arrays.asList("ull", "paced-ull", "predictive", "default", "vrr-low-latency", "vrr-balanced");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static Metrics perfectAt60() {
        Metrics m = new Metrics();
        m.targetFps = 60;
        m.framesRendered = 1200;
        m.framesExpected = 1200;
        m.presentP50Ms = 16.7;
        m.presentP99Ms = 16.7;
        return m;
    }

    private static BenchmarkPlanner.Input input(Mode mode) {
        BenchmarkPlanner.Input in = new BenchmarkPlanner.Input();
        in.mode = mode;
        in.baseline = new RunConfig(Codec.HEVC, 20000, "ull");
        in.codecs = new ArrayList<>(Arrays.asList(Codec.H264, Codec.HEVC, Codec.AV1));
        in.pacingModes = ANDROID_PACING;
        in.defaultBitrateKbps = 20000;
        in.pyroWaveBitrateKbps = 300000;
        return in;
    }

    private static RunResult resultFor(PlannedRun run, double score) {
        RunResult r = new RunResult();
        r.role = run.role;
        r.status = run.skip ? Status.SKIPPED : Status.OK;
        r.requested = run.config.copy();
        r.effective = run.config.copy();
        r.scores.latency = score;
        r.scores.smoothness = score;
        r.scores.balanced = score;
        return r;
    }

    private static List<PlannedRun> drive(BenchmarkPlanner planner, ToDoubleFunction<RunConfig> score, List<RunResult> out) {
        List<RunResult> results = out != null ? out : new ArrayList<>();
        List<PlannedRun> runs = new ArrayList<>();
        for (int guard = 0; guard < 100; guard++) {
            PlannedRun run = planner.next(results);
            if (run == null) {
                break;
            }
            runs.add(run);
            results.add(resultFor(run, score.applyAsDouble(run.config)));
        }
        return runs;
    }

    @Test
    public void histogram() {
        PresentHistogram h = new PresentHistogram();
        for (int i = 0; i < 98; i++) {
            h.record(16.7);
        }
        h.record(33.4);
        h.record(50.1);
        assertEquals(100, h.total());
        assertEquals(16.625, h.percentileMs(50), EPS);
        assertEquals(33.375, h.percentileMs(99), EPS);
        assertEquals(2, h.countAbove(25.0));

        PresentHistogram warmup = new PresentHistogram();
        warmup.record(16.7);
        warmup.record(50.1);
        PresentHistogram run = h.minus(warmup);
        assertEquals(98, run.total());
        assertEquals(1, run.countAbove(25.0));

        assertEquals(0, new PresentHistogram().percentileMs(50), EPS);
        h.record(0);
        h.record(-3);
        assertEquals(100, h.total());
        h.record(250);
        assertEquals(99.875, h.percentileMs(100), EPS);
    }

    @Test
    public void referenceScores() {
        Metrics m = perfectAt60();
        m.totalMs = 10;
        assertEquals(81.9, BenchmarkScoring.computeScores(m).latency, EPS);
        m.totalMs = 20;
        assertEquals(67.0, BenchmarkScoring.computeScores(m).latency, EPS);
        m.totalMs = 40;
        assertEquals(44.9, BenchmarkScoring.computeScores(m).latency, EPS);

        assertEquals(100.0, BenchmarkScoring.computeScores(perfectAt60()).smoothness, EPS);
        m = perfectAt60();
        m.stutterPct = 1;
        assertEquals(81.9, BenchmarkScoring.computeScores(m).smoothness, EPS);
        m = perfectAt60();
        m.framesRendered = 1080;
        assertEquals(81.0, BenchmarkScoring.computeScores(m).smoothness, EPS);
        m = perfectAt60();
        m.presentP99Ms = 25.0;
        assertEquals(66.8, BenchmarkScoring.computeScores(m).smoothness, EPS);

        m = perfectAt60();
        m.totalMs = 20;
        BenchmarkTypes.Scores s = BenchmarkScoring.computeScores(m);
        assertEquals(83.5, s.balanced, EPS);
        assertEquals(67.0, s.forGoal(Goal.LATENCY), EPS);

        m = perfectAt60();
        m.framesRendered = 1300;
        assertEquals(100.0, BenchmarkScoring.computeScores(m).smoothness, EPS);
        assertEquals(0.0, BenchmarkScoring.computeScores(new Metrics()).smoothness, EPS);

        assertEquals(1.0, BenchmarkScoring.standardDeviation(Arrays.asList(1.0, 3.0)), EPS);
        assertEquals(0.0, BenchmarkScoring.standardDeviation(Arrays.asList(5.0)), EPS);
    }

    @Test
    public void metricsFromStats() {
        RawStats s = new RawStats();
        s.elapsedUs = 20e6;
        s.framesTotal = 1210;
        s.framesNetworkLost = 10;
        s.framesReceived = 1200;
        s.framesDecoded = 1200;
        s.framesRendered = 1190;
        s.hostLatencySum = 1200 * 41;
        s.hostLatencyFrames = 1200;
        s.receiveSumUs = 1200 * 2300;
        s.decodeSumUs = 1200 * 1900;
        s.queueSumUs = 1190 * 1000;
        s.renderSumUs = 1190 * 6400;
        s.rttSumMs = 20 * 2.4;
        s.rttVarianceSumMs = 20 * 0.3;
        s.rttSamples = 20;
        for (int i = 0; i < 1189; i++) {
            s.presents.record(16.7);
        }

        Metrics m = BenchmarkScoring.metricsFromStats(s, 60);
        assertEquals(4.1, m.hostMs, EPS);
        assertEquals(1.2, m.networkMs, EPS);
        assertEquals(2.3, m.receiveMs, EPS);
        assertEquals(1.9, m.decodeMs, EPS);
        assertEquals(1.0, m.queueMs, EPS);
        assertEquals(6.4, m.renderMs, EPS);
        assertEquals(16.9, m.totalMs, EPS);
        assertEquals(1200, m.framesExpected);
        assertEquals(100.0 * 10 / 1210, m.networkLossPct, EPS);
        assertEquals(0, m.stutterPct, EPS);

        RawStats later = new RawStats();
        later.elapsedUs = s.elapsedUs + 5e6;
        later.framesRendered = s.framesRendered + 300;
        later.presents = new PresentHistogram(s.presents);
        later.presents.record(40);
        RawStats delta = later.minus(s);
        assertEquals(5e6, delta.elapsedUs, EPS);
        assertEquals(300, delta.framesRendered);
        assertEquals(1, delta.presents.total());
    }

    @Test
    public void quickPlan() {
        BenchmarkPlanner planner = new BenchmarkPlanner(input(Mode.QUICK));
        List<PlannedRun> runs = drive(planner, c -> 50, null);
        assertEquals(6, runs.size());
        assertEquals(planner.estimatedRuns(), runs.size());
        assertEquals(Role.BASELINE, runs.get(0).role);
        assertEquals(Role.BASELINE_REPEAT, runs.get(5).role);
        assertEquals(new RunConfig(Codec.H264, 20000, "ull"), runs.get(1).config);
        assertEquals(Codec.AV1, runs.get(2).config.codec);
        assertEquals("paced-ull", runs.get(3).config.pacing);
        assertEquals("predictive", runs.get(4).config.pacing);

        BenchmarkPlanner.Input pyro = input(Mode.QUICK);
        pyro.baseline = new RunConfig(Codec.PYROWAVE, 300000, "ull");
        pyro.codecs = Arrays.asList(Codec.H264, Codec.PYROWAVE);
        List<PlannedRun> pyroRuns = drive(new BenchmarkPlanner(pyro), c -> 50, null);
        assertEquals(new RunConfig(Codec.H264, 20000, "ull"), pyroRuns.get(1).config);
    }

    @Test
    public void fullPlan() {
        BenchmarkPlanner.Input in = input(Mode.FULL);
        in.codecs = Arrays.asList(Codec.H264, Codec.HEVC, Codec.AV1, Codec.PYROWAVE);
        List<PlannedRun> runs = drive(new BenchmarkPlanner(in), c -> 50, null);
        // baseline + h264 x3 + hevc x2 + av1 x3 + pyrowave + 5 pacing modes + repeat
        assertEquals(16, runs.size());
        boolean vrrSeen = false;
        for (int i = 1; i + 1 < runs.size(); i++) {
            if (BenchmarkTypes.isVrrPacing(runs.get(i).config.pacing)) {
                vrrSeen = true;
            }
            else {
                assertFalse("VRR runs come last, together", vrrSeen);
            }
        }
        assertTrue(vrrSeen);
        assertEquals(new RunConfig(Codec.H264, 10000, "ull"), runs.get(1).config);
        assertEquals(new RunConfig(Codec.HEVC, 10000, "ull"), runs.get(4).config);
    }

    @Test
    public void fallbacksSkipTheirKind() {
        BenchmarkPlanner planner = new BenchmarkPlanner(input(Mode.FULL));
        List<RunResult> results = new ArrayList<>();
        int skipped = 0;
        for (int guard = 0; guard < 100; guard++) {
            PlannedRun run = planner.next(results);
            if (run == null) {
                break;
            }
            RunResult r = resultFor(run, 50);
            if (!run.skip && run.config.codec == Codec.AV1 && run.role == Role.CANDIDATE) {
                r.status = Status.FALLBACK;
                r.effective.codec = Codec.HEVC;
            }
            if (!run.skip && run.config.pacing.equals("vrr-low-latency")) {
                r.status = Status.FALLBACK;
                r.effective.pacing = "ull";
            }
            if (run.skip) {
                skipped++;
                assertTrue(run.config.codec == Codec.AV1 || BenchmarkTypes.isVrrPacing(run.config.pacing));
            }
            results.add(r);
        }
        assertEquals(3, skipped);
    }

    @Test
    public void adaptivePlan() {
        BenchmarkPlanner.Input in = input(Mode.ADAPTIVE);
        in.goal = Goal.LATENCY;
        BenchmarkPlanner planner = new BenchmarkPlanner(in);
        // AV1 wins, latency peaks at 30 Mbps, and predictive pacing is best
        List<PlannedRun> runs = drive(planner, c -> {
            double s = c.codec == Codec.AV1 ? 60 : 50;
            s -= Math.abs(c.bitrateKbps - 30000) / 2000.0;
            if (c.pacing.equals("predictive")) {
                s += 5;
            }
            return s;
        }, null);

        assertEquals(Role.BASELINE, runs.get(0).role);
        assertEquals(Codec.H264, runs.get(1).config.codec);
        assertEquals(Codec.AV1, runs.get(2).config.codec);
        assertEquals(new RunConfig(Codec.AV1, 10000, "ull"), runs.get(3).config);
        assertEquals(30000, runs.get(4).config.bitrateKbps);
        assertEquals(40000, runs.get(5).config.bitrateKbps);
        assertEquals(60000, runs.get(6).config.bitrateKbps);
        assertEquals(new RunConfig(Codec.AV1, 30000, "paced-ull"), runs.get(7).config);
        PlannedRun confirm = runs.get(runs.size() - 2);
        assertEquals(Role.CONFIRM, confirm.role);
        assertEquals(new RunConfig(Codec.AV1, 30000, "predictive"), confirm.config);
        assertEquals(Role.BASELINE_REPEAT, runs.get(runs.size() - 1).role);
        assertTrue(runs.size() <= planner.estimatedRuns());

        // Climbing stops at the first lossy run
        BenchmarkPlanner lossy = new BenchmarkPlanner(input(Mode.ADAPTIVE));
        List<RunResult> results = new ArrayList<>();
        Set<Integer> climbed = new HashSet<>();
        for (int guard = 0; guard < 100; guard++) {
            PlannedRun run = lossy.next(results);
            if (run == null) {
                break;
            }
            if (run.config.bitrateKbps > 20000) {
                climbed.add(run.config.bitrateKbps);
            }
            RunResult r = resultFor(run, 50 + run.config.bitrateKbps / 1000.0);
            if (run.config.bitrateKbps >= 30000) {
                r.metrics.networkLossPct = 3;
            }
            results.add(r);
        }
        assertEquals(1, climbed.size());
    }

    @Test
    public void reportRoundTripAndHistory() throws Exception {
        BenchmarkReport report = new BenchmarkReport();
        report.id = "abc";
        report.createdAtMs = 1792190400000L; // 2026-10-16T22:40:00Z
        report.mode = Mode.ADAPTIVE;
        report.goal = Goal.SMOOTHNESS;
        report.hostUuid = "HOST";
        report.hostName = "ATLAS-PC";
        report.width = 1920;
        report.height = 1080;
        report.fps = 60;
        RunResult base = new RunResult();
        base.role = Role.BASELINE;
        base.requested = new RunConfig(Codec.HEVC, 20000, "ull");
        base.effective = base.requested.copy();
        base.scores.balanced = 80;
        base.metrics.totalMs = 16.9;
        RunResult fallback = new RunResult();
        fallback.role = Role.CANDIDATE;
        fallback.status = Status.FALLBACK;
        fallback.requested = new RunConfig(Codec.AV1, 20000, "ull");
        fallback.effective = new RunConfig(Codec.HEVC, 20000, "ull");
        fallback.note = "fell back";
        RunResult repeat = new RunResult();
        repeat.role = Role.BASELINE_REPEAT;
        repeat.requested = base.requested.copy();
        repeat.effective = base.requested.copy();
        repeat.scores.balanced = 73.5;
        report.runs.addAll(Arrays.asList(base, fallback, repeat));
        report.baselineDrift = BenchmarkReport.computeBaselineDrift(report.runs);
        assertEquals(6.5, report.baselineDrift, EPS);
        assertTrue(report.unstable());

        JsonObject json = report.toJson();
        assertEquals("2026-10-16T22:40:00Z", json.get("createdAt").getAsString());
        BenchmarkReport back = BenchmarkReport.fromJson(json);
        assertNotNull(back);
        assertEquals("abc", back.id);
        assertEquals(report.createdAtMs, back.createdAtMs);
        assertEquals(Mode.ADAPTIVE, back.mode);
        assertEquals(Goal.SMOOTHNESS, back.goal);
        assertEquals(3, back.runs.size());
        assertEquals(Status.FALLBACK, back.runs.get(1).status);
        assertEquals(Codec.HEVC, back.runs.get(1).effective.codec);
        assertEquals("fell back", back.runs.get(1).note);
        assertEquals(16.9, back.runs.get(0).metrics.totalMs, EPS);

        JsonArray runs = json.getAsJsonArray("runs");
        JsonObject future = runs.get(0).getAsJsonObject().deepCopy();
        future.getAsJsonObject("requested").addProperty("codec", "vvc");
        runs.add(future);
        json.addProperty("somethingNew", 1);
        assertEquals(3, BenchmarkReport.fromJson(json).runs.size());
        json.addProperty("schema", "other");
        assertNull(BenchmarkReport.fromJson(json));

        BenchmarkReport.History history = new BenchmarkReport.History(folder.newFolder("benchmarks"));
        for (int i = 0; i < BenchmarkReport.History.MAX_REPORTS + 3; i++) {
            BenchmarkReport r = BenchmarkReport.fromJson(report.toJson());
            r.id = "r" + i;
            r.createdAtMs = report.createdAtMs + i * 1000L;
            r.hostUuid = i % 2 == 1 ? "HOST" : "OTHER";
            assertTrue(history.save(r));
        }
        List<BenchmarkReport> all = history.load(null);
        assertEquals(BenchmarkReport.History.MAX_REPORTS, all.size());
        assertEquals("r" + (BenchmarkReport.History.MAX_REPORTS + 2), all.get(0).id);
        List<BenchmarkReport> mine = history.load("host");
        assertFalse(mine.isEmpty());
        for (BenchmarkReport r : mine) {
            assertEquals("HOST", r.hostUuid);
        }
        assertTrue(history.remove(all.get(0).id));
        assertEquals(BenchmarkReport.History.MAX_REPORTS - 1, history.load(null).size());
    }
}
