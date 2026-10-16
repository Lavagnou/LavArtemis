package com.limelight.benchmark;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.binding.video.VrrPacing;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a benchmark configuration maps onto this app's settings: codec, bitrate and the
 * Android pacing modes of docs/benchmark.md.
 */
public final class BenchmarkSettings {
    private BenchmarkSettings() {
    }

    /** Android's pacing modes, in the doc's order. VRR last: the host rebuilds its display for them. */
    public static final List<String> PACING_MODES = Collections.unmodifiableList(
            Arrays.asList("ull", "paced-ull", "predictive", "default", "vrr-low-latency", "vrr-balanced"));

    private static final String KEY_FORMAT = "video_format";
    private static final String KEY_BITRATE = "seekbar_bitrate_kbps";
    private static final String KEY_ULL = "checkbox_ultra_low_latency";
    private static final String KEY_PACED_ULL = "checkbox_paced_ull";
    private static final String KEY_PREDICTIVE = "checkbox_predictive_pacing";
    private static final String KEY_VRR = "checkbox_vrr_pacing";
    private static final String KEY_VRR_MODE = "list_vrr_pacing_mode";

    public static String pacingOf(boolean ull, boolean pacedUll, boolean predictive, boolean vrr, int vrrMode) {
        if (vrr) {
            return vrrMode == VrrPacing.MODE_LOW_LATENCY ? "vrr-low-latency"
                    : vrrMode == VrrPacing.MODE_SMOOTH ? "vrr-smooth" : "vrr-balanced";
        }
        if (ull) {
            return pacedUll ? "paced-ull" : "ull";
        }
        return predictive ? "predictive" : "default";
    }

    public static String pacingFromPreferences(PreferenceConfiguration p) {
        return pacingOf(p.enableUltraLowLatency, p.enablePacedUll, p.enablePredictivePacing, p.enableVrrPacing, p.vrrPacingMode);
    }

    /** The user's settings as a configuration. Auto's codec is a guess the baseline run corrects. */
    public static RunConfig fromPreferences(PreferenceConfiguration p) {
        Codec codec;
        switch (p.videoFormat) {
            case FORCE_H264:
                codec = Codec.H264;
                break;
            case FORCE_AV1:
                codec = Codec.AV1;
                break;
            case FORCE_PYROWAVE:
                codec = Codec.PYROWAVE;
                break;
            default:
                codec = Codec.HEVC;
                break;
        }
        return new RunConfig(codec, p.bitrate, pacingFromPreferences(p));
    }

    public static Codec codecOfVideoFormat(int videoFormat) {
        if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_PYROWAVE) != 0) {
            return Codec.PYROWAVE;
        }
        if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
            return Codec.AV1;
        }
        if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
            return Codec.HEVC;
        }
        return Codec.H264;
    }

    private static PreferenceConfiguration.FormatOption formatOf(Codec codec) {
        switch (codec) {
            case H264:
                return PreferenceConfiguration.FormatOption.FORCE_H264;
            case AV1:
                return PreferenceConfiguration.FormatOption.FORCE_AV1;
            case PYROWAVE:
                return PreferenceConfiguration.FormatOption.FORCE_PYROWAVE;
            case HEVC:
            default:
                return PreferenceConfiguration.FormatOption.FORCE_HEVC;
        }
    }

    /** In memory only, for one stream: the saved settings are not touched. */
    public static void applyTo(PreferenceConfiguration p, RunConfig c) {
        p.videoFormat = formatOf(c.codec);
        p.bitrate = c.bitrateKbps;
        p.enableVrrPacing = BenchmarkTypes.isVrrPacing(c.pacing);
        p.vrrPacingMode = c.pacing.equals("vrr-low-latency") ? VrrPacing.MODE_LOW_LATENCY
                : c.pacing.equals("vrr-smooth") ? VrrPacing.MODE_SMOOTH : VrrPacing.MODE_BALANCED;
        p.enableUltraLowLatency = c.pacing.equals("ull") || c.pacing.equals("paced-ull");
        p.enablePacedUll = c.pacing.equals("paced-ull");
        p.enablePredictivePacing = c.pacing.equals("predictive");
    }

    /** The preference values a configuration stands for, as the settings screen stores them. */
    static Map<String, Object> preferenceValues(RunConfig c) {
        Map<String, Object> values = new LinkedHashMap<>();
        String format;
        switch (c.codec) {
            case H264:
                format = "neverh265";
                break;
            case AV1:
                format = "forceav1";
                break;
            case PYROWAVE:
                format = "forcepyrowave";
                break;
            case HEVC:
            default:
                format = "forceh265";
                break;
        }
        PreferenceConfiguration p = new PreferenceConfiguration();
        applyTo(p, c);
        values.put(KEY_FORMAT, format);
        values.put(KEY_BITRATE, c.bitrateKbps);
        values.put(KEY_ULL, p.enableUltraLowLatency);
        values.put(KEY_PACED_ULL, p.enablePacedUll);
        values.put(KEY_PREDICTIVE, p.enablePredictivePacing);
        values.put(KEY_VRR, p.enableVrrPacing);
        values.put(KEY_VRR_MODE, Integer.toString(p.vrrPacingMode));
        return values;
    }

    /**
     * Into the user's settings: the active profile when there is one (writing the base
     * preferences under a profile would be overridden by it), else the base preferences.
     */
    public static void applyToSettings(Context context, RunConfig c) {
        ProfilesManager profiles = ProfilesManager.getInstance();
        SettingsProfile active = profiles.getActive();
        Map<String, Object> values = preferenceValues(c);
        if (active != null) {
            Map<String, Object> options = active.getOptions() != null ? new LinkedHashMap<>(active.getOptions()) : new LinkedHashMap<>();
            options.putAll(values);
            active.setOptions(options);
            active.setModifiedUtc(System.currentTimeMillis());
            profiles.update(active);
            profiles.save(context);
            return;
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                editor.putBoolean(e.getKey(), (Boolean) v);
            }
            else if (v instanceof Integer) {
                editor.putInt(e.getKey(), (Integer) v);
            }
            else {
                editor.putString(e.getKey(), String.valueOf(v));
            }
        }
        editor.apply();
    }

    /** A new profile holding the active one's settings plus this configuration, made active. */
    public static void applyAsProfile(Context context, RunConfig c, String name) {
        ProfilesManager profiles = ProfilesManager.getInstance();
        SettingsProfile active = profiles.getActive();
        Map<String, Object> options = new LinkedHashMap<>();
        if (active != null && active.getOptions() != null) {
            options.putAll(active.getOptions());
        }
        options.putAll(preferenceValues(c));
        long now = System.currentTimeMillis();
        SettingsProfile profile = new SettingsProfile(UUID.randomUUID(), name, now, now, options);
        profiles.add(profile);
        profiles.setActive(profile.getUuid());
        profiles.save(context);
    }
}
