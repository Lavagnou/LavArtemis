"""Scenario 20: navigation with the gamepad and the keyboard (audit of 2026-11-27, `audit-manette.md`, and the user's
arbitrations in `decisions-design.md`, « Manette (2026-11-27) »).

The channel's virtual pad (« LavArtemis Test Pad ») is attached outside a stream: SdlGamepadKeyNavigation opens it
and drives the UI with it, as a real pad (its keys go where the channel's « key » command sends them).  The focus is
read from `state.focus` (the item that takes the keys and the objectNames above it: « settingsNav » / « settingsRows »,
« nav:<page> », « host:<name> ») and `state.pageState` (the page's own focus grid: `focusId`, `currentPage`), or from
the rings on screen (`focusrings`) where a page keeps its focus in an index (dialogs, in-game menu).

S1 Réglages (R1-R7, R10, R11, V5, V6): Up / Down never leave the column they are in (list of pages, or the rows of a
   page: every page, a row whose button has the focus, right after LB / RB); LB / RB loop and put the focus on the new
   page in the list; B in the rows goes back to the list, then home; Left / Right change a dropdown choice (Résolution)
   and Left at its first option goes back to the list; theme cards: Left / Right move the focus, A applies; hints bar
   « A Ouvrir » on the list, « A Changer » on a row, « LB RB Page », « B Retour ».
S2 repeat (E1-E4, V4): d-pad, stick and keyboard arrows held 850 ms = one move, the next after 380 ms, then every 110 ms
   (4 to 7 moves; one before); the stick counts from ~55 % of its travel (not at 46 %), a diagonal moves vertically
   only; a second pad at rest does not restart the repeat of the stick held on the first one.
S3 home (A2, A4, A5, A6): the first arrow after the mouse shows the ring on Playnite without moving; changing rows goes
   to the item whose centre is the closest; back from Réglages (theme changed there) the focus is still on Réglages;
   Y on « Reprendre » = the options of what runs; no ring of the recents cut in a 1000 x 600 window (the minimum
   size), in each theme.
S4 Mes PC (M1-M6): opens on the PC the home shows; hints « Entrée Ouvrir · Menu Options · Échap Retour »; one row:
   Up / Down do nothing; Menu on « Ajouter un PC » = the add menu; Entrée held 700 ms = the PC's options (no open),
   pressed briefly = open.
S5 lists and dialogs (L1, D1, O1, O2, G1): a single column (options dialog, Affiche's library, details, Affiche's
   in-game menu): Left / Right do nothing; Ludique's dock (one row): Up / Down do nothing; « Plus » on two columns: Up
   on its first row and Down on its last stay.
S6 leaving from the home (prototype v113, as Android): B / Échap / Start = « Quitter LavArtemis ? » in the theme's
   dialog, « Quitter LavArtemis » (focused) then « Annuler »; B and Y close it, « Annuler » too; « Quitter LavArtemis »
   ends the client."""
import time

import helpers

PAD_HOLD = 0.15     # a press the 50 ms poll of the pad sees for sure
REPEAT_HOLD = 0.85  # one move, then 380, 490, 600, 710, (820) ms: 4 to 6, one more with the polling's jitter


def _pad(c, button, hold=PAD_HOLD, pad=1):
    c.call("gamepad", button=button, value=True, pad=pad)
    time.sleep(hold)
    c.call("gamepad", button=button, value=False, pad=pad)
    time.sleep(0.3)


def _key(c, *keys, window=None):
    for k in keys:
        args = {"key": k, "target": "qt"}
        if window is not None:
            args["window"] = window
        c.call("key", **args)
        time.sleep(0.25)


def _focus(c):
    return c.state()["focus"]


def _focus_name(c):
    return _focus(c)["objectName"]


def _page_state(c):
    return c.state()["pageState"]


def _attach_pad(c, pad=1):
    c.call("gamepad", attach=True, pad=pad)
    key = "pad" if pad == 1 else "pad2"
    c.wait_for(lambda s: s.get(key), 5)
    time.sleep(0.6)  # the navigation opens it at its next poll


def _nav_order(c):
    """The settings pages in the list, top to bottom (« nav:expert » first)."""
    items = [i for i in c.tree() if i.get("objectName", "").startswith("nav:")]
    return [i["objectName"] for i in sorted(items, key=lambda i: i["y"])]


