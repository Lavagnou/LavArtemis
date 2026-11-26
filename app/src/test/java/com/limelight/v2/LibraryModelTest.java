package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.limelight.nvstream.http.NvApp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** The library page: its filters, their lists, the Affiche window and « joué hier » (prototype library). */
public class LibraryModelTest {
    private static NvApp game(String name, int id, boolean installed, long last) {
        NvApp a = new NvApp(name, "u" + id, id, false);
        a.setPlayniteGame(true);
        a.setPlayniteInstalled(installed);
        a.setPlayniteLastActivity(last);
        return a;
    }

    private static List<NvApp> apps() {
        List<NvApp> l = new ArrayList<>();
        l.add(new NvApp("Desktop", "d", 1, false));
        l.add(game("Old", 100, false, 1000));
        l.add(game("Never", 101, true, 0));
        l.add(game("Recent", 102, true, 5000));
        l.add(game("Hidden", 103, true, 4000));
        return l;
    }

    private static List<String> names(List<NvApp> l) {
        List<String> out = new ArrayList<>();
        for (NvApp a : l) {
            out.add(a.getAppName());
        }
        return out;
    }

    @Test
    public void filtersAndTheirLists() {
        HomeModel m = HomeModel.build(apps(), 0, "", false, new HashSet<>(Arrays.asList(103)), false);
        assertEquals(Arrays.asList(LibraryModel.Filter.ALL, LibraryModel.Filter.RECENT, LibraryModel.Filter.INSTALLED,
                LibraryModel.Filter.HIDDEN), LibraryModel.filters(m));
        assertEquals(Arrays.asList("Recent", "Old", "Never"), names(LibraryModel.list(m, LibraryModel.Filter.ALL)));
        assertEquals(Arrays.asList("Recent", "Old", "Never"), names(LibraryModel.list(m, LibraryModel.Filter.RECENT)));
        assertEquals(Arrays.asList("Recent", "Never"), names(LibraryModel.list(m, LibraryModel.Filter.INSTALLED)));
        assertEquals(Arrays.asList("Hidden"), names(LibraryModel.list(m, LibraryModel.Filter.HIDDEN)));
        // The hidden filter shows only while a game is hidden
        HomeModel none = HomeModel.build(apps(), 0, "", false, null, false);
        assertEquals(3, LibraryModel.filters(none).size());
        assertEquals(0, LibraryModel.clamp(3, 3));
        assertEquals(2, LibraryModel.clamp(2, 3));
        // No game at all: no filter
        assertEquals(0, LibraryModel.filters(HomeModel.build(new ArrayList<>(), 0, "", false, null, false)).size());
    }

    @Test
    public void shouldersWrap() {
        assertEquals(1, LibraryModel.step(0, 4, 1));
        assertEquals(0, LibraryModel.step(3, 4, 1));
        assertEquals(3, LibraryModel.step(0, 4, -1));
    }

    @Test
    public void afficheWindowKeepsThreeRowsAbove() {
        assertEquals(0, LibraryModel.windowStart(0, 15));
        assertEquals(0, LibraryModel.windowStart(3, 15));
        assertEquals(2, LibraryModel.windowStart(5, 15));
        assertEquals(7, LibraryModel.windowStart(14, 15));
        assertEquals(0, LibraryModel.windowStart(4, 5));
    }

    /** The count line under the title: the desktop's LibraryView.qml countLine, rule for rule */
    @Test
    public void countLineAsTheDesktop() {
        HomeModel m = HomeModel.build(apps(), 0, "", false, new HashSet<>(Arrays.asList(103)), false);
        // Tous: "N jeux", N = the games shown (hidden ones are not)
        LibraryModel.Count all = LibraryModel.count(m, LibraryModel.Filter.ALL, LibraryModel.list(m, LibraryModel.Filter.ALL));
        assertEquals(LibraryModel.Count.Kind.GAMES, all.kind);
        assertEquals(3, all.shown);
        // A filter that narrows the list: "N affichés sur M", M = the games not hidden
        LibraryModel.Count inst = LibraryModel.count(m, LibraryModel.Filter.INSTALLED,
                LibraryModel.list(m, LibraryModel.Filter.INSTALLED));
        assertEquals(LibraryModel.Count.Kind.SHOWN_OF, inst.kind);
        assertEquals(2, inst.shown);
        assertEquals(3, inst.total);
        LibraryModel.Count hid = LibraryModel.count(m, LibraryModel.Filter.HIDDEN, LibraryModel.list(m, LibraryModel.Filter.HIDDEN));
        assertEquals(LibraryModel.Count.Kind.SHOWN_OF, hid.kind);
        assertEquals(1, hid.shown);
        assertEquals(3, hid.total);
        // The desktop compares to every game, hidden ones included: Récents under a hidden game says "3 affichés sur 3"
        assertEquals(LibraryModel.Count.Kind.SHOWN_OF,
                LibraryModel.count(m, LibraryModel.Filter.RECENT, LibraryModel.list(m, LibraryModel.Filter.RECENT)).kind);
        // Nothing hidden: Récents lists every game, so "N jeux"
        HomeModel none = HomeModel.build(apps(), 0, "", false, null, false);
        LibraryModel.Count rec = LibraryModel.count(none, LibraryModel.Filter.RECENT, LibraryModel.list(none, LibraryModel.Filter.RECENT));
        assertEquals(LibraryModel.Count.Kind.GAMES, rec.kind);
        assertEquals(4, rec.shown);
        // An empty list: "aucun jeu", whatever the filter, and without a library at all
        List<NvApp> notInstalled = new ArrayList<>();
        notInstalled.add(game("Old", 100, false, 1000));
        HomeModel bare = HomeModel.build(notInstalled, 0, "", false, null, false);
        assertEquals(LibraryModel.Count.Kind.NONE, LibraryModel.count(bare, LibraryModel.Filter.INSTALLED,
                LibraryModel.list(bare, LibraryModel.Filter.INSTALLED)).kind);
        assertEquals(LibraryModel.Count.Kind.NONE, LibraryModel.count(null, LibraryModel.Filter.ALL, new ArrayList<NvApp>()).kind);
    }

    @Test
    public void playedAgoAsTheDesktop() {
        long now = 1_800_000_000L, day = 86_400L;
        assertNull(LibraryModel.ago(now, 0));
        assertEquals(LibraryModel.Ago.Kind.NOW, LibraryModel.ago(now, now - 59 * 60).kind);
        assertEquals(LibraryModel.Ago.Kind.TODAY, LibraryModel.ago(now, now - 3600).kind);
        assertEquals(LibraryModel.Ago.Kind.YESTERDAY, LibraryModel.ago(now, now - day).kind);
        LibraryModel.Ago three = LibraryModel.ago(now, now - 3 * day);
        assertEquals(LibraryModel.Ago.Kind.DAYS, three.kind);
        assertEquals(3, three.n);
        assertEquals(LibraryModel.Ago.Kind.LAST_WEEK, LibraryModel.ago(now, now - 8 * day).kind);
        assertEquals(2, LibraryModel.ago(now, now - 14 * day).n);
        assertEquals(LibraryModel.Ago.Kind.LAST_MONTH, LibraryModel.ago(now, now - 35 * day).kind);
        assertEquals(3, LibraryModel.ago(now, now - 90 * day).n);
        assertEquals(LibraryModel.Ago.Kind.LAST_YEAR, LibraryModel.ago(now, now - 400 * day).kind);
        assertEquals(2, LibraryModel.ago(now, now - 730 * day).n);
    }
}
