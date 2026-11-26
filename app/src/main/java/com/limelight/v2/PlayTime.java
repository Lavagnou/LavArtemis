package com.limelight.v2;

/**
 * Time played of a Playnite game (PlaynitePlaytime, seconds), as the v2 screens write it. User
 * decision of 2026-11-26: under an hour the minutes show (« 25 min jouées ») instead of rounding up
 * to « 1 h jouées »; from an hour on, the rounded hours (« 450 h jouées »). Never played: « Jamais
 * lancé » (long form) or « — » (short form of the Affiche list). Pure logic, tested by PlayTimeTest.
 */
public final class PlayTime {
    private PlayTime() {}

    public enum Unit { NEVER, MINUTES, HOURS }

    public static Unit unit(long seconds) {
        if (seconds <= 0) {
            return Unit.NEVER;
        }
        return seconds < 3600 ? Unit.MINUTES : Unit.HOURS;
    }

    /** The number shown with the unit: minutes (at least 1) under an hour, rounded hours above */
    public static int amount(long seconds) {
        switch (unit(seconds)) {
            case MINUTES:
                return (int) Math.max(1, seconds / 60);
            case HOURS:
                return (int) Math.max(1, Math.round(seconds / 3600.0));
            default:
                return 0;
        }
    }
}
