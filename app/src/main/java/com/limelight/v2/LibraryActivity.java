package com.limelight.v2;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.nvstream.http.NvApp;

/** The whole Playnite library of the shown PC ("Tout voir"), prototype « library ». */
public class LibraryActivity extends V2HostActivity {
    private FrameLayout container;
    private LibraryScreen screen;
    private boolean initialFocusDone;

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
        Configuration cfg = getResources().getConfiguration();
        screen = new LibraryScreen(this, theme, HomeLayout.forSize(cfg.screenWidthDp, cfg.screenHeightDp), new LibraryScreen.Actions() {
            @Override
            public void back() {
                finish();
            }

            @Override
            public void launch(NvApp app) {
                LibraryActivity.this.launch(app);
            }

            @Override
            public void options(NvApp app) {
                showOptions(app);
            }
        }, covers);
        container.removeAllViews();
        container.addView(screen.getView(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        initialFocusDone = false;
        refresh();
    }

    @Override
    protected void onCoversChanged() {
        // The library shows one PC: a new cover source means a rebuilt grid
        rebuild();
    }

    @Override
    protected void refresh() {
        if (screen == null) {
            return;
        }
        HomeModel m = currentModel();
        screen.bind(currentComputer(), m, System.currentTimeMillis() / 1000);
        if (!initialFocusDone && m != null && !container.isInTouchMode()) {
            container.post(() -> initialFocusDone = screen.requestInitialFocus());
        }
    }

    @Override
    protected void setHidden(NvApp app, boolean hide) {
        super.setHidden(app, hide);
        if (screen != null) {
            screen.afterHideToggle();
        }
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
    }

    @Override
    protected boolean onV2Move(View focused, int dx, int dy) {
        return screen != null && screen.move(focused, dx, dy);
    }

    @Override
    protected boolean shoulderKeysEnabled() {
        return true;
    }

    @Override
    protected boolean onV2Shoulder(int delta) {
        if (screen != null) {
            screen.shiftFilter(delta);
        }
        return true;
    }

    @Override
    protected boolean onV2Options(View focused) {
        NvApp app = screen == null ? null : screen.appFor(focused);
        if (app != null) {
            showOptions(app);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onV2InitialFocus() {
        return screen != null && screen.requestInitialFocus();
    }
}
