package com.limelight.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Zooming must grow the letterboxed stream over the whole screen, like Chrome Remote Desktop, not
 * inside its 16:9 box, which clips it and keeps the black bars.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class PanZoomHandlerStreamTest {
    private FrameLayout screen;
    private FrameLayout container;
    private ZoomView h;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // A portrait phone (1000x2000) showing a 16:9 stream fitted to its width: bars above and below
        screen = new FrameLayout(context);
        container = new FrameLayout(context);
        container.addView(new View(context), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        screen.addView(container, new FrameLayout.LayoutParams(1000, 562, Gravity.CENTER));
        screen.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        screen.layout(0, 0, 1000, 2000);

        h = PanZoomHandler.forStreamContainer(context, null, container, new PreferenceConfiguration());
    }

    @Test
    public void zoomIsBoundedByTheScreenNotTheBox() {
        assertEquals(2000f, h.getParentHeight(), 0.01f);
        assertEquals(562f, h.getViewHeight(), 0.01f);
    }

    @Test
    public void zoomedStreamGrowsIntoTheBars() {
        float bandTop = container.getTop();                       // 719
        h.scaleAround(3f, 500, 1000);
        // 562 * 3 = 1686 tall, centred on a 2000 px screen: it now starts well above the old band
        assertTrue(container.getY() < bandTop);
        assertEquals((2000 - 1686) / 2f, container.getY(), 1f);
        assertEquals(3f, container.getScaleY(), 0.01f);
    }
}
