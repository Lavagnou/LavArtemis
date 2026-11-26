package com.limelight.v2;

/**
 * Repetition of a held direction (D-pad, stick, arrow key) on the v2 screens, as the prototype's
 * pollPads(): the press moves at once, then the move repeats 380 ms later and every 110 ms after
 * that while the direction stays held. The system's own key repeat is ignored (its delays differ
 * from one device to the next). One direction at a time: a new one replaces the held one. Pure
 * logic on a clock given by the caller, tested by DirRepeatTest.
 */
public final class DirRepeat {
    public static final long FIRST_MS = 380;
    public static final long NEXT_MS = 110;

    private int held;
    private long nextAt;

    /** A direction goes down: true when it moves now (false: it was already held, a system repeat) */
    public boolean press(int code, long now) {
        if (held == code) {
            return false;
        }
        held = code;
        nextAt = now + FIRST_MS;
        return true;
    }

    /** A direction comes up; the repetition stops if it was the held one */
    public void release(int code) {
        if (held == code) {
            held = 0;
        }
    }

    /** Stop everything (the window lost the focus, the activity paused) */
    public void cancel() {
        held = 0;
    }

    /** The held direction, 0 when none */
    public int held() {
        return held;
    }

    /** True when the held direction moves again at {@code now}; the next repetition is then planned */
    public boolean due(long now) {
        if (held == 0 || now < nextAt) {
            return false;
        }
        nextAt = now + NEXT_MS;
        return true;
    }

    /** Milliseconds until the next repetition (for a timer), -1 when nothing is held */
    public long delay(long now) {
        return held == 0 ? -1 : Math.max(0, nextAt - now);
    }
}
