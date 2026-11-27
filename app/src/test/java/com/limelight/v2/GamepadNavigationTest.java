package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingRowView;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;
import com.limelight.v2.settings.SettingsStore;
import com.limelight.v2.ui.HintsBar;
import com.limelight.v2.ui.V2Dialogs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * The gamepad rules of 2026-11-27 (audit-manette.md, decisions-design.md « Manette »), played with
 * real keys on the real activities: Réglages move by column and stop at the ends, the hints are
 * never focused, B / Start on the home ask before leaving, Start closes a dialog.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class GamepadNavigationTest {
    private static final String WIDE = "fr-w960dp-h540dp-land-xhdpi";
    private static final String NARROW = "fr-w412dp-h915dp-port-xhdpi";

    @Before
    public void setUp() {
        SettingsActivity.envOverride = SettingsEnv.forTests();
        V2Input.reset(V2Input.Kind.PAD);
    }

    @After
    public void tearDown() {
        SettingsActivity.envOverride = null;
        V2Input.reset(null);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static SettingsActivity settings(String qualifiers, String page) {
        RuntimeEnvironment.setQualifiers(qualifiers);
        Intent i = new Intent(ApplicationProvider.getApplicationContext(), SettingsActivity.class);
        if (page != null) {
            i.putExtra(SettingsActivity.EXTRA_PAGE, page);
        }
        SettingsActivity a = Robolectric.buildActivity(SettingsActivity.class, i).setup().get();
        ShadowLooper.idleMainLooper();
        return a;
    }

    /** A press and release, as the gamepad sends them */
    private static void press(Activity a, int code) {
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        ShadowLooper.idleMainLooper();
    }

    private static void press(Activity a, int code, int times) {
        for (int i = 0; i < times; i++) {
            press(a, code);
        }
    }

    /** Takes the focus as a key would (the test window starts in touch mode) */
    private static void focus(View v) {
        assertTrue("focus " + v, v.requestFocusFromTouch());
        ShadowLooper.idleMainLooper();
    }

    private static View focused(Activity a) {
        // getCurrentFocus() is null under Robolectric: the window's own views say it
        return a.getWindow().getDecorView().findFocus();
    }

    private static List<View> firstStops(SettingsScreen s) {
        List<View> out = new ArrayList<>();
        for (SettingRowView r : s.rows()) {
            if (r.entryStop() != null) {
                out.add(r.entryStop());
            }
        }
        if (s.moreButton() != null) {
            out.add(s.moreButton());
        }
        return out;
    }

    private static SettingRowView row(SettingsScreen s, String key) {
        SettingRowView r = s.rowFor(key);
        assertNotNull(key, r);
        return r;
    }

    // ---- Réglages -------------------------------------------------------------------------------

    @Test
    public void upOnTheFirstRowStaysThere() {
        // R1: Haut sur le premier réglage partait sur « Mode expert »
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        View first = firstStops(a.screen).get(0);
        focus(first);
        press(a, KeyEvent.KEYCODE_DPAD_UP, 3);
        assertSame(first, focused(a));
        assertEquals(SettingsCatalog.VIDEO, a.screen.currentPage());
    }

    @Test
    public void downStaysInTheRowsAndStopsAtTheEnd() {
        // R2 (a): the last row of a page, Down: the page list must not take the focus (nor change the page)
        for (String page : new String[]{SettingsCatalog.AUDIO, SettingsCatalog.PC, SettingsCatalog.VIDEO}) {
            SettingsActivity a = settings(WIDE, page);
            List<View> stops = firstStops(a.screen);
            focus(stops.get(0));
            press(a, KeyEvent.KEYCODE_DPAD_DOWN, stops.size() + 30);
            View last = focused(a);
            assertEquals(page, a.screen.currentPage());
            assertTrue(page + ": still in the rows", SettingsScreen.rowOf(last) != null || last == a.screen.moreButton());
            assertSame(page + ": the last stop", stops.get(stops.size() - 1), last == a.screen.moreButton() ? last : SettingsScreen.rowOf(last).entryStop());
            // And back up to the first, never further
            press(a, KeyEvent.KEYCODE_DPAD_UP, stops.size() + 30);
            assertSame(stops.get(0), focused(a));
        }
    }

    @Test
    public void thePageListStopsAtItsEndsAndRightGoesToTheRows() {
        // R2 (b): Down on the last page stays in the list; Up stops on « Mode expert »
        SettingsActivity a = settings(WIDE, SettingsCatalog.PROFIL);
        focus(a.screen.navFor(SettingsCatalog.PROFIL));
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 20);
        List<SettingsCatalog.Page> pages = SettingsCatalog.visiblePages(a.env, a.screen.isExpert());
        String lastPage = pages.get(pages.size() - 1).id;
        assertSame(a.screen.navFor(lastPage), focused(a));
        // Moving through the list opened each page; the last one is shown
        assertEquals(lastPage, a.screen.currentPage());
        press(a, KeyEvent.KEYCODE_DPAD_UP, 20);
        View expert = focused(a);
        assertNotNull(expert);
        assertFalse("Mode expert is no page", SettingsScreen.rowOf(expert) != null);
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertSame(expert, focused(a));
        // Right: the rows of the page
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertNotNull(SettingsScreen.rowOf(focused(a)));
    }

    @Test
    public void leftAndRightChangeAChoiceThenLeftGoesToThePages() {
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        SettingsStore store = new SettingsStore(a);
        SettingRowView codec = row(a.screen, "video_format");
        SettingDef def = codec.def;
        String before = store.choiceValue(def);
        focus(codec);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        String after = store.choiceValue(def);
        assertNotEquals("Right steps the value", before, after);
        // The codec rebuilds the page's rows: the focus stays on its row
        assertEquals("video_format", SettingsScreen.rowOf(focused(a)).def.key);
        // Back to the first option, then Left at the end of the range: the page list
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 10);
        assertSame(a.screen.navFor(SettingsCatalog.VIDEO), focused(a));
        assertEquals(SettingsCatalog.VIDEO, a.screen.currentPage());
    }

    @Test
    public void aSliderStepsThenLeavesOnTheLeft() {
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        SettingsStore store = new SettingsStore(a);
        SettingRowView bitrate = row(a.screen, SettingsCatalog.KEY_BITRATE);
        int before = store.getInt(SettingsCatalog.KEY_BITRATE);
        focus(bitrate);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertTrue(store.getInt(SettingsCatalog.KEY_BITRATE) < before);
        assertSame(bitrate, focused(a));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 200);
        assertSame(a.screen.navFor(SettingsCatalog.VIDEO), focused(a));
    }

    @Test
    public void aToggleRowGoesLeftToThePagesAndRightNowhere() {
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        SettingRowView toggle = null;
        for (SettingRowView r : a.screen.rows()) {
            if (r.def.type == SettingDef.Type.TOGGLE) {
                toggle = r;
                break;
            }
        }
        assertNotNull(toggle);
        focus(toggle);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertSame(toggle, focused(a));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertSame(a.screen.navFor(SettingsCatalog.VIDEO), focused(a));
    }

    @Test
    public void themeCardsMoveTheFocusAndAApplies() {
        // R10: left / right go from card to card without applying; A applies, the focus stays on the card
        Context app = ApplicationProvider.getApplicationContext();
        V2Theme.saveTheme(app, V2Theme.BRAISE);
        SettingsActivity a = settings(WIDE, SettingsCatalog.APPARENCE);
        SettingRowView themes = row(a.screen, V2Theme.PREF_THEME);
        List<View> cards = themes.focusStops();
        assertEquals(3, cards.size());
        assertFalse("the row itself is no stop", themes.isFocusable());
        focus(themes.entryStop());
        assertSame(cards.get(0), focused(a));
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 2);
        assertSame(cards.get(2), focused(a));
        assertEquals(V2Theme.BRAISE, V2Theme.load(a).id);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertSame(cards.get(2), focused(a));
        press(a, KeyEvent.KEYCODE_BUTTON_A);
        assertEquals(V2Theme.LUDIQUE, V2Theme.load(a).id);
        // The screen was rebuilt in the new theme: the focus is on the same card of the new row
        SettingRowView again = row(a.screen, V2Theme.PREF_THEME);
        assertSame(again.focusStops().get(2), focused(a));
        // Left from the first card: the page list
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 3);
        assertSame(a.screen.navFor(SettingsCatalog.APPARENCE), focused(a));
        V2Theme.saveTheme(app, V2Theme.BRAISE);
    }

    @Test
    public void backGoesRowsThenPagesThenLeaves() {
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        focus(firstStops(a.screen).get(0));
        press(a, KeyEvent.KEYCODE_BUTTON_B);
        assertSame(a.screen.navFor(SettingsCatalog.VIDEO), focused(a));
        assertFalse(a.isFinishing());
        press(a, KeyEvent.KEYCODE_BUTTON_B);
        assertTrue(a.isFinishing());
    }

    @Test
    public void narrowUpOnTheFirstRowStaysOffTheBackButton() {
        // R3
        SettingsActivity a = settings(NARROW, SettingsCatalog.VIDEO);
        assertTrue(a.screen.narrow);
        View first = firstStops(a.screen).get(0);
        focus(first);
        press(a, KeyEvent.KEYCODE_DPAD_UP, 3);
        assertSame(first, focused(a));
    }

    @Test
    public void narrowShouldersShowTheNextPageAndTheHints() {
        // R8: LB / RB show the rows of the page next to it; the hints bar is there
        SettingsActivity a = settings(NARROW, SettingsCatalog.VIDEO);
        focus(firstStops(a.screen).get(0));
        List<SettingsCatalog.Page> pages = SettingsCatalog.visiblePages(a.env, a.screen.isExpert());
        String next = SettingsCatalog.stepPage(pages, SettingsCatalog.VIDEO, 1);
        press(a, KeyEvent.KEYCODE_BUTTON_R1);
        assertEquals(next, a.screen.currentPage());
        assertTrue(a.screen.isShowingRows());
        assertNotNull(SettingsScreen.rowOf(focused(a)));
        HintsBar hints = find(a.screen.getView(), HintsBar.class);
        assertNotNull(hints);
        assertEquals(View.VISIBLE, hints.getVisibility());
        // From the page list too
        press(a, KeyEvent.KEYCODE_BUTTON_B);
        assertFalse(a.screen.isShowingRows());
        press(a, KeyEvent.KEYCODE_BUTTON_L1);
        assertTrue(a.screen.isShowingRows());
        assertEquals(SettingsCatalog.VIDEO, a.screen.currentPage());
    }

    @Test
    public void hintsSayOpenOnThePagesAndChangeOnTheRows() {
        // R11
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        HintsBar hints = find(a.screen.getView(), HintsBar.class);
        focus(a.screen.navFor(SettingsCatalog.VIDEO));
        assertTrue(texts(hints).contains(a.getString(R.string.v2_hint_open)));
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(texts(hints).contains(a.getString(R.string.v2_details_change)));
        assertFalse(texts(hints).contains(a.getString(R.string.v2_hint_open)));
    }

    // ---- hints, home, dialogs -------------------------------------------------------------------

    @Test
    public void hintsAreNeverFocusable() {
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        HintsBar hints = find(a.screen.getView(), HintsBar.class);
        assertTrue(hints.getChildCount() > 0);
        assertNoFocusable(hints);
        // A dialog's « B Annuler » has an action: still not focusable
        Dialog d = V2Dialogs.confirm(a, V2Theme.load(a), "Titre", "Texte", "Oui", "Annuler", null, null);
        ShadowLooper.idleMainLooper();
        HintsBar dh = find(d.findViewById(android.R.id.content), HintsBar.class);
        if (dh != null) {
            assertNoFocusable(dh);
        }
        d.dismiss();
    }

    @Test
    public void startClosesADialog() {
        // O3: Start is Retour in a dialog too
        SettingsActivity a = settings(WIDE, SettingsCatalog.VIDEO);
        Dialog d = V2Dialogs.confirm(a, V2Theme.load(a), "Titre", "Texte", "Oui", "Annuler", null, null);
        ShadowLooper.idleMainLooper();
        assertTrue(d.isShowing());
        d.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START));
        d.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_START));
        ShadowLooper.idleMainLooper();
        assertFalse(d.isShowing());
    }

    @Test
    public void bOnTheHomeAsksBeforeLeaving() {
        // A1: B / Start ask « Quitter LavArtemis ? » (prototype v113 appQuit); the system back still leaves
        RuntimeEnvironment.setQualifiers(WIDE);
        HomeActivity a = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        press(a, KeyEvent.KEYCODE_BUTTON_B);
        assertFalse(a.isFinishing());
        Dialog d = ShadowDialog.getLatestDialog();
        assertNotNull(d);
        assertTrue(d.isShowing());
        List<String> shown = texts(d.findViewById(android.R.id.content));
        assertTrue(shown.toString(), shown.contains("Quitter LavArtemis ?"));
        assertTrue(shown.toString(), shown.contains("Quitter LavArtemis"));
        assertTrue(shown.toString(), shown.contains(a.getString(R.string.v2_cancel)));
        assertTrue("Quitter LavArtemis comes before Annuler",
                shown.indexOf("Quitter LavArtemis") < shown.indexOf(a.getString(R.string.v2_cancel)));
        d.dismiss();
        press(a, KeyEvent.KEYCODE_BUTTON_START);
        assertFalse(a.isFinishing());
        ShadowDialog.getLatestDialog().dismiss();
        a.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(a.isFinishing());
    }

    @Test
    public void aRebuiltHomeKeepsTheFocusOnReglages() {
        // A6: back from Réglages with another theme (or a rotation), the focus stays on « Réglages »
        RuntimeEnvironment.setQualifiers(WIDE);
        HomeActivity a = Robolectric.buildActivity(HomeActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        focus(a.focusRows().get(0).get(1));
        a.rebuild();
        ShadowLooper.idleMainLooper();
        assertSame(a.focusRows().get(0).get(1), focused(a));
    }

    @Test
    public void mesPcOpensOnThePcTheHomeShows() {
        // M5: the card of the PC shown on the home, not the first card
        RuntimeEnvironment.setQualifiers(WIDE);
        Context app = ApplicationProvider.getApplicationContext();
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(V2HostActivity.PREF_LAST_HOST, "uuid-SALON").commit();
        HostsActivity a = Robolectric.buildActivity(HostsActivity.class).setup().get();
        for (String name : new String[]{"ATLAS-PC", "SALON"}) {
            com.limelight.nvstream.http.ComputerDetails d = new com.limelight.nvstream.http.ComputerDetails();
            d.uuid = "uuid-" + name;
            d.name = name;
            d.state = com.limelight.nvstream.http.ComputerDetails.State.ONLINE;
            d.pairState = com.limelight.nvstream.http.PairingManager.PairState.PAIRED;
            d.activeAddress = new com.limelight.nvstream.http.ComputerDetails.AddressTuple("192.168.1.24", 47989);
            a.computers.put(d.uuid, d);
        }
        a.refresh();
        ShadowLooper.idleMainLooper();
        List<View> cards = a.focusRows().get(0);
        // Out of touch mode, as the first key does, then the screen's starting point
        focus(cards.get(0));
        assertTrue(a.onV2InitialFocus());
        assertSame(cards.get(1), focused(a));
    }

    @Test
    public void ludiqueDockMovesRowByRowAndStaysOnOneRow() {
        // G1: one row, up / down do nothing; two rows (5 + 4, centred), the closest tile
        int[] one = {9};
        assertEquals(3, V2GameMenu.Dock.target(one, 3, 0, 1));
        assertEquals(3, V2GameMenu.Dock.target(one, 3, 0, -1));
        assertEquals(4, V2GameMenu.Dock.target(one, 3, 1, 0));
        assertEquals(8, V2GameMenu.Dock.target(one, 8, 1, 0));
        int[] two = {5, 4};
        assertEquals(5, V2GameMenu.Dock.target(two, 0, 0, 1));
        assertEquals(8, V2GameMenu.Dock.target(two, 4, 0, 1));
        assertEquals(6, V2GameMenu.Dock.target(two, 2, 0, 1));
        assertEquals(0, V2GameMenu.Dock.target(two, 5, 0, -1));
        assertEquals(3, V2GameMenu.Dock.target(two, 8, 0, -1));
        // Never into the next row, nothing past the last one
        assertEquals(4, V2GameMenu.Dock.target(two, 4, 1, 0));
        assertEquals(5, V2GameMenu.Dock.target(two, 5, -1, 0));
        assertEquals(7, V2GameMenu.Dock.target(two, 7, 0, 1));
    }

    // ---- views ----------------------------------------------------------------------------------

    private static <T extends View> T find(View v, Class<T> type) {
        if (type.isInstance(v)) {
            return type.cast(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T t = find(g.getChildAt(i), type);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static void assertNoFocusable(View v) {
        assertFalse("hint focusable: " + v, v.isFocusable());
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                assertNoFocusable(g.getChildAt(i));
            }
        }
    }

    private static List<String> texts(View v) {
        List<String> out = new ArrayList<>();
        collect(v, out);
        return out;
    }

    private static void collect(View v, List<String> out) {
        if (v instanceof android.widget.TextView) {
            out.add(((android.widget.TextView) v).getText().toString());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out);
            }
        }
    }
}
