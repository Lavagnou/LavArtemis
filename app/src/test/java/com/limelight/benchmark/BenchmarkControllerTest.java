package com.limelight.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;

import org.junit.Test;

/** The run sequence BenchmarkActivity follows, without Android. */
public class BenchmarkControllerTest {
    private static final long HOST_HEVC_AV1 = 0x00000100L | 0x00010000L;

    private static RunResult ok(BenchmarkController.Run run, RunConfig effective) {
        RunResult r = new RunResult();
        r.status = Status.OK;
        r.requested = run.config.copy();
        r.effective = effective.copy();
        r.scores.balanced = 50;
        return r;
    }

    @Test
    public void baselineFirstThenPlanFromWhatItRanAs() {
        // Auto was guessed as HEVC, but the device streamed AV1
        RunConfig guess = new RunConfig(Codec.HEVC, 20000, "default");
        BenchmarkController c = new BenchmarkController(new BenchmarkReport(),
                BenchmarkController.plannerInput(Mode.QUICK, Goal.BALANCED, guess, HOST_HEVC_AV1, 20000));

        BenchmarkController.Run first = c.next();
        assertEquals(Role.BASELINE, first.role);
        assertTrue(first.userSettings);
        c.complete(ok(first, new RunConfig(Codec.AV1, 20000, "default")));
        assertEquals(guess, c.report.runs.get(0).requested);

        BenchmarkController.Run second = c.next();
        assertNotNull(second);
        assertFalse(second.userSettings);
        assertTrue("other codecs are compared with the one really used", second.config.codec != Codec.AV1);
    }

    @Test
    public void leavingARunStopsTheBenchmark() {
        BenchmarkController c = new BenchmarkController(new BenchmarkReport(),
                BenchmarkController.plannerInput(Mode.QUICK, Goal.BALANCED, new RunConfig(Codec.H264, 10000, "ull"), 0, 10000));
        BenchmarkController.Run first = c.next();
        c.complete(ok(first, first.config));
        c.next();
        c.complete(null);
        assertTrue(c.isCancelled());
        assertNull(c.next());
        c.finish();
        assertTrue(c.report.cancelled);
    }

    @Test
    public void aFailedBaselineEndsIt() {
        BenchmarkController c = new BenchmarkController(new BenchmarkReport(),
                BenchmarkController.plannerInput(Mode.FULL, Goal.BALANCED, new RunConfig(Codec.H264, 10000, "ull"), 0, 10000));
        BenchmarkController.Run first = c.next();
        RunResult failed = ok(first, first.config);
        failed.status = Status.FAILED;
        c.complete(failed);
        assertNull(c.next());
        assertTrue(c.baselineFailed());
    }

    @Test
    public void hostCodecsLimitThePlan() {
        BenchmarkPlanner.Input in = BenchmarkController.plannerInput(Mode.QUICK, Goal.BALANCED,
                new RunConfig(Codec.H264, 10000, "ull"), 0, 10000);
        assertEquals(1, in.codecs.size());
        assertEquals(4, BenchmarkController.estimatedRuns(in)); // baseline, two pacing modes, baseline again
    }
}
