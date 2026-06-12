package com.limelight.binding.video;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.Process;

import com.limelight.LimeLog;

/**
 * Wraps ADPF's PerformanceHintManager (API 31+) so the system's CPU governor
 * keeps the decode/render pipeline threads clocked high enough to hit the
 * stream's frame deadline instead of downclocking during quiet scenes.
 *
 * All methods are safe to call on any API level and never throw; on
 * unsupported devices this class is a no-op.
 */
public final class AdpfHelper {
    private final Object lock = new Object();
    private final Context context;

    private PerformanceHintManager.Session session;
    private long targetWorkDurationNs;
    private boolean unsupported;

    // Registered pipeline thread IDs. The hint session must be recreated
    // whenever this set changes, since setThreads() requires API 34.
    private int[] tids = new int[0];
    private boolean tidsDirty;

    public AdpfHelper(Context context, long targetWorkDurationNs) {
        this.context = context;
        this.targetWorkDurationNs = Math.max(1, targetWorkDurationNs);
        this.unsupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null;
    }

    /** Registers the calling thread as part of the streaming pipeline. */
    public void registerCurrentThread() {
        if (unsupported) {
            return;
        }
        int tid = Process.myTid();
        synchronized (lock) {
            for (int existing : tids) {
                if (existing == tid) {
                    return;
                }
            }
            int[] newTids = new int[tids.length + 1];
            System.arraycopy(tids, 0, newTids, 0, tids.length);
            newTids[tids.length] = tid;
            tids = newTids;
            tidsDirty = true;
        }
    }

    /**
     * Reports the time spent producing the current frame (decode unit enqueue
     * to render release). Lazily (re)creates the hint session as threads join.
     */
    public void reportFrameWorkDuration(long actualWorkDurationNs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (unsupported || actualWorkDurationNs <= 0) {
            return;
        }
        synchronized (lock) {
            try {
                if (tidsDirty) {
                    if (session != null) {
                        session.close();
                        session = null;
                    }
                    if (tids.length > 0) {
                        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);
                        if (phm == null) {
                            unsupported = true;
                            return;
                        }
                        session = phm.createHintSession(tids, targetWorkDurationNs);
                        if (session == null) {
                            // Device doesn't support hint sessions; don't retry
                            unsupported = true;
                            return;
                        }
                        LimeLog.info("ADPF hint session created for " + tids.length +
                                " threads with target " + (targetWorkDurationNs / 1000000) + " ms");
                    }
                    tidsDirty = false;
                }
                if (session != null) {
                    // Clamp wildly long samples (codec recovery, stream hiccups) so a
                    // single outlier doesn't peg the clocks at maximum indefinitely.
                    long clamped = Math.min(actualWorkDurationNs, targetWorkDurationNs * 4);
                    session.reportActualWorkDuration(clamped);
                }
            } catch (Throwable t) {
                LimeLog.warning("ADPF reporting failed, disabling: " + t);
                unsupported = true;
                closeSessionLocked();
            }
        }
    }

    public void close() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        synchronized (lock) {
            closeSessionLocked();
            unsupported = true;
        }
    }

    private void closeSessionLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (session != null) {
            try {
                session.close();
            } catch (Throwable ignored) {}
            session = null;
        }
    }
}
