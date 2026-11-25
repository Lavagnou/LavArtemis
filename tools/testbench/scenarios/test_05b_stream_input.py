"""Scenario 5b: input sent through the command channel during a stream reaches the host.
First a quiet window, the channel sending nothing: no mouse, key or gamepad event may reach the host. This only shows
that nothing spontaneous arrives; it does not prove the injected-input filter (the channel cannot push an unmarked
event, and nobody is expected to touch the stream window meanwhile). The filter itself (testinput.h) is proven by
review only for now.  Then, each one checked in the host's input.jsonl: a mouse move and a left click on the stream,
the test pad attached and its A button pressed and released, a key sent to the stream."""
import time

import helpers

# Light stream, like scenario 5
SIZE = (1280, 720)
FPS = 30
QUIET_S = 2.0
A_FLAG = 0x1000  # moonlight-common-c: the A button in the gamepad's buttonFlags
BUTTON_LEFT = 1  # moonlight-common-c BUTTON_LEFT
VK_A = 0x41
INPUT_TYPES = ("mouse_move", "mouse_abs", "mouse_button", "scroll", "hscroll", "key", "unicode", "touch", "pen")


def new_input(host, before):
    return helpers.host_events(host, "input")[before:]


def wait_input(host, before, predicate, timeout=5.0):
    """The host's input events since index `before`, as soon as predicate(events) holds (or at the deadline)."""
    deadline = time.time() + timeout
    while True:
        events = new_input(host, before)
        if predicate(events) or time.time() > deadline:
            return events
        time.sleep(0.2)


def test_stream_input_through_the_channel(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="width", value=SIZE[0])
    c.call("setting", action="set", key="height", value=SIZE[1])
    c.call("setting", action="set", key="fps", value=FPS)
    helpers.wait_text(c, "Bureau")
    helpers.launch(c, "Bureau")

    # Quiet window: nothing sent through the channel, nothing may reach the host
    time.sleep(1.0)  # what the stream start itself sends has arrived
    before = len(helpers.host_events(atlas, "input"))
    time.sleep(QUIET_S)
    stray = [e for e in new_input(atlas, before) if e.get("type") in INPUT_TYPES or str(e.get("type")).startswith("gamepad")]
    bench.note(f"quiet {QUIET_S:.0f}s: {len(stray)} input events")
    assert not stray, f"input reached the host while the channel sent nothing (not injected): {stray[:10]}"

    # Mouse: a move, then a left click, on the stream window
    before = len(helpers.host_events(atlas, "input"))
    c.call("mouse", action="move", x=100, y=100)
    c.call("mouse", action="move", x=300, y=200)
    moves = wait_input(atlas, before, lambda ev: any(e.get("type") in ("mouse_move", "mouse_abs") for e in ev))
    moves = [e for e in moves if e.get("type") in ("mouse_move", "mouse_abs")]
    bench.note(f"mouse moves at the host: {moves[:5]}")
    assert moves, "no mouse movement reached the host"
    assert any(e.get("dx") or e.get("dy") for e in moves if e["type"] == "mouse_move") or \
        any(e["type"] == "mouse_abs" for e in moves), moves

    before = len(helpers.host_events(atlas, "input"))
    c.call("mouse", action="click", button="left")
    buttons = wait_input(atlas, before, lambda ev: sum(e.get("type") == "mouse_button" for e in ev) >= 2)
    buttons = [(e["button"], e["down"]) for e in buttons if e.get("type") == "mouse_button"]
    bench.note(f"mouse buttons at the host: {buttons}")
    assert buttons == [(BUTTON_LEFT, True), (BUTTON_LEFT, False)], buttons

    # Gamepad: the test pad, attached, then A pressed and released
    before = len(helpers.host_events(atlas, "input"))
    c.gamepad_attach()
    c.call("gamepad", button="a", value=True)
    time.sleep(0.3)
    c.call("gamepad", button="a", value=False)

    def pad_flags(ev):
        return [e["buttons"] for e in ev if e.get("type") == "gamepad"]

    def a_pressed_then_released(ev):
        flags = pad_flags(ev)
        first = next((i for i, f in enumerate(flags) if f & A_FLAG), None)
        return first is not None and any(not f & A_FLAG for f in flags[first + 1:])

    pad = wait_input(atlas, before, a_pressed_then_released)
    allocs = [e for e in pad if e.get("type") == "gamepad_alloc"]
    bench.note(f"gamepad at the host: {len(allocs)} alloc, buttons {pad_flags(pad)[:10]}")
    assert allocs, "the test pad was never announced to the host (no gamepad_alloc)"
    assert a_pressed_then_released(pad), f"A pressed then released never reached the host: {pad_flags(pad)}"

    # Keyboard: a key sent to the stream
    before = len(helpers.host_events(atlas, "input"))
    c.call("key", key="A", target="stream")
    keys = wait_input(atlas, before, lambda ev: any(e.get("type") == "key" and not e["down"] for e in ev))
    keys = [(e["vk"], e["down"]) for e in keys if e.get("type") == "key"]
    bench.note(f"keys at the host: {keys}")
    assert keys == [(VK_A, True), (VK_A, False)], keys

    assert c.state()["session"]["streaming"], "the stream ended during the input checks"

    # Leave the way a user does: pad unplugged, « Déconnecter » from the in-game menu, back home
    c.call("gamepad", detach=True)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
