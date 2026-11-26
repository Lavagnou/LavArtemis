package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ludique dock of the in-game menu, laid out with the real fonts at the sizes Android runs it
 * (phone portrait and landscape, console, tablet): a word of a tile is never cut in two, nothing is
 * ellipsized, nothing is clipped by its tile and every tile is on the screen. The portrait phone
 * cut « Repre/ndre » and showed « Retour à Plau… » under Quitter (2026-11-26). DockFit's maths:
 * DockFitTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameMenuDockTest {
    private static final int DENSITY = 2; // xhdpi

    @After
    public void tearDown() {
        V2Input.reset(null);
    }

    @Test
    public void portraitPhone() {
        check(412, 915);
        check(360, 800);
    }

    @Test
    public void landscapePhone() {
        check(915, 412);
        check(800, 360);
    }

    @Test
    public void console() {
        check(960, 540);
    }

    @Test
    public void tablet() {
        check(1280, 800);
        check(1280, 720);
    }

    private static void check(int wDp, int hDp) {
        for (boolean dark : new boolean[]{true, false}) {
            for (boolean playnite : new boolean[]{true, false}) {
                for (V2Input.Kind input : new V2Input.Kind[]{V2Input.Kind.TOUCH, V2Input.Kind.PAD}) {
                    check(wDp, hDp, dark, playnite, input);
                }
            }
        }
    }

    private static void check(int wDp, int hDp, boolean dark, boolean playnite, V2Input.Kind input) {
        RuntimeEnvironment.setQualifiers("fr-w" + wDp + "dp-h" + hDp + "dp-" + (wDp > hDp ? "land" : "port") + "-xhdpi");
        V2Input.reset(input);
        Context ctx = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_LavV2);
        String title = playnite ? "Hades II" : ctx.getString(R.string.v2_desktop);
        String via = ctx.getString(playnite ? R.string.v2_via_inside : R.string.v2_via_desktop);
        View menu = V2GameMenu.build(ctx, V2Theme.of("ludique", dark), HomeLayout.forSize(wDp, hDp), title, "ATLAS-PC", via,
                HomeScreenshotTest.menuTiles(ctx, playnite, title), null);
        int w = wDp * DENSITY, h = hDp * DENSITY;
        for (int pass = 0; pass < 2; pass++) {
            menu.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            menu.layout(0, 0, w, h);
        }
        String where = wDp + "x" + hDp + (dark ? " dark " : " light ") + (playnite ? "playnite " : "bureau ") + input;
        int count = HomeScreenshotTest.menuTiles(ctx, playnite, title).size();
        int found = 0;
        for (int i = 0; i < count; i++) {
            View tile = V2GameMenu.tile(menu, i);
            assertNotNull(where + ": tile " + i, tile);
            found++;
            Rect r = new Rect(0, 0, tile.getWidth(), tile.getHeight());
            ((ViewGroup) menu).offsetDescendantRectToMyCoords(tile, r);
            assertTrue(where + ": tile " + i + " off the screen " + r, r.left >= 0 && r.top >= 0 && r.right <= w && r.bottom <= h);
            List<TextView> texts = new ArrayList<>();
            texts(tile, texts);
            for (TextView t : texts) {
                checkText(where + ": tile " + i, tile, t);
            }
        }
        assertEquals(where, count, found);
    }

    /** No ellipsis, a line ends on a space only, the text is whole inside its view and the view inside its tile */
    private static void checkText(String where, View tile, TextView t) {
        String s = t.getText().toString();
        if (s.isEmpty() || t.getVisibility() != View.VISIBLE) {
            return;
        }
        Layout layout = t.getLayout();
        assertNotNull(where + " « " + s + " »", layout);
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(where + " « " + s + " » ellipsized", 0, layout.getEllipsisCount(line));
            int end = layout.getLineEnd(line);
            if (end < s.length()) {
                assertTrue(where + " « " + s + " » cut in a word: « " + s.substring(0, end) + " / " + s.substring(end) + " »",
                        Character.isWhitespace(s.charAt(end - 1)) || Character.isWhitespace(s.charAt(end)));
            }
            assertTrue(where + " « " + s + " » wider than its view",
                    layout.getLineWidth(line) <= t.getWidth() - t.getTotalPaddingLeft() - t.getTotalPaddingRight() + 1);
        }
        assertEquals(where + " « " + s + " » lines", s.length(), layout.getLineEnd(layout.getLineCount() - 1));
        assertTrue(where + " « " + s + " » taller than its view (" + layout.getHeight() + " > " + t.getHeight() + ")",
                layout.getHeight() <= t.getHeight() - t.getTotalPaddingTop() - t.getTotalPaddingBottom() + 1);
        Rect r = new Rect(0, 0, t.getWidth(), t.getHeight());
        ((ViewGroup) tile).offsetDescendantRectToMyCoords(t, r);
        assertTrue(where + " « " + s + " » clipped by its tile " + r + " in " + tile.getWidth() + "x" + tile.getHeight(),
                r.left >= 0 && r.top >= 0 && r.right <= tile.getWidth() && r.bottom <= tile.getHeight());
    }

    private static void texts(View v, List<TextView> out) {
        if (v instanceof TextView) {
            out.add((TextView) v);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                texts(g.getChildAt(i), out);
            }
        }
    }
}
