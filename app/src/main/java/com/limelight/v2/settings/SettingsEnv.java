package com.limelight.v2.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.audio.MicrophoneStreamer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What this device can do, read once when Réglages opens: the rows a capability hides (exactly the
 * ones the legacy screen, StreamSettings, removed) and the lists that depend on the display and
 * the decoders (resolutions, frame rates, microphones). Plain fields: tests and screenshots build
 * one by hand ({@link #forTests}).
 */
public final class SettingsEnv {
    public int sdk = Build.VERSION.SDK_INT;
    public boolean touchscreen = true;
    /** NVIDIA SHIELD: raw mouse in pointer capture, no remote desktop mouse mode */
    public boolean shield;
    public boolean usbHost = true;
    public boolean motionSensors = true;
    public boolean vibrator = true;
    public boolean amplitudeControl = true;
    public boolean pip = true;
    public boolean pyroWave;

    public final List<Option> resolutions = new ArrayList<>();
    public final List<Option> frameRates = new ArrayList<>();
    /** MicrophoneStreamer devices: {key, name} */
    public final List<String[]> microphones = new ArrayList<>();
    /**
     * Values the display or the decoders cannot take any more and what they become (the legacy
     * screen rewrote them when it opened), with the bitrate reset to its default
     */
    public final Map<String, String> fixups = new LinkedHashMap<>();

    public static SettingsEnv forTests() {
        SettingsEnv env = new SettingsEnv();
        env.sdk = 33;
        for (String[] r : BASE_RESOLUTIONS) {
            env.resolutions.add(new Option(r[0], r[1]));
        }
        env.resolutions.add(new Option("2400x1080", "Native (2400x1080)", false, true));
        for (String f : BASE_FPS) {
            env.frameRates.add(new Option(f, f + " FPS"));
        }
        return env;
    }

    static final String[][] BASE_RESOLUTIONS = {
            {"640x360", "360p"}, {"854x480", "480p"}, {"1280x720", "720p"},
            {"1920x1080", "1080p"}, {"2560x1440", "1440p"}, {"3840x2160", "4K"}
    };
    static final String[] BASE_FPS = {"30", "60", "90", "120"};

    /**
     * Reads the device. Port of StreamSettings.SettingsFragment.initializePreferences(): same
     * hidden rows, same native / custom entries, same removed resolutions and frame rates.
     */
    public static SettingsEnv detect(Activity activity, SharedPreferences prefs) {
        SettingsEnv env = new SettingsEnv();
        PackageManager pm = activity.getPackageManager();
        env.touchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        env.shield = pm.hasSystemFeature("com.nvidia.feature.shield");
        env.usbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        env.motionSensors = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
                || pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);
        Vibrator vib = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        env.vibrator = vib != null && vib.hasVibrator();
        env.amplitudeControl = env.vibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl();
        env.pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && pm.hasSystemFeature("android.software.picture_in_picture")
                && !pm.hasSystemFeature("com.amazon.software.fireos");
        try {
            env.pyroWave = MoonBridge.pyroWaveIsSupported();
        } catch (Throwable t) {
            env.pyroWave = false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                env.microphones.addAll(MicrophoneStreamer.listDevices(activity));
            } catch (RuntimeException e) {
                LimeLog.warning("Réglages: microphones not listed: " + e);
            }
        }
        env.readDisplayModes(activity, prefs);
        return env;
    }

    // ---- resolutions and frame rates -------------------------------------------------------------

    private int nativeStart = Integer.MAX_VALUE;

    /** Android 9: the display cutout, from the root insets of an attached v2 window */
    static DisplayCutout cutoutP;

    /**
     * Android 9 has no Display.getCutout(): the cutout comes from the window insets, which are
     * only there once the window is attached (and can be null after a rotation, so the last one
     * seen is kept). Returns true when it was not known before.
     */
    public static boolean rememberCutout(Activity activity) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
            return false;
        }
        android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        DisplayCutout cutout = insets != null ? insets.getDisplayCutout() : null;
        if (cutout == null || cutout.equals(cutoutP)) {
            return false;
        }
        cutoutP = cutout;
        return true;
    }

    private void readDisplayModes(Activity activity, SharedPreferences prefs) {
        for (String[] r : BASE_RESOLUTIONS) {
            resolutions.add(new Option(r[0], r[1]));
        }
        for (String f : BASE_FPS) {
            frameRates.add(new Option(f, f + " FPS"));
        }
        PackageManager pm = activity.getPackageManager();

        // Custom resolution and refresh rate typed in earlier
        String customRes = prefs.getString(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, null);
        if (customRes != null && !customRes.isEmpty()) {
            String[] seg = customRes.split("x");
            if (seg.length == 2) {
                try {
                    addNativeResolutions(activity, Integer.parseInt(seg[0]), Integer.parseInt(seg[1]), false, true);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String customFps = prefs.getString(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, null);
        if (customFps != null && !customFps.isEmpty()) {
            try {
                float v = Float.parseFloat(customFps);
                if (v > 0) {
                    addNativeFrameRate(activity, v, true);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Display display = activity.getWindowManager().getDefaultDisplay();
        float maxFps = display.getRefreshRate();
        String resolution = prefs.getString("list_resolution", "1280x720");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int maxResW = 0;
            boolean hasInsets = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Display.getCutout() is Android 10+; Android 9 has the cutout only through the
                // window's insets, read when a v2 window was attached (StreamSettings' hack)
                DisplayCutout cutout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? display.getCutout() : cutoutP;
                if (cutout != null) {
                    int wi = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                    int hi = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();
                    if (wi != 0 || hi != 0) {
                        DisplayMetrics m = new DisplayMetrics();
                        display.getRealMetrics(m);
                        int w = Math.max(m.widthPixels - wi, m.heightPixels - hi);
                        int h = Math.min(m.widthPixels - wi, m.heightPixels - hi);
                        addNativeResolutions(activity, w, h, false, false);
                        hasInsets = true;
                    }
                }
            }
            for (Display.Mode mode : display.getSupportedModes()) {
                int w = Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                int h = Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight());
                // TVs report strange values: native modes only above 4K there
                if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) || w > 3840 || h > 2160) {
                    addNativeResolutions(activity, w, h, hasInsets, false);
                }
                if ((w >= 3840 || h >= 2160) && maxResW < 3840) {
                    maxResW = 3840;
                } else if ((w >= 2560 || h >= 1440) && maxResW < 2560) {
                    maxResW = 2560;
                } else if ((w >= 1920 || h >= 1080) && maxResW < 1920) {
                    maxResW = 1920;
                }
                if (mode.getRefreshRate() > maxFps) {
                    maxFps = mode.getRefreshRate();
                }
            }

            try {
                MediaCodecHelper.initialize(activity, GlPreferences.readPreferences(activity).glRenderer);
                maxResW = Math.max(maxResW, decoderWidth("video/avc"));
                maxResW = Math.max(maxResW, decoderWidth("video/hevc"));
            } catch (Throwable t) {
                LimeLog.warning("Réglages: decoders not probed: " + t);
            }

            if (maxResW != 0) {
                if (maxResW < 3840) {
                    resolution = removeResolution("3840x2160", "2560x1440", resolution);
                }
                if (maxResW < 2560) {
                    resolution = removeResolution("2560x1440", "1920x1080", resolution);
                }
                if (maxResW < 1920) {
                    removeResolution("1920x1080", "1280x720", resolution);
                }
            }
        } else {
            DisplayMetrics m = new DisplayMetrics();
            display.getRealMetrics(m);
            addNativeResolutions(activity, Math.max(m.widthPixels, m.heightPixels), Math.min(m.widthPixels, m.heightPixels), false, false);
        }

        boolean unlockFps = prefs.getBoolean("checkbox_unlock_fps", false);
        if (!unlockFps) {
            String fps = prefs.getString("list_fps", "60");
            if (maxFps < 118) {
                fps = removeFrameRate("120", "90", fps);
            }
            if (maxFps < 88) {
                removeFrameRate("90", "60", fps);
            }
        }
        addNativeFrameRate(activity, maxFps, false);
    }

    /** The widest stream the decoder says it takes (0 when it does not report 720p, as upstream) */
    private static int decoderWidth(String mime) {
        MediaCodecInfo info = MediaCodecHelper.findProbableSafeDecoder(mime, -1);
        if (info == null) {
            return 0;
        }
        Range<Integer> widths = info.getCapabilitiesForType(mime).getVideoCapabilities().getSupportedWidths();
        if (!widths.contains(1280)) {
            return 0;
        }
        if (widths.contains(3840)) {
            return 3840;
        }
        return widths.contains(1920) ? 1920 : 1280;
    }

    private String removeResolution(String value, String next, String current) {
        remove(resolutions, value);
        if (current.equalsIgnoreCase(value)) {
            fixups.put("list_resolution", next);
            return next;
        }
        return current;
    }

    private String removeFrameRate(String value, String next, String current) {
        remove(frameRates, value);
        if (current.equalsIgnoreCase(value)) {
            fixups.put("list_fps", next);
            return next;
        }
        return current;
    }

    private static void remove(List<Option> list, String value) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).value.equalsIgnoreCase(value)) {
                list.remove(i);
            }
        }
    }

    private static boolean contains(List<Option> list, String value) {
        for (Option o : list) {
            if (o.value.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void addNativeResolutions(Context c, int w, int h, boolean insetsRemoved, boolean custom) {
        if (PreferenceConfiguration.isSquarishScreen(w, h)) {
            addNativeResolution(c, h, w, insetsRemoved, true, custom);
        }
        addNativeResolution(c, w, h, insetsRemoved, false, custom);
    }

    private void addNativeResolution(Context c, int w, int h, boolean insetsRemoved, boolean portrait, boolean custom) {
        String size = w + "x" + h;
        if (contains(resolutions, size)) {
            return;
        }
        String label = c.getString(insetsRemoved ? R.string.v2_set_res_native_full : (custom ? R.string.v2_set_res_custom_value : R.string.v2_set_res_native), size);
        if (PreferenceConfiguration.isSquarishScreen(w, h)) {
            label += " " + c.getString(portrait ? R.string.v2_set_res_portrait : R.string.v2_set_res_landscape);
        }
        nativeStart = Math.min(nativeStart, resolutions.size());
        resolutions.add(new Option(size, label, false, true));
    }

    private void addNativeFrameRate(Context c, float fps, boolean custom) {
        if (!custom) {
            fps = Math.round(fps);
            if (fps == 0) {
                return;
            }
        }
        String value = custom ? Float.toString(fps) : Integer.toString(Math.round(fps));
        if (contains(frameRates, value)) {
            return;
        }
        String shown = custom ? value.replace('.', ',') : value;
        frameRates.add(new Option(value, c.getString(custom ? R.string.v2_set_fps_custom_value : R.string.v2_set_fps_native, shown), false, true));
    }

    /** A refresh rate as the legacy screen stores it: three decimals at most, no trailing zero */
    public static String formatRefreshRate(float hz) {
        String s = String.format(Locale.US, "%.3f", hz);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
