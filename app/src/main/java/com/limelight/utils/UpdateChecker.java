package com.limelight.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.BuildConfig;
import com.limelight.LimeLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Checks GitHub releases of Lavagnou/LavArtemis for a newer version.
 *
 * Stable channel queries /releases/latest (always a stable release, CI builds are
 * published as prereleases). The prerelease channel queries /releases and takes the
 * first entry, which is the most recent release or prerelease.
 */
public class UpdateChecker {
    private static final String RELEASES_LATEST_URL =
            "https://api.github.com/repos/Lavagnou/LavArtemis/releases/latest";
    private static final String RELEASES_URL =
            "https://api.github.com/repos/Lavagnou/LavArtemis/releases";

    public static final String PREF_AUTO_UPDATE = "checkbox_auto_update";
    public static final String PREF_UPDATE_PRERELEASE = "checkbox_update_prerelease";

    // Bookkeeping for the automatic check (not user-visible settings)
    private static final String PREF_LAST_AUTO_CHECK_MS = "update_last_auto_check_ms";
    private static final String PREF_SKIPPED_VERSION = "update_skipped_version";

    /**
     * The automatic check runs from PcView.onCreate(), which fires on every rotation, every
     * return from a stream and every time a TV box evicts the activity. Unthrottled, that is
     * dozens of GitHub API calls a day against a 60-per-hour limit shared with the whole NAT,
     * and the same dialog over and over.
     */
    private static final long AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000;

    /** After a failed automatic check (offline, rate-limited), try again sooner than that. */
    private static final long AUTO_CHECK_RETRY_MS = 60L * 60 * 1000;

    /**
     * Release APKs are published one per ABI as {@code LavArtemis-<tag>-android-<abi>.apk}.
     * The asset has to match what the device can run: a Fire TV Stick or a Chromecast with
     * Google TV runs a 32-bit userspace, and the arm64 APK fails there with
     * INSTALL_FAILED_NO_MATCHING_ABIS -- after a full download and an install screen.
     */
    private static final String APK_ASSET_ABI_MARKER = "-android-";

    public static class UpdateInfo {
        public final String version;
        public final String pageUrl;
        public final String downloadUrl;
        public final boolean prerelease;

