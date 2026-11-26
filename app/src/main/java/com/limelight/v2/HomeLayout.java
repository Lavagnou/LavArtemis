package com.limelight.v2;

/**
 * Sizes of the home for one window, in dp from the prototype's px (stage 1280 x 720).
 * <ul>
 * <li>Tablet (at least 1100 x 640 dp): the prototype itself, scaled so a 1152 px wide stage fills
 * the width without overflowing 720 px of height (Densite-Tablette: 1280 x 800 dp = x1.111;
 * 1280 x 720 dp = x1).</li>
 * <li>Console (e.g. 960 x 540 dp) and phone (smaller side under 480 dp): blocks shrink with the
 * height (Densite-Console / Densite-Telephone: cards 132 / 104 dp for the prototype's 170), text
 * shrinks less (body text stays near its size, titles lose about a fifth / two fifths).</li>
 * </ul>
 * Portrait is out of the prototype: the same sizes, in a page that scrolls.
 */
public final class HomeLayout {
    public enum Kind { TABLET, CONSOLE, PHONE }

    public final Kind kind;
    /** dp per prototype px for blocks (sizes, gaps, margins) */
    public final float k;
    public final int widthDp, heightDp;
    public final boolean portrait;

    private HomeLayout(int widthDp, int heightDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        portrait = heightDp > widthDp;
        int small = Math.min(widthDp, heightDp);
        if (!portrait && widthDp >= 1100 && heightDp >= 640) {
            kind = Kind.TABLET;
            k = Math.min(widthDp / 1152f, heightDp / 720f);
        } else if (small < 480) {
            kind = Kind.PHONE;
            k = 0.6f;
        } else {
            kind = Kind.CONSOLE;
            k = Math.max(0.7f, Math.min(1f, Math.min(widthDp, 1280) / 1280f * (heightDp >= 600 ? 1.1f : 1f)));
        }
    }

    public static HomeLayout forSize(int widthDp, int heightDp) {
        return new HomeLayout(widthDp, heightDp);
    }

    public boolean phone() {
        return kind == Kind.PHONE;
    }

    public boolean tablet() {
        return kind == Kind.TABLET;
    }

    /** A block size (px of the prototype) in dp */
    public float b(float px) {
        return px * k;
    }

    /** A text size (px of the prototype) in dp: body text keeps most of its size on small screens */
    public float t(float px) {
        if (kind == Kind.TABLET) {
            return px * k;
        }
        float scaled = px * k;
        return scaled + (px - scaled) * (px <= 20 ? 0.75f : 0.35f);
    }

    /** Width of the stage in prototype px (1280 at 1280 dp, 1152 at 1280 x 800 dp) */
    public float stageWidth() {
        return widthDp / k;
    }
}
