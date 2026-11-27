package com.limelight.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Repetition of a held direction (D-pad, stick, arrow key) on the v2 screens and dialogs, as the
 * prototype's pollPads(): the press moves at once, then the move repeats 380 ms later and every
 * 110 ms after that while the direction stays held. The system's own key repeat is ignored (its
 * delays differ from one device to the next).
 * <p>
 * Several directions may be held at once (a diagonal, or a second key pressed before the first is
 * released). One of them moves: <b>the vertical one first</b> (the desktop's rule for a diagonal),
 * else the last one pressed. A horizontal press while a vertical direction is held does not move;
 * releasing the moving direction hands the repetition to one still held, which repeats after the
 * first delay (380 ms) without moving at once. Pure logic on a clock given by the caller, tested
 * by DirRepeatTest.
 */
public final class DirRepeat {
    public static final long FIRST_MS = 380;
    public static final long NEXT_MS = 110;

    /** Key codes of the D-pad (android.view.KeyEvent, kept here so the logic stays plain Java) */
    static final int UP = 19, DOWN = 20, LEFT = 21, RIGHT = 22;

    /** Held directions, in the order they were pressed */
    private final List<Integer> held = new ArrayList<>();
    private int active;
    private long nextAt;

    static boolean isVertical(int code) {
        return code == UP || code == DOWN;
    }

    /** The direction that moves: the last vertical one held, else the last one held */
    private int pick() {
        for (int i = held.size() - 1; i >= 0; i--) {
            if (isVertical(held.get(i))) {
                return held.get(i);
            }
        }
        return held.isEmpty() ? 0 : held.get(held.size() - 1);
    }

    /**
     * A direction goes down: true when it moves now (false: it was already held, a system repeat,
     * or it is horizontal while a vertical direction is held)
     */
    public boolean press(int code, long now) {
        if (held.contains(code)) {
            return false;
        }
        held.add(code);
        int next = pick();
        if (next != code) {
            return false;
        }
        active = code;
        nextAt = now + FIRST_MS;
        return true;
    }

    /** A direction comes up; the repetition goes to a direction still held, after the first delay */
    public void release(int code, long now) {
        held.remove(Integer.valueOf(code));
        int next = pick();
        if (next != active) {
            active = next;
            nextAt = now + FIRST_MS;
        }
    }

    /** Stop everything (the window lost the focus, the activity paused) */
    public void cancel() {
        held.clear();
        active = 0;
    }

    /** The direction that moves, 0 when none */
    public int held() {
        return active;
    }

    /** True when the held direction moves again at {@code now}; the next repetition is then planned */
    public boolean due(long now) {
        if (active == 0 || now < nextAt) {
            return false;
        }
        nextAt = now + NEXT_MS;
        return true;
    }

    /** Milliseconds until the next repetition (for a timer), -1 when nothing is held */
    public long delay(long now) {
        return active == 0 ? -1 : Math.max(0, nextAt - now);
    }
}
