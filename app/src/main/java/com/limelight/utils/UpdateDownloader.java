package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
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

    /**
     * The downloaded file is not an update of this app: wrong package, not newer, or signed
     * with a different key. The file has been deleted.
     */
    public static class VerificationException extends IOException {
        VerificationException(String message) {
            super(message);
        }
    }

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

                verifyApk(context, outputFile);

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
     * Checks that {@code apk} is a newer build of this very app, signed with the same key,
     * before the system installer is ever shown.
     *
     * The package installer enforces the signature too, but only after the user has confirmed,
     * and with an error that does not say why. More importantly, a build signed with the
     * shared Android debug key (CI builds without the release keystore fall back to it) can be
     * "updated" by any APK signed with that same public key; comparing against what is
     * installed keeps the updater from ever offering such a file.
     */
    @SuppressWarnings("deprecation")
    static void verifyApk(Context context, File apk) throws VerificationException {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null) {
            throw new VerificationException("the download is not a valid APK");
        }
        if (!packageName.equals(archive.packageName)) {
            throw new VerificationException("the APK is for " + archive.packageName + ", not " + packageName);
        }

        PackageInfo installed;
        try {
            installed = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new VerificationException("cannot read the installed package");
        }

        if (versionCode(archive) <= versionCode(installed)) {
            throw new VerificationException("the APK is not newer than the installed version");
        }

        // A key rotation keeps the old signer in the new APK's history, so the installed
        // signer must appear among the new APK's certificates, current or past.
        Set<String> archiveCerts = certificateDigests(archive, true);
        Set<String> installedCerts = certificateDigests(installed, false);
        if (archiveCerts.isEmpty() || installedCerts.isEmpty() || !archiveCerts.containsAll(installedCerts)) {
            throw new VerificationException("the APK is signed with a different key");
        }
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> certificateDigests(PackageInfo info, boolean includeHistory) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = info.signingInfo;
            if (signingInfo == null) {
                return new HashSet<>();
            }
            signatures = signingInfo.hasMultipleSigners() || !includeHistory
                    ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }

        Set<String> digests = new HashSet<>();
        if (signatures == null) {
            return digests;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            for (Signature signature : signatures) {
                byte[] digest = sha256.digest(signature.toByteArray());
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                digests.add(hex.toString());
            }
        } catch (NoSuchAlgorithmException e) {
            digests.clear();
        }
        return digests;
    }

    /**
     * Deletes downloaded update APKs that are no longer useful: this version or older (already
     * installed, or superseded). A newer one that was never installed is kept. Runs off the
     * main thread.
     */
    public static void cleanupOldDownloads(Context context) {
        final File dir = context.getExternalFilesDir("updates");
        if (dir == null) {
            return;
        }
        Thread thread = new Thread(() -> {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith("LavArtemis-") || !name.endsWith(".apk")) {
                    continue;
                }
                String version = name.substring("LavArtemis-".length(), name.length() - ".apk".length());
                if (UpdateChecker.compareVersions(version, BuildConfig.VERSION_NAME) <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }, "UpdateCleanup");
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
