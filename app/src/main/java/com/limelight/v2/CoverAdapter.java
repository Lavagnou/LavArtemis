package com.limelight.v2;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.nvstream.http.NvApp;
import com.limelight.v2.ui.CoverTile;
import com.limelight.v2.ui.SeeAllTile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Covers of the home's "Récents" row and of the library grid, plus optional full-width header and
 * footer rows (the library's title and its « jeux masqués » button) and a trailing "Tout voir" tile.
 */
public final class CoverAdapter extends RecyclerView.Adapter<CoverAdapter.Holder> {
    public interface Listener {
        void onLaunch(NvApp app);

        void onOptions(NvApp app);

        void onSeeAll();

        /** A tile got or lost the focus (pad/keys): its name is shown in the section title */
        void onFocusedApp(NvApp app);
    }

    static final int TYPE_TILE = 0;
    static final int TYPE_SEE_ALL = 1;
    static final int TYPE_HEADER = 2;
    static final int TYPE_FOOTER = 3;

    private final Context context;
    private final V2Theme theme;
    private final float tileW, tileH;
    private final Listener listener;
    private CoverSource covers;
    private View header, footer;
    private List<NvApp> apps = Collections.emptyList();
    private HomeModel model;
    private boolean seeAll;
    private String signature = "";
    private final String liveLabel;

    public CoverAdapter(Context context, V2Theme theme, float tileW, float tileH, CoverSource covers, Listener listener) {
        this.context = context;
        this.theme = theme;
        this.tileW = tileW;
        this.tileH = tileH;
        this.covers = covers;
        this.listener = listener;
        this.liveLabel = context.getString(com.limelight.R.string.v2_live);
        setHasStableIds(true);
    }

    public void setCoverSource(CoverSource covers) {
        this.covers = covers;
        notifyDataSetChanged();
    }

    public void setHeader(View header) {
        this.header = header;
    }

    public void setFooter(View footer) {
        this.footer = footer;
    }

    /** Only rebinds when what is shown changed, so polling keeps the focus and the scroll */
    public void submit(HomeModel model, List<NvApp> apps, boolean seeAll) {
        StringBuilder sb = new StringBuilder();
        sb.append(seeAll).append('|').append(footer != null && footer.getVisibility() == View.VISIBLE).append('|');
        for (NvApp a : apps) {
            sb.append(a.getAppId()).append(':').append(a.getAppName()).append(':')
                    .append(model.isRunning(a)).append(model.isDimmed(a)).append(model.isHidden(a)).append(';');
        }
        this.model = model;
        String sig = sb.toString();
        if (sig.equals(signature)) {
            return;
        }
        signature = sig;
        this.apps = new ArrayList<>(apps);
        this.seeAll = seeAll;
        notifyDataSetChanged();
    }

    public void forceRefresh() {
        signature = "";
        notifyDataSetChanged();
    }

    private int headerCount() {
        return header != null ? 1 : 0;
    }

    public boolean isFullSpan(int position) {
        int t = getItemViewType(position);
        return t == TYPE_HEADER || t == TYPE_FOOTER;
    }

    @Override
    public int getItemCount() {
        return headerCount() + apps.size() + (seeAll ? 1 : 0) + (footer != null ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (header != null && position == 0) {
            return TYPE_HEADER;
        }
        int p = position - headerCount();
        if (p < apps.size()) {
            return TYPE_TILE;
        }
        if (seeAll && p == apps.size()) {
            return TYPE_SEE_ALL;
        }
        return TYPE_FOOTER;
    }

    @Override
    public long getItemId(int position) {
        switch (getItemViewType(position)) {
            case TYPE_HEADER:
                return -2;
            case TYPE_FOOTER:
                return -3;
            case TYPE_SEE_ALL:
                return -1;
            default:
                return apps.get(position - headerCount()).getAppId() & 0xFFFFFFFFL;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        switch (viewType) {
            case TYPE_HEADER:
                v = detach(header);
                break;
            case TYPE_FOOTER:
                v = detach(footer);
                break;
            case TYPE_SEE_ALL:
                v = new SeeAllTile(context, theme, tileW, tileH);
                v.setLayoutParams(new RecyclerView.LayoutParams(com.limelight.v2.ui.Ui.dp(context, tileW), com.limelight.v2.ui.Ui.dp(context, tileH)));
                v.setOnClickListener(x -> listener.onSeeAll());
                break;
            default:
                CoverTile tile = new CoverTile(context, theme, tileW, tileH, liveLabel);
                tile.setLayoutParams(new RecyclerView.LayoutParams(com.limelight.v2.ui.Ui.dp(context, tileW), com.limelight.v2.ui.Ui.dp(context, tileH)));
                v = tile;
                break;
        }
        Holder h = new Holder(v);
        if (viewType == TYPE_HEADER || viewType == TYPE_FOOTER) {
            h.setIsRecyclable(false);
        }
        return h;
    }

    private static View detach(View v) {
        if (v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        if (!(v.getLayoutParams() instanceof RecyclerView.LayoutParams)) {
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return v;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        if (getItemViewType(position) != TYPE_TILE) {
            return;
        }
        final NvApp app = apps.get(position - headerCount());
        final CoverTile tile = (CoverTile) holder.itemView;
        tile.setTag(app);
        tile.bind(app.getAppId(), app.getAppName(), model.isRunning(app), model.isDimmed(app), model.isHidden(app));
        tile.refreshInput();
        tile.setOnClickListener(v -> listener.onLaunch(app));
        tile.setOnLongClickListener(v -> {
            listener.onOptions(app);
            return true;
        });
        tile.setOnMoreClickListener(v -> listener.onOptions(app));
        tile.setOnFocusChangeListener((v, has) -> listener.onFocusedApp(has ? app : null));
        if (covers != null) {
            if (!tile.hasCover()) {
                tile.setCover(covers.peek(app));
            }
            if (!tile.hasCover()) {
                covers.load(app, (id, bmp) -> {
                    if (tile.getAppId() == id) {
                        tile.setCover(bmp);
                    }
                });
            }
        }
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
