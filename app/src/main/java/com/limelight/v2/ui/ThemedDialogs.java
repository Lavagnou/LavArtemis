package com.limelight.v2.ui;

import android.app.Dialog;
import android.content.Context;

import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.v2.V2Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The bridge between the legacy screens' dialogs and the v2 theme: LavArtemis Dev shows the
 * themed dialog, the stable keeps its AlertDialog. Callers keep their own AlertDialog branch and
 * ask {@link #enabled()} first, so the stable's code path is untouched.
 */
public final class ThemedDialogs {
    private ThemedDialogs() {}

    /** The v2 dialogs: LavArtemis Dev only */
    public static boolean enabled() {
        return BuildConfig.LAV_DEV;
    }

    public static V2Theme theme(Context context) {
        return V2Theme.load(context);
    }

    /** A message with one button; Back / outside close it too, and run {@code onClose} as well */
    public static Dialog message(Context context, String title, CharSequence text, Runnable onClose) {
        return new V2Alert(context, theme(context)).title(title).text(text)
                .button(context.getString(R.string.v2_ok), onClose).onCancel(onClose).show().dialog;
    }

    /**
     * Two buttons, as AlertDialog's positive / negative: {@code onYes} or {@code onNo} run only
     * from their button; Back / outside close without either (as an AlertDialog does).
     */
    public static Dialog confirm(Context context, String title, CharSequence text, String yes, String no,
                                 Runnable onYes, Runnable onNo) {
        return new V2Alert(context, theme(context)).title(title).text(text)
                .button(yes, onYes).button(no, onNo).show().dialog;
    }

    /** A list of choices (AlertDialog.setItems): the index picked, nothing on cancel */
    public static Dialog list(Context context, String title, CharSequence[] items, V2ListDialog.Pick onPick) {
        List<String> labels = new ArrayList<>();
        for (CharSequence s : items) {
            labels.add(s.toString());
        }
        labels.add(context.getString(R.string.v2_cancel));
        return V2ListDialog.show(context, theme(context), title, labels, labels.size() - 1, onPick, null);
    }

    /** {@link #list} with the legacy's own last « Cancel » entry turned into the button */
    public static Dialog listWithCancel(Context context, String title, CharSequence[] items, int cancelIndex,
                                        V2ListDialog.Pick onPick, Runnable onCancel) {
        List<String> labels = new ArrayList<>(Arrays.asList(toStrings(items)));
        return V2ListDialog.show(context, theme(context), title, labels, cancelIndex, onPick, onCancel);
    }

    private static String[] toStrings(CharSequence[] items) {
        String[] out = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i].toString();
        }
        return out;
    }
}
