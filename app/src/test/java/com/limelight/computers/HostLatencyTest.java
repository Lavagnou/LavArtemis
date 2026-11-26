package com.limelight.computers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.net.ServerSocket;

/** « En ligne · 4 ms »: the round trip of one TCP connect, as the desktop measures it. */
public class HostLatencyTest {
    @Test
    public void roundsToTheMillisecondNeverUnderOne() {
        assertEquals(1, HostLatency.toMs(0));
        assertEquals(1, HostLatency.toMs(200_000));
        assertEquals(4, HostLatency.toMs(3_600_000));
        assertEquals(4, HostLatency.toMs(4_400_000));
    }

    @Test
    public void measuresAHostThatAcceptsAndNotOneThatDoesNot() throws Exception {
        int closedPort;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int ms = HostLatency.measure("127.0.0.1", server.getLocalPort(), 1000);
            assertTrue("loopback answers: " + ms, ms >= 1 && ms < 1000);
            closedPort = server.getLocalPort();
        }
        assertEquals(-1, HostLatency.measure("127.0.0.1", closedPort, 1000));
        assertEquals(-1, HostLatency.measure(null, 80, 1000));
    }
}
