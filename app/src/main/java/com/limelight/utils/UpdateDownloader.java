package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.limelight.LimeLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads an update APK from a GitHub release asset and hands it over to the
 * system package installer.
 */
public class UpdateDownloader {

    public interface Callback {
        /** Called on the main thread with a 0-100 progress value. */
        void onProgress(int percent);
        /** Called on the main thread once the APK is fully downloaded. */
        void onDownloaded(File apkFile);
        /** Called on the main thread if the download fails. */
        void onError(Exception e);
    }

    public static void download(Context context, String url, String version, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        File outputDir = context.getExternalFilesDir("updates");
        File outputFile = new File(outputDir, "LavArtemis-" + version + ".apk");

        Thread thread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "LavArtemis-Android")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Download failed with HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty download response");
                }

                long totalBytes = body.contentLength();
                if (!outputDir.exists() && !outputDir.mkdirs()) {
                    throw new IOException("Cannot create updates directory");
                }

                try (InputStream in = body.byteStream();
                     OutputStream out = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[64 * 1024];
                    long downloaded = 0;
                    int lastPercent = -1;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        if (totalBytes > 0) {
                            int percent = (int) (downloaded * 100 / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                mainHandler.post(() -> callback.onProgress(p));
                            }
                        }
                    }
                    out.flush();
                }

                mainHandler.post(() -> callback.onDownloaded(outputFile));
            } catch (Exception e) {
                LimeLog.warning("Update download failed: " + e.getMessage());
                // Clean up a partial download
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
                mainHandler.post(() -> callback.onError(e));
            }
        }, "UpdateDownloader");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Returns true if this app is allowed to install APKs. On API 26+ the user must
     * grant the "install unknown apps" permission per-app.
     */
    public static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Opens the system settings page where the user can grant the "install unknown
     * apps" permission for this app. Only meaningful on API 26+.
     */
    public static void openInstallPermissionSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }

    /**
     * Launches the system package installer for the given APK file. The caller must
     * have checked canInstallPackages() first on API 26+.
     */
    public static void installApk(Context context, File apkFile) {
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
