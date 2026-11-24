"""Scenario 2: the home of a paired PC: cards « Bureau » then « Playnite Fullscreen », the Playnite library with
its covers in the host's order (library.json: most recently played first), « Masquer » a game, then show it again
from the library."""
import json
import time

import pytest
from PIL import Image

import bugs
import covers
import helpers
import hosts

GAME = "Tidewater Tactics"


def library_order(host):
    lib = json.loads((hosts.FIXTURES / host.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))
    return [g["name"] for g in lib["games"]]


def tiles(c):
    return [i for i in c.tree() if i["type"] == "CoverTile"]


def tile_names(c, shot_path, names, dpr):
    """Name of each cover tile, recognised by its generated colour (covers.py) sampled in a capture: the tile's
    picture has no text item in the tree.  Returns names in reading order and the colour distance of each."""
    expected = {n: covers._colour(n) for n in names}
    out = []
    with Image.open(shot_path) as img:
        img = img.convert("RGB")
        for t in sorted(tiles(c), key=lambda i: (i["y"], i["x"])):
            x = int((t["x"] + t["w"] / 2) * dpr)
            y = int((t["y"] + t["h"] * 0.15) * dpr)
            r, g, b = img.getpixel((x, y))
            best = min(expected, key=lambda n: sum((a - e) ** 2 for a, e in zip((r, g, b), expected[n])))
            dist = sum((a - e) ** 2 for a, e in zip((r, g, b), expected[best])) ** 0.5
            out.append((best, round(dist), t))
    return out


def test_home_cards_library_and_hide(bench):
    """Bureau and Playnite Fullscreen cards (in that order); the 4 library games each show their cover; the library
    keeps library.json's order; « Masquer » takes a game out of the library and « Afficher le jeu masqué » shows it
    again, where its menu unhides it."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.wait_page(c, "HomeView")
    names = library_order(atlas)
    deadline = time.time() + 20
    while len(tiles(c)) < len(names) and time.time() < deadline:
        time.sleep(0.3)

    # Cards: Bureau first, then Playnite Fullscreen, on the same row
    bureau = helpers.wait_text(c, "Bureau")[0]
    playnite = helpers.wait_text(c, "Playnite Fullscreen")[0]
    assert bureau["x"] < playnite["x"] and abs(bureau["y"] - playnite["y"]) < 5
    helpers.wait_text(c, "Bibliothèque Playnite")
    time.sleep(1.0)  # covers fetched from the host fade in
    shot = bench.shot("01-home")
    found = tile_names(c, shot["path"], names, shot["dpr"])
    seen = [n for n, _d, _t in found]
    bench.note(f"tiles: {[(n, d) for n, d, _t in found]}")
    assert len(found) == len(names), f"{len(found)} cover tiles for {len(names)} games"
    assert all(d < 40 for _n, d, _t in found), f"a tile does not show its cover: {found}"
    assert sorted(seen) == sorted(names), f"tiles {seen} != games {names}"
    order_ok = seen == names

    # « Masquer » from the game's menu (right click on its cover)
    tile = next(t for n, _d, t in found if n == GAME)
    c.call("click", x=tile["x"] + tile["w"] // 2, y=tile["y"] + tile["h"] // 2, button="right")
    helpers.wait_text(c, "Masquer")
    bench.shot("02-menu")
    # « Créer un raccourci .art » is in this menu too: never clicked (it opens a native file dialog, which activates)
    c.click("Masquer")
    helpers.wait_text(c, "Afficher le jeu masqué")
    bench.shot("03-just-hidden")
    # Hidden games show only on demand: toggle the hidden games on and off, the library no longer has it
    c.click("Afficher le jeu masqué")
    helpers.wait_text(c, "Cacher les jeux masqués")
    c.click("Cacher les jeux masqués")
    helpers.wait_text(c, "Afficher le jeu masqué")
    time.sleep(0.5)
    shot = bench.shot("04-hidden")
    after = [n for n, _d, _t in tile_names(c, shot["path"], names, shot["dpr"])]
    assert len(after) == len(names) - 1 and GAME not in after, f"after Masquer: {after}"

    # The library shows it again on demand; its menu unhides it
    c.click("Afficher le jeu masqué")
    helpers.wait_text(c, "Cacher les jeux masqués")
    time.sleep(0.5)
    shot = bench.shot("05-shown-again")
    again = tile_names(c, shot["path"], names, shot["dpr"])
    assert len(again) == len(names), f"hidden game not shown again: {[n for n, _d, _t in again]}"
    # faded (opacity 0.45): its colour is far from the cover's, the others are not
    tile = next(t for n, d, t in again if d > 40) if any(d > 40 for _n, d, _t in again) else None
    assert tile is not None, f"the hidden game should be shown faded: {[(n, d) for n, d, _t in again]}"
    c.call("click", x=tile["x"] + tile["w"] // 2, y=tile["y"] + tile["h"] // 2, button="right")
    helpers.wait_text(c, GAME)
    bench.shot("06-hidden-menu")
    assert helpers.find(c, "Ne plus masquer"), f"no unhide entry in the hidden game's menu: {helpers.texts(c)}"
    assert not helpers.find(c, "Masquer")
    c.click("Ne plus masquer")
    helpers.wait_text(c, "Afficher le jeu masqué", gone=True)
    helpers.wait_text(c, "Cacher les jeux masqués", gone=True)
    shot = bench.shot("07-unhidden")
    final = tile_names(c, shot["path"], names, shot["dpr"])
    assert len(final) == len(names) and all(d < 40 for _n, d, _t in final), f"after unhide: {final}"

    # Library order last: a known bug, the rest of the scenario must not depend on it
    if not order_ok:
        bench.note(f"library order on screen {seen}, library.json {names}")
        pytest.xfail(bugs.LIBRARY_ORDER + f" -- seen {seen}, expected {names}")
