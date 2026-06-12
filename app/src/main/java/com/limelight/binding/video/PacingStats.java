package com.limelight.binding.video;

import java.util.Arrays;

/**
 * Rolling window of frame presentation intervals used to quantify pacing
 * smoothness. Percentiles of the present-to-present delta are the key
 * fluidity metric: a perfectly paced 60 FPS stream shows p99 ~= 16.7 ms,
 * while judder shows up as a large spread between p50 and p99.
 */
final class PacingStats {
    private static final int WINDOW_SIZE = 512;

    private final float[] deltasMs = new float[WINDOW_SIZE];
    private int writeIndex;
    private int count;
    private long lastPresentNs;

    static final class Snapshot {
        float p50Ms;
        float p95Ms;
        float p99Ms;
        int sampleCount;
    }

    synchronized void recordPresent(long presentTimeNs) {
        if (lastPresentNs != 0) {
            long deltaNs = presentTimeNs - lastPresentNs;
            // Ignore pathological samples (stream pauses, codec recovery)
            if (deltaNs > 0 && deltaNs < 1_000_000_000L) {
                deltasMs[writeIndex] = deltaNs / 1_000_000.0f;
                writeIndex = (writeIndex + 1) % WINDOW_SIZE;
                if (count < WINDOW_SIZE) {
                    count++;
                }
            }
        }
        lastPresentNs = presentTimeNs;
    }

    synchronized Snapshot snapshot() {
        Snapshot snap = new Snapshot();
        snap.sampleCount = count;
        if (count == 0) {
            return snap;
        }
        float[] sorted = new float[count];
        if (count < WINDOW_SIZE) {
            System.arraycopy(deltasMs, 0, sorted, 0, count);
        } else {
            System.arraycopy(deltasMs, 0, sorted, 0, WINDOW_SIZE);
        }
        Arrays.sort(sorted);
        snap.p50Ms = sorted[(int) (count * 0.50f)];
        snap.p95Ms = sorted[Math.min(count - 1, (int) (count * 0.95f))];
        snap.p99Ms = sorted[Math.min(count - 1, (int) (count * 0.99f))];
        return snap;
    }

    synchronized void reset() {
        writeIndex = 0;
        count = 0;
        lastPresentNs = 0;
    }
}
