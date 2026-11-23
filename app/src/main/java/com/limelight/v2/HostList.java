package com.limelight.v2;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The PCs the home can switch between: the paired ones, online first. */
public final class HostList {
    private HostList() {}

    /**
     * A PC from the database has no pair state until it answers a poll; its stored certificate
     * says it was paired. Only an explicit NOT_PAIRED (or no certificate at all) leaves it out.
     */
    public static boolean isPaired(ComputerDetails d) {
        if (d.uuid == null) {
            return false;
        }
        if (d.pairState == PairingManager.PairState.PAIRED) {
            return true;
        }
        return d.pairState != PairingManager.PairState.NOT_PAIRED && d.serverCert != null;
    }

    public static boolean isOnline(ComputerDetails d) {
        return d.state == ComputerDetails.State.ONLINE;
    }

    /** Paired PCs, online first, then by name */
    public static List<ComputerDetails> paired(Collection<ComputerDetails> all) {
        List<ComputerDetails> list = new ArrayList<>();
        for (ComputerDetails d : all) {
            if (isPaired(d)) {
                list.add(d);
            }
        }
        Collections.sort(list, (a, b) -> {
            int online = Boolean.compare(isOnline(b), isOnline(a));
            if (online != 0) {
                return online;
            }
            String an = a.name == null ? "" : a.name.toLowerCase(Locale.ROOT);
            String bn = b.name == null ? "" : b.name.toLowerCase(Locale.ROOT);
            return an.compareTo(bn);
        });
        return list;
    }

    /** Position of the PC with this uuid, else 0 */
    public static int indexOf(List<ComputerDetails> hosts, String uuid) {
        if (uuid != null) {
            for (int i = 0; i < hosts.size(); i++) {
                if (uuid.equalsIgnoreCase(hosts.get(i).uuid)) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** LB / RB: the previous or next PC, wrapping around */
    public static int step(int position, int delta, int count) {
        if (count <= 0) {
            return 0;
        }
        return ((position + delta) % count + count) % count;
    }
}
