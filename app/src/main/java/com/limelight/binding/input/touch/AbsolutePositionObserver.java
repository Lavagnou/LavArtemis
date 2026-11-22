package com.limelight.binding.input.touch;

/** Told where an absolute touch put the host cursor, as fractions of the stream. */
public interface AbsolutePositionObserver {
    void onAbsolutePosition(float fx, float fy);
}
