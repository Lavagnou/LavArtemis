package com.limelight.benchmark;

/**
 * The running benchmark as the prototype shows it (planche V8-44, bench.step / bench.pct): « Essai 4 sur
 * 6 » and a bar at the share of runs done. The number never passes the estimate (the adaptive mode
 * can add a run). Pure logic, tested by BenchmarkTest.
 */
public final class BenchmarkProgress {
    private BenchmarkProgress() {}

    /** The run being measured, 1-based, at most the estimate */
    public static int step(int runsDone, int estimate) {
        int total = Math.max(1, estimate);
        return Math.max(1, Math.min(runsDone + 1, total));
    }

    /** Share of the bar, 0..100 (prototype: runs done / runs) */
    public static int percent(int runsDone, int estimate) {
        int total = Math.max(1, estimate);
        return Math.max(0, Math.min(100, Math.round(runsDone * 100f / total)));
    }
}
