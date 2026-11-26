package com.limelight.v2;

import com.limelight.nvstream.http.NvApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rules of the library page (prototype « library », V8-22 / 23 / 60 / 62 / 72; design-port-spec
 * §1.6), without Android: its filters, the games each one lists, the Affiche window of 8 rows, and
 * « joué hier ». Same rules as the desktop's LibraryView.qml / HomeView.lastPlayedText.
 */
public final class LibraryModel {
    private LibraryModel() {}

    public enum Filter { ALL, RECENT, INSTALLED, HIDDEN }

    /** Rows of the Affiche list at the gamepad / keyboard (prototype listWindow) */
    public static final int WINDOW = 8;

    /** Tous, Récents, Installés, then « Masqués · N » only while a game is hidden (prototype filters()) */
    public static List<Filter> filters(HomeModel m) {
        List<Filter> out = new ArrayList<>();
        if (m == null || m.library.isEmpty()) {
            return out;
        }
        out.add(Filter.ALL);
        out.add(Filter.RECENT);
        out.add(Filter.INSTALLED);
        if (m.hiddenCount > 0) {
            out.add(Filter.HIDDEN);
        }
        return out;
    }

    /**
     * The games a filter lists (prototype list()): Tous = every game not hidden, Récents = the same
     * by last activity (never played last), Installés = installed and not hidden, Masqués = the hidden
     * ones. The model's library is already most recent first, as the host sends it after sorting.
     */
    public static List<NvApp> list(HomeModel m, Filter f) {
        if (m == null) {
            return Collections.emptyList();
        }
        List<NvApp> out = new ArrayList<>();
        for (NvApp g : m.library) {
            boolean hidden = m.isHidden(g);
            switch (f) {
                case HIDDEN:
                    if (hidden) {
                        out.add(g);
                    }
                    break;
                case INSTALLED:
                    if (!hidden && g.isPlayniteInstalled()) {
                        out.add(g);
                    }
                    break;
                default:
                    if (!hidden) {
                        out.add(g);
                    }
                    break;
            }
        }
        if (f == Filter.RECENT) {
            HomeModel.sortRecentFirst(out);
        }
        return out;
    }

    /** The filter after LB / RB (or Q / E), wrapping (prototype shoulder() on the library) */
    public static int step(int filter, int count, int d) {
        if (count <= 0) {
            return 0;
        }
        return ((filter + d) % count + count) % count;
    }

    /** The filter to show when the list of filters changed: Masqués gone (nothing hidden any more) = Tous */
    public static int clamp(int filter, int count) {
        return filter >= 0 && filter < count ? filter : 0;
    }

    /** First row of the Affiche window: 3 rows above the focus, never past the end (prototype listWindow) */
    public static int windowStart(int focus, int size) {
        return Math.max(0, Math.min(focus - 3, size - WINDOW));
    }

    /**
     * The count line under the title (desktop LibraryView.qml countLine): « aucun jeu » when the list is
     * empty, « N jeux » under Tous or when it shows as many games as are not hidden, « N affichés sur M »
     * otherwise, M = the games not hidden (singular « 1 jeu », « 1 affiché sur M »).
     */
    public static final class Count {
        public enum Kind { NONE, GAMES, SHOWN_OF }

        public final Kind kind;
        public final int shown;
        public final int total;

        Count(Kind kind, int shown, int total) {
            this.kind = kind;
            this.shown = shown;
            this.total = total;
        }
    }

    /** shown = what list(m, f) returned */
    public static Count count(HomeModel m, Filter f, List<NvApp> shown) {
        int n = shown == null ? 0 : shown.size();
        int visible = m == null ? 0 : m.library.size() - m.hiddenCount;
        if (n == 0) {
            return new Count(Count.Kind.NONE, 0, visible);
        }
        if (n == visible || f == Filter.ALL) {
            return new Count(Count.Kind.GAMES, n, visible);
        }
        return new Count(Count.Kind.SHOWN_OF, n, visible);
    }

    /** How long ago a game was played (desktop lastPlayedText): what the caller turns into words */
    public static final class Ago {
        public enum Kind { NOW, TODAY, YESTERDAY, DAYS, LAST_WEEK, WEEKS, LAST_MONTH, MONTHS, LAST_YEAR, YEARS }

        public final Kind kind;
        public final int n;

        Ago(Kind kind, int n) {
            this.kind = kind;
            this.n = n;
        }
    }

    /** Null when never played (lastSeconds 0) */
    public static Ago ago(long nowSeconds, long lastSeconds) {
        if (lastSeconds <= 0) {
            return null;
        }
        double mins = (nowSeconds - lastSeconds) / 60.0;
        if (mins < 60) {
            return new Ago(Ago.Kind.NOW, 0);
        }
        int days = (int) Math.floor(mins / 1440);
        if (days < 1) {
            return new Ago(Ago.Kind.TODAY, 0);
        }
        if (days < 2) {
            return new Ago(Ago.Kind.YESTERDAY, 1);
        }
        if (days < 7) {
            return new Ago(Ago.Kind.DAYS, days);
        }
        if (days < 14) {
            return new Ago(Ago.Kind.LAST_WEEK, 1);
        }
        if (days < 28) {
            return new Ago(Ago.Kind.WEEKS, days / 7);
        }
        if (days < 60) {
            return new Ago(Ago.Kind.LAST_MONTH, 1);
        }
        if (days < 365) {
            return new Ago(Ago.Kind.MONTHS, days / 30);
        }
        if (days < 730) {
            return new Ago(Ago.Kind.LAST_YEAR, 1);
        }
        return new Ago(Ago.Kind.YEARS, days / 365);
    }
}
