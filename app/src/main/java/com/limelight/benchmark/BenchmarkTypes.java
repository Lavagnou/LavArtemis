package com.limelight.benchmark;

import java.util.Objects;

/**
 * The benchmark's vocabulary, shared by the planner, the scoring and the report. The rules
 * are specified in LavApollo's docs/benchmark.md and implemented by the desktop client too
 * (backend/benchmark): change both clients together.
 */
public final class BenchmarkTypes {
    private BenchmarkTypes() {
    }

    public enum Codec {
        H264("h264"), HEVC("hevc"), AV1("av1"), PYROWAVE("pyrowave");

        public final String id;

        Codec(String id) {
            this.id = id;
        }

        public static Codec fromId(String id) {
            for (Codec c : values()) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            return null;
        }
    }

    public enum Mode {
        QUICK("quick"), FULL("full"), ADAPTIVE("adaptive");

        public final String id;

        Mode(String id) {
            this.id = id;
        }

        public static Mode fromId(String id) {
            for (Mode m : values()) {
                if (m.id.equals(id)) {
                    return m;
                }
            }
            return null;
        }
    }

    public enum Goal {
        LATENCY("latency"), SMOOTHNESS("smoothness"), BALANCED("balanced");

        public final String id;

        Goal(String id) {
            this.id = id;
        }

        public static Goal fromId(String id) {
            for (Goal g : values()) {
                if (g.id.equals(id)) {
                    return g;
                }
            }
            return null;
        }
    }

    public enum Role {
        BASELINE("baseline"), CANDIDATE("candidate"), BASELINE_REPEAT("baseline-repeat"), CONFIRM("confirm");

        public final String id;

        Role(String id) {
            this.id = id;
        }

        public static Role fromId(String id) {
            for (Role r : values()) {
                if (r.id.equals(id)) {
                    return r;
                }
            }
            return null;
        }
    }

    public enum Status {
        OK("ok"), FALLBACK("fallback"), FAILED("failed"), SKIPPED("skipped");

        public final String id;

        Status(String id) {
            this.id = id;
        }

        public static Status fromId(String id) {
            for (Status s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    /** Pacing modes are platform names; the "vrr-" prefix is the part that changes what the host does. */
    public static boolean isVrrPacing(String pacing) {
        return pacing != null && pacing.startsWith("vrr-");
    }

    public static final class RunConfig {
        public Codec codec = Codec.H264;
        public int bitrateKbps;
        public String pacing = "";

        public RunConfig() {
        }

        public RunConfig(Codec codec, int bitrateKbps, String pacing) {
            this.codec = codec;
            this.bitrateKbps = bitrateKbps;
            this.pacing = pacing;
        }

        public RunConfig copy() {
            return new RunConfig(codec, bitrateKbps, pacing);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RunConfig)) {
                return false;
            }
            RunConfig other = (RunConfig) o;
            return codec == other.codec && bitrateKbps == other.bitrateKbps && Objects.equals(pacing, other.pacing);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codec, bitrateKbps, pacing);
        }

        @Override
        public String toString() {
            return codec.id + "/" + bitrateKbps + "/" + pacing;
        }
    }

    /**
     * What a client measured over (part of) a run, as sums: two snapshots of the same session
     * subtract into the stats of the time between them.
     */
    public static final class RawStats {
        public double elapsedUs;

        public long framesTotal;          // sent by the host, lost ones included
        public long framesNetworkLost;
        public long framesReceived;
        public long framesDecoded;
        public long framesRendered;
        public long framesPacerDropped;

        public long hostLatencySum;       // tenths of a millisecond, as the host sends them
        public long hostLatencyFrames;
        public long receiveSumUs;         // per received frame
        public long decodeSumUs;          // per decoded frame
        public long queueSumUs;           // per rendered frame
        public long renderSumUs;          // per rendered frame

        public double rttSumMs;
        public double rttVarianceSumMs;
        public long rttSamples;

        public PresentHistogram presents = new PresentHistogram();

        public RawStats minus(RawStats earlier) {
            RawStats d = new RawStats();
            d.elapsedUs = elapsedUs - earlier.elapsedUs;
            d.framesTotal = Math.max(0, framesTotal - earlier.framesTotal);
            d.framesNetworkLost = Math.max(0, framesNetworkLost - earlier.framesNetworkLost);
            d.framesReceived = Math.max(0, framesReceived - earlier.framesReceived);
            d.framesDecoded = Math.max(0, framesDecoded - earlier.framesDecoded);
            d.framesRendered = Math.max(0, framesRendered - earlier.framesRendered);
            d.framesPacerDropped = Math.max(0, framesPacerDropped - earlier.framesPacerDropped);
            d.hostLatencySum = Math.max(0, hostLatencySum - earlier.hostLatencySum);
            d.hostLatencyFrames = Math.max(0, hostLatencyFrames - earlier.hostLatencyFrames);
            d.receiveSumUs = Math.max(0, receiveSumUs - earlier.receiveSumUs);
            d.decodeSumUs = Math.max(0, decodeSumUs - earlier.decodeSumUs);
            d.queueSumUs = Math.max(0, queueSumUs - earlier.queueSumUs);
            d.renderSumUs = Math.max(0, renderSumUs - earlier.renderSumUs);
            d.rttSumMs = rttSumMs - earlier.rttSumMs;
            d.rttVarianceSumMs = rttVarianceSumMs - earlier.rttVarianceSumMs;
            d.rttSamples = Math.max(0, rttSamples - earlier.rttSamples);
            d.presents = presents.minus(earlier.presents);
            return d;
        }
    }

    public static final class Metrics {
        public double hostMs;
        public double networkMs;
        public double receiveMs;
        public double decodeMs;
        public double queueMs;
        public double renderMs;
        public double totalMs;
        public double rttMs;
        public double rttVarianceMs;
        public double targetFps;
        public double measuredSeconds;
        public long framesRendered;
        public long framesExpected;
        public double networkLossPct;
        public double presentP50Ms;
        public double presentP95Ms;
        public double presentP99Ms;
        public double stutterPct;
    }

    public static final class Scores {
        public double latency;
        public double smoothness;
        public double balanced;

        public double forGoal(Goal goal) {
            switch (goal) {
                case LATENCY:
                    return latency;
                case SMOOTHNESS:
                    return smoothness;
                case BALANCED:
                default:
                    return balanced;
            }
        }
    }

    public static final class RunResult {
        public Role role = Role.CANDIDATE;
        public Status status = Status.OK;
        public String note = "";
        public RunConfig requested = new RunConfig();
        public RunConfig effective = new RunConfig();
        public Metrics metrics = new Metrics();
        public Scores scores = new Scores();
        public double spreadLatency;
        public double spreadSmoothness;
    }
}
