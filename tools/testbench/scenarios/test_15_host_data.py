"""Scenario 15: what LavApollo tells the clients for the prototype's screens (wave 5, data only, no UI).

* « Récents » follow the last play: a game played while a session runs on the host (Playnite Fullscreen, the client
  disconnected) moves to the front of the library.  The bug: LavApollo rebuilt its app list only when nothing ran,
  and the user always leaves Playnite running, so the order froze at the host's start.  Now /applist orders the games
  by the library as it is now (playnite::library_snapshot) and sends PlayniteLastActivity, which the clients sort on.
* Per game: installed or not (LavApollo Link 1.1 exports the games that are not installed too), hours played,
  last activity, a background image served by /appasset?asset=background (404 for a game without one).
* Per PC: when the running app started (CurrentGameElapsed), when the game running in Playnite started
  (PlayniteGameElapsed, from LavApollo Link's state.json), what kind of PC it is (HostFormFactor) and its link
  (HostLink), and the round trip the client measures (TCP connect of each poll)."""
import calendar
import datetime
import json
import time

import helpers
import hosts


def library(host):
    return json.loads((hosts.FIXTURES / host.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))


def epoch(iso):
    return calendar.timegm(datetime.datetime.strptime(iso, "%Y-%m-%dT%H:%M:%SZ").timetuple()) if iso else 0


def games(comp):
    return [a for a in comp["appDetails"] if a["playniteGame"]]


def record_play(host, name):
    """What LavApollo Link exports when Playnite starts a game: its lastActivity, now, in the PC's library.json."""
    f = host.dir / "pd" / "LavApollo" / "playnite" / "library.json"
    lib = json.loads(f.read_text(encoding="utf-8"))
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    for g in lib["games"]:
        if g["name"] == name:
            # Playnite's own format ("o"): 7 decimals, unlike the fixture's dates (whole seconds)
            g["lastActivity"] = now.strftime("%Y-%m-%dT%H:%M:%S.0000000Z")
    f.write_text(json.dumps(lib, ensure_ascii=False, indent=2), encoding="utf-8")
    return int(now.timestamp())


def tile_titles(c, names):
    """Name of each cover tile of the home, in reading order, from its menu (right click), whose title is the
    game's name. Waits for the title itself: the menu's rows can be laid out before it."""
    out = []
    for t in sorted(helpers.tiles(c), key=helpers.reading_order):
        helpers.click_item(c, t, "right")
        deadline = time.time() + 5
        shown = []
        while not any(n in shown for n in names) and time.time() < deadline:
            time.sleep(0.2)
            shown = helpers.popup_texts(c)
        c.call("key", key="Escape", target="qt")
        deadline = time.time() + 5
        while helpers.popup_texts(c) and time.time() < deadline:
            time.sleep(0.2)
        out.append(next((n for n in shown if n in names), None))
    return out


