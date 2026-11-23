package com.limelight.v2.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A grid of fixed-size tiles: as many columns as fit (recomputed at each layout: rotation, window
 * resize), each tile centred in its column, rows separated by the gap. Full-width rows (titles,
 * buttons) are declared by {@link FullSpan}.
 */
public class FitGridLayoutManager extends GridLayoutManager {
    public interface FullSpan {
        boolean isFullSpan(int position);
    }

    private final int tilePx;
    private final int gapPx;

    public FitGridLayoutManager(Context context, int tilePx, int gapPx, final FullSpan fullSpan) {
        super(context, 1);
        this.tilePx = tilePx;
        this.gapPx = gapPx;
        setSpanSizeLookup(new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return fullSpan != null && fullSpan.isFullSpan(position) ? getSpanCount() : 1;
            }
        });
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width > 0) {
            int spans = Math.max(1, (width + gapPx) / (tilePx + gapPx));
            if (spans != getSpanCount()) {
                setSpanCount(spans);
            }
        }
        super.onLayoutChildren(recycler, state);
    }

    /** Centres each tile in its column and spaces the rows */
    public RecyclerView.ItemDecoration decoration(final FullSpan fullSpan) {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State s) {
                int pos = parent.getChildAdapterPosition(view);
                if (pos == RecyclerView.NO_POSITION || (fullSpan != null && fullSpan.isFullSpan(pos))) {
                    return;
                }
                int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
                int cell = width / Math.max(1, getSpanCount());
                outRect.left = Math.max(0, (cell - tilePx) / 2);
                outRect.bottom = gapPx;
            }
        };
    }
}
