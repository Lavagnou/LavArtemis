package com.limelight.binding.video;

import java.util.Arrays;

/**
 * Times each frame by the host's capture clock instead of by arrival (VRR pacing).
 *
 * The host stamps every frame with a 90 kHz RTP timestamp when it captures it. With a
 * VRR-aware host (clientVrrRequested=1, LavApollo: 1000 Hz virtual display) that stamp is
 * within a millisecond of the game presenting the frame, so presenting each frame at
 * "its RTP time + a constant delay" reproduces the game's own cadence, network jitter
 * absorbed. That is the core of Nonary's desktop VRR pacer, reduced to what Android can
 * act on: MediaCodec.releaseOutputBuffer(index, timestampNs).
 *
 * - Clock offset: arrival - RTP time, minimum over a sliding window. The minimum is the
 *   frame that met no queuing; the window lets it follow clock drift between the devices.
 * - Delay: a high percentile of how long after that ideal time frames are ready to show,
 *   so most frames are ready by their target. The profile picks the percentile and caps
 *   the delay in stream frames (1 / 2 / 4, Nonary's allowances).
 *
 * Plain Java with no Android dependency, so it can be unit tested on the JVM.
 */
public final class VrrPacing {
    public static final int MODE_LOW_LATENCY = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_SMOOTH = 2;

    private static final long RTP_CLOCK_HZ = 90_000;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    // Long enough to contain a frame that met an idle network, short enough to follow drift
    private static final long OFFSET_WINDOW_NS = 2_000_000_000L;
    private static final int READINESS_SAMPLES = 240;
    // Presented frames arrive at SurfaceFlinger a little before their target
    private static final long PRESENT_MARGIN_NS = 1_000_000L;

    private final long framePeriodNs;
    private final double readinessPercentile;
    private final long maxDelayNs;

    // RTP timestamps are 32-bit and wrap every ~13 hours; keep a 64-bit running count
    private long lastRtp32 = -1;
    private long rtpTicks;

    private final long[] offsetSampleNs = new long[512];
    private final long[] offsetSampleTimeNs = new long[512];
    private int offsetHead;
    private int offsetCount;
    private long offsetNs;
    private boolean offsetValid;

    private final long[] readinessNs = new long[READINESS_SAMPLES];
    private final long[] sortScratch = new long[READINESS_SAMPLES];
    private int readinessHead;
    private int readinessCount;
    private long delayNs;

    private int consecutiveDrops;

    public VrrPacing(int streamFps, int mode) {
        framePeriodNs = NANOS_PER_SECOND / Math.max(1, streamFps);
        switch (mode) {
            case MODE_LOW_LATENCY:
                readinessPercentile = 0.90;
                maxDelayNs = framePeriodNs;
                break;
            case MODE_SMOOTH:
                readinessPercentile = 0.995;
                maxDelayNs = 4 * framePeriodNs;
                break;
            default:
                readinessPercentile = 0.97;
                maxDelayNs = 2 * framePeriodNs;
                break;
        }
        delayNs = framePeriodNs / 2;
    }

    /**
     * Record a frame's arrival and return its RTP time, in nanoseconds on the host's clock.
     */
    public long onArrival(int rtpTimestamp, long arrivalNs) {
        long rtp32 = rtpTimestamp & 0xFFFFFFFFL;
        if (lastRtp32 < 0) {
            rtpTicks = rtp32;
        } else {
            // Signed 32-bit difference: wraps forward, and a reordered frame steps back
            rtpTicks += (int) (rtp32 - lastRtp32);
        }
        lastRtp32 = rtp32;
        long rtpNs = rtpTicks * NANOS_PER_SECOND / RTP_CLOCK_HZ;
        recordOffset(arrivalNs - rtpNs, arrivalNs);
        return rtpNs;
    }

    /**
     * Record that a frame has been decoded and return when to show it, on this device's clock.
     */
    public long targetFor(long rtpNs, long readyNs) {
        if (!offsetValid) {
            return readyNs;
        }
        long idealNs = rtpNs + offsetNs;
        recordReadiness(Math.max(0, readyNs - idealNs));
        return idealNs + delayNs + PRESENT_MARGIN_NS;
    }

    /**
     * Whether to skip a frame whose target has already passed by more than a frame: showing
     * it now would only push every following frame late. Never twice in a row, so a long
     * stall still shows something.
     */
    public boolean shouldDrop(long targetNs, long nowNs) {
        if (nowNs - targetNs > framePeriodNs && consecutiveDrops == 0) {
            consecutiveDrops++;
            return true;
        }
        consecutiveDrops = 0;
        return false;
    }

    public long getDelayNs() {
        return delayNs;
    }

    public long getOffsetNs() {
        return offsetNs;
    }

    private void recordOffset(long sampleNs, long nowNs) {
        offsetSampleNs[offsetHead] = sampleNs;
        offsetSampleTimeNs[offsetHead] = nowNs;
        offsetHead = (offsetHead + 1) % offsetSampleNs.length;
        offsetCount = Math.min(offsetCount + 1, offsetSampleNs.length);

        long min = Long.MAX_VALUE;
        for (int i = 0; i < offsetCount; i++) {
            if (nowNs - offsetSampleTimeNs[i] <= OFFSET_WINDOW_NS && offsetSampleNs[i] < min) {
                min = offsetSampleNs[i];
            }
        }
        offsetNs = min;
        offsetValid = true;
    }

    private void recordReadiness(long lateNs) {
        readinessNs[readinessHead] = lateNs;
        readinessHead = (readinessHead + 1) % readinessNs.length;
        readinessCount = Math.min(readinessCount + 1, readinessNs.length);

        System.arraycopy(readinessNs, 0, sortScratch, 0, readinessCount);
        Arrays.sort(sortScratch, 0, readinessCount);
        int index = (int) Math.min(readinessCount - 1, Math.floor(readinessPercentile * readinessCount));
        delayNs = Math.min(maxDelayNs, sortScratch[index]);
    }
}
