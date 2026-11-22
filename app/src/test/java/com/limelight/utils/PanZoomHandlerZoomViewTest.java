package com.limelight.utils;

import android.content.Context;
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

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class PanZoomHandlerZoomViewTest {
    private View surface;
    private ZoomView h;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        surface = new View(context);
        parent.addView(surface, new FrameLayout.LayoutParams(1000, 500));
        parent.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1000, 500);
        h = new PanZoomHandler(context, null, surface, parent, new PreferenceConfiguration());
    }

    @Test
    public void scaleAroundClampsToTen() {
        h.scaleAround(50f, 500, 250);
        assertEquals(10f, h.getScaleFactor(), 0.01f);
    }

    @Test
    public void scaleAroundKeepsFocusFixed() {
        h.scaleAround(2f, 0, 0);
        assertEquals(0f, h.getChildX(), 0.5f);
        assertEquals(0f, h.getChildY(), 0.5f);
    }

    @Test
    public void setTranslationIsConstrained() {
        h.scaleAround(2f, 500, 250);
        h.setTranslation(100, -5000);
        assertEquals(0f, h.getChildX(), 0.5f);
        assertEquals(-500f, h.getChildY(), 0.5f);
    }

    @Test
    public void resetZoomReturnsToOneX() {
        h.scaleAround(3f, 500, 250);
        h.resetZoom();
        assertEquals(1f, h.getScaleFactor(), 0.01f);
        assertEquals(1f, surface.getScaleX(), 0.01f);
    }

    @Test
    public void exposesViewAndParentSizes() {
        assertEquals(1000f, h.getViewWidth(), 0.01f);
        assertEquals(500f, h.getViewHeight(), 0.01f);
        assertEquals(1000f, h.getParentWidth(), 0.01f);
        assertEquals(500f, h.getParentHeight(), 0.01f);
    }
}
