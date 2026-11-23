package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.utils.StreamStages;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Locale;

/**
 * The pure rules added to close the gaps of the Dev interface: the unsupported host, the stage
 * names of moonlight-common-c shown in French, the static fonts of Android 7.1 and older.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class GapsLogicTest {

    // ---- HostSupport ----------------------------------------------------------------------------

    @Test
    public void supportedVersions() {
        // Sunshine / Apollo / LavApollo end with -1; GeForce Experience 3.x is generation 7
        assertTrue(HostSupport.isSupportedVersion("7.1.431.-1"));
        assertTrue(HostSupport.isSupportedVersion("7.1.450.0"));
        assertTrue(HostSupport.isSupportedVersion("5.0.0.0"));
        assertTrue(HostSupport.isSupportedVersion("3.0.0.0"));
        // A future generation is treated as the newest moonlight-common-c knows
        assertTrue(HostSupport.isSupportedVersion("8.0.0.0"));
        assertTrue(HostSupport.isSupportedVersion(" 7.1.431.-1 "));
        // Unknown (offline, never polled): asked again once the PC answers
        assertTrue(HostSupport.isSupportedVersion(null));
    }

    @Test
    public void unsupportedVersions() {
        assertFalse(HostSupport.isSupportedVersion("2.0.0.0"));
        assertFalse(HostSupport.isSupportedVersion("7.1.431"));
        assertFalse(HostSupport.isSupportedVersion("7.1.431.0.1"));
        assertFalse(HostSupport.isSupportedVersion(""));
        assertFalse(HostSupport.isSupportedVersion("seven"));
        assertFalse(HostSupport.isSupportedVersion("7.x.0.0"));
    }

    @Test
    public void onlyAnOnlineHostIsJudged() {
        ComputerDetails d = new ComputerDetails();
        d.serverAppVersion = "2.0.0.0";
        d.state = ComputerDetails.State.OFFLINE;
        assertTrue(HostSupport.isSupported(d));
        d.state = ComputerDetails.State.ONLINE;
        assertFalse(HostSupport.isSupported(d));
        d.serverAppVersion = "7.1.431.-1";
        assertTrue(HostSupport.isSupported(d));
        assertTrue(HostSupport.isSupported(null));
    }

    @Test
    public void updateCarriesTheVersion() {
        ComputerDetails polled = new ComputerDetails();
        polled.serverAppVersion = "7.1.431.-1";
        ComputerDetails kept = new ComputerDetails();
        kept.update(polled);
        assertEquals("7.1.431.-1", kept.serverAppVersion);
    }

    // ---- StreamStages ---------------------------------------------------------------------------

    /** stageNames[] of moonlight-common-c's Connection.c, past "none" */
    private static final String[] STAGES = {"platform initialization", "name resolution", "audio stream initialization",
            "RTSP handshake", "control stream initialization", "video stream initialization", "input stream initialization",
            "control stream establishment", "video stream establishment", "audio stream establishment",
            "input stream establishment"};

    private static Context french() {
        Context app = ApplicationProvider.getApplicationContext();
        Configuration c = new Configuration(app.getResources().getConfiguration());
        c.setLocale(Locale.FRENCH);
        return app.createConfigurationContext(c);
    }

    @Test
    public void everyStageHasAFrenchName() {
        Context en = ApplicationProvider.getApplicationContext();
        Context fr = french();
        for (String stage : STAGES) {
            assertNotEquals(stage, 0, StreamStages.labelRes(stage));
            // The English default is moonlight-common-c's own name: the stable reads the same
            assertEquals(stage, StreamStages.label(en, stage));
            assertNotEquals(stage, StreamStages.label(fr, stage));
        }
    }

    @Test
    public void anUnknownStageIsShownAsIs() {
        // NvConnection reports the app's name as the stage of the launch
        assertEquals(0, StreamStages.labelRes("Hades II"));
        assertEquals("Hades II", StreamStages.label(french(), "Hades II"));
        assertEquals(0, StreamStages.labelRes(null));
    }

    @Test
    public void frenchStreamMessages() {
        Context fr = french();
        assertEquals("Échec au démarrage : négociation RTSP (erreur -408)",
                fr.getString(com.limelight.R.string.conn_error_stage, StreamStages.label(fr, "RTSP handshake"), -408));
        assertEquals("Démarrage : ouverture du flux vidéo",
                fr.getString(com.limelight.R.string.conn_starting_stage, StreamStages.label(fr, "video stream establishment")));
    }

    // ---- static fonts (API < 26) --------------------------------------------------------------------

    @Test
    public void staticWeights() {
        assertEquals(400, V2Theme.staticWeight("inter", 400));
        assertEquals(400, V2Theme.staticWeight("inter", 500));
        // 550 and up read as bold: the 600 labels keep their weight
        assertEquals(700, V2Theme.staticWeight("inter", 600));
        assertEquals(700, V2Theme.staticWeight("inter", 800));
        assertEquals(700, V2Theme.staticWeight("space", 700));
        assertEquals(900, V2Theme.staticWeight("archivo", 900));
        assertEquals(700, V2Theme.staticWeight("archivo", 700));
        assertEquals(800, V2Theme.staticWeight("bricolage", 800));
        assertEquals(400, V2Theme.staticWeight("bricolage", 400));
    }

    @Test
    public void everyStaticFontIsInTheAssets() {
        File dir = new File("src/main/assets/fonts");
        String[][] families = {{"inter", "400", "500", "600", "700", "800"}, {"space", "400", "500", "600", "700"},
                {"archivo", "400", "500", "600", "700", "800", "900"}, {"bricolage", "400", "500", "600", "700", "800"}};
        for (String[] f : families) {
            for (int i = 1; i < f.length; i++) {
                int w = V2Theme.staticWeight(f[0], Integer.parseInt(f[i]));
                File font = new File(dir, "v2_" + f[0] + "_" + w + ".ttf");
                assertTrue(font + " missing (scripts/v2-static-fonts.py)", font.isFile() && font.length() > 10000);
            }
        }
    }
}
