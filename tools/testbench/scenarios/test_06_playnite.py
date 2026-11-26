"""Scenario 6: a Playnite game while Playnite runs on the host.  Playnite Fullscreen is started, the client
disconnects (Playnite keeps running there); touching a game of the library then starts it inside Playnite: the
client resumes the session (no new launch) and the host gets playnite_launch; « Quitter <jeu> » in the in-game
menu stops the game only (playnite_stop) and the stream goes on, back in Playnite.  Once the session is quit and
Playnite has recorded the play (the testbench, as LavApollo Link, writes the new lastActivity into the PC's
library.json), the game leads the library on the home, the other games keep their order, every cover intact (the
client moves the row, AppModel::updateAppList, LavArtemis-Qt 23f65be2)."""
import datetime
import json
import time

import helpers
import hosts

# Third in library.json (most recently played first): playing it must bring it to the front
GAME = "Tidewater Tactics"


def library_order(host):
    lib = json.loads((hosts.FIXTURES / host.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))
    return [g["name"] for g in lib["games"]]


def record_play(host, name):
    """What LavApollo Link exports after a play: the game's lastActivity, now, in the PC's library.json."""
    f = host.dir / "pd" / "LavApollo" / "playnite" / "library.json"
    lib = json.loads(f.read_text(encoding="utf-8"))
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    for g in lib["games"]:
        if g["name"] == name:
            g["lastActivity"] = now
    f.write_text(json.dumps(lib, ensure_ascii=False, indent=2), encoding="utf-8")


def game_id(host, name):
    lib = json.loads((hosts.FIXTURES / host.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))
    return next(g["id"] for g in lib["games"] if g["name"] == name).lower()


def test_playnite_game_inside_running_playnite(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="fps", value=30)
    helpers.wait_text(c, "Playnite Fullscreen")

    # Playnite Fullscreen runs on the host, then the client disconnects (the session stays there)
    helpers.launch(c, "Playnite Fullscreen")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["online"] and x["currentGameId"] != 0, timeout=20)
    playnite_app = comp["currentGameId"]
    helpers.wait_text(c, helpers.HOME_LIBRARY_TITLE)
    time.sleep(1.0)
    bench.shot("01-home-playnite-running")

    # The game, touched in the library: resumed inside Playnite
    tile = helpers.tile_by_name(c, GAME)
    before_log = bench.client.log_text()
    helpers.click_item(c, tile)
    deadline = time.time() + 40
    while not c.state()["session"]["streaming"]:
        assert time.time() < deadline, f"the game did not stream; texts {helpers.texts(c)[:10]}"
        time.sleep(0.3)
    log = bench.client.log_text()[len(before_log):]
    assert "/resume?" in log and "/launch?" not in log, "the game must join the running session (resume)"
    gid = game_id(atlas, GAME)
    deadline = time.time() + 10
    while not any(e.get("playnite_launch") == gid for e in helpers.host_events(atlas, "events")):
        assert time.time() < deadline, f"no playnite_launch for {gid}: {helpers.host_events(atlas, 'events')}"
        time.sleep(0.3)
    bench.shot("02-stream-game", window="stream")

    # « Quitter <jeu> » stops the game only: the stream goes on (back in Playnite)
    qm = helpers.open_quick_menu(c)
    label = f"Quitter {GAME}"
    helpers.wait_any_text(c, [label])
    bench.shot("03-quick-menu", window=qm)
    helpers.quick_menu_click(c, label, qm)
    deadline = time.time() + 10
    while not any(e.get("playnite_stop") == gid for e in helpers.host_events(atlas, "events")):
        assert time.time() < deadline, f"no playnite_stop for {gid}: {helpers.host_events(atlas, 'events')}"
        time.sleep(0.3)
    time.sleep(1.0)
    st = c.state()
    assert st["session"]["streaming"], "quitting the game must not end the stream"
    d1 = st["session"]["decoded"]
    time.sleep(1.0)
    assert c.state()["session"]["decoded"] > d1
    bench.note(f"playnite app {playnite_app}; events {helpers.host_events(atlas, 'events')[-4:]}")

    # « Quitter la session »: Playnite closes on the host, nothing runs there any more (a play recorded while a
    # session runs is scenario 15)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Quitter la session", qm)
    helpers.wait_any_text(c, [helpers.QUIT_CONFIRM])
    helpers.quick_menu_click(c, "Quitter la session", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    assert c.proc.poll() is None, f"the client exited (code {c.proc.returncode})"
    helpers.wait_computer(c, atlas.name, lambda x: x["online"] and x["currentGameId"] == 0, timeout=20)

    # Playnite records the play: the game moves to the front of the host's list, then of the home's library
    names = library_order(atlas)
    expected = [GAME] + [n for n in names if n != GAME]
    assert names[0] != GAME, "the game must not lead the library before it is played"
    record_play(atlas, GAME)
    # the client fetches the app list every 10 polls (about 30 s)
    helpers.wait_computer(c, atlas.name, lambda x: [a for a in x["apps"] if a in names] == expected, timeout=60)
    helpers.wait_text(c, helpers.HOME_LIBRARY_TITLE)
    deadline = time.time() + 10
    while True:
        time.sleep(1.0)  # covers fade in; the moved row settles
        shot = bench.shot("04-library-after-play")
        found = helpers.tile_names(c, shot["path"], names, shot["dpr"])
        seen = [n for n, _d, _t in found]
        if seen == expected or time.time() > deadline:
            break
    bench.note(f"library after the play: {[(n, d) for n, d, _t in found]}, expected {expected}")
    assert seen == expected, f"the played game must lead the library, the others keep their order: {seen}"
    assert all(d < 40 for _n, d, _t in found), f"a tile lost its cover after the move: {found}"
