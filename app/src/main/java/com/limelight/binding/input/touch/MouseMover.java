package com.limelight.binding.input.touch;

/**
 * Where a trackpad touch context sends its cursor moves. Without one the contexts send relative
 * moves to the host as before; the trackpad pinch-zoom installs one to own the cursor position.
 */
// dx/dy are the finger's movement in screen pixels -- Game skips the zoomed-view normalization for
// trackpads while the pinch-zoom owns the cursor (TouchSpace) -- times the mode's sensitivity, before any acceleration
// or reference-resolution scaling: the mover decides how far the cursor goes.
public interface MouseMover {
    void move(short dx, short dy);
}
