package com.limelight.utils;

/**
 * Stream at the host display's mode when this client did not create that display.
 *
 * Joining a running session (or launching without a virtual display) streams whatever the host
 * shows: asking for the client's own size made the host pad the image with black bars, and asking
 * for more frames than the display produces only made the client pace empty frames. So: the host
 * display's aspect, at the largest size that fits both the client's setting and the host display
 * (never upscaled), at the lower of the two frame rates. Same rule and reference values as the
 * desktop client (LavArtemis-Qt backend/hostdisplayfit).
 */
public final class HostDisplayFit {
    public static final class Mode {
        public final int width;
        public final int height;
        public final int fps;

        public Mode(int width, int height, int fps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Mode)) {
                return false;
            }
            Mode m = (Mode) o;
            return width == m.width && height == m.height && fps == m.fps;
        }

        @Override
        public int hashCode() {
            return (width * 31 + height) * 31 + fps;
        }

        @Override
        public String toString() {
            return width + "x" + height + "@" + fps;
        }
    }

    private HostDisplayFit() {}

    public static Mode fit(int hostW, int hostH, int hostRefresh, int settingW, int settingH, int settingFps) {
        double scale = Math.min(1.0, Math.min(settingW / (double) hostW, settingH / (double) hostH));
        int w = even((int) Math.floor(hostW * scale));
        int h = even((int) Math.floor(hostH * scale));
        int fps = hostRefresh > 0 ? Math.min(settingFps, hostRefresh) : settingFps;
        return new Mode(w, h, fps);
    }

    /**
     * @param resuming                    joining the session the host is running
     * @param launchWithVirtualDisplay    a new session whose display is created for this client
     */
    public static boolean applies(boolean resuming, boolean launchWithVirtualDisplay) {
        return resuming || !launchWithVirtualDisplay;
    }

    private static int even(int v) {
        return Math.max(2, v & ~1);
    }
}
