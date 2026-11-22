package com.limelight.binding.input.touch;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.NvConnection;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class TouchContextMoverTest {
    private static final class RecordingMover implements MouseMover {
        int moves;
        short lastDx, lastDy;
        int sumDx;
        @Override public void move(short dx, short dy) { moves++; lastDx = dx; lastDy = dy; sumDx += dx; }
    }

    private NvConnection conn;
    private RecordingMover mover;
    private View view;

    @Before
    public void setUp() {
        conn = mock(NvConnection.class);
        mover = new RecordingMover();
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        view = new View(context);
        parent.addView(view, new FrameLayout.LayoutParams(1000, 500));
        parent.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1000, 500);
    }

    @Test
    public void trackpadMoveGoesThroughMover() {
        TrackpadContext tc = new TrackpadContext(conn, 0);
        tc.setMouseMover(mover);
        tc.setPointerCount(1);
        tc.touchDownEvent(100, 100, 0, true);
        tc.touchMoveEvent(200, 100, 10);
        assertTrue(mover.moves > 0);
        verify(conn, never()).sendMouseMove(anyShort(), anyShort());
    }

    @Test
    public void flickMomentumGoesThroughMover() {
        TrackpadContext tc = new TrackpadContext(conn, 0);
        tc.setMouseMover(mover);
        tc.setPointerCount(1);
        tc.touchDownEvent(100, 100, 0, true);
        for (int i = 1; i <= 5; i++) {
            tc.touchMoveEvent(100 + i * 60, 100, i * 10);
        }
        int beforeFlick = mover.moves;
        tc.touchUpEvent(400, 100, 51);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertTrue(mover.moves > beforeFlick);
        verify(conn, never()).sendMouseMove(anyShort(), anyShort());
    }

    @Test
    public void gamingMoveGoesThroughMoverEvenInAbsoluteMode() {
        PreferenceConfiguration prefs = new PreferenceConfiguration();
        prefs.absoluteMouseMode = true;
        prefs.touchPadSensitivity = 100;
        prefs.touchPadYSensitity = 100;
        RelativeTouchContext rc = new RelativeTouchContext(conn, 0, 1280, 720, view, prefs);
        rc.setMouseMover(mover);
        rc.setPointerCount(1);
        rc.touchDownEvent(100, 100, 0, true);
        rc.touchMoveEvent(200, 100, 10);
        assertTrue(mover.moves > 0);
        verify(conn, never()).sendMouseMoveAsMousePosition(anyShort(), anyShort(), anyShort(), anyShort());
        verify(conn, never()).sendMouseMove(anyShort(), anyShort());
    }

    @Test
    public void noMoverKeepsRelativeSend() {
        TrackpadContext tc = new TrackpadContext(conn, 0);
        tc.setPointerCount(1);
        tc.touchDownEvent(100, 100, 0, true);
        tc.touchMoveEvent(200, 100, 10);
        verify(conn, atLeastOnce()).sendMouseMove(anyShort(), anyShort());
    }

    @Test
    public void trackpadMoverGetsRawFingerDelta() {
        TrackpadContext tc = new TrackpadContext(conn, 0);
        tc.setMouseMover(mover);
        tc.setPointerCount(1);
        tc.touchDownEvent(100, 100, 0, true);
        tc.touchMoveEvent(200, 100, 10);
        assertEquals(100, mover.lastDx);
        assertEquals(0, mover.lastDy);
    }

    @Test
    public void gamingMoverGetsRawFingerDelta() {
        RelativeTouchContext rc = gaming(100);
        rc.touchMoveEvent(200, 100, 10);
        assertEquals(100, mover.lastDx);
    }

    @Test
    public void gamingMoverScalesBySensitivity() {
        RelativeTouchContext rc = gaming(50);
        rc.touchMoveEvent(200, 100, 10);
        assertEquals(50, mover.lastDx);
    }

    @Test
    public void noMoverKeepsAcceleration() {
        TrackpadContext tc = new TrackpadContext(conn, 0);
        tc.setPointerCount(1);
        tc.touchDownEvent(100, 100, 0, true);
        tc.touchMoveEvent(200, 100, 10);
        verify(conn).sendMouseMove((short) (100 * Math.cbrt(100 / 8.0)), (short) 0);
    }

    @Test
    public void gamingMoverKeepsSlowMotionBelowFullSensitivity() {
        // 1 px at 50 % is half a pixel: dropped every event, a slow finger never moved the cursor
        RelativeTouchContext rc = gaming(50);
        for (int i = 1; i <= 10; i++) {
            rc.touchMoveEvent(100 + i, 100, i * 10);
        }
        assertEquals(5, mover.sumDx);
    }

    private RelativeTouchContext gaming(int sensitivity) {
        PreferenceConfiguration prefs = new PreferenceConfiguration();
        prefs.touchPadSensitivity = sensitivity;
        prefs.touchPadYSensitity = sensitivity;
        RelativeTouchContext rc = new RelativeTouchContext(conn, 0, 1280, 720, view, prefs);
        rc.setMouseMover(mover);
        rc.setPointerCount(1);
        rc.touchDownEvent(100, 100, 0, true);
        return rc;
    }

    @Test
    public void absoluteTouchNotifiesObserver() {
        final float[] seen = {-1, -1};
        AbsoluteTouchContext ac = new AbsoluteTouchContext(conn, 0, view, false);
        ac.setPositionObserver((fx, fy) -> { seen[0] = fx; seen[1] = fy; });
        ac.setPointerCount(1);
        ac.touchDownEvent(0, 0, 0, true);
        ac.touchMoveEvent(250, 100, 10);
        assertEquals(0.25f, seen[0], 0.001f);
        assertEquals(0.2f, seen[1], 0.001f);
    }
}
