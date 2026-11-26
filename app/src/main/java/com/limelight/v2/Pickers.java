package com.limelight.v2;

/** Small rules of the pickers (prototype ctxOpts themePick / hostPick, choice). Pure, tested. */
public final class Pickers {
    private Pickers() {}

    /** « ✓ Braise » for the current entry, « Affiche » otherwise (prototype '✓ ' prefix) */
    public static String checked(String label, boolean current) {
        return current ? "✓ " + label : label;
    }
}
