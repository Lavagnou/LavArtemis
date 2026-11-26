package com.limelight.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.grid.assets.ScaledBitmap;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Covers of one host for the v2 screens, from the same caches as the legacy grid (memory, disk,
 * then the host). Unlike CachedAppAssetLoader it reports "no cover" explicitly: the grey
 * placeholder of the legacy grid is not a cover, the v2 tiles show the game's name instead.
 */
public final class CoverLoader implements CoverSource {
    private final ComputerDetails computer;
    private final DiskAssetLoader disk;
    private final NetworkAssetLoader network;
    private final MemoryAssetLoader memory = new MemoryAssetLoader();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, List<Sink>> pending = new HashMap<>();
    private final Set<Integer> none = new HashSet<>();
    private volatile boolean closed;

    private final Context appContext;
    private final String uniqueId;

    public CoverLoader(Context context, ComputerDetails computer, String uniqueId) {
        this.appContext = context.getApplicationContext();
        this.uniqueId = uniqueId;
        this.computer = computer;
        this.disk = new DiskAssetLoader(context);
        this.network = new NetworkAssetLoader(context, uniqueId);
    }

    public String getComputerUuid() {
        return computer.uuid;
    }

    @Override
    public Bitmap peek(NvApp app) {
        ScaledBitmap b = memory.loadBitmapFromCache(new CachedAppAssetLoader.LoaderTuple(computer, app));
        return b == null || isPlaceholder(b) ? null : b.bitmap;
    }

    @Override
    public void load(final NvApp app, Sink sink) {
        final int id = app.getAppId();
        Bitmap cached = peek(app);
        if (cached != null || none.contains(id)) {
            sink.onCover(id, cached);
            return;
        }
        synchronized (pending) {
            List<Sink> sinks = pending.get(id);
            if (sinks != null) {
                sinks.add(sink);
                return;
            }
            sinks = new ArrayList<>();
            sinks.add(sink);
            pending.put(id, sinks);
        }
        try {
            executor.execute(() -> {
                ScaledBitmap result = fetch(app);
                final Bitmap bitmap = result == null || isPlaceholder(result) ? null : result.bitmap;
                final boolean definitelyNone = result != null && isPlaceholder(result);
                main.post(() -> {
                    List<Sink> sinks;
                    synchronized (pending) {
                        sinks = pending.remove(id);
                    }
                    if (definitelyNone) {
                        none.add(id);
                    }
                    if (closed || sinks == null) {
                        return;
                    }
                    for (Sink s : sinks) {
                        s.onCover(id, bitmap);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            synchronized (pending) {
                pending.remove(id);
            }
        }
    }

    private ScaledBitmap fetch(NvApp app) {
        CachedAppAssetLoader.LoaderTuple tuple = new CachedAppAssetLoader.LoaderTuple(computer, app);
        ScaledBitmap bmp = disk.loadBitmapFromCache(tuple, 1);
        for (int attempt = 0; bmp == null && attempt < 2 && !closed; attempt++) {
            InputStream in = network.getBitmapStream(tuple);
            if (in != null) {
                disk.populateCacheWithStream(tuple, in);
                try {
                    in.close();
                } catch (IOException ignored) {
                }
                bmp = disk.loadBitmapFromCache(tuple, 1);
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        if (bmp != null) {
            memory.populateCache(tuple, bmp);
        }
        return bmp;
    }

    /** The host's generic image (GFE sizes), as CachedAppAssetLoader.isBitmapPlaceholder */
    static boolean isPlaceholder(ScaledBitmap b) {
        return b.bitmap == null
                || (b.originalWidth == 130 && b.originalHeight == 180)
                || (b.originalWidth == 628 && b.originalHeight == 888);
    }

    // ---- backgrounds (Braise's hero) -----------------------------------------------------------

    private final android.util.LruCache<Integer, Bitmap> backgrounds = new android.util.LruCache<>(4);
    private final Set<Integer> noBackground = new HashSet<>();

    @Override
    public Bitmap peekBackground(NvApp app) {
        return backgrounds.get(app.getAppId());
    }

    @Override
    public void loadBackground(final NvApp app, final Sink sink) {
        final int id = app.getAppId();
        Bitmap cached = backgrounds.get(id);
        if (cached != null || noBackground.contains(id) || !app.hasPlayniteBackground()) {
            sink.onCover(id, cached);
            return;
        }
        try {
            executor.execute(() -> {
                Bitmap b = null;
                try {
                    com.limelight.nvstream.http.NvHTTP http = new com.limelight.nvstream.http.NvHTTP(
                            com.limelight.utils.ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                            uniqueId, computer.serverCert, com.limelight.binding.PlatformBinding.getCryptoProvider(appContext));
                    try (InputStream in = http.getBackground(app)) {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inPreferredConfig = Bitmap.Config.RGB_565;
                        b = android.graphics.BitmapFactory.decodeStream(in, null, o);
                    }
                } catch (IOException | RuntimeException ignored) {
                }
                final Bitmap result = b;
                main.post(() -> {
                    if (result != null) {
                        backgrounds.put(id, result);
                    } else {
                        noBackground.add(id);
                    }
                    if (!closed) {
                        sink.onCover(id, result);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
