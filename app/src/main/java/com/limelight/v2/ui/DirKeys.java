package com.limelight.v2.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;

import com.limelight.v2.DirRepeat;

/**
 * The D-pad, stick and arrow keys of a v2 window (a screen or a dialog): the 380 / 110 ms
 * repetition of {@link DirRepeat} instead of the system's, the vertical direction first on a
 * diagonal, and the repetition handed back to a direction still held when another is released.
 * <p>
 * The stick reaches the window as D-pad keys made by the system (flagged FLAG_FALLBACK), the
 * horizontal one first: a horizontal one is held back until the input batch is over, and dropped
 * when a vertical one comes in the same batch (a diagonal moves vertically only, as on the desktop).
 * <p>
 * The window calls {@link #consume} first for every direction key: true means the key is eaten (a
 * system repeat, a direction that does not move); false lets the window move as for a fresh press.
 * Repeats and held-back moves come back through {@link Target#dispatch}; when nothing handles them
 * the focus moves as Android's own focus search would after a real key.
 */
public final class DirKeys {
    public interface Target {
        /** The key through the window's normal path; true when something handled it */
        boolean dispatch(KeyEvent event);

        /** The focused view of the window, or null */
        View focused();
    }

    private final Target target;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DirRepeat repeat = new DirRepeat();
    private final SparseArray<KeyEvent> downs = new SparseArray<>();
    /** A key of ours is going through the window: let it pass */
    private boolean replaying;
    /** A horizontal stick key held back until the input batch is over */
    private KeyEvent deferred;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            int code = repeat.held();
            KeyEvent down = downs.get(code);
            if (code == 0 || down == null) {
                return;
            }
            if (repeat.due(now)) {
                replay(KeyEvent.changeTimeRepeat(down, now, 0));
            }
            schedule(now);
        }
    };

    private final Runnable runDeferred = this::flushDeferred;

    public DirKeys(Target target) {
        this.target = target;
    }

    public static boolean isDirection(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean vertical(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** True when the window must eat this direction key (see the class comment) */
    public boolean consume(KeyEvent event) {
        int code = event.getKeyCode();
        if (replaying || !isDirection(code)) {
            return false;
        }
        long now = SystemClock.uptimeMillis();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                // The system's repeat: ours is the prototype's 380 / 110 ms
                return true;
            }
            if (vertical(code) && deferred != null) {
                // The same stick push went diagonal: the vertical direction only
                handler.removeCallbacks(runDeferred);
                deferred = null;
            }
            boolean moves = repeat.press(code, now);
            downs.put(code, event);
            schedule(now);
            if (moves && !vertical(code) && (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0) {
                deferred = event;
                handler.removeCallbacks(runDeferred);
                handler.post(runDeferred);
                return true;
            }
            return !moves;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (deferred != null && deferred.getKeyCode() == code) {
                flushDeferred();
            }
            repeat.release(code, now);
            downs.remove(code);
            schedule(now);
        }
        return false;
    }

    private void flushDeferred() {
        handler.removeCallbacks(runDeferred);
        KeyEvent e = deferred;
        deferred = null;
        if (e != null && repeat.held() == e.getKeyCode()) {
            replay(e);
        }
    }

    private void schedule(long now) {
        handler.removeCallbacks(tick);
        long d = repeat.delay(now);
        if (d >= 0) {
            handler.postDelayed(tick, d);
        }
    }

    private void replay(KeyEvent e) {
        boolean handled;
        replaying = true;
        try {
            handled = target.dispatch(e);
        } finally {
            replaying = false;
        }
        if (!handled) {
            // Android's own focus search runs after a real key only: do it for ours
            View f = target.focused();
            int dir = focusDirection(e.getKeyCode());
            View next = f == null ? null : f.focusSearch(dir);
            if (next != null && next != f) {
                next.requestFocus(dir);
            }
        }
    }

    static int focusDirection(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_LEFT;
            default:
                return View.FOCUS_RIGHT;
        }
    }

    /** Stop everything (the window lost the focus, the screen paused, the dialog closed) */
    public void cancel() {
        repeat.cancel();
        downs.clear();
        deferred = null;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(runDeferred);
    }
}
