package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.limelight.R;

import org.junit.Test;

/** The segue before the stream and its toast (prototype goSegue / launch(), V8-24, V8-71). */
public class PendingSegueTest {
    @Test
    public void linesFollowTheMode() {
        assertEquals(R.string.v2_segue_launch, PendingSegue.lineRes(PendingSegue.Mode.START, false));
        assertEquals(R.string.v2_segue_launch, PendingSegue.lineRes(PendingSegue.Mode.INSIDE, true));
        assertEquals(R.string.v2_segue_launch, PendingSegue.lineRes(PendingSegue.Mode.RELAUNCH, false));
        assertEquals(R.string.v2_segue_resume, PendingSegue.lineRes(PendingSegue.Mode.RESUME, false));
        // Another device streams: « Connexion à la session de ATLAS-PC… »
        assertEquals(R.string.v2_segue_join, PendingSegue.lineRes(PendingSegue.Mode.RESUME, true));
    }

    @Test
    public void toastsFollowTheMode() {
        assertEquals(R.string.v2_toast_connected, PendingSegue.toastRes(PendingSegue.Mode.START, true));
        assertEquals(R.string.v2_toast_resumed, PendingSegue.toastRes(PendingSegue.Mode.RESUME, false));
        assertEquals(R.string.v2_toast_joined, PendingSegue.toastRes(PendingSegue.Mode.RESUME, true));
        assertEquals(R.string.v2_launched_inside, PendingSegue.toastRes(PendingSegue.Mode.INSIDE, false));
        assertEquals(R.string.v2_toast_relaunched, PendingSegue.toastRes(PendingSegue.Mode.RELAUNCH, false));
    }

    @Test
    public void theSegueShowsAtLeastOneAndAHalfSeconds() {
        assertEquals(1500, PendingSegue.remaining(10_000, 10_000));
        assertEquals(400, PendingSegue.remaining(10_000, 11_100));
        assertEquals(0, PendingSegue.remaining(10_000, 15_000));
        // A clock that went back never gives more than the full time
        assertEquals(1500, PendingSegue.remaining(10_000, 9_000));
    }

    @Test
    public void takenOnceAndNotWhenStale() {
        PendingSegue s = new PendingSegue();
        s.createdAt = 1_000;
        PendingSegue.set(s);
        assertNotNull(PendingSegue.take(2_000));
        assertNull(PendingSegue.take(2_000));
        PendingSegue.set(s);
        assertNull(PendingSegue.take(1_000 + PendingSegue.STALE_MS + 1));
        assertNull(PendingSegue.take(3_000));
    }
}
