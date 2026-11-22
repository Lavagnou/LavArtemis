package com.limelight.binding.input.touch;

/**
 * Where a trackpad touch context sends its cursor moves. Without one the contexts send relative
 * moves to the host as before; the trackpad pinch-zoom installs one to own the cursor position.
 */
public interface MouseMover {
    void move(short dx, short dy);
}
