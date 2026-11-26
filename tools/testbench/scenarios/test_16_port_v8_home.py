"""Scenario 16 (port of the V8 prototype, LavArtemis-Qt branch port-v8): the home's new behaviour.

S1 focus grid at the keyboard: rows [Mes PC, Réglages] / [Bureau, Playnite] / [6 recents, Tout voir]; a recent
   focused shows « {jeu} · {N} h jouées » after « Bibliothèque Playnite · récents ».
S2 hints bar at the keyboard (« Entrée Lancer · Menu Options (ou maintenir Entrée) · Q E Changer de PC »), none at
   the mouse.
S3 Menu key = options of the focused game (centred dialog: « Jouer », « Afficher les détails »…); Échap closes.
S4 Entrée held 500 ms = the same options, and the release does not launch.
S5 Q / E = previous / next PC (two fake PCs), the focus goes back to Playnite Fullscreen.
S6 toasts: the Multi-écran pill without the virtual display says why (« Le multi-écran a besoin de l’écran virtuel… »).
S7 Bureau left running by « Déconnecter »: its card says « Reprendre » and a click resumes the stream (the bug of
   wave 4: « ne relance rien »).

Written during the night of 2026-11-26 with the display off: never run yet (port-notes.md)."""
import time

import helpers


def _texts(c):
    return helpers.texts(c)


def _key(c, *keys):
    for k in keys:
        c.call("key", key=k, target="qt")
        time.sleep(0.2)


def test_focus_grid_hints_options_hold(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.wait_text(c, "Bibliothèque Playnite · récents")

    # S2: no hints at the mouse, hints at the keyboard
    assert "Changer de PC" not in _texts(c)
    _key(c, "Insert")
    helpers.wait_text(c, "Changer de PC")
    for t in ("Entrée", "Lancer", "Options (ou maintenir Entrée)", "Q E"):
        assert t in _texts(c), f"hint {t!r} missing: {_texts(c)}"
    bench.shot("01-hints")

    # S1: Up x3 = header row, Down x2 = first recent: its caption
    _key(c, "Up", "Up", "Up", "Down", "Down")
    caption = helpers.wait_text(c, " h jouées", contains=True)[0]["text"]
    assert "·" in caption
    first = caption
    _key(c, "Right")
    helpers.wait_text(c, first, gone=True)
    bench.shot("02-second-recent")

    # S3: Menu = options of that game, centred; Échap closes
    _key(c, "Menu")
    helpers.wait_text(c, "Afficher les détails")
    assert "Annuler" in _texts(c)
    bench.shot("03-options")
    _key(c, "Escape")
    helpers.wait_text(c, "Afficher les détails", gone=True)

    # S4: Entrée held 600 ms = options; the release does not launch
    c.call("key", key="Enter", state="down", target="qt")
    time.sleep(0.7)
    c.call("key", key="Enter", state="up", target="qt")
    helpers.wait_text(c, "Afficher les détails")
    time.sleep(1.0)
    assert not c.state()["session"]["active"], "the release of a held Entrée launched the game"
    _key(c, "Escape")


def test_shoulder_and_toast(bench):
    atlas, cynisca = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.add_and_pair(c, cynisca)
    helpers.go_home(c)
    _key(c, "Insert")
    before = [t for t in _texts(c) if t in (atlas.name, cynisca.name)]
    assert before, f"no PC name on the home: {_texts(c)}"
    # S5: E = next PC, Q = back
    _key(c, "E")
    time.sleep(0.5)
    after = [t for t in _texts(c) if t in (atlas.name, cynisca.name)]
    assert after and after[0] != before[0], f"E did not change the PC: {before} -> {after}"
    _key(c, "Q")
    time.sleep(0.5)
    assert [t for t in _texts(c) if t in (atlas.name, cynisca.name)][0] == before[0]
    bench.shot("01-shoulder")

    # S6: Multi-écran without the virtual display: a toast says why
    c.call("setting", action="set", key="useVirtualDisplay", value=False)
    c.click(objectName="multiScreenPill")
    helpers.wait_text(c, "Le multi-écran a besoin de l’écran virtuel", contains=True)
    bench.shot("02-toast")


def test_bureau_resumes_after_disconnect(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    # S7: the Scène and the Bureau card say « Reprendre »; a click on the card resumes the stream
    helpers.wait_text(c, "Reprendre")
    bench.shot("01-scene")
    c.click("Bureau", index=-1)
    st = c.wait_for(lambda s: s["session"]["streaming"] and s["session"]["decoded"] > 0, 40)
    assert st["session"]["streaming"]