def write_playnite_state(host, game=None):
    """LavApollo Link's state.json: Playnite running, with `game` ({id, name, startedAt}) or none."""
    f = host.dir / "pd" / "LavApollo" / "playnite" / "state.json"
    state = {"version": 1, "playniteRunning": True, "mode": "fullscreen", "pid": 4242, "game": game,
             "updatedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
    f.write_text(json.dumps(state), encoding="utf-8")


def test_host_data_in_the_models(bench):
    """Installed, hours, last activity, background per game; kind of PC, link and round trip per PC; the client's
    library order follows the last activity; the session's and the Playnite game's start times."""
    atlas, cynisca = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    # The home shows the PC paired last: Test-Atlas, whose Playnite Fullscreen is launched below
    helpers.add_and_pair(c, cynisca)
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    lib = {g["name"]: g for g in library(atlas)["games"]}

    # Per game, as LavApollo sends it
    comp = helpers.wait_computer(c, atlas.name, lambda x: len(games(x)) == len(lib), timeout=30)
    got = {g["name"]: g for g in games(comp)}
    bench.note(f"games {[(g['name'], g['installed'], g['playtime'], g['lastActivity'], g['hasBackground']) for g in games(comp)]}")
    for name, g in lib.items():
        assert got[name]["installed"] == g["installed"], f"{name}: installed {got[name]}"
        assert got[name]["playtime"] == g["playtime"], f"{name}: playtime {got[name]}"
        assert got[name]["lastActivity"] == epoch(g["lastActivity"]), f"{name}: last activity {got[name]}"
        assert got[name]["hasBackground"] == bool(g.get("background")), f"{name}: background {got[name]}"
    assert any(not g["installed"] for g in got.values()), "the fixture must list a game that is not installed"
    # Most recently played first, never played last
    order = [g["name"] for g in games(comp)]
    expected = sorted(lib, key=lambda n: (epoch(lib[n]["lastActivity"]) == 0, -epoch(lib[n]["lastActivity"]), n.lower()))
    assert order == expected, f"library order {order}, expected {expected}"

    # Background: served for a game that has one (the fixture's 960 x 540 JPEG), 404 for one that has none; the
    # cover is unchanged
    with_bg = next(g for g in games(comp) if g["hasBackground"])
    without_bg = next(g for g in games(comp) if not g["hasBackground"])
    bg = c.call("appasset", host=atlas.name, appId=with_bg["id"], kind="background")
    assert (bg["width"], bg["height"]) == (960, 540), f"background of {with_bg['name']}: {bg}"
    none = c.call("appasset", host=atlas.name, appId=without_bg["id"], kind="background")
    assert (none["width"], none["height"]) == (0, 0), f"{without_bg['name']} has no background: {none}"
    cover = c.call("appasset", host=atlas.name, appId=with_bg["id"], kind="cover")
    assert (cover["width"], cover["height"]) == (300, 450), f"cover of {with_bg['name']}: {cover}"

    # Per PC: kind, link, round trip (loopback: at least 1 ms, well under a second)
    a = helpers.wait_computer(c, atlas.name, lambda x: x["online"] and x["latencyMs"] >= 1, timeout=20)
    y = helpers.wait_computer(c, cynisca.name, lambda x: x["online"] and x["latencyMs"] >= 1, timeout=20)
    bench.note(f"atlas {a['formFactor']}/{a['link']} {a['latencyMs']} ms; cynisca {y['formFactor']}/{y['link']} "
               f"{y['latencyMs']} ms")
    assert (a["formFactor"], a["link"]) == ("tower", "wired")
    assert (y["formFactor"], y["link"]) == ("laptop", "wifi")
    assert a["latencyMs"] < 1000 and y["latencyMs"] < 1000
    assert a["sessionStartedAt"] == 0, "nothing runs yet"

    # The session's start: Playnite Fullscreen launched now
    before = time.time()
    helpers.launch(c, "Playnite Fullscreen")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    a = helpers.wait_computer(c, atlas.name, lambda x: x["currentGameId"] != 0 and x["sessionStartedAt"] > 0,
                              timeout=20)
    started = a["sessionStartedAt"] / 1000
    bench.note(f"session started {started - before:+.1f} s after the click")
    assert before - 5 <= started <= time.time() + 1, f"session start {started}, launched at {before}"

    # The game running in Playnite, started 10 minutes ago according to LavApollo Link
    game = next(g for g in library(atlas)["games"] if g["name"] == "Cobalt Quarry")
    ten_min_ago = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(minutes=10)
    write_playnite_state(atlas, {"id": game["id"].lower(), "name": game["name"], "pid": 1234, "starting": False,
                                 "startedAt": ten_min_ago.strftime("%Y-%m-%dT%H:%M:%S.0000000Z")})
    try:
        a = helpers.wait_computer(c, atlas.name, lambda x: x["playniteGameName"] == game["name"]
                                  and x["playniteGameStartedAt"] > 0, timeout=20)
        drift = a["playniteGameStartedAt"] / 1000 - ten_min_ago.timestamp()
        bench.note(f"Playnite game start off by {drift:+.1f} s")
        assert abs(drift) <= 5, f"Playnite game started {drift:+.1f} s from LavApollo Link's startedAt"
    finally:
        write_playnite_state(atlas, None)
    helpers.wait_computer(c, atlas.name, lambda x: x["playniteGameName"] == "" and x["playniteGameStartedAt"] == 0,
                          timeout=20)


def test_recents_follow_a_play_while_a_session_runs(bench):
    """The récents bug: Playnite Fullscreen runs on the host (the client disconnected, as the user always leaves
    it), a game is played in it: it must lead the library without waiting for the session to end."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    names = [g["name"] for g in library(atlas)["games"]]
    game = names[-1]  # last of the library (never played): playing it must bring it to the front
    comp = helpers.wait_computer(c, atlas.name, lambda x: len(games(x)) == len(names), timeout=30)
    assert [g["name"] for g in games(comp)][0] != game

    helpers.launch(c, "Playnite Fullscreen")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    helpers.wait_computer(c, atlas.name, lambda x: x["online"] and x["currentGameId"] != 0, timeout=20)

    played_at = record_play(atlas, game)
    expected = [game] + [n for n in names if n != game]
    # the client fetches the app list every 10 polls (about 30 s); Playnite still runs on the host throughout
    comp = helpers.wait_computer(c, atlas.name, lambda x: [g["name"] for g in games(x)] == expected, timeout=70)
    assert comp["currentGameId"] != 0, "the session must still run on the host"
    first = games(comp)[0]
    assert first["lastActivity"] == played_at, f"{game}: last activity {first['lastActivity']}, played {played_at}"
    bench.note(f"library after the play, session still running: {[g['name'] for g in games(comp)]}")

    # The home's library follows (AppModel moves the row). The covers are dimmed while Playnite runs (their colour
    # says nothing): each tile is named by its menu's title
    helpers.wait_text(c, "Bibliothèque Playnite")
    time.sleep(1.0)
    bench.shot("01-library-after-play")
    seen = tile_titles(c, names)
    assert seen == expected, f"the home's library must follow: {seen}"
