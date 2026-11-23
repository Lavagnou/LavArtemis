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

    public CoverLoader(Context context, ComputerDetails computer, String uniqueId) {
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

    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
