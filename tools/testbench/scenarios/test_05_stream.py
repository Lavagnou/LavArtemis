"""Scenario 5: a real stream of « Bureau ».  Frames decoded and shown (the stream window captured by PrintWindow:
not black, the host's frame numbers moving forward), the stream negotiated at the expected size, a shortcut sent
from the in-game menu reaching the host as keys, the microphone (enabled in Réglages) reaching the host, and
« Quitter la session » quitting the app on the host.  The test client never takes the foreground (conftest)."""
import time

import client as clientmod
import frames
import helpers

# The test client's settings: 1280x720 (the defaults of a reset client), 30 fps to keep the software encode light.
# The negotiated size follows from them and the display the fake PC announces (atlas.display).
SIZE = (1280, 720)
FPS = 30


def test_stream_desktop_frames_keys_mic_quit(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="width", value=SIZE[0])
    c.call("setting", action="set", key="height", value=SIZE[1])
    c.call("setting", action="set", key="fps", value=FPS)

    # Micro enabled the way the user does it: Réglages › Audio
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Audio")
    helpers.set_switch(c, "Envoyer mon micro au PC distant", "micPassthroughEnabled", True)
    bench.shot("01-mic-on")
    helpers.go_home(c)
    helpers.wait_text(c, "Bureau")

    # HostDisplayFit applies only without a virtual display (or on a resume): the expectation below relies on it
    vd = c.call("setting", action="get", key="useVirtualDisplay")["value"]
    assert str(vd).lower() in ("false", "0", "none", ""), f"virtual display on ({vd!r}): the fit below doesn't apply"
    st = helpers.launch(c, "Bureau")
    sess = st["session"]
    # Negotiated: no virtual display is asked, so the client fits its setting to the display the fake PC announces
    # (the fixture's test_host_display, never this PC's monitor): HostDisplayFit, 1280x720 at 30 fps for the default
    # 1920x1080@60
    expected = helpers.host_display_fit(atlas.display, (SIZE[0], SIZE[1], FPS))
    bench.note(f"host display {atlas.display}, setting {SIZE}@{FPS}: expected {expected}")
    assert (sess["width"], sess["height"], sess["fps"]) == expected, sess
    assert c.stream_size() == (expected[0], expected[1], "negotiated")

    # Frames keep arriving and being decoded
    d1 = c.state()["session"]["decoded"]
    time.sleep(1.5)
    s2 = c.state()["session"]
    assert s2["decoded"] > d1 > 0 and s2["rendered"] > 0, (d1, s2)

    # The stream window as the user sees it: PrintWindow, never black, frame numbers moving forward
    n1 = c.frame_number(bench.captures / "02-stream-a.png")
    time.sleep(0.7)
    n2 = c.frame_number(bench.captures / "03-stream-b.png")
    bench.note(f"frame numbers {n1} -> {n2}; session {s2}")
    assert frames.is_increasing([n1, n2], allow_equal=False), (n1, n2)

    # Micro: the host counts the frames it receives
    time.sleep(1.5)
    m1 = [e["mic_frames"] for e in helpers.host_events(atlas, "events", "mic_frames")]
    time.sleep(2.0)
    m2 = [e["mic_frames"] for e in helpers.host_events(atlas, "events", "mic_frames")]
    bench.note(f"mic_frames {m1[-3:]} then {m2[-3:]}")
    assert m1 and m2 and m2[-1] > m1[-1] > 0, (m1[-3:], m2[-3:])

    # In-game menu › Envoyer des touches › Alt+Entrée: the host gets Alt down, Enter down/up, Alt up
    qm = helpers.open_quick_menu(c)
    bench.shot("04-quick-menu", window=qm)
    helpers.assert_quick_menu_tiles(c, qm)
    helpers.quick_menu_click(c, "Envoyer des touches", qm)
    helpers.wait_any_text(c, ["Alt+Entrée"])
    bench.shot("05-send-keys", window=qm)
    before = len(helpers.host_events(atlas, "input"))
    helpers.quick_menu_click(c, "Alt+Entrée", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    deadline = time.time() + 5
    keys = []
    while time.time() < deadline:
        keys = [(e["vk"], e["down"]) for e in helpers.host_events(atlas, "input")[before:] if e.get("type") == "key"]
        if (164, False) in keys:
            break
        time.sleep(0.2)
    bench.note(f"keys after Alt+Entrée: {keys}")
    assert keys == [(164, True), (13, True), (13, False), (164, False)], keys  # VK_LMENU, VK_RETURN

    # Still streaming after the menu
    assert c.state()["session"]["streaming"]

    # In-game menu › Quitter la session › confirm: the app quits on the host
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Quitter la session", qm)
    helpers.wait_any_text(c, [helpers.QUIT_CONFIRM])
    bench.shot("06-confirm-quit", window=qm)
    helpers.quick_menu_click(c, "Quitter la session", qm)
    # The client must stay alive all along: an exit or a crash is a failure with its exit code, never a pass
    deadline = time.time() + 30
    st = None
    while True:
        code = c.proc.poll()
        assert code is None, f"the client exited after « Quitter la session » (exit code {code}): it must go home"
        try:
            st = c.state()
        except clientmod.ClientError as e:
            raise AssertionError(f"the command channel broke after « Quitter la session » ({e}); client exit code "
                                 f"{c.proc.poll()}")
        if not st["session"]["active"] and st["page"] == "HomeView":
            break
        assert time.time() < deadline, f"the session did not end; state {st}"
        time.sleep(0.3)

    comp = helpers.wait_computer(c, atlas.name, lambda x: x["online"] and x["currentGameId"] == 0, timeout=20)
    assert comp["currentGameId"] == 0, comp
    # The home shows it: no session left on screen (no « Reprendre », « En ligne » in the header). The UI follows
    # the host's state a moment after state.computers (signal queued to the GUI thread), not a poll later.
    helpers.wait_text(c, "Reprendre", gone=True, timeout=3)
    helpers.wait_text(c, "En ligne", timeout=3)
    bench.shot("07-home-after-quit")
    assert c.proc.poll() is None, f"the client exited at the end (exit code {c.proc.returncode})"