def _ring(c, window=None):
    """The one focus ring on screen (the last one drawn when a dialog is over a page): its centre."""
    args = {} if window is None else {"window": window}
    rings = c.call("focusrings", **args)["rings"]
    assert rings, "no focus ring on screen"
    r = rings[-1]
    return (r["x"] + r["w"] / 2, r["y"] + r["h"] / 2)


def _same(a, b, tol=3):
    return abs(a[0] - b[0]) <= tol and abs(a[1] - b[1]) <= tol


# ---- S1 + S2: Réglages, and the repeat ---------------------------------------------------------------------------

def test_settings_columns_and_repeat(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.open_settings(c)
    time.sleep(0.6)
    _attach_pad(c)
    order = _nav_order(c)
    assert order[0] == "nav:expert" and len(order) >= 8, f"settings list: {order}"
    pages = [n[4:] for n in order[1:]]

    # On the list: Up from the first page = Mode expert, which stops there
    assert _focus_name(c) == "nav:" + _page_state(c)["currentPage"], f"focus on opening: {_focus(c)}"
    for _ in range(len(pages) + 1):
        _pad(c, "dpup")
    assert _focus_name(c) == "nav:expert", f"Up along the list: {_focus(c)}"
    _pad(c, "dpup")
    assert _focus_name(c) == "nav:expert", f"Up on Mode expert left it: {_focus(c)}"

    # R7 / R11: the hints bar, « A Ouvrir » on the list
    shown = helpers.texts(c)
    for t in ("Ouvrir", "Page", "Retour", "LB RB"):
        assert t in shown, f"hint {t!r} missing on the list: {shown[-12:]}"
    assert "Changer" not in shown
    bench.shot("01-list-hints")

    # S2: the d-pad held repeats (E1): from Mode expert, 850 ms = 4 to 7 moves (one before)
    c.call("gamepad", button="dpdown", value=True)
    time.sleep(REPEAT_HOLD)
    c.call("gamepad", button="dpdown", value=False)
    time.sleep(0.3)
    moved = order.index(_focus_name(c))
    assert 4 <= moved <= 7, f"d-pad held {REPEAT_HOLD} s: {moved} moves ({_focus_name(c)})"
    bench.note(f"d-pad held {REPEAT_HOLD} s: {moved} moves")

    # The keyboard's arrows (E4): the same repeat
    for _ in range(len(order)):
        _key(c, "Up")
    assert _focus_name(c) == "nav:expert"
    c.call("key", key="Down", state="down", target="qt")
    time.sleep(REPEAT_HOLD)
    c.call("key", key="Down", state="up", target="qt")
    time.sleep(0.3)
    moved = order.index(_focus_name(c))
    assert 4 <= moved <= 7, f"Down held {REPEAT_HOLD} s: {moved} moves ({_focus_name(c)})"
    bench.note(f"keyboard Down held {REPEAT_HOLD} s: {moved} moves")

    # The stick (E2): 61 % of its travel repeats, 46 % does nothing; a diagonal moves vertically only (E3)
    for _ in range(len(order)):
        _pad(c, "dpup", hold=0.12)
    assert _focus_name(c) == "nav:expert"
    c.call("gamepad", axis="lefty", value=15000)
    time.sleep(0.5)
    c.call("gamepad", axis="lefty", value=0)
    time.sleep(0.3)
    assert _focus_name(c) == "nav:expert", f"the stick at 46 % moved: {_focus(c)}"
    c.call("gamepad", axis="lefty", value=20000)
    time.sleep(REPEAT_HOLD)
    c.call("gamepad", axis="lefty", value=0)
    time.sleep(0.3)
    moved = order.index(_focus_name(c))
    assert 4 <= moved <= 7, f"stick held {REPEAT_HOLD} s at 61 %: {moved} moves ({_focus_name(c)})"
    bench.note(f"stick held {REPEAT_HOLD} s: {moved} moves")
    before = order.index(_focus_name(c))
    c.call("gamepad", axis="leftx", value=26000)
    c.call("gamepad", axis="lefty", value=-26000)
    time.sleep(0.2)
    c.call("gamepad", axis="leftx", value=0)
    c.call("gamepad", axis="lefty", value=0)
    time.sleep(0.3)
    assert _focus_name(c) == order[before - 1], f"a diagonal up-right: {_focus(c)} (from {order[before]})"

    # V4: a second pad at rest never restarts the repeat of the stick held on the first one (it repeated every poll)
    _attach_pad(c, pad=2)
    for _ in range(len(order)):
        _pad(c, "dpup", hold=0.12)
    c.call("gamepad", axis="lefty", value=20000)
    time.sleep(REPEAT_HOLD)
    c.call("gamepad", axis="lefty", value=0)
    time.sleep(0.3)
    moved = order.index(_focus_name(c))
    assert 4 <= moved <= 7, f"stick held with a second pad at rest: {moved} moves ({_focus_name(c)})"
    c.call("gamepad", detach=True, pad=2)

    # R6 / V6: LB / RB loop and put the focus on the new page in the list
    for _ in range(len(order)):
        _pad(c, "dpdown", hold=0.12)
    last = pages[-1]
    assert _focus_name(c) == "nav:" + last, f"Down to the last page: {_focus(c)}"
    _pad(c, "rightshoulder")
    st = c.state()
    assert st["pageState"]["currentPage"] == pages[0], f"RB on the last page: {st['pageState']}"
    assert st["focus"]["objectName"] == "nav:" + pages[0], f"RB left the focus at {st['focus']}"
    _pad(c, "leftshoulder")
    st = c.state()
    assert st["pageState"]["currentPage"] == last and st["focus"]["objectName"] == "nav:" + last, \
        f"LB on the first page: {st['pageState']} {st['focus']}"

    # R1 / V5: on every page, Right = the rows, Up never leaves them; B = back to the list (R4)
    for n, page in enumerate(pages):
        _pad(c, "rightshoulder")
        assert _page_state(c)["currentPage"] == pages[(pages.index(last) + 1 + n) % len(pages)]
        page = _page_state(c)["currentPage"]
        _pad(c, "dpright")
        f = _focus(c)
        assert "settingsRows" in f["path"], f"{page}: Right from the list did not reach the rows: {f}"
        if n == 0:
            shown = helpers.texts(c)
            assert "Changer" in shown and "Ouvrir" not in shown, f"hints on a row: {shown[-12:]}"
            bench.shot("02-row-hints")
        for _ in range(3):
            _pad(c, "dpup")
            f = _focus(c)
            assert "settingsRows" in f["path"], f"{page}: Up at the top of the rows left them: {f}"
        _pad(c, "b")
        st = c.state()
        assert st["page"] == "SettingsView2" and st["focus"]["objectName"] == "nav:" + page, \
            f"{page}: B on a row: page {st['page']}, focus {st['focus']}"

    # A row whose button has the focus (Profil › Gérer les profils: Nouveau): Up stays in the rows
    while _page_state(c)["currentPage"] != "profil":
        _pad(c, "rightshoulder")
    _pad(c, "dpright")
    _pad(c, "dpdown")
    f = _focus(c)
    assert "settingsRows" in f["path"] and f["type"] != "SettingChoice", f"Profil: Down to the buttons: {f}"
    for _ in range(3):
        _pad(c, "dpup")
        assert "settingsRows" in _focus(c)["path"], f"Profil: Up from a button left the rows: {_focus(c)}"
    _pad(c, "b")

    # R5: Résolution (a dropdown): Right / Left change it, Left at its first option goes back to the list
    while _page_state(c)["currentPage"] != "affichage":
        _pad(c, "rightshoulder")
    _pad(c, "dpright")
    row = next(i for i in c.tree() if i.get("objectName") == "set:resolution")
    start = row["row"].get("valueText") or row["row"].get("value")
    assert _focus_name(c) == "set:resolution", f"Affichage: first row {_focus(c)}"
    changed = False
    for _ in range(30):
        _pad(c, "dpleft", hold=0.12)
        if _focus_name(c) != "set:resolution":
            break
        now = next(i for i in c.tree() if i.get("objectName") == "set:resolution")["row"]
        changed = changed or (now.get("valueText") or now.get("value")) != start
    assert _focus_name(c) == "nav:affichage", f"Left at the first resolution: {_focus(c)}"
    _pad(c, "dpright")
    first = next(i for i in c.tree() if i.get("objectName") == "set:resolution")["row"]
    _pad(c, "dpright")
    second = next(i for i in c.tree() if i.get("objectName") == "set:resolution")["row"]
    assert (second.get("valueText") or second.get("value")) != (first.get("valueText") or first.get("value")), \
        f"Right on Résolution did not change it: {first} -> {second}"
    assert _focus_name(c) == "set:resolution"
    bench.note(f"Résolution: {start} ... {second.get('valueText') or second.get('value')} (changed on the way: {changed})")
    _pad(c, "b")

    # R10: theme cards: Left / Right move the focus, A applies
    while _page_state(c)["currentPage"] != "apparence":
        _pad(c, "rightshoulder")
    theme = c.call("setting", action="get", key="uiTheme")["value"]
    _pad(c, "dpright")
    card = _focus_name(c)
    assert card.startswith("theme:"), f"Apparence: first row {_focus(c)}"
    _pad(c, "dpright")
    assert _focus_name(c).startswith("theme:") and _focus_name(c) != card,         f"Right on a theme card: {card} -> {_focus(c)}"
    assert c.call("setting", action="get", key="uiTheme")["value"] == theme, "Right on a theme card applied it"
    _pad(c, "a")
    time.sleep(0.6)
    assert c.call("setting", action="get", key="uiTheme")["value"] != theme, "A on a theme card did not apply it"
    c.call("setting", action="set", key="uiTheme", value=theme)

    # B: rows -> list -> home
    _pad(c, "b")
    assert c.state()["page"] == "SettingsView2"
    _pad(c, "b")
    helpers.wait_page(c, "HomeView")


# ---- S3: the home --------------------------------------------------------------------------------------------------

def test_home_grid(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.wait_text(c, helpers.HOME_LIBRARY_TITLE)
    time.sleep(0.6)

    # A4: at the mouse (no hints), the first arrow shows the ring on Playnite without moving; the next one moves
    assert "Changer de PC" not in helpers.texts(c), "hints shown before any key"
    _key(c, "Left")
    assert _page_state(c)["focusId"] == "a1", f"first Left after the mouse: {_page_state(c)}"
    _key(c, "Left")
    assert _page_state(c)["focusId"] == "a0", f"second Left: {_page_state(c)}"

    # A2: Down from Playnite goes to the cover under it (the closest centre), Up comes back to Playnite
    _key(c, "Right")
    assert _page_state(c)["focusId"] == "a1"
    time.sleep(0.3)
    above = _ring(c)
    _key(c, "Down")
    time.sleep(0.3)
    fid = _page_state(c)["focusId"]
    below = _ring(c)
    assert fid.startswith("r") or fid == "lib", f"Down from Playnite: {fid}"
    covers = [i for i in c.tree() if i["type"] == "HomeTile" and abs((i["y"] + i["h"] / 2) - below[1]) < 30]
    assert len(covers) >= 3, f"covers of the recents row: {covers}"
    nearest = min(covers, key=lambda i: abs(i["x"] + i["w"] / 2 - above[0]))
    assert abs(nearest["x"] + nearest["w"] / 2 - below[0]) < nearest["w"] / 2, \
        f"Down from Playnite (centre x {above[0]:.0f}) went to {fid} at x {below[0]:.0f}, the closest is at " \
        f"{nearest['x'] + nearest['w'] / 2:.0f}"
    bench.note(f"Down from Playnite: {fid}")
    # Up: the card whose centre is the closest to that cover's (with 4 recents, « Tout voir » is between the two
    # cards: the closest one is not always Playnite)
    _key(c, "Up")
    time.sleep(0.3)
    up = _ring(c)
    cards = [i for i in c.tree() if i["type"] == "HomeTile" and abs((i["y"] + i["h"] / 2) - above[1]) < 30]
    assert len(cards) == 2, f"cards row: {cards}"
    nearest = min(cards, key=lambda i: abs(i["x"] + i["w"] / 2 - below[0]))
    assert abs(nearest["x"] + nearest["w"] / 2 - up[0]) < nearest["w"] / 2,         f"Up from {fid} (x {below[0]:.0f}) went to {_page_state(c)['focusId']} at x {up[0]:.0f}"
    _key(c, "Right")

    # A6: Réglages, the theme changed there, back: the focus is still on Réglages
    _key(c, "Up")
    if _page_state(c)["focusId"] != "ns":
        _key(c, "Right")
    assert _page_state(c)["focusId"] == "ns", f"header row: {_page_state(c)}"
    _key(c, "Enter")
    helpers.wait_page(c, "SettingsView2")
    time.sleep(0.5)
    c.call("setting", action="set", key="uiTheme", value="ludique")
    time.sleep(0.5)
    helpers.go_home(c)
    time.sleep(0.5)
    assert _page_state(c)["focusId"] == "ns", f"back from Réglages: {_page_state(c)}"
    c.call("setting", action="set", key="uiTheme", value="braise")

    # Rings of the recents never cut in a 1000 x 600 window (the window's minimum size), every theme
    cuts = _small_window_rings(c, bench, "idle")
    assert not cuts, f"rings cut in a 1000 x 600 window: {cuts}"

    # A5: Y on « Reprendre » = the options of what runs (the Bureau, left running by « Déconnecter »)
    helpers.launch(c, "Bureau")
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    helpers.wait_text(c, "Reprendre")
    time.sleep(0.6)
    _key(c, "Insert")
    for _ in range(6):
        if _page_state(c)["focusId"] == "resume":
            break
        _key(c, "Up")
        if _page_state(c)["focusId"] in ("nh", "ns"):
            _key(c, "Down", "Left")
    assert _page_state(c)["focusId"] == "resume", f"Reprendre not reached: {_page_state(c)}"
    _key(c, "Menu")
    deadline = time.time() + 5
    while "Relancer la session" not in helpers.popup_texts(c):
        assert time.time() < deadline, f"Menu on Reprendre: {helpers.popup_texts(c)}"
        time.sleep(0.2)
    bench.shot("04-resume-options")
    _key(c, "Escape")
    helpers.wait_no_popup(c)

    # The same with the Scène shown (a session runs: the rows are lower)
    cuts = _small_window_rings(c, bench, "session")
    assert not cuts, f"rings cut in a 1000 x 600 window, session running: {cuts}"


def _small_window_rings(c, bench, label):
    """The main window at 1000 x 600 (its minimum size): the keyboard walks the recents in each theme; the rings cut
    by an edge, [(theme, focusId, cutBy)]."""
    size = c.call("resize", w=1000, h=600)
    assert (size["w"], size["h"]) == (1000, 600), size
    cuts = []
    for theme in ("braise", "affiche", "ludique"):
        c.call("setting", action="set", key="uiTheme", value=theme)
        time.sleep(0.8)
        for _ in range(5):
            if _page_state(c)["focusId"].startswith("r") or _page_state(c)["focusId"] == "lib":
                break
            _key(c, "Down")
        for _ in range(8):
            _key(c, "Left")
        for step in range(8):
            time.sleep(0.25)
            for r in c.call("focusrings", window=0)["rings"]:
                if r.get("cutBy"):
                    cuts.append((theme, _page_state(c)["focusId"], r["cutBy"]))
            if step == 0:
                bench.shot(f"03-small-{label}-{theme}")
            _key(c, "Right")
    c.call("resize", w=1280, h=720)
    c.call("setting", action="set", key="uiTheme", value="braise")
    time.sleep(0.6)
    return cuts


# ---- S4: Mes PC ----------------------------------------------------------------------------------------------------

def test_hosts(bench):
    atlas, cynisca = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.add_and_pair(c, cynisca)
    helpers.go_home(c)
    names = (atlas.name, cynisca.name)
    _key(c, "Insert")
    _key(c, "E")
    time.sleep(0.6)
    shown = [t for t in helpers.texts(c) if t in names]
    assert shown, f"no PC name on the home: {helpers.texts(c)[:20]}"
    displayed = shown[0]

    # M5: Mes PC opens on the card of the PC the home shows
    for _ in range(4):
        _key(c, "Up")
    if _page_state(c)["focusId"] != "nh":
        _key(c, "Left")
    assert _page_state(c)["focusId"] == "nh", _page_state(c)
    _key(c, "Enter")
    helpers.wait_page(c, "HostsView")
    time.sleep(0.6)
    assert _focus_name(c) == "host:" + displayed, f"Mes PC opened on {_focus(c)}, the home shows {displayed}"

    # M2: the hints bar
    shown = helpers.texts(c)
    for t in ("Ouvrir", "Options", "Retour", "Entrée", "Menu", "Échap"):
        assert t in shown, f"hint {t!r} missing in Mes PC: {shown[-12:]}"
    bench.shot("01-hosts-hints")

    # M3: one row: Up / Down do nothing
    here = _focus_name(c)
    _key(c, "Down")
    assert _focus_name(c) == here, f"Down on a single row: {_focus(c)}"
    _key(c, "Up")
    assert _focus_name(c) == here, f"Up on a single row: {_focus(c)}"

    # M6: Menu on « Ajouter un PC » = the add menu
    for _ in range(4):
        _key(c, "Right")
    assert _focus_name(c) == "host:add", _focus(c)
    _key(c, "Menu")
    helpers.wait_text(c, "Saisir une adresse…")
    _key(c, "Escape")
    helpers.wait_no_popup(c)

    # M1: Entrée held 700 ms on a PC = its options, the page stays; pressed briefly = open
    _key(c, "Left")
    assert _focus_name(c).startswith("host:") and _focus_name(c) != "host:add"
    c.call("key", key="Enter", state="down", target="qt")
    time.sleep(0.7)
    c.call("key", key="Enter", state="up", target="qt")
    deadline = time.time() + 5
    while "Benchmark des réglages" not in helpers.popup_texts(c):
        assert time.time() < deadline, f"Entrée held on a PC: {helpers.texts(c)[-12:]}"
        time.sleep(0.2)
    time.sleep(0.5)
    assert c.state()["page"] == "HostsView", "the held Entrée opened the PC"
    bench.shot("02-held-options")
    _key(c, "Escape")
    helpers.wait_no_popup(c)
    time.sleep(0.4)
    _key(c, "Enter")
    helpers.wait_page(c, "HomeView")


# ---- S5: lists and dialogs -----------------------------------------------------------------------------------------

def test_lists_and_dialogs(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.wait_text(c, helpers.HOME_LIBRARY_TITLE)
    _key(c, "Insert")

    # O2: a dialog of one column (the options of a card): Left / Right do nothing
    assert _page_state(c)["focusId"] == "a1"
    _key(c, "Menu")
    deadline = time.time() + 5
    while not helpers.popup_texts(c):
        assert time.time() < deadline, "Menu opened no options"
        time.sleep(0.2)
    time.sleep(0.5)
    first = _ring(c)
    _key(c, "Right")
    assert _same(_ring(c), first), "Right moved the focus in a one-column dialog"
    _key(c, "Down")
    second = _ring(c)
    assert not _same(second, first), "Down did not move in the dialog"
    _key(c, "Left")
    assert _same(_ring(c), second), "Left moved the focus in a one-column dialog"
    _key(c, "Escape")
    helpers.wait_no_popup(c)

    # D1: the details' launch button: Left / Right do nothing
    _key(c, "Menu")
    helpers.wait_text(c, "Afficher les détails")
    c.click("Afficher les détails")
    helpers.wait_page(c, "DetailsView")
    time.sleep(0.5)
    _key(c, "Insert")
    assert _page_state(c)["focusId"] == "go", _page_state(c)
    _key(c, "Left", "Right")
    assert _page_state(c)["focusId"] == "go", f"Left / Right on « Lancer »: {_page_state(c)}"
    helpers.go_home(c)

    # L1: Affiche's library (one column): Left / Right do nothing, Down moves
    c.call("setting", action="set", key="uiTheme", value="affiche")
    time.sleep(0.6)
    c.click("Tout voir →", index=-1)
    helpers.wait_page(c, "LibraryView")
    time.sleep(0.5)
    _key(c, "Insert")
    assert _page_state(c)["focusId"] == "lg0", _page_state(c)
    _key(c, "Right")
    assert _page_state(c)["focusId"] == "lg0", f"Right in Affiche's list: {_page_state(c)}"
    _key(c, "Down")
    assert _page_state(c)["focusId"] == "lg1", f"Down in Affiche's list: {_page_state(c)}"
    _key(c, "Left")
    assert _page_state(c)["focusId"] == "lg1", f"Left in Affiche's list: {_page_state(c)}"
    helpers.go_home(c)

    # The in-game menu: Affiche (one column), Ludique (one row), Braise's « Plus » (two columns)
    helpers.launch(c, "Bureau")
    qm = helpers.open_quick_menu(c)
    _key(c, "Insert", window=qm)
    assert c.state()["menuFocusId"] == "m0", c.state().get("menuFocusId")
    _key(c, "Right", window=qm)
    assert c.state()["menuFocusId"] == "m0", "Right moved the focus in Affiche's in-game menu"
    _key(c, "Down", window=qm)
    assert c.state()["menuFocusId"] == "m1", "Down did not move in Affiche's in-game menu"
    bench.shot("01-affiche-menu", window=qm)
    _key(c, "Escape", window=qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)

    c.call("setting", action="set", key="uiTheme", value="ludique")
    qm = helpers.open_quick_menu(c)
    _key(c, "Insert", window=qm)
    assert c.state()["menuFocusId"] == "m0", c.state().get("menuFocusId")
    _key(c, "Down", window=qm)
    assert c.state()["menuFocusId"] == "m0", "Down moved the focus in Ludique's dock (one row)"
    _key(c, "Up", window=qm)
    assert c.state()["menuFocusId"] == "m0", "Up moved the focus in Ludique's dock (one row)"
    _key(c, "Right", window=qm)
    assert c.state()["menuFocusId"] == "m1", "Right did not move in Ludique's dock"
    _key(c, "Escape", window=qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)

    # O1: « Plus » on two columns: Up on the first row and Down on the last row stay where they are
    c.call("setting", action="set", key="uiTheme", value="braise")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Plus", qm)
    helpers.wait_quick_text(c, "Comportement de la souris", qm)
    time.sleep(0.5)
    _key(c, "Insert", window=qm)
    top_left = _ring(c, qm)
    _key(c, "Right", window=qm)
    top_right = _ring(c, qm)
    assert top_right[0] > top_left[0] + 20 and abs(top_right[1] - top_left[1]) < 5, \
        f"Right on « Plus »: {top_left} -> {top_right}"
    _key(c, "Up", window=qm)
    assert _same(_ring(c, qm), top_right), f"Up on the first row of « Plus » moved to {_ring(c, qm)}"
    for _ in range(6):
        _key(c, "Down", window=qm)
    bottom = _ring(c, qm)
    _key(c, "Down", window=qm)
    assert _same(_ring(c, qm), bottom), f"Down on the last row of « Plus » moved to {_ring(c, qm)}"
    assert bottom[1] > top_right[1] + 50
    bench.shot("02-plus", window=qm)
    _key(c, "Escape", window=qm)
    time.sleep(0.4)
    helpers.quick_menu_click(c, "Reprendre", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)


# ---- S6: leaving from the home ------------------------------------------------------------------------------------

def _wait_popup(c, text, gone=False, timeout=5.0):
    deadline = time.time() + timeout
    while (text in helpers.popup_texts(c)) == gone:
        assert time.time() < deadline, f"{'still' if gone else 'never'} shown: {text!r}; {helpers.popup_texts(c)}"
        time.sleep(0.2)
    time.sleep(0.4)  # opening / closing animation


def test_quit_dialog(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.go_home(c)
    _attach_pad(c)

    # B on the home: the dialog of the theme, its title and two entries, the focus on « Quitter LavArtemis »
    _pad(c, "b")
    _wait_popup(c, "Quitter LavArtemis ?")
    texts = helpers.popup_texts(c)
    assert texts.index("Quitter LavArtemis") < texts.index("Annuler"), texts
    assert "Are you sure you want to quit?" not in helpers.texts(c)
    entry = next(i for i in c.tree(textOnly=True) if i.get("text") == "Quitter LavArtemis")
    ring = _ring(c)
    assert entry["y"] - 10 <= ring[1] <= entry["y"] + entry["h"] + 10, f"focus not on « Quitter LavArtemis »: {ring} {entry}"
    bench.shot("01-quit-dialog")
    # B closes it, the app stays
    _pad(c, "b")
    _wait_popup(c, "Quitter LavArtemis ?", gone=True)
    assert c.state()["page"] == "HomeView"
    # Start opens it, Y closes it
    _pad(c, "start")
    _wait_popup(c, "Quitter LavArtemis ?")
    _pad(c, "y")
    _wait_popup(c, "Quitter LavArtemis ?", gone=True)
    # Échap opens it, Bas + Entrée = « Annuler »
    _key(c, "Escape")
    _wait_popup(c, "Quitter LavArtemis ?")
    _key(c, "Down", "Enter")
    _wait_popup(c, "Quitter LavArtemis ?", gone=True)
    assert c.state()["page"] == "HomeView"
    # A on « Quitter LavArtemis »: the client ends
    _pad(c, "b")
    _wait_popup(c, "Quitter LavArtemis ?")
    c.call("gamepad", button="a", value=True)
    time.sleep(PAD_HOLD)
    try:
        c.call("gamepad", button="a", value=False)
    except Exception:
        pass  # the client may already be gone
    code = bench.expect_client_exit(15)
    assert code == 0, f"« Quitter LavArtemis » must end the client normally, code {code}"
