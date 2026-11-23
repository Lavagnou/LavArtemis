package com.limelight.v2;

import android.content.Context;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What a « Mes PC » card says and does, from a PC's details (LavArtemis-Qt gui/v2/HostsView.qml):
 * status, detail line, « En cours : … », main action, what a tap does, the options menu, and the
 * number of columns. Plain data and rules, tested without views.
 */
public final class HostCards {
    private HostCards() {}

    public enum Status { ONLINE, SESSION, OFFLINE, SEARCHING }

    /** What a tap (A, Entrée) on a card does */
    public enum Tap { OPEN, PAIR, WAKE, NONE }

    /** The options menu, in this order; the first entry is the main one */
    public enum Option { OPEN, BENCHMARK, PAIR, WAKE, RENAME, FORGET }

    /** The texts of a card, French (strings_v2.xml) */
    public static final class Labels {
        public String online, session, offline, searching, wakeable, unreachable, apollo,
                running, runningApp, open, wake, desktop, playnite;

        public static Labels from(Context c) {
            Labels l = new Labels();
            l.online = c.getString(R.string.v2_host_online);
            l.session = c.getString(R.string.v2_host_session);
            l.offline = c.getString(R.string.v2_host_offline);
            l.searching = c.getString(R.string.v2_host_searching);
            l.wakeable = c.getString(R.string.v2_host_wakeable);
            l.unreachable = c.getString(R.string.v2_host_unreachable);
            l.apollo = c.getString(R.string.v2_host_apollo);
            l.running = c.getString(R.string.v2_host_running);
            l.runningApp = c.getString(R.string.v2_host_running_app);
            l.open = c.getString(R.string.v2_host_open);
            l.wake = c.getString(R.string.v2_wake);
            l.desktop = c.getString(R.string.v2_desktop);
            l.playnite = c.getString(R.string.v2_playnite);
            return l;
        }
    }

    public static final class Card {
        public String uuid;
        public String name;
        public Status status;
        public String statusText;
        /** Address (+ LavApollo version) online, why it is unreachable offline, empty while searching */
        public String detail;
        /** « En cours : … » when a session runs, else null */
        public String running;
        /** Ouvrir / Réveiller, empty while searching */
        public String action;
        public boolean online;
        public Tap tap;
    }

    public static Status status(ComputerDetails d) {
        if (d.state == ComputerDetails.State.ONLINE) {
            return d.runningGameId != 0 ? Status.SESSION : Status.ONLINE;
        }
        return d.state == ComputerDetails.State.OFFLINE ? Status.OFFLINE : Status.SEARCHING;
    }

    public static Tap tap(ComputerDetails d) {
        Status s = status(d);
        if (s == Status.ONLINE || s == Status.SESSION) {
            return HostList.isPaired(d) ? Tap.OPEN : Tap.PAIR;
        }
        return s == Status.OFFLINE ? Tap.WAKE : Tap.NONE;
    }

    public static List<Option> options(ComputerDetails d) {
        List<Option> list = new ArrayList<>();
        Status s = status(d);
        boolean online = s == Status.ONLINE || s == Status.SESSION;
        if (online && HostList.isPaired(d)) {
            list.add(Option.OPEN);
            list.add(Option.BENCHMARK);
        } else if (online) {
            list.add(Option.PAIR);
        } else {
            list.add(Option.WAKE);
        }
        list.add(Option.RENAME);
        list.add(Option.FORGET);
        return list;
    }

    /** @param apps the PC's app list when known (names of the running app), else null */
    public static Card of(ComputerDetails d, List<NvApp> apps, Labels l) {
        Card c = new Card();
        c.uuid = d.uuid;
        c.name = d.name == null ? "" : d.name;
        c.status = status(d);
        c.online = c.status == Status.ONLINE || c.status == Status.SESSION;
        c.tap = tap(d);
        switch (c.status) {
            case SESSION:
                c.statusText = l.session;
                break;
            case ONLINE:
                c.statusText = l.online;
                break;
            case OFFLINE:
                c.statusText = l.offline;
                break;
            default:
                c.statusText = l.searching;
                break;
        }
        if (c.online) {
            String address = address(d);
            c.detail = d.apolloVersion != null && !d.apolloVersion.trim().isEmpty()
                    ? String.format(Locale.ROOT, l.apollo, address, d.apolloVersion.trim()) : address;
        } else if (c.status == Status.OFFLINE) {
            c.detail = d.macAddress != null ? l.wakeable : l.unreachable;
        } else {
            c.detail = "";
        }
        if (c.status == Status.SESSION) {
            String app = runningName(d, apps, l);
            c.running = app.isEmpty() ? l.running : String.format(Locale.ROOT, l.runningApp, app);
        }
        c.action = c.online ? l.open : (c.status == Status.OFFLINE ? l.wake : "");
        return c;
    }

