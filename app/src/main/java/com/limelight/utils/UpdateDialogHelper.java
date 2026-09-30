package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.limelight.LimeLog;
import com.limelight.R;

import java.io.File;

/**
 * Shared UI flow for the in-app updater: shows the "update available" dialog,
 * runs the download with a progress dialog, then hands the APK to the system
 * package installer.
 */
public class UpdateDialogHelper {

    private static final String NOTIFICATION_CHANNEL_ID = "app_updates";
    private static final int UPDATE_READY_NOTIFICATION_ID = 0x1a7e;

    /**
     * Shows the update dialog for the given release. If the APK asset was found,
     * the positive button downloads and installs it in-app; otherwise it opens the
     * release page in the browser.
     */
    public static void showUpdateDialog(Activity activity, UpdateChecker.UpdateInfo info) {
        if (!isAlive(activity)) {
            return;
        }

        String message = activity.getString(R.string.update_available_message,
                info.version, com.limelight.BuildConfig.VERSION_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setNegativeButton(R.string.update_later, null)
                // Without this, "Later" was the only answer and the same dialog came back on
                // every launch until the update was installed.
                .setNeutralButton(R.string.update_skip,
                        (dialog, which) -> UpdateChecker.skipVersion(activity, info.version));

        if (info.downloadUrl != null) {
            builder.setPositiveButton(R.string.update_download,
                    (dialog, which) -> startDownload(activity, info));
        } else {
            // No APK for this device's ABI: fall back to the release page
            builder.setPositiveButton(R.string.update_download,
                    (dialog, which) -> HelpLauncher.launchUrl(activity, info.pageUrl));
        }

        builder.show();
    }

    /**
     * Offers to install an update that was downloaded but never installed; runs
     * {@code otherwise} when there is none (or it is skipped). This is the path that does not
     * depend on notifications, which Android 13+ suppresses without POST_NOTIFICATIONS.
     */
    public static void offerDownloadedUpdateOr(Activity activity, Runnable otherwise) {
        final Context appContext = activity.getApplicationContext();
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Thread thread = new Thread(() -> {
            UpdateDownloader.DownloadedUpdate update = UpdateDownloader.findDownloadedUpdate(appContext);
            mainHandler.post(() -> {
                if (update == null || UpdateChecker.isSkipped(appContext, update.version)) {
                    otherwise.run();
                    return;
                }
                if (!isAlive(activity)) {
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.update_ready_title)
                        .setMessage(activity.getString(R.string.update_ready_message, update.version))
                        .setNegativeButton(R.string.update_later, null)
                        .setNeutralButton(R.string.update_skip,
                                (dialog, which) -> UpdateChecker.skipVersion(activity, update.version))
                        .setPositiveButton(R.string.update_install,
                                (dialog, which) -> UpdateDownloader.installApk(activity, update.apk))
                        .show();
            });
        }, "UpdatePending");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isAlive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void dismissQuietly(Activity activity, ProgressDialog dialog) {
        // Dismissing a dialog whose window is gone throws; the activity may have been destroyed
        // (rotation, back, TV box reclaiming memory) during a download that outlives it.
        if (isAlive(activity) && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void startDownload(Activity activity, UpdateChecker.UpdateInfo info) {
        if (!UpdateDownloader.canInstallPackages(activity)) {
            Toast.makeText(activity, R.string.update_install_permission, Toast.LENGTH_LONG).show();
            UpdateDownloader.openInstallPermissionSettings(activity);
            return;
        }

        final Context appContext = activity.getApplicationContext();

        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_available_title);
        progressDialog.setMessage(activity.getString(R.string.update_downloading, 0));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        UpdateDownloader.download(appContext, info.downloadUrl, info.version,
                new UpdateDownloader.Callback() {
                    @Override
                    public void onProgress(int percent) {
                        if (isAlive(activity) && progressDialog.isShowing()) {
                            progressDialog.setProgress(percent);
                            progressDialog.setMessage(
                                    activity.getString(R.string.update_downloading, percent));
                        }
                    }

                    @Override
                    public void onDownloaded(File apkFile) {
                        dismissQuietly(activity, progressDialog);
                        if (isAlive(activity)) {
                            UpdateDownloader.installApk(activity, apkFile);
                        } else {
                            // The screen that started the download is gone. Don't throw the
                            // download away: let the user install it from a notification.
                            notifyUpdateReady(appContext, apkFile, info.version);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        dismissQuietly(activity, progressDialog);
                        if (!isAlive(activity)) {
                            return;
                        }
                        if (e instanceof UpdateDownloader.VerificationException) {
                            Toast.makeText(activity, activity.getString(R.string.update_verification_failed,
                                    e.getMessage()), Toast.LENGTH_LONG).show();
                            HelpLauncher.launchUrl(activity, info.pageUrl);
                        } else {
                            Toast.makeText(activity, R.string.update_download_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Best effort: on Android 13+ without POST_NOTIFICATIONS this is silently dropped, and the
    // next app launch offers the download instead (offerDownloadedUpdateOr).
    private static void notifyUpdateReady(Context context, File apkFile, String version) {
        try {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, installIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_icon)
                    .setContentTitle(context.getString(R.string.update_ready_title))
                    .setContentText(context.getString(R.string.update_ready_text, version))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            notificationManager.notify(UPDATE_READY_NOTIFICATION_ID, builder.build());
        } catch (RuntimeException e) {
            // No provider path, or the system refused: the APK stays downloaded and
            // offerDownloadedUpdateOr() offers it the next time the app opens.
            LimeLog.warning("Could not post the update-ready notification: " + e);
        }
    }
}
