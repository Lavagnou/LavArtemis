package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;

import com.limelight.R;
import com.limelight.v2.ui.ThemedDialogs;
import com.limelight.v2.ui.V2Alert;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private android.app.Dialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert.isShowing()) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        if (ThemedDialogs.enabled()) {
            showThemed();
            return;
        }

        AlertDialog alert = new AlertDialog.Builder(activity).create();
        this.alert = alert;

        alert.setTitle(title);
        alert.setMessage(message);
        alert.setCancelable(false);
        alert.setCanceledOnTouchOutside(false);
 
        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getText(android.R.string.ok), new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                  synchronized (rundownDialogs) {
                      rundownDialogs.remove(Dialog.this);
                      alert.dismiss();
                  }

                  runOnDismiss.run();
              }
        });
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getResources().getText(R.string.help), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                synchronized (rundownDialogs) {
                    rundownDialogs.remove(Dialog.this);
                    alert.dismiss();
                }

                runOnDismiss.run();

                HelpLauncher.launchTroubleshooting(activity);
            }
        });
        alert.setOnShowListener(new DialogInterface.OnShowListener(){

            @Override
            public void onShow(DialogInterface dialog) {
                // Set focus to the OK button by default
                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setFocusable(true);
                button.setFocusableInTouchMode(true);
                button.requestFocus();
            }
        });

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }


    /**
     * LavArtemis Dev: the same dialog in the v2 theme. Same buttons (OK, Aide), same rules: not
     * cancelable, both buttons run {@code runOnDismiss}, closeDialogs() still closes it.
     */
    private void showThemed() {
        Runnable dismissed = () -> {
            synchronized (rundownDialogs) {
                rundownDialogs.remove(Dialog.this);
            }
            runOnDismiss.run();
        };
        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert = new V2Alert(activity, ThemedDialogs.theme(activity))
                    .title(title)
                    .text(message)
                    .button(activity.getString(R.string.v2_ok), dismissed)
                    .button(activity.getString(R.string.help), () -> {
                        dismissed.run();
                        HelpLauncher.launchTroubleshooting(activity);
                    })
                    .cancelable(false)
                    .show().dialog;
        }
    }
}
