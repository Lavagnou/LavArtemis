"""Scenario 19: a focus ring is never cut by an edge (user's rule, 2026-11-26).

The ring of the focused item (FocusRing: Braise 3 + 3 px, Ludique 4 + 5 px with the item grown to 1.04, Affiche's
ring2 8 px away) is drawn outside the item: a card, a panel, a list that scrolls (clip) or the window must leave it
room.  The test channel's `focusrings` gives every ring on screen and the first ancestor that cuts it (an item with
`clip: true` or `layer.enabled`, or the window).  The keyboard walks every v2 screen in each theme (dark mode) --
home, its options dialog, the library, Mes PC, Réglages, the in-game menu and its « Plus » -- and no ring may be cut.
The user saw it on the « Ouvrir » button of a Mes PC card in Ludique (cut left and right)."""
import time

import helpers

THEMES = ("braise", "affiche", "ludique")


def _set(c, key, value):
    c.call("setting", action="set", key=key, value=value)


class Walk:
    def __init__(self, c):
        self.c = c
        self.cuts = {}
        self.seen = 0

    def check(self, where, window=None):
        time.sleep(0.25)  # the focused item grows to 1.04 in 150 ms
        if window is not None:
            # the in-game menu's window: its index follows the visible windows
            window = helpers.quick_menu_window(self.c)
            assert window is not None, f"{where}: the in-game menu closed during the walk"
        args = {} if window is None else {"window": window}
        rings = self.c.call("focusrings", **args)["rings"]
        self.seen += len(rings)
        for r in rings:
            cut = r.get("cutBy")
            if cut:
                key = (where, r["owner"], cut["type"], cut.get("objectName", ""))
                self.cuts.setdefault(key, (r, cut))

    def keys(self, where, *names, window=None, target="qt"):
        for name in names:
            args = {"key": name, "target": target}
            if window is not None:
                args["window"] = helpers.quick_menu_window(self.c)
                assert args["window"] is not None, f"{where}: the in-game menu closed during the walk"
            self.c.call("key", **args)
            self.check(where, window)

    def sweep(self, where, rows, cols, window=None):
        """Right x cols, Left x cols, Down; `rows` times (a snake over a grid of focusable items)."""
        for _ in range(rows):
            self.keys(where, *(["Right"] * cols), window=window)
            self.keys(where, *(["Left"] * cols), window=window)
            self.keys(where, "Down", window=window)

    def report(self):
        return "\n".join(f"{w}: {owner} cut by {t}{' (' + n + ')' if n else ''}: ring {r['x']},{r['y']} {r['w']}x{r['h']}"
                         f" / box {cut['x']},{cut['y']} {cut['w']}x{cut['h']}"
                         for (w, owner, t, n), (r, cut) in sorted(self.cuts.items()))


def test_focus_rings_never_cut(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    walk = Walk(c)
    _set(c, "uiThemeMode", 2)
    for theme in THEMES:
        _set(c, "uiTheme", theme)
        helpers.go_home(c)
        time.sleep(0.6)

        # Home: header, Scène, cards, recents
        walk.keys(f"{theme}/home", "Down")
        walk.keys(f"{theme}/home", "Up", "Up", "Up")
        walk.sweep(f"{theme}/home", rows=5, cols=8)

        # Options of a card (Menu key): the centred dialog
        helpers.go_home(c)
        walk.keys(f"{theme}/home", "Down", "Down")
        c.call("key", key="Menu", target="qt")
        time.sleep(0.6)
        walk.keys(f"{theme}/options", *(["Down"] * 6), *(["Up"] * 6))
        c.call("key", key="Escape", target="qt")
        time.sleep(0.4)

        # Details of a recent game (its options › « Afficher les détails »)
        helpers.go_home(c)
        walk.keys(f"{theme}/home", "Down", "Down", "Down")
        c.call("key", key="Menu", target="qt")
        time.sleep(0.6)
        if helpers.find(c, "Afficher les détails"):
            c.click("Afficher les détails")
            helpers.wait_page(c, "DetailsView")
            time.sleep(0.6)
            walk.keys(f"{theme}/details", "Down", "Up", "Up")
            walk.sweep(f"{theme}/details", rows=3, cols=4)
        else:
            c.call("key", key="Escape", target="qt")
            time.sleep(0.4)

        # Library
        helpers.go_home(c)
        c.click("Tout voir →", index=-1)
        helpers.wait_page(c, "LibraryView")
        time.sleep(0.6)
        walk.keys(f"{theme}/library", "Down")
        walk.keys(f"{theme}/library", "Up", "Up")
        walk.sweep(f"{theme}/library", rows=4, cols=9)

        # Mes PC
        helpers.go_home(c)
        helpers.open_hosts(c)
        time.sleep(0.6)
        walk.keys(f"{theme}/hosts", "Down")
        walk.keys(f"{theme}/hosts", "Up", "Up")
        walk.sweep(f"{theme}/hosts", rows=4, cols=5)
        # a PC's options (Menu key on its card): « Ouvrir » first, where the user saw a cut ring in Ludique
        helpers.go_home(c)
        helpers.open_hosts(c)
        time.sleep(0.6)
        walk.keys(f"{theme}/hosts", "Down")
        c.call("key", key="Menu", target="qt")
        time.sleep(0.6)
        walk.keys(f"{theme}/host-options", *(["Down"] * 6), *(["Up"] * 6))
        c.call("key", key="Escape", target="qt")
        time.sleep(0.4)

        # Réglages: the list of pages, then the rows of a page
        helpers.go_home(c)
        helpers.open_settings(c)
        time.sleep(0.6)
        walk.keys(f"{theme}/settings", *(["Down"] * 12), *(["Up"] * 12))
        walk.keys(f"{theme}/settings", "Down", "Right", *(["Down"] * 14), *(["Up"] * 14))
        helpers.go_home(c)

    # In-game menu and « Plus », each theme (one stream)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    for theme in THEMES:
        _set(c, "uiTheme", theme)
        qm = helpers.open_quick_menu(c)
        walk.keys(f"{theme}/ingame", "Right", "Left", window=qm)
        walk.sweep(f"{theme}/ingame", rows=3, cols=9, window=qm)
        helpers.quick_menu_click(c, "Plus", qm)
        helpers.wait_quick_text(c, "Comportement de la souris", qm)
        walk.keys(f"{theme}/plus", *(["Down"] * 8), *(["Up"] * 8), window=qm)
        helpers.quick_menu_click(c, "Annuler", qm)
        helpers.quick_menu_click(c, "Reprendre", qm)
        c.wait_for(lambda s: not s.get("quickMenu"), 10)
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)

    bench.note(f"{walk.seen} rings checked")
    assert walk.seen > 0, "no focus ring was ever shown: the walk did not reach the keyboard focus"
    assert not walk.cuts, "focus rings cut by an edge:\n" + walk.report()
