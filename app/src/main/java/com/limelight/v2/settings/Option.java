package com.limelight.v2.settings;

/** One choice of a choice row: the stored value and its label. */
public final class Option {
    public final String value;
    public final String label;
    /** Opens a dialog instead of being written as is (« Personnalisée… », « Autre… ») */
    public final boolean ask;
    /** A native or custom display mode: choosing it shows the warning of the legacy screen */
    public final boolean nativeWarn;

    public Option(String value, String label) {
        this(value, label, false, false);
    }

    public Option(String value, String label, boolean ask, boolean nativeWarn) {
        this.value = value;
        this.label = label;
        this.ask = ask;
        this.nativeWarn = nativeWarn;
    }

    public static Option ask(String value, String label) {
        return new Option(value, label, true, false);
    }

    @Override
    public String toString() {
        return value + "=" + label;
    }
}
