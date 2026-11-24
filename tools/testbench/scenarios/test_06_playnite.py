"""Scenario 6: a Playnite game while Playnite runs on the host.  Playnite Fullscreen is started, the client
disconnects (Playnite keeps running there); touching a game of the library then starts it inside Playnite: the
client resumes the session (no new launch) and the host gets playnite_launch; « Quitter <jeu> » in the in-game
menu stops the game only (playnite_stop) and the stream goes on, back in Playnite."""
import json
import time

import helpers
import hosts

GAME = "Nebula Drift"


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
    helpers.wait_text(c, "Bibliothèque Playnite")
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

    # Leave the stream (disconnect: Playnite stays running on the host)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"], 30)
