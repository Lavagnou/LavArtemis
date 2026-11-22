package com.limelight.binding.input.touch;

import org.junit.Test;

import static com.limelight.binding.input.touch.PinchArbiter.Decision.OTHER;
import static com.limelight.binding.input.touch.PinchArbiter.Decision.PINCH;
import static com.limelight.binding.input.touch.PinchArbiter.Decision.UNDECIDED;
import static org.junit.Assert.assertEquals;

public class PinchArbiterTest {
    @Test
    public void spreadingFingersIsPinch() {
        PinchArbiter a = new PinchArbiter(24);
        a.start(400, 500, 600, 500);
        assertEquals(UNDECIDED, a.update(390, 500, 610, 500));
        assertEquals(PINCH, a.update(385, 500, 615, 500));
    }

    @Test
    public void movingTogetherIsOther() {
        PinchArbiter a = new PinchArbiter(24);
        a.start(400, 500, 600, 500);
        assertEquals(OTHER, a.update(400, 530, 600, 530));
    }

    @Test
    public void decisionIsSticky() {
        PinchArbiter a = new PinchArbiter(24);
        a.start(400, 500, 600, 500);
        a.update(400, 530, 600, 530);
        assertEquals(OTHER, a.update(300, 530, 700, 530));
    }

    @Test
    public void startResets() {
        PinchArbiter a = new PinchArbiter(24);
        a.start(400, 500, 600, 500);
        a.update(400, 530, 600, 530);
        a.start(400, 500, 600, 500);
        assertEquals(UNDECIDED, a.update(401, 500, 601, 500));
    }
}
