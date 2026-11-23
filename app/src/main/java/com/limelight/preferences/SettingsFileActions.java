package com.limelight.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.limelight.BuildConfig;
import com.limelight.GameMenu;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.PerformanceDataTracker;
import com.limelight.utils.UpdateChecker;
import com.limelight.utils.UpdateDialogHelper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * The settings that act on files or launch something (import / export of the on-screen keys,
 * performance logs, update check), shared by the legacy settings screen (StreamSettings) and
 * Réglages v2 (v2.SettingsActivity) so both behave alike.
 */
public final class SettingsFileActions {
    private SettingsFileActions() {}

    /** The document picker the imports open */
    public static Intent openJsonIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        return intent;
    }

    /** Name of the SharedPreferences file of the chosen on-screen key layout */
    private static String keyboardLayoutName(SharedPreferences prefs) {
        return prefs.getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
    }

    /** Replaces the chosen on-screen key layout with a JSON file */
    public static void importKeyboardLayout(Activity activity, Uri uri, SharedPreferences prefs) {
        try {
            String json = FileUriUtils.openUriForRead(activity, uri);
            if (TextUtils.isEmpty(json)) {
                Toast.makeText(activity, activity.getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor prefEditor = activity.getSharedPreferences(keyboardLayoutName(prefs), Activity.MODE_PRIVATE).edit();
            JSONObject object = new JSONObject(json);
            Iterator<String> it = object.keys();
            prefEditor.clear();
            while (it.hasNext()) {
                String key = it.next();
                prefEditor.putString(key, object.getString(key));
            }
            prefEditor.apply();
            Toast.makeText(activity, activity.getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, activity.getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Replaces the custom special keys (in-game menu and on-screen keys) with a JSON file */
    public static void importSpecialButtons(Activity activity, Uri uri) {
        try {
            String json = FileUriUtils.openUriForRead(activity, uri);
            if (TextUtils.isEmpty(json)) {
                Toast.makeText(activity, activity.getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                return;
            }
            SharedPreferences.Editor prefEditor = activity.getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit();
            prefEditor.putString(GameMenu.KEY_NAME, json);
            prefEditor.apply();
            Toast.makeText(activity, activity.getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, activity.getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Shares the chosen on-screen key layout as a JSON file */
    public static void exportKeyboardLayout(Activity activity, SharedPreferences prefs) {
        File dir = new File(activity.getExternalCacheDir(), "export_settings");
        if (!dir.exists()) {
            dir.mkdir();
        }
        String name = keyboardLayoutName(prefs);
        Map<String, ?> map = activity.getSharedPreferences(name, Activity.MODE_PRIVATE).getAll();
        File file = new File(dir, name + ".json");
        if (!FileUriUtils.writerFileString(file, new Gson().toJson(map))) {
            Toast.makeText(activity, activity.getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType("application/json");
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.pref_save_keyboard_profile)));
    }

    /** Shares the recorded performance data as a text file */
    public static void sharePerformanceLogs(Context context) {
        String logs = new PerformanceDataTracker().getLog(context);
        if (logs == null || logs.trim().isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_no_logs), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File logFile = new File(context.getCacheDir(), "lavartemis_performance_log.txt");
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                fos.write(logs.getBytes(StandardCharsets.UTF_8));
            }

            Uri logFileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", logFile);

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("text/plain");
            // No preset recipient: the user decides who gets their log. It used to be
            // addressed to the upstream maintainer's collection mailbox.
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject));
            emailIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.email_prefix_message));
            emailIntent.putExtra(Intent.EXTRA_STREAM, logFileUri);
            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.email_chooser_title)));
        } catch (IOException e) {
            Log.d("PerformanceDataTracker", "Error creating log file");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(context, context.getString(R.string.toast_no_email_clients), Toast.LENGTH_SHORT).show();
        }
    }

    /** The manual update check: a toast, then the update dialog or "up to date" */
    public static void checkForUpdates(final Activity activity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        UpdateChecker.check(activity, new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                UpdateDialogHelper.showUpdateDialog(activity, info);
            }

            @Override
            public void onUpToDate() {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    Toast.makeText(activity, activity.getString(R.string.update_none,
                            BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception e) {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
