package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.limelight.BuildConfig;
import com.limelight.PcView;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** LavArtemis Dev: the launcher's PcView hands over to the v2 home, except for the legacy paths. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class PcViewRedirectTest {
    @Test
    public void devLauncherOpensTheV2Home() {
        Assume.assumeTrue("Dev build only", BuildConfig.LAV_DEV);
        PcView activity = Robolectric.buildActivity(PcView.class).create().get();
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(HomeActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void legacyListStaysReachable() {
        Intent legacy = new Intent().putExtra(PcView.EXTRA_LEGACY_UI, true);
        PcView activity = Robolectric.buildActivity(PcView.class, legacy).create().get();
        assertFalse(activity.isFinishing());
    }
}
