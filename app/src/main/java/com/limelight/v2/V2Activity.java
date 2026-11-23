package com.limelight.v2;

import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.limelight.utils.UiHelper;

/**
 * Base of the v2 activities: paints the window in the theme (light/dark follows the device live
 * in Système mode), tracks the last input (V2Input) and maps the gamepad: A validates, B goes back,
 * Y / Menu opens the options of the focused item.
 */
public abstract class V2Activity extends AppCompatActivity implements V2Input.Listener {
    protected V2Theme theme;
    private boolean lastSystemDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        theme = V2Theme.load(this);
        lastSystemDark = V2Theme.isSystemDark(this);
        applyWindowColors();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onV2Back();
            }
        });
        // Leaving touch mode (first D-pad / gamepad key after touching): Android would focus the
        // first item of the page (the header); the screen's own starting point is better.
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnTouchModeChangeListener(inTouchMode -> {
            if (!inTouchMode) {
                decor.post(this::onV2InitialFocus);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        V2Input.addListener(this);
        // The theme may have changed elsewhere (Réglages) while this screen was hidden
        V2Theme now = V2Theme.load(this);
        if (!now.id.equals(theme.id) || now.dark != theme.dark || now.mode != theme.mode) {
            theme = now;
            applyWindowColors();
            rebuild();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        V2Input.removeListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean systemDark = V2Theme.isSystemDark(this);
        V2Theme now = V2Theme.load(this);
        boolean themeChanged = systemDark != lastSystemDark && now.dark != theme.dark;
        lastSystemDark = systemDark;
        theme = now;
        if (themeChanged) {
            applyWindowColors();
        }
        // Size and orientation change the density too: rebuild either way
        rebuild();
    }

    /** Rebuilds the content with the current theme and density */
    protected abstract void rebuild();

    /** Back, B, Escape */
    protected void onV2Back() {
        finish();
    }

    /** Y / Menu on the focused view; return true when handled */
    protected boolean onV2Options(View focused) {
        return false;
    }

    /** LB / RB, Q / E (home only) */
    protected boolean onV2Shoulder(int delta) {
        return false;
    }

    /** The first key of a gamepad or keyboard with nothing focused: place the focus */
    protected boolean onV2InitialFocus() {
        return false;
    }

    protected void cycleThemeMode() {
        V2Theme.saveMode(this, V2Theme.nextMode(theme.mode));
        theme = V2Theme.load(this);
        applyWindowColors();
        rebuild();
    }

    @SuppressWarnings("deprecation")
    protected void applyWindowColors() {
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(theme.bg));
        w.setStatusBarColor(theme.bg);
        w.setNavigationBarColor(theme.bg);
        View decor = w.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(theme.dark ? 0 : light, light);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = decor.getSystemUiVisibility();
            if (theme.dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onInputKindChanged(V2Input.Kind kind) {
        onInputChanged();
    }

    protected void onInputChanged() {
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        V2Input.onTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        V2Input.onGenericMotion(ev);
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        V2Input.onKey(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        View focused = getCurrentFocus();

        // First directional or confirm key with nothing focused: put the focus somewhere sensible
        if (down && event.getRepeatCount() == 0 && (focused == null || isFocusParked(focused)) && isNavigationKey(code)) {
            if (onV2InitialFocus()) {
                return true;
            }
        }

        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
                // Android has no default for A in a normal view: click the focused item
                if (up && focused != null && focused.isClickable()) {
                    focused.performClick();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
                if (up) {
                    onV2Back();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                if (up) {
                    onV2Options(focused);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_Q:
                if (shoulderKeysEnabled()) {
                    if (down && event.getRepeatCount() == 0) {
                        onV2Shoulder(-1);
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_E:
                if (shoulderKeysEnabled()) {
                    if (down && event.getRepeatCount() == 0) {
                        onV2Shoulder(1);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    /** LB / RB / Q / E switch PCs (home only) */
    protected boolean shoulderKeysEnabled() {
        return false;
    }

    /** A focus on the scroll container itself is no real focus */
    private static boolean isFocusParked(View v) {
        return v instanceof android.widget.ScrollView || v instanceof androidx.recyclerview.widget.RecyclerView;
    }

    private static boolean isNavigationKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_TAB:
                return true;
            default:
                return false;
        }
    }
}
