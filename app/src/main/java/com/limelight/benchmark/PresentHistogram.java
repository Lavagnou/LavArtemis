package com.limelight.benchmark;

/**
 * Present-to-present intervals over a whole session, in 0.25 ms buckets up to 100 ms.
 *
 * PacingStats keeps the last 512 intervals, enough for an overlay but not for a benchmark
 * run, whose percentiles must cover every frame it measured. Two copies of the same
 * session's histogram subtract into the histogram of the time between them, which is how a
 * run drops its warm-up. Same buckets and percentile rule as the desktop client
 * (backend/benchmark/presenthistogram.h). Not thread-safe.
 */
public final class PresentHistogram {
    public static final int BUCKETS_PER_MS = 4;
    public static final int BUCKETS = 100 * BUCKETS_PER_MS; // the last one also holds everything longer

    private final long[] counts = new long[BUCKETS];
    private long total;

    public PresentHistogram() {
    }

    public PresentHistogram(PresentHistogram other) {
        System.arraycopy(other.counts, 0, counts, 0, BUCKETS);
        total = other.total;
    }

    public void record(double intervalMs) {
        if (!(intervalMs > 0)) {
            return;
        }
        int bucket = (int) (intervalMs * BUCKETS_PER_MS);
        counts[Math.min(bucket, BUCKETS - 1)]++;
        total++;
    }

    public long total() {
        return total;
    }

    /** Nearest rank, reported at the bucket's centre; 0 without samples. */
    public double percentileMs(double p) {
        if (total == 0) {
            return 0;
        }
        long rank = Math.max(1, (long) Math.ceil(p / 100.0 * total));
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += counts[i];
            if (seen >= rank) {
                return (i + 0.5) / BUCKETS_PER_MS;
            }
        }
        return (BUCKETS - 0.5) / BUCKETS_PER_MS;
    }

    /** Intervals whose bucket lies wholly above thresholdMs. */
    public long countAbove(double thresholdMs) {
        long above = 0;
        for (int i = 0; i < BUCKETS; i++) {
            if ((double) i / BUCKETS_PER_MS >= thresholdMs) {
                above += counts[i];
            }
        }
        return above;
    }

    public PresentHistogram minus(PresentHistogram earlier) {
        PresentHistogram result = new PresentHistogram();
        for (int i = 0; i < BUCKETS; i++) {
            result.counts[i] = Math.max(0, counts[i] - earlier.counts[i]);
            result.total += result.counts[i];
        }
        return result;
    }

    public void add(PresentHistogram other) {
        for (int i = 0; i < BUCKETS; i++) {
            counts[i] += other.counts[i];
        }
        total += other.total;
    }
}
