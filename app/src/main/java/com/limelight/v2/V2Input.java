package com.limelight.v2;

import android.content.Context;
import android.content.res.Configuration;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The input that last drove the v2 interface: touch, gamepad or keyboard (a TV remote's D-pad
 * counts as keyboard). Gamepad and keyboard hints, and the focus ring, are shown only for pad and
 * keys, as on the desktop (Theme.input / Theme.showFocus). Process-wide: the next screen keeps it.
 */
public final class V2Input {
    public enum Kind { TOUCH, PAD, KEYS }

    public interface Listener {
        void onInputKindChanged(Kind kind);
    }

    private static Kind kind = null;
    private static final List<Listener> listeners = new ArrayList<>();

    private V2Input() {}

    /** First call: a TV or a device with a gamepad attached starts in pad mode, others in touch. */
    public static Kind get(Context context) {
        if (kind == null) {
            kind = initialKind(context);
        }
        return kind;
    }

    public static boolean showFocus(Context context) {
        Kind k = get(context);
        return k == Kind.PAD || k == Kind.KEYS;
    }

    private static Kind initialKind(Context context) {
        int type = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        boolean tv = type == Configuration.UI_MODE_TYPE_TELEVISION;
        boolean touch = context.getPackageManager().hasSystemFeature("android.hardware.touchscreen");
        if (hasGamepad()) {
            return Kind.PAD;
        }
        return tv || !touch ? Kind.KEYS : Kind.TOUCH;
    }

    private static boolean hasGamepad() {
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice dev = InputDevice.getDevice(id);
                if (dev != null && !dev.isVirtual()
                        && ((dev.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                            || (dev.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    public static void set(Kind newKind) {
        if (newKind == kind) {
            return;
        }
        kind = newKind;
        for (Listener l : new ArrayList<>(listeners)) {
            l.onInputKindChanged(newKind);
        }
    }

    /** For tests and previews */
    public static void reset(Kind initial) {
        kind = initial;
    }

    public static void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** What a key event says about the device that sent it. */
    public static Kind kindOf(KeyEvent event) {
        int source = event.getSource();
        if (KeyEvent.isGamepadButton(event.getKeyCode())
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            return Kind.PAD;
        }
        return Kind.KEYS;
    }

    public static void onKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            set(kindOf(event));
        }
    }

    public static void onTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            set(Kind.TOUCH);
        }
    }

    /** Stick movements from a gamepad */
    public static void onGenericMotion(MotionEvent event) {
        int source = event.getSource();
        if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            set(Kind.PAD);
        }
    }
}
