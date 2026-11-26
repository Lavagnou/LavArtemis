package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;

/**
 * The in-game menu: every tile has the same size (rule of the user, 2026-11-26). Braise: every row
 * of the grid as tall as the tallest tile (« Quitter Red Dead Redemption 2 » and its sub-line),
 * never one row taller than another; Affiche: every row of the list as tall as the others; Ludique:
 * every tile of the dock as wide and as tall as the others, on one row or on two (portrait). Laid out
 * with the real fonts at the sizes Android runs it, a Playnite game and the Bureau.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameMenuTileSizeTest {
    private static final int DENSITY = 2; // xhdpi
    /** A game inside Playnite, short or long enough to take two lines (with its sub-line), and the Bureau (null) */
    private static final String[] GAMES = {"Hades II", "Red Dead Redemption 2", "Warhammer 40,000: Space Marine 2", null};
    private static final int[][] SIZES = {{915, 412}, {800, 360}, {412, 915}, {360, 800}, {960, 540}, {1280, 800}, {1280, 720}};

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    @Test
    public void braise() {
        check("braise");
    }

    @Test
    public void affiche() {
        check("affiche");
    }

    @Test
    public void ludique() {
        check("ludique");
    }

    /** Ludique in portrait takes two rows (5 + 4): the tiles of both rows are still one size */
    @Test
    public void ludiquePortraitHasTwoRows() {
        RuntimeEnvironment.setQualifiers("fr-w412dp-h915dp-port-xhdpi");
        View menu = build("ludique", true, true, "Red Dead Redemption 2", 412, 915);
        List<V2GameMenu.Tile> tiles = HomeScreenshotTest.menuTiles(ctx(), true, "Red Dead Redemption 2");
        assertTrue("one row only in portrait", top(menu, V2GameMenu.tile(menu, tiles.size() - 1)) > top(menu, V2GameMenu.tile(menu, 0)));
    }

    private static int top(View root, View v) {
        android.graphics.Rect r = new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight());
        ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        return r.top;
    }

    private static Context ctx() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
    }

    private static View build(String theme, boolean dark, boolean playnite, String game, int wDp, int hDp) {
        Context ctx = ctx();
        String title = playnite ? game : ctx.getString(R.string.v2_desktop);
        String via = ctx.getString(playnite ? R.string.v2_via_inside : R.string.v2_via_desktop);
        View menu = V2GameMenu.build(ctx, V2Theme.of(theme, dark), HomeLayout.forSize(wDp, hDp), title, "ATLAS-PC", via,
                HomeScreenshotTest.menuTiles(ctx, playnite, title), null);
        int w = wDp * DENSITY, h = hDp * DENSITY;
        for (int pass = 0; pass < 2; pass++) {
            menu.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            menu.layout(0, 0, w, h);
        }
        return menu;
    }

    private static void check(String theme) {
        for (int[] s : SIZES) {
            for (boolean dark : new boolean[]{true, false}) {
                for (String game : GAMES) {
                    boolean playnite = game != null;
                    for (V2Input.Kind input : new V2Input.Kind[]{V2Input.Kind.TOUCH, V2Input.Kind.PAD}) {
                        RuntimeEnvironment.setQualifiers("fr-w" + s[0] + "dp-h" + s[1] + "dp-" + (s[0] > s[1] ? "land" : "port") + "-xhdpi");
                        V2Input.reset(input);
                        View menu = build(theme, dark, playnite, game, s[0], s[1]);
                        int count = HomeScreenshotTest.menuTiles(ctx(), playnite, game).size();
                        String where = theme + " " + s[0] + "x" + s[1] + (dark ? " dark " : " light ")
                                + (playnite ? "playnite « " + game + " » " : "bureau ") + input;
                        View first = V2GameMenu.tile(menu, 0);
                        assertNotNull(where, first);
                        for (int i = 1; i < count; i++) {
                            View t = V2GameMenu.tile(menu, i);
                            assertNotNull(where + ": tile " + i, t);
                            assertEquals(where + ": height of tile " + i + " (« " + t.getContentDescription() + " »)",
                                    first.getHeight(), t.getHeight());
                            // Braise: three weighted columns, a pixel of rounding between them at most
                            assertTrue(where + ": width of tile " + i + " (« " + t.getContentDescription() + " ») "
                                    + t.getWidth() + " vs " + first.getWidth(), Math.abs(first.getWidth() - t.getWidth()) <= 1);
                        }
                    }
                }
            }
        }
    }
}
