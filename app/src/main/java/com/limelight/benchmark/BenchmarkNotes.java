package com.limelight.benchmark;

import android.content.Context;

import com.limelight.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows a run's note in the device language. Notes are written in English (the planner and
 * {@link BenchmarkGameRun} are line-by-line ports of the desktop's, and reports already on disk keep
 * them), so they are translated when shown, not when stored. An unknown note is shown as is.
 */
public final class BenchmarkNotes {
    private BenchmarkNotes() {}

    private static final Pattern FELL_BACK = Pattern.compile("^(.+) is not available: the (?:device|client) fell back to (.+)$");
    private static final Pattern RAN_AS = Pattern.compile("^Ran as (.+), (.+) instead\\.$");
    private static final Pattern START_FAILED = Pattern.compile("^Starting (.+) failed: error (-?\\d+)\\.?$");
    private static final Pattern CONNECTION_ENDED = Pattern.compile("^The connection ended \\(error (-?\\d+)\\)\\.$");
    // Written by the desktop client (Benchmark::Notes): its reports open here too
    private static final Pattern BITRATE_CAPPED = Pattern.compile("^The bitrate was capped at ([0-9.]+) Mbps\\.$");

    public static String localize(Context context, String note) {
        if (note == null || note.isEmpty()) {
            return "";
        }
        switch (note) {
            case "No frames arrived to measure.":
                return context.getString(R.string.bench_note_no_frames_arrived);
            case "The run was cut short.":
                return context.getString(R.string.bench_note_cut_short);
            case "No frames were shown.":
                return context.getString(R.string.bench_note_no_frames_shown);
            case "The stream did not start.":
                return context.getString(R.string.bench_note_stream_did_not_start);
            case "The stream could not start.":
                return context.getString(R.string.bench_note_stream_could_not_start);
            case "VRR pacing is not available":
            case "VRR is not available on this display":
                return context.getString(R.string.bench_note_no_vrr);
            default:
                break;
        }
        Matcher m = FELL_BACK.matcher(note);
        if (m.matches()) {
            // Codec ids (« hevc ») by their name, as the desktop shows them
            return context.getString(R.string.bench_note_fell_back, codecLabel(m.group(1)), codecLabel(m.group(2)));
        }
        m = RAN_AS.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_ran_as, codecLabel(m.group(1)), pacingLabel(context, m.group(2)));
        }
        m = START_FAILED.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_start_failed,
                    com.limelight.utils.StreamStages.label(context, m.group(1)), Integer.parseInt(m.group(2)));
        }
        m = CONNECTION_ENDED.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_connection_ended, Integer.parseInt(m.group(1)));
        }
        m = BITRATE_CAPPED.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_bitrate_capped, m.group(1));
        }
        return note;
    }

    /** A codec id as shown elsewhere (« hevc » → « HEVC », the desktop's codecLabel); an unknown one as is */
    public static String codecLabel(String id) {
        BenchmarkTypes.Codec codec = BenchmarkTypes.Codec.fromId(id);
        if (codec == null) {
            return id;
        }
        switch (codec) {
            case H264: return "H.264";
            case HEVC: return "HEVC";
            case AV1: return "AV1";
            default: return "PyroWave";
        }
    }

    /** A pacing id by its name, Android's and the desktop's (pacingLabel); an unknown one as is */
    public static String pacingLabel(Context context, String pacing) {
        switch (pacing) {
            case "default": return context.getString(R.string.benchmark_pacing_default);
            case "ull": return context.getString(R.string.benchmark_pacing_ull);
            case "paced-ull": return context.getString(R.string.benchmark_pacing_paced_ull);
            case "predictive": return context.getString(R.string.benchmark_pacing_predictive);
            case "vrr-low-latency": return context.getString(R.string.benchmark_pacing_vrr_low_latency);
            case "vrr-balanced": return context.getString(R.string.benchmark_pacing_vrr_balanced);
            case "vrr-smooth": return context.getString(R.string.benchmark_pacing_vrr_smooth);
            case "vsync-off": return context.getString(R.string.benchmark_pacing_vsync_off);
            case "vsync": return context.getString(R.string.benchmark_pacing_vsync);
            case "vsync-paced": return context.getString(R.string.benchmark_pacing_vsync_paced);
            default: return pacing;
        }
    }
}
