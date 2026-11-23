package com.limelight.v2.settings;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of Réglages v2, declared in {@link SettingsCatalog}: the preference key it reads and
 * writes (the keys of res/xml/preferences.xml, with their defaults), its control, its page, whether
 * it is an expert row, when the device offers it and when another setting greys it out.
 */
public final class SettingDef {
    public enum Type {
        /** On / off switch, the whole row flips it */
        TOGGLE,
        /** Segmented buttons for a few short options, a list dialog beyond */
        CHOICE,
        /** Integer slider */
        SLIDER,
        /** One button: opens something, resets something */
        ACTION,
        /** Several buttons (profiles: Nouveau / Renommer / Supprimer) */
        BUTTONS
    }

    /** How the value is stored in the preferences */
    public enum Kind { BOOL, STRING, INT, NONE }

    /** Whether this device offers the row at all (hidden otherwise) */
    public interface Avail {
        boolean test(SettingsEnv env);
    }

    /** Current values, for dependencies and dynamic texts */
    public interface Values {
        boolean getBool(String key);

        String getString(String key);

        int getInt(String key);
    }

    /** Whether the row is usable now (greyed otherwise, like android:dependency) */
    public interface Dep {
        boolean enabled(Values values);
    }

    /** Options computed when the page is built (resolutions, microphones, profiles...) */
    public interface OptionSource {
        List<Option> options(Context context, SettingsEnv env, Values values, boolean expert);
    }

    /** Text of a slider value */
    public interface Format {
        String format(Context context, int value);
    }

    public final String key;
    public final Type type;
    public final Kind kind;
    public final String page;
    public final int label;
    public int desc;
    public boolean expert;
    /** Boolean, String or Integer: the default of the legacy screen and of PreferenceConfiguration */
    public Object def;
    /** The switch shows the opposite of the stored value (« Couper le son du PC distant ») */
    public boolean inverted;
    /** Written to the base preferences, never into a profile (appearance) */
    public boolean global;
    public Avail avail;
    public Dep dep;

    // choice
    public final List<String> values = new ArrayList<>();
    public final List<Integer> labels = new ArrayList<>();
    public OptionSource source;
    /** Segmented up to this many options, a list dialog beyond */
    public int segmentMax = 4;

    // slider
    public int min, max, step = 1, keyStep = 1;
    /** Exponent of the track: 1 linear, 3 gives the low values room (bitrates up to 800 Mbit/s) */
    public float curve = 1f;
    public Format format;

    // action / buttons
    public final List<Integer> buttons = new ArrayList<>();

    SettingDef(String key, Type type, Kind kind, String page, int label) {
        this.key = key;
        this.type = type;
        this.kind = kind;
        this.page = page;
        this.label = label;
    }

    // ---- declaration helpers (SettingsCatalog) ---------------------------------------------------

    SettingDef desc(int res) {
        desc = res;
        return this;
    }

    SettingDef expert() {
        expert = true;
        return this;
    }

    SettingDef inverted() {
        inverted = true;
        return this;
    }

    SettingDef global() {
        global = true;
        return this;
    }

    SettingDef when(Avail a) {
        avail = a;
        return this;
    }

    SettingDef dependsOn(final String boolKey) {
        dep = v -> v.getBool(boolKey);
        return this;
    }

    SettingDef dependsOn(Dep d) {
        dep = d;
        return this;
    }

    SettingDef option(String value, int labelRes) {
        values.add(value);
        labels.add(labelRes);
        return this;
    }

    SettingDef options(OptionSource s) {
        source = s;
        return this;
    }

    SettingDef segments(int max) {
        segmentMax = max;
        return this;
    }

    SettingDef range(int min, int max, int step, int keyStep) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.keyStep = keyStep;
        return this;
    }

    SettingDef curve(float c) {
        curve = c;
        return this;
    }

    SettingDef format(Format f) {
        format = f;
        return this;
    }

    SettingDef button(int labelRes) {
        buttons.add(labelRes);
        return this;
    }

    // ---- queries -----------------------------------------------------------------------------------

    public boolean available(SettingsEnv env) {
        return avail == null || avail.test(env);
    }

    public boolean enabled(Values v) {
        return dep == null || dep.enabled(v);
    }

    /** Shown on this device in this mode */
    public boolean shown(SettingsEnv env, boolean expertMode) {
        return available(env) && (!expert || expertMode);
    }

    /** The options of a choice row, labels resolved */
    public List<Option> options(Context context, SettingsEnv env, Values v, boolean expertMode) {
        if (source != null) {
            return source.options(context, env, v, expertMode);
        }
        List<Option> out = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            out.add(new Option(values.get(i), context.getString(labels.get(i))));
        }
        return out;
    }

    public String formatValue(Context context, int value) {
        return format != null ? format.format(context, value) : String.valueOf(value);
    }

    /** Snapped to the slider's steps and range */
    public int snap(int value) {
        int v = Math.max(min, Math.min(max, value));
        if (step > 1) {
            v = min + Math.round((v - min) / (float) step) * step;
        }
        return Math.max(min, Math.min(max, v));
    }

    /** Track position (0..1) of a value */
    public float position(int value) {
        if (max <= min) {
            return 0;
        }
        float p = Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
        return curve == 1f ? p : (float) Math.pow(p, 1.0 / curve);
    }

    /** Value at a track position (0..1), snapped */
    public int valueAt(float position) {
        float p = Math.max(0f, Math.min(1f, position));
        if (curve != 1f) {
            p = (float) Math.pow(p, curve);
        }
        return snap(Math.round(min + p * (max - min)));
    }

    /**
     * One D-pad step from a value: keyStep, or for a curved track (bitrate) a step that grows with
     * the value, so that a few presses go from 20 to 200 Mbit/s
     */
    public int stepFrom(int value, int delta) {
        int s = keyStep;
        if (curve != 1f) {
            int span = max - min;
            if (value >= min + span / 4) {
                s = keyStep * 20;
            } else if (value >= min + span / 16) {
                s = keyStep * 5;
            }
        }
        return snap(value + delta * s);
    }

    @Override
    public String toString() {
        return key;
    }
}
