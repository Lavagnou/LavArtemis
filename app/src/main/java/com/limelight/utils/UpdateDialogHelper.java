package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.widget.Toast;

import com.limelight.R;

import java.io.File;

/**
 * Shared UI flow for the in-app updater: shows the "update available" dialog,
 * runs the download with a progress dialog, then hands the APK to the system
 * package installer.
 */
public class UpdateDialogHelper {

    /**
     * Shows the update dialog for the given release. If the APK asset was found,
     * the positive button downloads and installs it in-app; otherwise it opens the
     * release page in the browser.
     */
    public static void showUpdateDialog(Activity activity, UpdateChecker.UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        String message = activity.getString(R.string.update_available_message,
                info.version, com.limelight.BuildConfig.VERSION_NAME);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setNegativeButton(R.string.update_later, null);

        if (info.downloadUrl != null) {
            builder.setPositiveButton(R.string.update_download,
                    (dialog, which) -> startDownload(activity, info));
        } else {
            // No direct APK asset: fall back to the release page
            builder.setPositiveButton(R.string.update_download,
                    (dialog, which) -> HelpLauncher.launchUrl(activity, info.pageUrl));
        }

        builder.show();
    }

    private static void startDownload(Activity activity, UpdateChecker.UpdateInfo info) {
        if (!UpdateDownloader.canInstallPackages(activity)) {
            Toast.makeText(activity, R.string.update_install_permission, Toast.LENGTH_LONG).show();
            UpdateDownloader.openInstallPermissionSettings(activity);
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle(R.string.update_available_title);
        progressDialog.setMessage(activity.getString(R.string.update_downloading, 0));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        UpdateDownloader.download(activity, info.downloadUrl, info.version,
                new UpdateDownloader.Callback() {
                    @Override
                    public void onProgress(int percent) {
                        progressDialog.setProgress(percent);
                        progressDialog.setMessage(
                                activity.getString(R.string.update_downloading, percent));
                    }

                    @Override
                    public void onDownloaded(File apkFile) {
                        progressDialog.dismiss();
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            UpdateDownloader.installApk(activity, apkFile);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        progressDialog.dismiss();
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Toast.makeText(activity, R.string.update_download_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}
