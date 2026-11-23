package com.limelight.v2;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.v2.ui.FitGridLayoutManager;
import com.limelight.v2.ui.IconButton;
import com.limelight.v2.ui.PillButton;
import com.limelight.v2.ui.Ui;

import java.util.Locale;

/**
 * The whole Playnite library of one PC ("Tout voir" from the home): a grid of the same tiles,
 * the same options menu, and the « jeux masqués » switch under it.
 */
public final class LibraryScreen {
    public interface Actions {
        void back();

        void launch(NvApp app);

        void options(NvApp app);

        void toggleHidden();
    }

    private final Context ctx;
    private final V2Theme theme;
    private final Density d;
    private final FrameLayout root;
    private final RecyclerView grid;
    private final CoverAdapter adapter;
    private final TextView subtitle;
    private final TextView emptyText;
    private final PillButton hiddenButton;
    private final LinearLayout footer;
    private final float tileW, tileH;
    private ComputerDetails host;
    private HomeModel model;
    private NvApp focusedApp;
    private final IconButton backButton;

    public LibraryScreen(Context context, V2Theme theme, Density density, Actions actions, CoverSource covers) {
        this.ctx = context;
        this.theme = theme;
        this.d = density;
        tileW = d.compact ? 96 : 116;
        tileH = d.compact ? 144 : 174;

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.bg);

        // Header row (inside the grid, full width, so it scrolls away like the page)
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, Ui.dp(context, d.top), 0, Ui.dp(context, 14));
        Ui.noClip(header);
        backButton = new IconButton(context, theme, R.drawable.v2_ic_chevron_left, 48, 44, 20, theme.surface, true);
        backButton.setContentDescription(context.getString(R.string.v2_back));
        backButton.setOnClickListener(v -> actions.back());
        header.addView(backButton, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(context, 14));
        header.addView(titles, tlp);
        TextView title = Ui.display(context, theme, d.compact ? 22 : 26, theme.fg);
        title.setText(theme.displayText(context.getString(R.string.v2_library)));
        titles.addView(title);
        subtitle = Ui.text(context, theme, 13, 500, theme.muted);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(context, 4);
        titles.addView(subtitle, slp);

        footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(0, Ui.dp(context, 22), 0, Ui.dp(context, 24));
        Ui.noClip(footer);
        emptyText = Ui.text(context, theme, 16, 400, theme.muted);
        emptyText.setMaxLines(Integer.MAX_VALUE);
        emptyText.setText(R.string.v2_library_empty);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.bottomMargin = Ui.dp(context, 16);
        footer.addView(emptyText, elp);
        hiddenButton = new PillButton(context, theme, PillButton.Style.SECONDARY, R.drawable.v2_ic_eye, "", 48, 16);
        hiddenButton.setOnClickListener(v -> actions.toggleHidden());
        footer.addView(hiddenButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new RecyclerView(context);
        grid.setItemAnimator(null);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int side = Ui.dp(context, d.side);
        grid.setPadding(side, 0, side, 0);
        Ui.noClip(grid, root);
        final int gap = Ui.dp(context, d.coverGap);
        final CoverAdapter[] holder = new CoverAdapter[1];
        FitGridLayoutManager.FullSpan fullSpan = position -> holder[0] != null && holder[0].isFullSpan(position);
        FitGridLayoutManager glm = new FitGridLayoutManager(context, Ui.dp(context, tileW), gap, fullSpan);
        grid.setLayoutManager(glm);
        grid.addItemDecoration(glm.decoration(fullSpan));
        adapter = new CoverAdapter(context, theme, tileW, tileH, covers, new CoverAdapter.Listener() {
            @Override
            public void onLaunch(NvApp app) {
                actions.launch(app);
            }

            @Override
            public void onOptions(NvApp app) {
                actions.options(app);
            }

            @Override
            public void onSeeAll() {
            }

            @Override
            public void onFocusedApp(NvApp app) {
                focusedApp = app;
                updateSubtitle();
            }
        });
        holder[0] = adapter;
        adapter.setHeader(header);
        adapter.setFooter(footer);
        grid.setAdapter(adapter);
        root.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public View getView() {
        return root;
    }

    public void bind(ComputerDetails host, HomeModel model, boolean showHidden) {
        this.host = host;
        this.model = model;
        if (model == null) {
            adapter.submit(HomeModel.build(null, 0, null, false, null, false), java.util.Collections.<NvApp>emptyList(), false);
            emptyText.setVisibility(View.GONE);
            hiddenButton.setVisibility(View.GONE);
            updateSubtitle();
            return;
        }
        Ui.setVisible(emptyText, model.games.isEmpty() && model.hiddenCount == 0);
        Ui.setVisible(hiddenButton, model.hiddenCount > 0);
        hiddenButton.setIcon(showHidden ? R.drawable.v2_ic_eye_off : R.drawable.v2_ic_eye);
        if (showHidden) {
            hiddenButton.setText(ctx.getString(R.string.v2_hide_hidden));
        } else {
            hiddenButton.setText(model.hiddenCount > 1 ? ctx.getString(R.string.v2_show_hidden, model.hiddenCount)
                    : ctx.getString(R.string.v2_show_hidden_one));
        }
        adapter.submit(model, model.games, false);
        updateSubtitle();
    }

    private void updateSubtitle() {
        if (host == null) {
            subtitle.setText("");
            return;
        }
        String name = host.name == null ? "" : host.name.toUpperCase(Locale.ROOT);
        int count = model == null ? 0 : model.games.size();
        String s = count == 1 ? ctx.getString(R.string.v2_library_count_one, name)
                : ctx.getString(R.string.v2_library_count, name, count);
        if (focusedApp != null && V2Input.showFocus(ctx)) {
            s += "  —  " + focusedApp.getAppName();
        }
        subtitle.setText(s);
    }

    public void refreshInput() {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        updateSubtitle();
        grid.invalidate();
    }

    public boolean requestInitialFocus() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            if (c.getTag() instanceof NvApp && c.requestFocus()) {
                return true;
            }
        }
        return backButton.requestFocus();
    }

    public NvApp appFor(View focused) {
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getTag() instanceof NvApp) {
                return (NvApp) v.getTag();
            }
            if (v == root) {
                break;
            }
        }
        return null;
    }
}
