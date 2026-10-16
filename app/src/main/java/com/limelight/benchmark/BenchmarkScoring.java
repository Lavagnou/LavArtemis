package com.limelight.benchmark;

import com.limelight.benchmark.BenchmarkTypes.Metrics;
import com.limelight.benchmark.BenchmarkTypes.RawStats;
import com.limelight.benchmark.BenchmarkTypes.Scores;

import java.util.List;

/**
 * Metrics and scores (docs/benchmark.md, "Measurements" and "Scores"). The desktop client's
 * benchmarkscoring.cpp computes the same numbers; both clients' tests check the doc's
 * reference values.
 */
public final class BenchmarkScoring {
    private BenchmarkScoring() {
    }

    private static double average(double sum, long count) {
        return count > 0 ? sum / count : 0;
    }

    /** Averages and percentiles of a stretch of stream. targetFps is the stream's rate. */
    public static Metrics metricsFromStats(RawStats s, double targetFps) {
        Metrics m = new Metrics();
        m.targetFps = targetFps;
        m.measuredSeconds = s.elapsedUs / 1e6;

        m.hostMs = average(s.hostLatencySum, s.hostLatencyFrames) / 10.0;
        m.rttMs = s.rttSamples > 0 ? s.rttSumMs / s.rttSamples : 0;
        m.rttVarianceMs = s.rttSamples > 0 ? s.rttVarianceSumMs / s.rttSamples : 0;
        m.networkMs = m.rttMs / 2;
        m.receiveMs = average(s.receiveSumUs, s.framesReceived) / 1000.0;
        m.decodeMs = average(s.decodeSumUs, s.framesDecoded) / 1000.0;
        m.queueMs = average(s.queueSumUs, s.framesRendered) / 1000.0;
        m.renderMs = average(s.renderSumUs, s.framesRendered) / 1000.0;
        m.totalMs = m.hostMs + m.networkMs + m.receiveMs + m.decodeMs + m.queueMs + m.renderMs;

        m.framesRendered = s.framesRendered;
        m.framesExpected = Math.round(targetFps * m.measuredSeconds);
        m.networkLossPct = s.framesTotal > 0 ? 100.0 * s.framesNetworkLost / s.framesTotal : 0;

        m.presentP50Ms = s.presents.percentileMs(50);
        m.presentP95Ms = s.presents.percentileMs(95);
        m.presentP99Ms = s.presents.percentileMs(99);
        if (targetFps > 0 && s.presents.total() > 0) {
            double periodMs = 1000.0 / targetFps;
            m.stutterPct = 100.0 * s.presents.countAbove(1.5 * periodMs) / s.presents.total();
        }
        return m;
    }

    /** The two scores out of 100. Absolute formulas, so that reports stay comparable over time. */
    public static Scores computeScores(Metrics m) {
        Scores scores = new Scores();

        // A frame's whole trip, from capture to screen: every 50 ms divides the score by e
        double latency = 100.0 * Math.exp(-m.totalMs / 50.0);

        // Frames that never show up (squared: a missing frame is seen twice, as a gap and a
        // repeat), hitches longer than one and a half frames, and the irregularity of the rest
        double periodMs = m.targetFps > 0 ? 1000.0 / m.targetFps : 0;
        double delivered = m.framesExpected > 0 ? Math.min(1.0, (double) m.framesRendered / m.framesExpected) : 0;
        double jitter = periodMs > 0 ? Math.max(0.0, m.presentP99Ms - m.presentP50Ms) / periodMs : 0;
        double smoothness = 100.0 * delivered * delivered * Math.exp(-20.0 * m.stutterPct / 100.0) / (1.0 + jitter);

        scores.latency = roundScore(latency);
        scores.smoothness = roundScore(smoothness);
        scores.balanced = roundScore((scores.latency + scores.smoothness) / 2.0);
        return scores;
    }

    /** Population standard deviation; 0 for fewer than two values. */
    public static double standardDeviation(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double mean = 0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.size();
        double squares = 0;
        for (double v : values) {
            squares += (v - mean) * (v - mean);
        }
        return Math.sqrt(squares / values.size());
    }

    /** Rounds to one decimal, half away from zero like C++'s std::round. */
    public static double roundScore(double value) {
        double scaled = value * 10.0;
        return (scaled < 0 ? -Math.floor(-scaled + 0.5) : Math.floor(scaled + 0.5)) / 10.0;
    }
}
