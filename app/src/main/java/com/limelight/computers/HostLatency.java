package com.limelight.computers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Round trip to a host (« En ligne · 4 ms », LavArtemis Dev): one TCP connect to the address that
 * just answered a poll. A whole serverinfo request would add TLS and the host's work to it. Same
 * measure as the desktop's PcMonitorThread::measureLatencyMs.
 */
public final class HostLatency {
    private HostLatency() {}

    /** Milliseconds, at least 1 when the host accepted the connection; -1 when it did not. */
    public static int measure(String address, int port, int timeoutMs) {
        if (address == null || port <= 0) {
            return -1;
        }
        try (Socket socket = new Socket()) {
            InetSocketAddress target = new InetSocketAddress(address, port);
            if (target.isUnresolved()) {
                return -1;
            }
            long start = System.nanoTime();
            socket.connect(target, timeoutMs);
            return toMs(System.nanoTime() - start);
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            return -1;
        }
    }

    /** Rounded to the millisecond, never under 1: a host that answers is never "0 ms" away */
    static int toMs(long nanos) {
        long ms = (nanos + 500_000) / 1_000_000;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ms));
    }
}
