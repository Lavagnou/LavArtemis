package com.limelight.binding.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class VoicePttButtonTest {
    private static final class RecordingSender implements VoicePttButton.KeySender {
        int downs, ups;
        @Override public void keyDown() { downs++; }
        @Override public void keyUp() { ups++; }
    }

    private RecordingSender s;
    private ImageButton button;
    private View shield;
    private SharedPreferences prefs;
    private VoicePttButton c;
    private float lastX = 900, lastY = 500;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        shield = new View(context);
        shield.setVisibility(View.GONE);
        parent.addView(shield, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        button = new ImageButton(context);
        button.setVisibility(View.GONE);
        parent.addView(button, new FrameLayout.LayoutParams(150, 150));
        parent.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1920, 1080);

        prefs = context.getSharedPreferences("voice_ptt_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        s = new RecordingSender();
        c = new VoicePttButton(button, shield, prefs, s);
    }

    @Test
    public void pressHoldsKeyUntilRelease() {
        down();
        assertEquals(1, s.downs);
        assertEquals(0, s.ups);
        up();
        assertEquals(1, s.ups);
    }

    @Test
    public void cancelReleasesKey() {
        down();
        cancel();
        assertEquals(1, s.ups);
    }

    @Test
    public void secondPointerDoesNotRetrigger() {
        down();
        pointer(MotionEvent.ACTION_POINTER_DOWN);
        pointer(MotionEvent.ACTION_POINTER_UP);
        assertEquals(1, s.downs);
        assertEquals(0, s.ups);
        up();
        assertEquals(1, s.ups);
    }

    @Test
    public void releaseKeyIsIdempotent() {
        down();
        c.releaseKey();
        c.releaseKey();
        up();
        assertEquals(1, s.ups);
    }

    @Test
    public void hideWhilePressedReleasesKey() {
        c.setShown(true);
        down();
        c.setShown(false);
        assertEquals(1, s.ups);
        assertEquals(View.GONE, button.getVisibility());
    }

    @Test
    public void moveModeSendsNothing() {
        c.beginMove();
        down();
        move(+200, -100);
        up();
        assertEquals(0, s.downs);
        assertEquals(0, s.ups);
        assertTrue(c.isMoving());
    }

    @Test
    public void beginMoveWhilePressedReleases() {
        down();
        c.beginMove();
        assertEquals(1, s.ups);
    }

    @Test
    public void dragMovesTheButton() {
        c.beginMove();
        float before = button.getX();
        down();
        move(+200, 0);
        assertEquals(before + 200, button.getX(), 0.5f);
    }

    @Test
    public void shieldTapEndsMoveAndSaves() {
        c.beginMove();
        down();
        move(-300, -300);
        up();
        tapShield();
        assertFalse(c.isMoving());
        assertEquals(View.GONE, shield.getVisibility());
        assertTrue(prefs.getFloat("voice_ptt_button_x", -1f) >= 0f);
        assertTrue(prefs.getFloat("voice_ptt_button_y", -1f) >= 0f);
    }

    @Test
    public void rotationDuringMoveLeavesButtonOnScreen() {
        c.beginMove();
        button.setX(1700f); // right edge in landscape
        FrameLayout parent = (FrameLayout) button.getParent();
        parent.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1080, 1920); // rotated to portrait mid-move
        tapShield();
        assertTrue(button.getX() <= 1080 - 150);
    }

    @Test
    public void alphaFollowsPress() {
        c.setShown(true);
        assertEquals(0.5f, button.getAlpha(), 0.01f);
        down();
        assertEquals(0.9f, button.getAlpha(), 0.01f);
        up();
        assertEquals(0.5f, button.getAlpha(), 0.01f);
    }

    private void down() {
        send(MotionEvent.ACTION_DOWN);
    }

    private void up() {
        send(MotionEvent.ACTION_UP);
    }

    private void cancel() {
        send(MotionEvent.ACTION_CANCEL);
    }

    private void move(float dx, float dy) {
        lastX += dx;
        lastY += dy;
        send(MotionEvent.ACTION_MOVE);
    }

    private void pointer(int action) {
        send(action | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
    }

    private void send(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(now, now, action, lastX, lastY, 0);
        button.dispatchTouchEvent(e);
        e.recycle();
    }

    private void tapShield() {
        long now = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10, 10, 0);
        shield.dispatchTouchEvent(e);
        e.recycle();
    }
}
