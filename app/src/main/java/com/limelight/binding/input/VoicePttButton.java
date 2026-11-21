package com.limelight.binding.input;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import com.limelight.R;

/**
 * On-screen push-to-talk button: holding it holds one key on the host.
 *
 * Two modes. Normal: a press sends key down, the release (or a cancel) sends key up, and every
 * touch is consumed so nothing reaches the stream. Moving (entered from the in-game menu): the
 * button follows the finger and never sends anything; a touch on the full-screen shield behind
 * it confirms and stores the position.
 *
 * The one failure that matters is a key left down -- the host's dictation would run forever --
 * so {@link #releaseKey()} is idempotent and every way out (hide, move, menu, focus, pause, stop)
 * calls it.
 */
public class VoicePttButton {
    public interface KeySender {
        void keyDown();
        void keyUp();
    }

    public static final String PREF_X = "voice_ptt_button_x";
    public static final String PREF_Y = "voice_ptt_button_y";

    static final float ALPHA_IDLE = 0.5f;
    static final float ALPHA_ACTIVE = 0.9f;
    private static final int MARGIN_DP = 16;

    private final ImageButton button;
    private final View moveShield;
    private final SharedPreferences prefs;
    private final KeySender sender;

    private boolean keyDown;
    private boolean moving;
    private float dragDX, dragDY;

    public VoicePttButton(ImageButton button, View moveShield, SharedPreferences basePrefs, KeySender sender) {
        this.button = button;
        this.moveShield = moveShield;
        this.prefs = basePrefs;
        this.sender = sender;
        button.setAlpha(ALPHA_IDLE);
        installListeners();
    }

    public void setShown(boolean shown) {
        if (!shown) {
            releaseKey();
            endMove();
            button.setVisibility(View.GONE);
            return;
        }
        button.setAlpha(ALPHA_IDLE);
        button.setVisibility(View.VISIBLE);
        button.post(this::reapplyPosition);
    }

    public boolean isShown() {
        return button.getVisibility() == View.VISIBLE;
    }

    public void beginMove() {
        releaseKey();
        moving = true;
        button.setBackgroundResource(R.drawable.voice_ptt_button_moving);
        button.setAlpha(ALPHA_ACTIVE);
        moveShield.setVisibility(View.VISIBLE);
    }

    public boolean isMoving() {
        return moving;
    }

    public void endMove() {
        if (!moving) {
            return;
        }
        moving = false;
        View parent = (View) button.getParent();
        if (parent != null) {
            float[] f = VoicePttButtonPosition.toFraction(button.getX(), button.getY(),
                    parent.getWidth(), parent.getHeight(), buttonSize());
            prefs.edit().putFloat(PREF_X, f[0]).putFloat(PREF_Y, f[1]).apply();
        }
        button.setBackgroundResource(R.drawable.floating_menu_button);
        button.setAlpha(ALPHA_IDLE);
        moveShield.setVisibility(View.GONE);
    }

    public void releaseKey() {
        if (!keyDown) {
            return;
        }
        keyDown = false;
        sender.keyUp();
        if (!moving) {
            button.setAlpha(ALPHA_IDLE);
        }
    }

    public void reapplyPosition() {
        View parent = (View) button.getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            return;
        }
        int marginPx = Math.round(MARGIN_DP * button.getResources().getDisplayMetrics().density);
        float[] p = VoicePttButtonPosition.toPixels(prefs.getFloat(PREF_X, -1f), prefs.getFloat(PREF_Y, -1f),
                parent.getWidth(), parent.getHeight(), buttonSize(), marginPx);
        button.setX(p[0]);
        button.setY(p[1]);
    }

    private int buttonSize() {
        int w = button.getWidth();
        if (w > 0) {
            return w;
        }
        return button.getLayoutParams() != null ? Math.max(0, button.getLayoutParams().width) : 0;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installListeners() {
        button.setOnTouchListener((view, event) -> {
            if (moving) {
                drag(view, event);
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!keyDown) {
                        keyDown = true;
                        button.setAlpha(ALPHA_ACTIVE);
                        sender.keyDown();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    releaseKey();
                    break;
                default:
                    break;
            }
            return true;
        });

        moveShield.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                endMove();
            }
            return true;
        });
    }

    private void drag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragDX = view.getX() - event.getRawX();
                dragDY = view.getY() - event.getRawY();
                break;
            case MotionEvent.ACTION_MOVE: {
                View parent = (View) view.getParent();
                float maxX = parent != null ? Math.max(0, parent.getWidth() - view.getWidth()) : Float.MAX_VALUE;
                float maxY = parent != null ? Math.max(0, parent.getHeight() - view.getHeight()) : Float.MAX_VALUE;
                view.setX(VoicePttButtonPosition.clamp(event.getRawX() + dragDX, 0, maxX));
                view.setY(VoicePttButtonPosition.clamp(event.getRawY() + dragDY, 0, maxY));
                break;
            }
            default:
                break;
        }
    }
}
