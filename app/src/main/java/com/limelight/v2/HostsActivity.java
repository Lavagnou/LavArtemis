package com.limelight.v2;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.v2.ui.IconButton;
import com.limelight.v2.ui.PillButton;
import com.limelight.v2.ui.Ui;
import com.limelight.v2.ui.V2Frame;

import java.util.List;
import java.util.Locale;

/**
 * « Mes PC » — placeholder of phase 1: the paired PCs (tap one to show it on the home) and a way
 * to the legacy PC list, which still adds, pairs, wakes and forgets PCs.
 * TODO phase 2: the real page (cards per PC, Ajouter un PC, PIN pairing, Benchmark, Renommer, Oublier).
 */
public class HostsActivity extends V2HostActivity {
    private FrameLayout container;
    private LinearLayout list;
    private View firstFocusable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        container = new FrameLayout(this);
        setContentView(container);
        rebuild();
    }

    @Override
    protected void rebuild() {
        if (container == null) {
            return;
        }
        Density d = Density.of(getResources().getConfiguration());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, d.side);
        page.setPadding(side, Ui.dp(this, d.top), side, Ui.dp(this, 24));
        scroll.addView(page);
        Ui.noClip(scroll, page);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.noClip(header);
        IconButton back = new IconButton(this, theme, R.drawable.v2_ic_chevron_left, 48, 44, 20, theme.surface, true);
        back.setContentDescription(getString(R.string.v2_back));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView title = Ui.display(this, theme, d.compact ? 22 : 26, theme.fg);
        title.setText(theme.displayText(getString(R.string.v2_hosts_title)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginStart(Ui.dp(this, 14));
        header.addView(title, tlp);
        page.addView(header);

        TextView note = Ui.text(this, theme, 15, 400, theme.muted);
        note.setMaxLines(Integer.MAX_VALUE);
        note.setText(R.string.v2_hosts_todo);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(this, 14);
        page.addView(note, nlp);

        PillButton legacy = new PillButton(this, theme, PillButton.Style.PRIMARY, R.drawable.v2_ic_monitor,
                getString(R.string.v2_hosts_open_legacy), 52, 17);
        legacy.setOnClickListener(v -> {
            Intent i = new Intent(this, PcView.class);
            i.putExtra(PcView.EXTRA_LEGACY_UI, true);
            startActivity(i);
        });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(this, 16);
        page.addView(legacy, llp);
        firstFocusable = legacy;

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(list);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 22);
        page.addView(list, lp);

        container.removeAllViews();
        container.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    @Override
    protected void onCoversChanged() {
    }

    @Override
    protected void refresh() {
        if (list == null) {
            return;
        }
        List<ComputerDetails> hosts = safeHosts();
        list.removeAllViews();
        for (final ComputerDetails d : hosts) {
            V2Frame row = new V2Frame(this, theme);
            row.setFocusable(true);
            row.setClickable(true);
            row.setRing(theme.r, 4, theme.accent);
            row.setBackground(Ui.roundRect(theme.surface, Ui.dpf(this, theme.r), Ui.dp(this, theme.border), theme.borderColor));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(this, 16);
            texts.setPadding(pad, pad, pad, pad);
            TextView name = Ui.text(this, theme, 17, 700, theme.fg);
            name.setText(d.name == null ? "" : d.name.toUpperCase(Locale.ROOT));
            texts.addView(name);
            TextView status = Ui.text(this, theme, 14, 500, theme.muted);
            boolean online = HostList.isOnline(d);
            String s = getString(online ? (d.runningGameId != 0 ? R.string.v2_status_session : R.string.v2_status_online)
                    : (d.state == ComputerDetails.State.OFFLINE ? R.string.v2_status_offline : R.string.v2_status_unknown));
            if (d.uuid.equalsIgnoreCase(currentUuid)) {
                s += "  ·  " + getString(R.string.v2_hosts_current);
            }
            status.setText(s);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(this, 4);
            texts.addView(status, slp);
            row.addView(texts);
            row.setContentDescription(d.name);
            row.setOnClickListener(v -> {
                selectHost(d.uuid, false);
                finish();
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = Ui.dp(this, 12);
            list.addView(row, rlp);
        }
    }

    @Override
    protected boolean onV2InitialFocus() {
        return firstFocusable != null && firstFocusable.requestFocus();
    }
}
