package com.limelight.v2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** « 25 min jouées » under an hour, « N h jouées » above (decision of 2026-11-26). */
public class PlayTimeTest {
    @Test
    public void neverPlayed() {
        assertEquals(PlayTime.Unit.NEVER, PlayTime.unit(0));
        assertEquals(PlayTime.Unit.NEVER, PlayTime.unit(-5));
        assertEquals(0, PlayTime.amount(0));
    }

    @Test
    public void minutesUnderAnHour() {
        assertEquals(PlayTime.Unit.MINUTES, PlayTime.unit(25 * 60));
        assertEquals(25, PlayTime.amount(25 * 60 + 40));
        // A few seconds still count as a minute played
        assertEquals(1, PlayTime.amount(10));
        assertEquals(59, PlayTime.amount(3599));
    }

    @Test
    public void hoursFromAnHour() {
        assertEquals(PlayTime.Unit.HOURS, PlayTime.unit(3600));
        assertEquals(1, PlayTime.amount(3600));
        assertEquals(1, PlayTime.amount(5399));
        assertEquals(2, PlayTime.amount(5400));
        assertEquals(450, PlayTime.amount(450 * 3600L));
    }
}
