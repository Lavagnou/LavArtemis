package com.limelight.binding.video;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class VrrPacingTest {
    private static final long MS = 1_000_000L;
    private static final int TICKS_PER_FRAME_60 = 1500; // 90 kHz / 60 fps

    // Feeds frames captured at an uneven game cadence through a jittery network and a
    // decoder taking decodeMs; returns the presented intervals' max deviation from the
    // capture intervals, in ns.
    private long maxCadenceErrorNs(int mode, long jitterMs, long decodeMs) {
        VrrPacing pacing = new VrrPacing(60, mode);
        Random random = new Random(42);
        long clockOffsetNs = 123_456_789_000L; // the device clock is unrelated to the host's
        int rtp = 0x7FFFF000; // close to the signed wrap on purpose
        long lastCaptureNs = 0;
        long lastTargetNs = 0;
        long maxErrorNs = 0;

        for (int i = 0; i < 600; i++) {
            // Game cadence varies between 14 and 20 ms, as a VRR game would
            int frameTicks = 1260 + random.nextInt(540);
            rtp += frameTicks;
            long captureNs = (rtp & 0xFFFFFFFFL) * 1_000_000_000L / 90_000;

            long arrivalNs = captureNs + clockOffsetNs + 5 * MS + (long) (random.nextDouble() * jitterMs * MS);
            long rtpNs = pacing.onArrival(rtp, arrivalNs);
            long targetNs = pacing.targetFor(rtpNs, arrivalNs + decodeMs * MS);

            if (i > 300) {
                long captureInterval = captureNs - lastCaptureNs;
                long presentInterval = targetNs - lastTargetNs;
                maxErrorNs = Math.max(maxErrorNs, Math.abs(presentInterval - captureInterval));
            }
            lastCaptureNs = captureNs;
            lastTargetNs = targetNs;
        }
        return maxErrorNs;
    }

    @Test
    public void presentsAtTheCaptureCadenceDespiteJitter() {
        // Once the delay has settled, presented intervals follow captured ones: network
        // jitter no longer reaches the screen. Only the delay's slow drift remains.
        assertTrue(maxCadenceErrorNs(VrrPacing.MODE_BALANCED, 4, 3) < 2 * MS);
        assertTrue(maxCadenceErrorNs(VrrPacing.MODE_SMOOTH, 6, 3) < 2 * MS);
    }

    @Test
    public void delayIsCappedPerProfile() {
        long period = 1_000_000_000L / 60;
        for (int mode : new int[] {VrrPacing.MODE_LOW_LATENCY, VrrPacing.MODE_BALANCED, VrrPacing.MODE_SMOOTH}) {
            VrrPacing pacing = new VrrPacing(60, mode);
            int rtp = 0;
            for (int i = 0; i < 300; i++) {
                rtp += TICKS_PER_FRAME_60;
                long arrival = i * period + (i % 2 == 0 ? 0 : 40 * MS); // half the frames very late
                long rtpNs = pacing.onArrival(rtp, arrival);
                pacing.targetFor(rtpNs, arrival);
            }
            long cap = mode == VrrPacing.MODE_LOW_LATENCY ? period : mode == VrrPacing.MODE_SMOOTH ? 4 * period : 2 * period;
            assertTrue("mode " + mode, pacing.getDelayNs() <= cap);
        }
    }

    @Test
    public void offsetFollowsTheEarliestFrame() {
        VrrPacing pacing = new VrrPacing(60, VrrPacing.MODE_BALANCED);
        long period = 1_000_000_000L / 60;
        int rtp = 0;
        for (int i = 0; i < 60; i++) {
            rtp += TICKS_PER_FRAME_60;
            long rtpNs = (rtp & 0xFFFFFFFFL) * 1_000_000_000L / 90_000;
            long queuing = (i == 30) ? 0 : 3 * MS;
            pacing.onArrival(rtp, rtpNs + 10 * MS + queuing);
        }
        assertEquals(10 * MS, pacing.getOffsetNs(), 2_000);
    }

    @Test
    public void dropsALateFrameButNeverTwiceInARow() {
        VrrPacing pacing = new VrrPacing(60, VrrPacing.MODE_BALANCED);
        long now = 1_000 * MS;
        assertTrue(pacing.shouldDrop(now - 30 * MS, now));
        assertFalse(pacing.shouldDrop(now - 30 * MS, now));
        assertFalse(pacing.shouldDrop(now + 5 * MS, now));
        assertTrue(pacing.shouldDrop(now - 30 * MS, now));
    }

    @Test
    public void rtpWrapKeepsTimeMonotonic() {
        VrrPacing pacing = new VrrPacing(60, VrrPacing.MODE_BALANCED);
        long previous = pacing.onArrival(0xFFFFF000, 0);
        long next = pacing.onArrival(0x00000A00, 16 * MS);
        assertTrue(next > previous);
        assertEquals((0x1000 + 0xA00) * 1_000_000_000L / 90_000, next - previous, 20_000);
    }
}
