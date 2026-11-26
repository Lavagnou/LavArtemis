package com.limelight.v2;

import android.graphics.Bitmap;

import com.limelight.nvstream.http.NvApp;

/** Where the v2 screens get covers from: the host's cache in the app, fixed images in tests. */
public interface CoverSource {
    interface Sink {
        /** On the main thread; null when the app has no cover (the name shows instead) */
        void onCover(int appId, Bitmap cover);
    }

    /** May answer synchronously (memory cache) or later. */
    void load(NvApp app, Sink sink);

    /** Already in memory, or null */
    Bitmap peek(NvApp app);

    /** A Playnite game's background image (LavApollo, NvApp.hasPlayniteBackground()); null when none */
    default void loadBackground(NvApp app, Sink sink) {
        sink.onCover(app.getAppId(), null);
    }

    default Bitmap peekBackground(NvApp app) {
        return null;
    }
}
