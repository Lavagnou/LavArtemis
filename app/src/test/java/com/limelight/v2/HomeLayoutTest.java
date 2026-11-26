package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager;

import org.junit.Test;

import java.util.Collections;

/** Home sizes per window (prototype / density boards) and the PC state card (prototype homeCard). */
public class HomeLayoutTest {
    @Test
    public void tabletIsThePrototypeStage() {
        HomeLayout proto = HomeLayout.forSize(1280, 720);
        assertEquals(HomeLayout.Kind.TABLET, proto.kind);
        assertEquals(1f, proto.k, 1e-4);
        assertEquals(1280f, proto.stageWidth(), 0.5f);
        // Densite-Tablette: a 1152 px stage x 1.111
        HomeLayout tab = HomeLayout.forSize(1280, 800);
        assertEquals(1280f / 1152f, tab.k, 1e-4);
        assertEquals(1152f, tab.stageWidth(), 0.5f);
        assertEquals(14 * tab.k, tab.t(14), 1e-4);
    }

    @Test
    public void phoneAndConsoleKeepBodyTextReadable() {
        HomeLayout phone = HomeLayout.forSize(915, 412);
        assertEquals(HomeLayout.Kind.PHONE, phone.kind);
        // Cards 170 px -> 102 dp (Densite-Telephone: 104)
        assertEquals(102f, phone.b(170), 0.5f);
        // Body text stays near 14
        assertEquals(12.6f, phone.t(14), 0.1f);
        HomeLayout console = HomeLayout.forSize(960, 540);
        assertEquals(HomeLayout.Kind.CONSOLE, console.kind);
        assertEquals(0.75f, console.k, 1e-4);
        // Scène title 42 -> about 35 (Densite-Console: 36)
        assertEquals(35.2f, console.t(42), 0.2f);
    }

    @Test
    public void portraitIsNeverTablet() {
        assertEquals(HomeLayout.Kind.PHONE, HomeLayout.forSize(412, 915).kind);
        assertEquals(true, HomeLayout.forSize(800, 1280).portrait);
    }

    private static ComputerDetails pc(ComputerDetails.State state, String mac) {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "u";
        d.name = "ATLAS-PC";
        d.state = state;
        d.pairState = PairingManager.PairState.PAIRED;
        d.macAddress = mac;
        return d;
    }

    @Test
    public void stateCard() {
        assertEquals(HomeScreen.CardState.FIRSTRUN, HomeScreen.cardState(null, null));
        assertEquals(HomeScreen.CardState.LOADING, HomeScreen.cardState(pc(ComputerDetails.State.ONLINE, null), null));
        HomeModel m = HomeModel.build(Collections.<NvApp>emptyList(), 0, "", false, null, false);
        assertNull(HomeScreen.cardState(pc(ComputerDetails.State.ONLINE, null), m));
        assertEquals(HomeScreen.CardState.OFFLINE_WAKE, HomeScreen.cardState(pc(ComputerDetails.State.OFFLINE, "00:11:22:33:44:55"), m));
        assertEquals(HomeScreen.CardState.OFFLINE_NO_MAC, HomeScreen.cardState(pc(ComputerDetails.State.OFFLINE, null), m));
        assertEquals(HomeScreen.CardState.SEARCHING, HomeScreen.cardState(pc(ComputerDetails.State.UNKNOWN, null), m));
    }
}
