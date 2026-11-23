package com.limelight.v2.ui;

import android.app.Dialog;
import android.view.KeyEvent;
import android.view.View;

import com.limelight.v2.V2Input;

import java.util.HashSet;
import java.util.Set;

/**
 * Gamepad keys of a v2 dialog: A clicks the focused row (Android has no default for it), B and Y
 * close. A key acts on its release only when it was also pressed in this dialog: the release of
 * the key that opened it (A held, a controller combo, Back) would otherwise click the first entry
 * or close the dialog at once.
 */
public final class DialogKeys implements android.content.DialogInterface.OnKeyListener {
    private final Dialog dialog;
    private final Runnable onClose;
    private final Set<Integer> down = new HashSet<>();

    /** @param onClose what B / Y do (null: cancel the dialog) */
    public DialogKeys(Dialog dialog, Runnable onClose) {
        this.dialog = dialog;
        this.onClose = onClose;
    }

    public static void install(Dialog dialog) {
        dialog.setOnKeyListener(new DialogKeys(dialog, null));
    }

    @Override
    public boolean onKey(android.content.DialogInterface d, int keyCode, KeyEvent event) {
        V2Input.onKey(event);
        boolean a = keyCode == KeyEvent.KEYCODE_BUTTON_A;
        boolean close = keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_Y;
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
