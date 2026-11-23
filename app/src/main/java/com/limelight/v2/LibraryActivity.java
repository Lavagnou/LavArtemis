package com.limelight.v2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import android.os.Bundle;

import com.limelight.nvstream.http.NvApp;

/** The whole Playnite library of the shown PC ("Tout voir"). */
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
        screen = new LibraryScreen(this, theme, Density.of(getResources().getConfiguration()), new LibraryScreen.Actions() {
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

            @Override
            public void toggleHidden() {
                showHidden = !showHidden;
                refresh();
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
        screen.bind(currentComputer(), currentModel(), showHidden);
        if (!initialFocusDone && currentModel() != null && !container.isInTouchMode()) {
            container.post(() -> initialFocusDone = screen.requestInitialFocus());
        }
    }

    @Override
    protected void onInputChanged() {
        if (screen != null) {
            screen.refreshInput();
        }
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
