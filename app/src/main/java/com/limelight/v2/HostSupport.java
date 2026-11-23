package com.limelight.v2;

import com.limelight.nvstream.http.ComputerDetails;

/**
 * « Version du logiciel hôte non prise en charge » (the desktop's ServerSupportedRole /
 * NvComputer::isSupportedServerVersion): a host LavArtemis cannot stream from is refused with a
 * message instead of a launch that fails half-way.
 *
 * <p>Android's own rule, from what the stream needs: the {@code appversion} of serverinfo must be
 * a version quad (NvConnection gives up on a missing one, the pairing reads its four numbers,
 * moonlight-common-c picks the protocol from the first), and its generation at least 3, the
 * oldest moonlight-common-c speaks. Unknown (PC offline, never polled) counts as supported: the
 * question is asked again once the PC answers. The desktop compares GeForce Experience versions
 * against a list fetched from moonlight-stream.org; Apollo / Sunshine hosts always pass that one.
 */
public final class HostSupport {
    private HostSupport() {}

    /** The oldest server generation (first number of appversion) moonlight-common-c handles */
    public static final int OLDEST_GENERATION = 3;

    public static boolean isSupported(ComputerDetails d) {
        return d == null || d.state != ComputerDetails.State.ONLINE || isSupportedVersion(d.serverAppVersion);
    }

    /** {@code null} = not known yet = supported */
    public static boolean isSupportedVersion(String appVersion) {
        if (appVersion == null) {
            return true;
        }
        String[] parts = appVersion.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int generation;
        try {
            generation = Integer.parseInt(parts[0].trim());
            for (int i = 1; i < 4; i++) {
                // Sunshine ends its version with -1: a negative number is still a number
                Integer.parseInt(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return generation >= OLDEST_GENERATION;
    }
}
