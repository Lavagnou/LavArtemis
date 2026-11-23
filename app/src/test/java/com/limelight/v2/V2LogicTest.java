package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Theme tokens, dimming, PC list, hidden games, session clock. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class V2LogicTest {
    private Context ctx;

    @Before
    public void setUp() {
        ctx = ApplicationProvider.getApplicationContext();
    }

    private static void assertDim(V2Theme.Dim d, float gray, float bright, float opacity) {
        assertEquals(gray, d.gray, 1e-6);
        assertEquals(bright, d.bright, 1e-6);
        assertEquals(opacity, d.opacity, 1e-6);
    }

    @Test
    public void dimValuesMatchTheDesktop() {
        // Theme.qml dim(focused), per theme and mode
        assertDim(V2Theme.of("braise", true).dim(false), 0.9f, 0.42f, 1.0f);
        assertDim(V2Theme.of("braise", true).dim(true), 0.35f, 0.85f, 1.0f);
        assertDim(V2Theme.of("braise", false).dim(false), 0.9f, 1.0f, 0.45f);
        assertDim(V2Theme.of("braise", false).dim(true), 0.3f, 1.0f, 0.9f);
        assertDim(V2Theme.of("affiche", true).dim(false), 1.0f, 1.0f, 0.3f);
        assertDim(V2Theme.of("affiche", false).dim(true), 0.5f, 1.0f, 0.75f);
        assertDim(V2Theme.of("ludique", true).dim(false), 1.0f, 0.7f, 0.55f);
        assertDim(V2Theme.of("ludique", true).dim(true), 0.3f, 1.0f, 0.95f);
        assertDim(V2Theme.of("ludique", false).dim(false), 1.0f, 1.0f, 0.4f);
        assertDim(V2Theme.of("ludique", false).dim(true), 0.4f, 1.0f, 0.85f);
    }

    @Test
    public void tokensMatchTheDesktop() {
        V2Theme braise = V2Theme.of("braise", true);
        assertEquals(Color.parseColor("#0E0D0C"), braise.bg);
        assertEquals(Color.parseColor("#FF6A2B"), braise.live);
        assertEquals(18, braise.r);
        assertEquals(1, braise.border);
        V2Theme affiche = V2Theme.of("affiche", false);
        assertEquals(Color.parseColor("#FFD60A"), affiche.live);
        assertEquals(Color.parseColor("#111111"), affiche.onLive);
        assertEquals(0, affiche.r);
        assertEquals(3, affiche.border);
        assertEquals(affiche.fg, affiche.borderColor);
        assertTrue(affiche.upper);
        assertEquals(900, affiche.displayWeight);
        V2Theme ludique = V2Theme.of("ludique", true);
        assertEquals(Color.parseColor("#C6F432"), ludique.live);
        assertEquals(28, ludique.r);
        assertEquals(0, ludique.border);
        assertEquals("braise", V2Theme.normalize("unknown"));
    }

    @Test
    public void modeCyclesAndFollowsTheSystem() {
        assertEquals(V2Theme.MODE_LIGHT, V2Theme.nextMode(V2Theme.MODE_SYSTEM));
        assertEquals(V2Theme.MODE_DARK, V2Theme.nextMode(V2Theme.MODE_LIGHT));
        assertEquals(V2Theme.MODE_SYSTEM, V2Theme.nextMode(V2Theme.MODE_DARK));
        assertTrue(V2Theme.resolveDark(V2Theme.MODE_SYSTEM, true));
        assertFalse(V2Theme.resolveDark(V2Theme.MODE_SYSTEM, false));
        assertFalse(V2Theme.resolveDark(V2Theme.MODE_LIGHT, true));
        assertTrue(V2Theme.resolveDark(V2Theme.MODE_DARK, false));

        V2Theme.saveTheme(ctx, "ludique");
        V2Theme.saveMode(ctx, V2Theme.MODE_DARK);
        V2Theme t = V2Theme.load(ctx);
        assertEquals("ludique", t.id);
        assertTrue(t.dark);
    }

    @Test
    public void fontWeightsRoundToTheEmbeddedOnes() {
        assertEquals(700, V2Theme.nearestWeight("space", 900));
        assertEquals(900, V2Theme.nearestWeight("archivo", 900));
        assertEquals(800, V2Theme.nearestWeight("bricolage", 800));
        assertEquals(600, V2Theme.nearestWeight("inter", 600));
        // The fonts load (falls back to sans-serif otherwise, never null)
        assertTrue(V2Theme.of("affiche", true).displayFont(ctx) != null);
    }

    private static ComputerDetails pc(String name, ComputerDetails.State state, PairingManager.PairState pair) {
        ComputerDetails d = new ComputerDetails();
        d.uuid = "uuid-" + name;
        d.name = name;
        d.state = state;
        d.pairState = pair;
        return d;
    }

    @Test
    public void pcListKeepsPairedOnlineFirst() {
        ComputerDetails a = pc("Zeta", ComputerDetails.State.ONLINE, PairingManager.PairState.PAIRED);
        ComputerDetails b = pc("Alpha", ComputerDetails.State.OFFLINE, PairingManager.PairState.PAIRED);
        ComputerDetails c = pc("Beta", ComputerDetails.State.ONLINE, PairingManager.PairState.NOT_PAIRED);
        ComputerDetails d = pc("Gamma", ComputerDetails.State.ONLINE, PairingManager.PairState.PAIRED);
        List<ComputerDetails> list = HostList.paired(Arrays.asList(a, b, c, d));
        assertEquals(3, list.size());
        assertEquals("Gamma", list.get(0).name);
        assertEquals("Zeta", list.get(1).name);
        assertEquals("Alpha", list.get(2).name);
        assertEquals(2, HostList.indexOf(list, "UUID-ALPHA"));
        assertEquals(0, HostList.indexOf(list, "nope"));
        assertEquals(2, HostList.step(0, -1, 3));
        assertEquals(0, HostList.step(2, 1, 3));
    }

    @Test
    public void unpolledPcWithoutCertificateIsNotPaired() {
        ComputerDetails unknown = pc("Old", ComputerDetails.State.UNKNOWN, null);
        assertFalse(HostList.isPaired(unknown));
    }

    @Test
    public void hidingAndShowingAGame() {
        assertTrue(HiddenApps.read(ctx, "pc1").isEmpty());
        Set<Integer> s = HiddenApps.setHidden(ctx, "pc1", 42, true);
        assertTrue(s.contains(42));
        assertTrue(HiddenApps.read(ctx, "pc1").contains(42));
        assertTrue(HiddenApps.read(ctx, "pc2").isEmpty());
        HiddenApps.setHidden(ctx, "pc1", 43, true);
        s = HiddenApps.setHidden(ctx, "pc1", 42, false);
        assertFalse(s.contains(42));
        assertEquals(1, HiddenApps.read(ctx, "pc1").size());
    }

    @Test
    public void sessionClockCountsFromFirstSeen() {
        long t0 = 1_000_000L;
        assertEquals(t0, SessionClock.observe(ctx, "pc", 7, t0));
        assertEquals(t0, SessionClock.observe(ctx, "pc", 7, t0 + 5 * 60_000));
        assertEquals(-1, SessionClock.minutes(t0, t0 + 30_000));
        assertEquals(42, SessionClock.minutes(t0, t0 + 42 * 60_000 + 10));
        // Another app: a new session
        long t1 = t0 + 10 * 60_000;
        assertEquals(t1, SessionClock.observe(ctx, "pc", 8, t1));
        // Nothing runs: forgotten
        assertEquals(0, SessionClock.observe(ctx, "pc", 0, t1));
        assertEquals(t1 + 1, SessionClock.observe(ctx, "pc", 8, t1 + 1));
    }
}