        UpdateInfo(String version, String pageUrl, String downloadUrl, boolean prerelease) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.downloadUrl = downloadUrl;
            this.prerelease = prerelease;
        }
    }

    public interface Callback {
        /** Called on the main thread with the newer release info. */
        void onUpdateAvailable(UpdateInfo info);
        /** Called on the main thread when the installed version is up to date. */
        void onUpToDate();
        /** Called on the main thread when the check fails (network, parsing, ...). */
        void onError(Exception e);
    }

    public static boolean isAutoUpdateEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_AUTO_UPDATE, true);
    }

    /** Whether the automatic check is enabled and due. The manual check is never throttled. */
    public static boolean shouldAutoCheck(Context context) {
        if (!isAutoUpdateEnabled(context)) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return isDue(prefs.getLong(PREF_LAST_AUTO_CHECK_MS, 0), System.currentTimeMillis());
    }

    /** Records the outcome of an automatic check, for {@link #shouldAutoCheck(Context)}. */
    public static void recordAutoCheck(Context context, boolean succeeded) {
        long now = System.currentTimeMillis();
        // A failure is recorded as if the last check were older, so the next one comes after
        // AUTO_CHECK_RETRY_MS instead of the full interval.
        long recorded = succeeded ? now : now - AUTO_CHECK_INTERVAL_MS + AUTO_CHECK_RETRY_MS;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putLong(PREF_LAST_AUTO_CHECK_MS, recorded)
                .apply();
    }

    public static boolean isDue(long lastCheckMs, long nowMs) {
        // A clock that went backwards must not suppress checks until it catches up again.
        return nowMs < lastCheckMs || nowMs - lastCheckMs >= AUTO_CHECK_INTERVAL_MS;
    }

    /** The user chose to skip this version; the automatic check will not offer it again. */
    public static void skipVersion(Context context, String version) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_SKIPPED_VERSION, version)
                .apply();
    }

    public static boolean isSkipped(Context context, String version) {
        String skipped = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SKIPPED_VERSION, null);
        return version != null && version.equals(skipped);
    }

    public static void check(Context context, Callback callback) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean includePrerelease = prefs.getBoolean(PREF_UPDATE_PRERELEASE, false);
        check(includePrerelease, callback);
    }

    public static void check(boolean includePrerelease, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Thread thread = new Thread(() -> {
            try {
                UpdateInfo info = performCheck(includePrerelease);
                if (info != null) {
                    mainHandler.post(() -> callback.onUpdateAvailable(info));
                } else {
                    mainHandler.post(callback::onUpToDate);
                }
            } catch (Exception e) {
                LimeLog.warning("Update check failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError(e));
            }
        }, "UpdateChecker");
        thread.setDaemon(true);
        thread.start();
    }

    private static UpdateInfo performCheck(boolean includePrerelease) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        String url = includePrerelease ? RELEASES_URL : RELEASES_LATEST_URL;
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LavArtemis-Android/" + BuildConfig.VERSION_NAME)
                .build();

        String body;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API returned HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty response from GitHub API");
            }
            body = responseBody.string();
        }

        JsonObject release;
        if (includePrerelease) {
            JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
            if (releases.size() == 0) {
                return null;
            }
            // The API returns releases ordered by creation date, most recent first
            release = releases.get(0).getAsJsonObject();
        } else {
            release = JsonParser.parseString(body).getAsJsonObject();
        }

        String tagName = release.get("tag_name").getAsString();
        String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        if (compareVersions(BuildConfig.VERSION_NAME, latestVersion) >= 0) {
            return null;
        }

        String pageUrl = release.get("html_url").getAsString();
        boolean prerelease = release.get("prerelease").getAsBoolean();

        List<String> names = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement assetElement : assets) {
                JsonObject asset = assetElement.getAsJsonObject();
                names.add(asset.get("name").getAsString());
                urls.add(asset.get("browser_download_url").getAsString());
            }
        }

        // No asset for this device's ABIs leaves downloadUrl null, and the dialog falls back
        // to the release page rather than downloading something that cannot be installed.
        String downloadUrl = null;
        int picked = pickAsset(names, Build.SUPPORTED_ABIS);
        if (picked >= 0) {
            downloadUrl = urls.get(picked);
        }

        return new UpdateInfo(latestVersion, pageUrl, downloadUrl, prerelease);
    }

    /**
     * Picks the APK asset for the first ABI in {@code abis} (most preferred first, as in
     * {@link Build#SUPPORTED_ABIS}) that has one.
     *
     * @return the index of the chosen asset in {@code names}, or -1 if none fits.
     */
    public static int pickAsset(List<String> names, String[] abis) {
        if (abis == null) {
            return -1;
        }
        for (String abi : abis) {
            String suffix = APK_ASSET_ABI_MARKER + abi + ".apk";
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).endsWith(suffix)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Compares two version strings of the form x.y.z with an optional "-ci.N" suffix.
     *
     * A prerelease (suffix present) sorts before the stable release with the same base
     * version. Returns a negative value if a &lt; b, 0 if equal, positive if a &gt; b.
     */
    public static int compareVersions(String a, String b) {
        String[] partsA = a.split("-", 2);
        String[] partsB = b.split("-", 2);

        String[] numsA = partsA[0].split("\\.");
        String[] numsB = partsB[0].split("\\.");

        for (int i = 0; i < Math.max(numsA.length, numsB.length); i++) {
            int numA = i < numsA.length ? parseIntSafe(numsA[i]) : 0;
            int numB = i < numsB.length ? parseIntSafe(numsB[i]) : 0;
            if (numA != numB) {
                return numA - numB;
            }
        }

        // Same base version: a stable release (no suffix) is newer than any prerelease
        boolean preA = partsA.length > 1;
        boolean preB = partsB.length > 1;
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        if (preA) {
            // Both are prereleases, e.g. "ci.42": compare the build number
            return parseIntSafe(extractBuildNumber(partsA[1]))
                    - parseIntSafe(extractBuildNumber(partsB[1]));
        }
        return 0;
    }

    private static String extractBuildNumber(String suffix) {
        int dot = suffix.lastIndexOf('.');
        return dot >= 0 ? suffix.substring(dot + 1) : suffix;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