    /**
     * The running app as the home names it: the Playnite game when there is one, Bureau and
     * Playnite Fullscreen for those two apps, else its name; empty when unknown.
     */
    public static String runningName(ComputerDetails d, List<NvApp> apps, Labels l) {
        if (d.runningGameId == 0) {
            return "";
        }
        if (d.playniteGameName != null && !d.playniteGameName.isEmpty()) {
            return d.playniteGameName;
        }
        if (apps != null) {
            for (NvApp a : apps) {
                if (a.getAppId() == d.runningGameId) {
                    switch (HomeModel.kindOf(a)) {
                        case DESKTOP:
                            return l.desktop;
                        case PLAYNITE:
                            return l.playnite;
                        default:
                            return a.getAppName();
                    }
                }
            }
        }
        return "";
    }

    /** The address in use (else the one typed in), with its port when it is not the default one */
    public static String address(ComputerDetails d) {
        ComputerDetails.AddressTuple t = d.activeAddress != null ? d.activeAddress
                : (d.manualAddress != null ? d.manualAddress : d.localAddress);
        return formatAddress(t);
    }

    public static String formatAddress(ComputerDetails.AddressTuple t) {
        if (t == null || t.address == null) {
            return "";
        }
        boolean v6 = t.address.indexOf(':') >= 0;
        if (t.port <= 0 || t.port == NvHTTP.DEFAULT_HTTP_PORT) {
            return t.address;
        }
        return (v6 ? "[" + t.address + "]" : t.address) + ":" + t.port;
    }

    /** « Ajouter un PC »: the PCs answering on the network that are not paired yet */
    public static List<ComputerDetails> unpairedOnline(Collection<ComputerDetails> all) {
        List<ComputerDetails> list = new ArrayList<>();
        for (ComputerDetails d : all) {
            if (d.uuid != null && d.state == ComputerDetails.State.ONLINE && !HostList.isPaired(d)) {
                list.add(d);
            }
        }
        Collections.sort(list, (a, b) -> name(a).compareTo(name(b)));
        return list;
    }

    /** The paired PCs by name: cards do not move when one goes on or off line */
    public static List<ComputerDetails> pairedByName(Collection<ComputerDetails> all) {
        List<ComputerDetails> list = new ArrayList<>();
        for (ComputerDetails d : all) {
            if (HostList.isPaired(d)) {
                list.add(d);
            }
        }
        Collections.sort(list, (a, b) -> {
            int n = name(a).compareTo(name(b));
            return n != 0 ? n : String.valueOf(a.uuid).compareTo(String.valueOf(b.uuid));
        });
        return list;
    }

    private static String name(ComputerDetails d) {
        return d.name == null ? "" : d.name.toLowerCase(Locale.ROOT);
    }

    /** Narrowest card, in dp: 5 columns on a 960 dp console, 2 on a phone in portrait */
    public static final float MIN_CARD_DP = 160;

    /**
     * Columns of the grid: one per card (the « Ajouter un PC » card included), at least 2, at most
     * 3 on a phone and 5 on a bigger screen, and never narrower than {@link #MIN_CARD_DP}.
     */
    public static int columns(int cardsWithAdd, float availableDp, float gapDp, boolean compact) {
        int byWidth = Math.max(1, (int) ((availableDp + gapDp) / (MIN_CARD_DP + gapDp)));
        int want = Math.max(2, Math.min(compact ? 3 : 5, cardsWithAdd));
        return Math.max(1, Math.min(want, byWidth));
    }

    /** "1234" -> "1 2 3 4", easier to read out and type */
    public static String spacedPin(String pin) {
        if (pin == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            if (i > 0) {
                b.append(' ');
            }
            b.append(pin.charAt(i));
        }
        return b.toString();
    }

    /**
     * A pairing link (art://host:port?pin=…&passphrase=…) waits for the PC answering at its
     * address. No port in the link means the default one.
     */
    public static boolean matchesLink(ComputerDetails.AddressTuple active, String host, int port) {
        if (active == null || active.address == null || host == null) {
            return false;
        }
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        int p = port <= 0 ? NvHTTP.DEFAULT_HTTP_PORT : port;
        return active.address.equalsIgnoreCase(h) && active.port == p;
    }
}
