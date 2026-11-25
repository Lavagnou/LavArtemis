"""Scenario 13: the command line, art:// links and .art files (LavArtemis-Qt cli/, main.cpp), given to the client the
way a shell, a desktop shortcut or the OS link handler would (`bench.restart_client(args=[...])`).

- stream / quit / not found: `stream <PC> Bureau` streams it, `quit <PC>` quits it on the host and the process ends by
  itself (exit 0), `stream <PC> <unknown>` says so in French (the message stays: closing it quits the process).
- Playnite: `stream <PC> <Playnite game>` while Playnite runs on the PC starts the game inside Playnite (resume +
  playnite_launch), as a touch on the home does, with no question about quitting Playnite (findings B7).
- .art: a valid file streams its app; a malformed file or art:// link opens the home with a French message that
  says what is wrong (findings B6), instead of a silent home."""
import json
import time

import helpers
import hosts

GAME = "Tidewater Tactics"


def requests(host, kind=None):
    return [e for e in helpers.host_events(host, "events", "request") if kind is None or e["request"] == kind]


def wait_streaming(c, timeout=40.0, forbidden=()):
    deadline = time.time() + timeout
    while True:
        st = c.state()
        if st["session"]["streaming"] and st["session"]["decoded"] > 0:
            return st
        shown = helpers.texts(c) if st["page"] else []
        for f in forbidden:
            assert not any(f in t for t in shown), f"{f!r} shown: {shown[:12]}"
        assert time.time() < deadline, f"no stream after {timeout:.0f}s: page {st['page']}, texts {shown[:12]}"
        time.sleep(0.3)


def paired_client(bench, host):
    c = bench.start_client()
    helpers.add_and_pair(c, host)
    helpers.wait_apps(c, host.name)
    c.call("setting", action="set", key="fps", value=30)
    return c


def test_cli_stream_quit_not_found(bench):
    atlas, = bench.up("atlas-test")
    paired_client(bench, atlas)

    c = bench.restart_client(args=["stream", atlas.name, "Bureau"])
    wait_streaming(c)
    bench.shot("01-cli-stream", window="stream")
    assert len(requests(atlas, "launch")) == 1
    bench.client.stop()  # the host keeps Bureau running: `quit` below ends it

    cancels = len(requests(atlas, "cancel"))
    bench.restart_client(args=["quit", atlas.name])
    code = bench.expect_client_exit(30)
    assert code == 0, f"`quit` must end the process normally, code {code}"
    assert len(requests(atlas, "cancel")) == cancels + 1, "the host app must be quit"

    c = bench.restart_client(args=["stream", atlas.name, "Nope"])
    msg = helpers.wait_any_text(c, ["Nope"], timeout=30)
    bench.shot("02-not-found")
    assert msg == "L'application Nope n'a pas été trouvée", msg


def test_cli_stream_playnite_game_inside_playnite(bench):
    atlas, = bench.up("atlas-test")
    c = paired_client(bench, atlas)
    helpers.wait_text(c, "Playnite Fullscreen")
    helpers.launch(c, "Playnite Fullscreen")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    helpers.wait_computer(c, atlas.name, lambda x: x["currentGameId"] != 0, timeout=20)
    n_launch, n_resume = len(requests(atlas, "launch")), len(requests(atlas, "resume"))

    c = bench.restart_client(args=["stream", atlas.name, GAME])
    # Never asked to quit Playnite: the game starts inside it
    wait_streaming(c, forbidden=["Quitter Playnite"])
    bench.shot("01-game-inside-playnite", window="stream")
    assert len(requests(atlas, "launch")) == n_launch, "the game must join the running session, not launch"
    assert len(requests(atlas, "resume")) == n_resume + 1
    lib = json.loads((hosts.FIXTURES / atlas.fixture / "playnite" / "library.json").read_text(encoding="utf-8"))
    gid = next(g["id"] for g in lib["games"] if g["name"] == GAME).lower()
    deadline = time.time() + 10
    while not any(e.get("playnite_launch") == gid for e in helpers.host_events(atlas, "events")):
        assert time.time() < deadline, f"no playnite_launch for {gid}"
        time.sleep(0.3)


def test_art_file_and_links(bench):
    atlas, = bench.up("atlas-test")
    c = paired_client(bench, atlas)
    uuid = helpers.wait_computer(c, atlas.name)["uuid"]

    # A valid .art file (Android's format) streams its app
    art = bench.workdir / "Bureau sur Atlas.art"
    art.write_text(f"# Artemis app entry\n[host_uuid] {uuid}\n[host_name] {atlas.name}\n[app_name] Bureau\n",
                   encoding="utf-8")
    c = bench.restart_client(args=[str(art)])
    wait_streaming(c)
    bench.shot("01-art-file", window="stream")

    # A malformed file: the home, and a French message saying what is wrong
    bad = bench.workdir / "Cassé.art"
    bad.write_text("ceci n'est pas un raccourci\n", encoding="utf-8")
    c = bench.restart_client(args=[str(bad)])
    msg = helpers.wait_any_text(c, ["n'est pas un fichier .art valide"], timeout=20)
    bench.shot("02-art-malformed")
    assert str(bad) in msg or "Cassé.art" in msg, msg
    assert c.state()["page"] == "HomeView"
    helpers.click(c, "OK")
    helpers.wait_text(c, "n'est pas un fichier .art valide", contains=True, gone=True)
    assert c.proc.poll() is None, "the message closes; the app stays"

    # A launch link without an app
    c = bench.restart_client(args=[f"art://launch?host_uuid={uuid}"])
    msg = helpers.wait_any_text(c, ["Aucune appli indiquée"], timeout=20)
    bench.shot("03-art-link-no-app")
    assert c.state()["page"] == "HomeView"
