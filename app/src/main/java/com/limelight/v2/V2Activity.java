package com.limelight.v2;

import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.limelight.utils.UiHelper;

import java.util.List;

/**
 * Base of the v2 activities: paints the window in the theme (light/dark follows the device live
 * in Système mode), tracks the last input (V2Input) and maps the gamepad: A validates, B goes back,
 * Y / Menu opens the options of the focused item.
 */
public abstract class V2Activity extends AppCompatActivity implements V2Input.Listener {
    protected V2Theme theme;
    private boolean lastSystemDark;
    protected final Handler handler = new Handler(Looper.getMainLooper());
    private boolean aPressed;
    private boolean aHeldFired;
    /** Held directions: 380 / 110 ms repetition, vertical first on a diagonal (DirKeys) */
    private final com.limelight.v2.ui.DirKeys dirKeys = new com.limelight.v2.ui.DirKeys(new com.limelight.v2.ui.DirKeys.Target() {
        @Override
        public boolean dispatch(KeyEvent event) {
            return dispatchKeyEvent(event);
        }

        @Override
        public View focused() {
            return focusedView();
        }
    });
    private final Runnable aHeld = () -> {
        if (aPressed) {
            aHeldFired = true;
            View f = focusedView();
            if (f != null) {
                onV2Options(f);
            }
        }
    };

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
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Android 9: the cutout-safe native resolution of Réglages needs the window's insets
        if (com.limelight.v2.settings.SettingsEnv.rememberCutout(this)) {
            onCutoutKnown();
        }
    }

    /** Android 9: the display cutout became known (Réglages re-reads its resolutions) */
    protected void onCutoutKnown() {
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
        handler.removeCallbacks(aHeld);
        aPressed = false;
        stopDirRepeat();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            // A dialog took the keys: its release will not come here
            stopDirRepeat();
        }
    }

    private void stopDirRepeat() {
        dirKeys.cancel();
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

    /**
     * B, Start, Échap (the gamepad and keyboard back); the system's back gesture or button goes to
     * {@link #onV2Back} directly. The home overrides it to ask before leaving the app.
     */
    protected void onV2BackKey() {
        onV2Back();
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
        View focused = focusedView();

        // First directional or confirm key with nothing focused: put the focus somewhere sensible
        if (down && event.getRepeatCount() == 0 && (focused == null || isFocusParked(focused)) && isNavigationKey(code)) {
            if (onV2InitialFocus()) {
                return true;
            }
        }

        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
                // Android has no default for A in a normal view. Prototype pressA(): on an item
                // that has options (app card, game, PC card) A starts a 500 ms timer, held it opens
                // the options (as Y) and the release does nothing, released before it activates the
                // item; anything else activates on the press.
                if (down && event.getRepeatCount() == 0) {
                    if (focused != null && !isHoldable(focused)) {
                        aPressed = false;
                        if (focused.isClickable()) {
                            focused.performClick();
                        }
                        return true;
                    }
                    aPressed = true;
                    aHeldFired = false;
                    handler.removeCallbacks(aHeld);
                    handler.postDelayed(aHeld, HOLD_MS);
                } else if (up) {
                    handler.removeCallbacks(aHeld);
                    if (aPressed && !aHeldFired && !event.isCanceled() && focused != null && focused.isClickable()) {
                        focused.performClick();
                    }
                    aPressed = false;
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_ESCAPE:
                if (up) {
                    onV2BackKey();
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
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (dirKeys.consume(event)) {
                    return true;
                }
                if (down) {
                    int mx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0);
                    int my = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0);
                    if (onV2Move(focused, mx, my)) {
                        return true;
                    }
                    List<List<View>> rows = focusRows();
                    if (rows != null && !rows.isEmpty()) {
                        int dx = mx;
                        int dy = my;
                        if (dx != 0 && onV2Horizontal(focused, dx)) {
                            return true;
                        }
                        View target = FocusGrid.move(rows, focusedItem(rows, focused), dx, dy, V2Activity::screenCenterX);
                        if (target != null && target != focused) {
                            target.requestFocus();
                            onGridFocus(target);
                        }
                        return true;
                    }
                } else if (focusRows() != null) {
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    /** The focused view of the window (as getCurrentFocus(), read from the window's own views) */
    protected View focusedView() {
        View decor = getWindow() == null ? null : getWindow().peekDecorView();
        return decor == null ? null : decor.findFocus();
    }

    /** A themed toast at the top of the screen (prototype toast, 2.4 s) */
    public void toast(CharSequence text) {
        com.limelight.v2.ui.V2Toast.show(this, theme, text);
    }

    public void toast(int resId) {
        toast(getString(resId));
    }

    /** Hold time of A before the options open (prototype pressA) */
    public static final long HOLD_MS = 500;

    /**
     * The screen's focus grid (prototype grid()): rows of focusable views, moved through by the
     * D-pad with the prototype's rule ({@link FocusGrid}). Null leaves Android's own focus search.
     */
    protected List<List<View>> focusRows() {
        return null;
    }

    /**
     * A D-pad move handled by the screen itself (a scrolling grid whose items are not all built, as
     * the library); true when handled. Called before the focus grid.
     */
    protected boolean onV2Move(View focused, int dx, int dy) {
        return false;
    }

    /** Left / right on the focused view before the grid moves (a choice row steps its value); true when handled */
    protected boolean onV2Horizontal(View focused, int dx) {
        return false;
    }

    /** The focus moved to a grid item (scroll it into view, update captions...) */
    protected void onGridFocus(View target) {
    }

    /** Items with options (A held = options, A released early = activate): those with a long press */
    protected boolean isHoldable(View v) {
        return v.isLongClickable();
    }

    /** Horizontal centre of a view on screen (FocusGrid: a row change goes to the closest), NaN before layout */
    static float screenCenterX(View v) {
        if (v == null || v.getWidth() == 0 || !v.isAttachedToWindow()) {
            return Float.NaN;
        }
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        return at[0] + v.getWidth() / 2f;
    }

    /** The grid item that holds the focused view (the focus may sit on a child of an item) */
    static View focusedItem(List<List<View>> rows, View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (FocusGrid.find(rows, v) != null) {
                return v;
            }
        }
        return null;
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
