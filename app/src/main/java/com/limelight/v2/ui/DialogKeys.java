package com.limelight.v2.ui;

import android.app.Dialog;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;

import com.limelight.v2.V2Input;

import java.util.HashSet;
import java.util.Set;

/**
 * Gamepad keys of a v2 dialog: A clicks the focused row (Android has no default for it), B, Start
 * and Y close (Start is Retour, as in the screens). A key acts on its release only when it was also
 * pressed in this dialog: the release of the key that opened it (A held, a controller combo, Back)
 * would otherwise click the first entry or close the dialog at once. Held directions repeat as in
 * the screens (380 / 110 ms, vertical first on a diagonal: {@link DirKeys}), not at the device's
 * own rhythm.
 */
public final class DialogKeys implements android.content.DialogInterface.OnKeyListener {
    private final Dialog dialog;
    private final Runnable onClose;
    private final Set<Integer> down = new HashSet<>();
    private final DirKeys dirKeys;
    private boolean watching;

    /** @param onClose what B / Start / Y do (null: cancel the dialog) */
    public DialogKeys(Dialog dialog, Runnable onClose) {
        this.dialog = dialog;
        this.onClose = onClose;
        this.dirKeys = new DirKeys(new DirKeys.Target() {
            @Override
            public boolean dispatch(KeyEvent event) {
                return dialog.isShowing() && dialog.dispatchKeyEvent(event);
            }

            @Override
            public View focused() {
                return dialog.getCurrentFocus();
            }
        });
    }

    public static void install(Dialog dialog) {
        dialog.setOnKeyListener(new DialogKeys(dialog, null));
    }

    /** The repetition stops when the dialog loses the keys (closed, or another one on top) */
    private void watchFocus() {
        Window w = dialog.getWindow();
        if (watching || w == null) {
            return;
        }
        watching = true;
        w.getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener() {
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                if (!hasFocus) {
                    dirKeys.cancel();
                }
            }
        });
    }

    @Override
    public boolean onKey(android.content.DialogInterface d, int keyCode, KeyEvent event) {
        V2Input.onKey(event);
        if (DirKeys.isDirection(keyCode)) {
            watchFocus();
            return dirKeys.consume(event);
        }
        boolean a = keyCode == KeyEvent.KEYCODE_BUTTON_A;
        boolean close = keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_Y
                || keyCode == KeyEvent.KEYCODE_BUTTON_START;
        if (!a && !close) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                down.add(keyCode);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && down.remove(keyCode) && !event.isCanceled()) {
            if (a) {
                View f = dialog.getCurrentFocus();
                if (f != null && f.isClickable()) {
                    f.performClick();
                }
            } else if (onClose != null) {
                onClose.run();
            } else {
                dialog.cancel();
            }
        }
        return true;
    }
}
