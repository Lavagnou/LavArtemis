package com.limelight.nvstream;

import com.limelight.utils.HostDisplayFit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class StreamConfigurationHostDisplayTest {
    private static StreamConfiguration config(int w, int h, float fps) {
        return new StreamConfiguration.Builder()
                .setResolution(w, h)
                .setRefreshRate(fps)
                .setLaunchRefreshRate(fps)
                .build();
    }

    @Test
    public void joiningALaptopSessionFromThePhone() {
        StreamConfiguration c = config(2400, 1080, 90);
        HostDisplayFit.Mode m = c.applyHostDisplay(new int[]{1920, 1080, 60});
        assertEquals(1920, c.getWidth());
        assertEquals(1080, c.getHeight());
        assertEquals(60, c.getLaunchRefreshRate());
        assertEquals(60, c.getRefreshRate());
        assertEquals(60, m.fps);
    }

    @Test
    public void joiningUpdatesTheSizeSentToTheHost() {
        // Pixel 7 (2400x1080@90) joining the Honor Pad's 2560x1600@120 session: the launch
        // URL and the RTSP setup read negotiatedWidth/Height, not the stream config.
        ConnectionContext ctx = new ConnectionContext();
        ctx.streamConfig = config(2400, 1080, 90);
        ctx.negotiatedWidth = 2400;
        ctx.negotiatedHeight = 1080;
        HostDisplayFit.Mode m = NvConnection.applyHostDisplayMode(ctx, new int[]{2560, 1600, 120}, true);
        assertEquals(1728, m.width);
        assertEquals(1080, m.height);
        assertEquals(1728, ctx.negotiatedWidth);
        assertEquals(1080, ctx.negotiatedHeight);
        assertEquals(90, ctx.streamConfig.getLaunchRefreshRate());
    }

    @Test
    public void noHostModeLeavesTheNegotiatedSizeAlone() {
        ConnectionContext ctx = new ConnectionContext();
        ctx.streamConfig = config(2400, 1080, 90);
        ctx.negotiatedWidth = 2400;
        ctx.negotiatedHeight = 1080;
        assertEquals(null, NvConnection.applyHostDisplayMode(ctx, null, true));
        assertEquals(2400, ctx.negotiatedWidth);
    }

    @Test
    public void fasterHostKeepsTheClientsExactRate() {
        // 59.94 stays 59.94 (encoded x1000 by the getters) when the host runs faster
        StreamConfiguration c = config(1920, 1080, 59.94f);
        c.applyHostDisplay(new int[]{2400, 1080, 90});
        assertEquals(1920, c.getWidth());
        assertEquals(864, c.getHeight());
        assertEquals(59940, c.getLaunchRefreshRate());
    }
}
