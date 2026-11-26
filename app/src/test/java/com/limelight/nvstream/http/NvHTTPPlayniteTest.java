package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.StringReader;
import java.util.LinkedList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// LavApollo Link: Playnite games and the Playnite front end in /applist and serverinfo.
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class NvHTTPPlayniteTest {
    @Test
    public void parsesPlayniteFlags() throws Exception {
        String xml = "<root status_code=\"200\">"
                + "<App><AppTitle>Desktop</AppTitle><UUID>78D18471</UUID><ID>1</ID></App>"
                + "<App><AppTitle>Playnite</AppTitle><UUID>573845F7</UUID><ID>2</ID><IsPlayniteHost>1</IsPlayniteHost></App>"
                + "<App><AppTitle>Cyberpunk 2077</AppTitle><UUID>FB5D018A</UUID><ID>3</ID><IsPlayniteGame>1</IsPlayniteGame></App>"
                + "</root>";
        LinkedList<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(xml));
        assertEquals(3, apps.size());
        assertFalse(apps.get(0).isPlayniteGame());
        assertFalse(apps.get(0).isPlayniteHost());
        assertTrue(apps.get(1).isPlayniteHost());
        assertTrue(apps.get(2).isPlayniteGame());
    }

    @Test
    public void playniteHostRunningOnlyWhenAnnounced() throws Exception {
        assertTrue(NvHTTP.isPlayniteHostRunning("<root status_code=\"200\"><PlayniteHostRunning>1</PlayniteHostRunning></root>"));
        assertFalse(NvHTTP.isPlayniteHostRunning("<root status_code=\"200\"><PlayniteHostRunning>0</PlayniteHostRunning></root>"));
        assertFalse(NvHTTP.isPlayniteHostRunning("<root status_code=\"200\"><hostname>OLD</hostname></root>"));
    }

    @Test
    public void playniteGameNameOnlyWhenAGameRuns() throws Exception {
        assertEquals("Cyberpunk 2077", NvHTTP.getPlayniteGameName(
                "<root status_code=\"200\"><PlayniteGameName>Cyberpunk 2077</PlayniteGameName></root>"));
        org.junit.Assert.assertNull(NvHTTP.getPlayniteGameName("<root status_code=\"200\"><hostname>X</hostname></root>"));
    }

    @Test
    public void parsesPlayniteGameState() throws Exception {
        String xml = "<root status_code=\"200\">"
                + "<App><AppTitle>Desktop</AppTitle><UUID>78D18471</UUID><ID>1</ID></App>"
                + "<App><AppTitle>Nebula Drift</AppTitle><UUID>4DCCF2BC</UUID><ID>3</ID><IsPlayniteGame>1</IsPlayniteGame>"
                + "<PlayniteInstalled>1</PlayniteInstalled><PlaynitePlaytime>151200</PlaynitePlaytime>"
                + "<PlayniteLastActivity>1795208400</PlayniteLastActivity><PlayniteBackground>1</PlayniteBackground></App>"
                + "<App><AppTitle>Cobalt Quarry</AppTitle><UUID>388A275E</UUID><ID>4</ID><IsPlayniteGame>1</IsPlayniteGame>"
                + "<PlayniteInstalled>0</PlayniteInstalled><PlaynitePlaytime>27000</PlaynitePlaytime></App>"
                + "</root>";
        LinkedList<NvApp> apps = NvHTTP.getAppListByReader(new StringReader(xml));
        assertEquals(3, apps.size());
        // An app without the fields (older host, or not a game): installed, never played, nothing more
        assertTrue(apps.get(0).isPlayniteInstalled());
        assertEquals(0, apps.get(0).getPlayniteLastActivity());
        assertFalse(apps.get(0).hasPlayniteBackground());

        assertTrue(apps.get(1).isPlayniteInstalled());
        assertEquals(151200, apps.get(1).getPlaynitePlaytime());
        assertEquals(1795208400L, apps.get(1).getPlayniteLastActivity());
        assertTrue(apps.get(1).hasPlayniteBackground());

        assertFalse(apps.get(2).isPlayniteInstalled());
        assertEquals(27000, apps.get(2).getPlaynitePlaytime());
        assertEquals(0, apps.get(2).getPlayniteLastActivity());
        assertFalse(apps.get(2).hasPlayniteBackground());
    }

    @Test
    public void hostDataHelpers() {
        // CurrentGameElapsed / PlayniteGameElapsed: seconds ago -> a date on this device's clock
        assertEquals(1_000_000L - 42 * 60 * 1000L, NvHTTP.startFromElapsed("2520", 1_000_000L));
        assertEquals(0, NvHTTP.startFromElapsed(null, 1_000_000L));
        assertEquals(0, NvHTTP.startFromElapsed("soon", 1_000_000L));
        assertEquals(0, NvHTTP.startFromElapsed("-5", 1_000_000L));
        // HostFormFactor / HostLink: known values only
        assertEquals("tower", NvHTTP.oneOf("tower", "tower", "mini", "laptop"));
        assertEquals("wifi", NvHTTP.oneOf(" wifi ", "wired", "wifi"));
        org.junit.Assert.assertNull(NvHTTP.oneOf("server", "tower", "mini", "laptop"));
        org.junit.Assert.assertNull(NvHTTP.oneOf(null, "wired", "wifi"));
        // OtherClientsConnected: someone else streams the session (« Rejoindre »); absent or bad = 0
        assertEquals(2, NvHTTP.count("2"));
        assertEquals(1, NvHTTP.count(" 1 "));
        assertEquals(0, NvHTTP.count(null));
        assertEquals(0, NvHTTP.count("-3"));
        assertEquals(0, NvHTTP.count("many"));
        // A start recomputed at each poll does not jitter; another session replaces it
        assertEquals(10_000L, ComputerDetails.stableStart(10_000L, 12_000L));
        assertEquals(60_000L, ComputerDetails.stableStart(10_000L, 60_000L));
        assertEquals(0, ComputerDetails.stableStart(10_000L, 0));
        assertEquals(5_000L, ComputerDetails.stableStart(0, 5_000L));
    }
}
