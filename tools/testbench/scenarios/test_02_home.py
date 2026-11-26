"""Scenario 2: the home of a paired PC (prototype V8): cards « Bureau » then « Playnite Fullscreen », the « Récents »
covers in the host's order (library.json: most recently played first), « Masquer » a game from its options, then
unhide it from the library's « Masqués · N » filter.

The V8 home has no names under the covers and no « Afficher le jeu masqué » button: hidden games come back only in
the library (prototype `filters()`: « Masqués · N » after Tous / Récents / Installés).  Written for the V8 port,
never run yet (port-notes.md)."""
import json
import time

import helpers
import hosts

GAME = "Tidewater Tactics"


def library_order(host):
    lib = json.loads((hosts.FIXTURES / host.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))
    return [g["name"] for g in lib["games"]]


def cover_tiles(c, page_items=None):
    """The cover tiles of the screen (home « Récents » or the library grid): HomeTile items taller than wide,
    « Tout voir » left out."""
    items = page_items or c.tree()
    tout_voir = [i for i in items if i.get("text") == "Tout voir"]
    out = []
    for t in items:
        if t["type"] != "HomeTile" or t["h"] < t["w"] * 1.2:
            continue
        if any(t["x"] <= tv["x"] < t["x"] + t["w"] and t["y"] <= tv["y"] < t["y"] + t["h"] for tv in tout_voir):
            continue
        out.append(t)
    return sorted(out, key=helpers.reading_order)


def names_of(c, shot, names):
    """(name, colour distance, tile) of each cover tile, by its generated colour (covers.py)."""
    import covers
    from PIL import Image
    expected = {n: covers._colour(n) for n in names}
    out = []
    with Image.open(shot["path"]) as img:
        img = img.convert("RGB")
        for t in cover_tiles(c):
            x = int((t["x"] + t["w"] / 2) * shot["dpr"])
            y = int((t["y"] + t["h"] * 0.15) * shot["dpr"])
            rgb = img.getpixel((x, y))
            best = min(expected, key=lambda n: sum((a - e) ** 2 for a, e in zip(rgb, expected[n])))
            out.append((best, round(sum((a - e) ** 2 for a, e in zip(rgb, expected[best])) ** 0.5), t))
    return out


def test_home_cards_recents_and_hide(bench):
    """Bureau then Playnite Fullscreen on one row; the 4 games in « Récents », each with its cover, in library.json's
    order; « Masquer » (options of a cover) takes the game out of Récents and adds « Masqués · 1 » to the library,
    where its options say « Ne plus masquer »."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.wait_page(c, "HomeView")
    names = library_order(atlas)
    deadline = time.time() + 20
    while len(cover_tiles(c)) < len(names) and time.time() < deadline:
        time.sleep(0.3)

    bureau = helpers.wait_text(c, "Bureau")[0]
    playnite = helpers.wait_text(c, "Playnite Fullscreen")[0]
    assert bureau["x"] < playnite["x"] and abs(bureau["y"] - playnite["y"]) < 5
    time.sleep(1.0)  # covers fetched from the host fade in
    shot = bench.shot("01-home")
    found = names_of(c, shot, names)
    seen = [n for n, _d, _t in found]
    bench.note(f"récents: {[(n, d) for n, d, _t in found]}")
    assert len(found) == len(names), f"{len(found)} cover tiles for {len(names)} games"
    assert all(d < 40 for _n, d, _t in found), f"a tile does not show its cover: {found}"
    assert seen == names, f"Récents must keep the host's order (most recently played first): {seen} != {names}"

    # « Masquer » from the game's options (right click on its cover: the popover)
    tile = next(t for n, _d, t in found if n == GAME)
    helpers.click_item(c, tile, "right")
    helpers.wait_text(c, "Masquer")
    bench.shot("02-options")
    c.click("Masquer")
    helpers.wait_text(c, "Masquer", gone=True)
    time.sleep(0.8)
    shot = bench.shot("03-hidden")
    after = [n for n, _d, _t in names_of(c, shot, names)]
    assert len(after) == len(names) - 1 and GAME not in after, f"after Masquer: {after}"

    # The library: « Masqués · 1 », the game there, « Ne plus masquer » in its options
    c.click("Tout voir →", index=-1)
    helpers.wait_page(c, "LibraryView")
    helpers.wait_text(c, "Masqués · 1")
    c.click("Masqués · 1")
    time.sleep(0.8)
    tiles = cover_tiles(c)
    assert len(tiles) == 1, f"the Masqués filter should hold the hidden game only: {len(tiles)} tiles"
    helpers.click_item(c, tiles[0], "right")
    helpers.wait_text(c, "Ne plus masquer")
    bench.shot("04-hidden-options")
    assert not helpers.find(c, "Masquer")
    c.click("Ne plus masquer")
    helpers.wait_text(c, "Masqués · 1", gone=True)

    # Back home: the game is in Récents again, at its place
    helpers.go_home(c)
    time.sleep(0.8)
    shot = bench.shot("05-unhidden")
    final = [n for n, _d, _t in names_of(c, shot, names)]
    assert final == names, f"after unhide: {final} != {names}"
