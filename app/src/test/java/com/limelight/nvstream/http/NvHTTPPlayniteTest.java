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
}
