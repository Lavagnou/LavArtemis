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
            case "VRR pacing is not available":
            case "VRR is not available on this display":
                return context.getString(R.string.bench_note_no_vrr);
            default:
                break;
        }
        Matcher m = FELL_BACK.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_fell_back, m.group(1), m.group(2));
        }
        m = RAN_AS.matcher(note);
        if (m.matches()) {
            return context.getString(R.string.bench_note_ran_as, m.group(1), m.group(2));
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
        return note;
    }
}
