package com.limelight.v2;

import android.content.res.Configuration;

/**
 * Sizes of the v2 screens for one window size (validated density boards: phone in landscape
 * 915x412 dp, console 960x540 dp). Compact when the smaller side is under 480 dp: phones, in either
 * orientation. Every touch target stays at 48 dp at least.
 */
public final class Density {
    public final boolean compact;
    public final int widthDp;
    public final int heightDp;

    public final float side;          // page side margin
    public final float top;           // space above the header
    public final float headerHeight;
    public final boolean headerLabels; // Mes PC / Réglages with their label (console) or icon only
    public final float sceneTitle;
    public final float cardHeight;
    public final float coverWidth;
    public final float coverHeight;
    public final float coverGap;
    public final float sectionGap;
    public final float sectionLabel;
    /** Bureau and Playnite Fullscreen one above the other (narrow portrait) */
    public final boolean stackCards;
    /** Reprendre / Quitter under the title instead of beside it */
    public final boolean stackScene;

    private Density(int widthDp, int heightDp) {
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        compact = Math.min(widthDp, heightDp) < 480;
        side = compact ? 20 : 24;
        top = compact ? 8 : 10;
        headerHeight = 48;
        headerLabels = !compact && widthDp >= 720;
        sceneTitle = compact ? 26 : 36;
        cardHeight = compact ? 104 : 132;
        coverWidth = compact ? 82 : 100;
        coverHeight = compact ? 122 : 150;
        coverGap = compact ? 12 : 14;
        sectionGap = 12;
        sectionLabel = compact ? 14 : 15;
        stackCards = widthDp < 600;
        stackScene = widthDp < 560;
    }

    public static Density forSize(int widthDp, int heightDp) {
        return new Density(widthDp, heightDp);
    }

    public static Density of(Configuration config) {
        return new Density(config.screenWidthDp, config.screenHeightDp);
    }
}
