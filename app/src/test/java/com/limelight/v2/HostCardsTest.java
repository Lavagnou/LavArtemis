package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.computers.HostNames;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.AddComputerManually;
import com.limelight.v2.ui.V2ListDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** « Mes PC »: what each card says and does, the menus per state, columns, pairing helpers, names. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class HostCardsTest {
    private Context ctx;
    private HostCards.Labels labels;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
        labels = HostCards.Labels.from(ctx);
    }

    private static ComputerDetails pc(String name, ComputerDetails.State state, boolean paired) {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-" + name;
        d.name = name;
        d.state = state;
        d.pairState = paired ? PairingManager.PairState.PAIRED : PairingManager.PairState.NOT_PAIRED;
        d.activeAddress = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        return d;
    }

    private static List<NvApp> apps() {
        List<NvApp> list = new ArrayList<>();
        list.add(new NvApp("Desktop", "u1", 1, false));
        NvApp playnite = new NvApp("Playnite", "u2", 2, false);
        playnite.setPlayniteHost(true);
        list.add(playnite);
        list.add(new NvApp("Steam Big Picture", "u3", 3, false));
        return list;
    }

    @Test
    public void onlineCardShowsAddressAndApolloVersion() {
        ComputerDetails d = pc("ATLAS-PC", ComputerDetails.State.ONLINE, true);
        d.apolloVersion = "1.2.0";
        HostCards.Card c = HostCards.of(d, null, labels);
        assertEquals(HostCards.Status.ONLINE, c.status);
        assertEquals("En ligne", c.statusText);
        assertEquals("192.168.1.24 · LavApollo 1.2.0", c.detail);
        assertNull(c.running);
        assertEquals("Ouvrir", c.action);
        assertEquals(HostCards.Tap.OPEN, c.tap);

        d.apolloVersion = null;
        assertEquals("192.168.1.24", HostCards.of(d, null, labels).detail);
    }

    @Test
    public void busyCardNamesTheRunningApp() {
        ComputerDetails d = pc("ATLAS-PC", ComputerDetails.State.ONLINE, true);
        d.runningGameId = 1;
        HostCards.Card c = HostCards.of(d, apps(), labels);
        assertEquals(HostCards.Status.SESSION, c.status);
        assertEquals("Session en cours", c.statusText);
        assertEquals("En cours : Bureau", c.running);

        d.runningGameId = 2;
        assertEquals("En cours : Playnite Fullscreen", HostCards.of(d, apps(), labels).running);
        d.runningGameId = 3;
        assertEquals("En cours : Steam Big Picture", HostCards.of(d, apps(), labels).running);

        // The Playnite game wins over the app (Playnite Fullscreen runs it)
        d.runningGameId = 2;
        d.playniteGameName = "Hades II";
        assertEquals("En cours : Hades II", HostCards.of(d, apps(), labels).running);

        // Unknown app: no name
        d.playniteGameName = null;
        d.runningGameId = 42;
        assertEquals("En cours", HostCards.of(d, apps(), labels).running);
        assertEquals("En cours", HostCards.of(d, null, labels).running);
    }

    @Test
    public void offlineAndSearchingCards() {
        ComputerDetails d = pc("SALON", ComputerDetails.State.OFFLINE, true);
        d.macAddress = "00:11:22:33:44:55";
        HostCards.Card c = HostCards.of(d, null, labels);
        assertEquals("Hors ligne", c.statusText);
        assertEquals("Éteint ou en veille : il peut être réveillé d'ici.", c.detail);
        assertEquals("Réveiller", c.action);
        assertEquals(HostCards.Tap.WAKE, c.tap);
        assertFalse(c.online);

        d.macAddress = null;
        c = HostCards.of(d, null, labels);
        assertEquals("Éteint ou injoignable.", c.detail);
        // Without a MAC the tap still goes to the wake path, which explains why it cannot
        assertEquals(HostCards.Tap.WAKE, c.tap);

        d.state = ComputerDetails.State.UNKNOWN;
        c = HostCards.of(d, null, labels);
        assertEquals("Recherche…", c.statusText);
        assertEquals("", c.detail);
        assertEquals("", c.action);
        assertEquals(HostCards.Tap.NONE, c.tap);
    }

    @Test
    public void tapPairsAnUnpairedOnlinePc() {
        assertEquals(HostCards.Tap.PAIR, HostCards.tap(pc("NEW", ComputerDetails.State.ONLINE, false)));
    }

    @Test
    public void optionsFollowTheState() {
        assertEquals(Arrays.asList(HostCards.Option.OPEN, HostCards.Option.BENCHMARK, HostCards.Option.RENAME, HostCards.Option.FORGET),
                HostCards.options(pc("A", ComputerDetails.State.ONLINE, true)));
        assertEquals(Arrays.asList(HostCards.Option.PAIR, HostCards.Option.RENAME, HostCards.Option.FORGET),
                HostCards.options(pc("B", ComputerDetails.State.ONLINE, false)));
        assertEquals(Arrays.asList(HostCards.Option.WAKE, HostCards.Option.RENAME, HostCards.Option.FORGET),
                HostCards.options(pc("C", ComputerDetails.State.OFFLINE, true)));
        assertEquals(Arrays.asList(HostCards.Option.WAKE, HostCards.Option.RENAME, HostCards.Option.FORGET),
                HostCards.options(pc("D", ComputerDetails.State.UNKNOWN, true)));
    }

    @Test
    public void addressesShowTheirPortWhenNotDefault() {
        assertEquals("192.168.1.24", HostCards.formatAddress(new ComputerDetails.AddressTuple("192.168.1.24", 47989)));
        assertEquals("192.168.1.24:48010", HostCards.formatAddress(new ComputerDetails.AddressTuple("192.168.1.24", 48010)));
        assertEquals("[fe80::1]:48010", HostCards.formatAddress(new ComputerDetails.AddressTuple("fe80::1", 48010)));
        assertEquals("", HostCards.formatAddress(null));

        ComputerDetails d = new ComputerDetails();
        d.manualAddress = new ComputerDetails.AddressTuple("atlas.local", 47989);
        assertEquals("atlas.local", HostCards.address(d));
    }

    @Test
    public void columnsFollowTheWidth() {
        // Phone in landscape (915 dp - 2 x 20): 2 PCs + add = 3 columns, never more than 3
        assertEquals(3, HostCards.columns(3, 875, 14, true));
        assertEquals(3, HostCards.columns(8, 875, 14, true));
        // Always 2 at least: a lone « Ajouter un PC » takes half the width
        assertEquals(2, HostCards.columns(1, 875, 14, true));
        // Console (960 dp - 2 x 24): up to 5
        assertEquals(5, HostCards.columns(6, 912, 16, false));
        assertEquals(4, HostCards.columns(4, 912, 16, false));
        // Phone in portrait (412 dp - 2 x 20): 2
        assertEquals(2, HostCards.columns(5, 372, 14, true));
        // Very narrow: one column
        assertEquals(1, HostCards.columns(3, 250, 14, true));
    }

    @Test
    public void lists() {
        ComputerDetails b = pc("bravo", ComputerDetails.State.OFFLINE, true);
        ComputerDetails a = pc("Alpha", ComputerDetails.State.ONLINE, true);
        ComputerDetails n = pc("Nouveau", ComputerDetails.State.ONLINE, false);
        ComputerDetails gone = pc("Absent", ComputerDetails.State.OFFLINE, false);
        List<ComputerDetails> all = Arrays.asList(b, n, a, gone);
        // By name, online or not: cards do not move when a PC wakes up
        assertEquals(Arrays.asList(a, b), HostCards.pairedByName(all));
        // The add menu offers the unpaired PCs that answer
        assertEquals(Arrays.asList(n), HostCards.unpairedOnline(all));
    }

    @Test
    public void pairingHelpers() {
        assertEquals("1 2 3 4", HostCards.spacedPin("1234"));
        assertEquals("", HostCards.spacedPin(null));

        ComputerDetails.AddressTuple active = new ComputerDetails.AddressTuple("192.168.1.24", 47989);
        assertTrue(HostCards.matchesLink(active, "192.168.1.24", -1));
        assertTrue(HostCards.matchesLink(active, "192.168.1.24", 47989));
        assertFalse(HostCards.matchesLink(active, "192.168.1.24", 48010));
        assertFalse(HostCards.matchesLink(active, "192.168.1.25", -1));
        assertTrue(HostCards.matchesLink(new ComputerDetails.AddressTuple("fe80::1", 47989), "[fe80::1]", -1));
        assertFalse(HostCards.matchesLink(null, "192.168.1.24", -1));
    }

    @Test
    public void manualAddressesAreReadAsAddComputerManuallyReadsThem() {
        Uri u = AddComputerManually.parseRawUserInputToUri("192.168.1.24");
        assertNotNull(u);
        assertEquals("192.168.1.24", u.getHost());
        assertEquals(-1, u.getPort());
        u = AddComputerManually.parseRawUserInputToUri("192.168.1.24:48010");
        assertEquals(48010, u.getPort());
        u = AddComputerManually.parseRawUserInputToUri("atlas.local");
        assertEquals("atlas.local", u.getHost());
    }

    @Test
    public void chosenNamesWinOverTheHostName() {
        assertNull(HostNames.clean("   "));
        assertEquals("SALON", HostNames.clean("  SALON "));
        assertEquals(64, HostNames.clean(new String(new char[80]).replace('\0', 'x')).length());

        ComputerDetails d = pc("DESKTOP-4F2K", ComputerDetails.State.ONLINE, true);
        HostNames.apply(ctx, d);
        assertEquals("DESKTOP-4F2K", d.name);
        HostNames.set(ctx, d.uuid, "Salon");
        HostNames.apply(ctx, d);
        assertEquals("Salon", d.name);
        // Cleared: the host's name comes back with the next poll
        HostNames.remove(ctx, d.uuid);
        assertNull(HostNames.get(ctx, d.uuid));
    }

    @Test
    public void longCoverNamesShrink() {
        assertTrue(com.limelight.v2.ui.CoverTile.longName("Red Dead Redemption 2"));
        assertTrue(com.limelight.v2.ui.CoverTile.longName("Supercalifragilistic"));
        assertFalse(com.limelight.v2.ui.CoverTile.longName("Hades II"));
        assertFalse(com.limelight.v2.ui.CoverTile.longName(null));
    }

    @Test
    public void gameMenuColumns() {
        // A phone in landscape: two columns for the long menu, one for a short one
        assertEquals(2, V2ListDialog.columnsFor(11, 915, 412));
        assertEquals(1, V2ListDialog.columnsFor(5, 915, 412));
        // A console or tablet has the height: one column
        assertEquals(1, V2ListDialog.columnsFor(11, 960, 600));
        assertEquals(1, V2ListDialog.columnsFor(11, 412, 915));
    }
}
