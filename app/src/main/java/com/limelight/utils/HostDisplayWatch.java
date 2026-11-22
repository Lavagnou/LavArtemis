package com.limelight.utils;

/**
 * Decides when a host display mode change is worth reconnecting for.
 *
 * The stream's size is fixed at connection time; when the host display changes mode mid-session
 * (Windows display settings), the client reconnects at the new fit. Two rules keep that sane:
 * - compare the fit *target* with the stream *in progress*, never the host display with the
 *   stream: a 2400x1080 display streamed at 1920x864 is the fitted state, not a change;
 * - require the same new target on two consecutive polls: Windows passes through intermediate
 *   modes while it switches.
 * Same semantics as the desktop client's HostDisplayWatch.
 */
public class HostDisplayWatch {
    private final int settingW, settingH, settingFps;
    private final HostDisplayFit.Mode current;
    private HostDisplayFit.Mode pending;
    private boolean fired;

    public HostDisplayWatch(int settingW, int settingH, int settingFps, int currentW, int currentH, int currentFps) {
        this.settingW = settingW;
        this.settingH = settingH;
        this.settingFps = settingFps;
        this.current = new HostDisplayFit.Mode(currentW, currentH, currentFps);
    }

    /**
     * @param hostMode {width, height, refresh} from serverinfo, or null when absent
     * @return the mode to reconnect at, once; null otherwise
     */
    public HostDisplayFit.Mode onPoll(int[] hostMode) {
        if (fired) {
            return null;
        }
        if (hostMode == null) {
            pending = null;
            return null;
        }
        HostDisplayFit.Mode target = HostDisplayFit.fit(hostMode[0], hostMode[1], hostMode[2],
                settingW, settingH, settingFps);
        if (target.equals(current)) {
            pending = null;
            return null;
        }
        if (target.equals(pending)) {
            fired = true;
            return target;
        }
        pending = target;
        return null;
    }
}
