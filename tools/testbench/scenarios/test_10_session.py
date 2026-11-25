"""Scenario 10: the life of a streaming session and the in-game menu, engine side.

S1 host display change mid-session: the client reconnects with a *resume* at the new mode, never quits the app on the
   host (even with « Quitter l'appli sur le PC distant à la fin »), and shows that it reconnects.
S2 no reconnect loop: a stream launched with a virtual display reconnects at most once to the host display's mode.
S3 failures: a launch the host refuses (French error, client alive, the next launch works); a quit the host refuses
   (« Le jeu en cours n'a pas été lancé par ce PC… »).
S4 the host ends the stream while the in-game menu is open: the menu goes away with the session, nothing left that
   could end a later stream; the menu opens centred over the stream window.
S5 « Quitter la session » confirmed twice: one quit, the client stays, the next stream runs.
S6 clipboard: sent at the start, fetched on demand and on « Déconnecter », each result notified; nothing sent once
   the stream is over.
S7 server commands: none on the host -> « Aucune commande de serveur » and no guessed endpoint probed; listed ones
   run on the host.
S8 shortcuts in the stream: performance overlay in French, microphone mute, zoom.
S9 the zoom of a stream does not leak into the next one.

Fake PC capabilities used (LavApollo test host mode): `request` events (launch / resume / cancel and their
parameters), /api/test/display, /api/test/fail, /api/test/terminate, /api/test/clipboard, server_cmd in pc.json."""
import ctypes
import time

import client as clientmod
import hosts
import helpers

SIZE = (1280, 720)
FPS = 30


# ---------------------------------------------------------------- helpers

def requests(host, kind=None):
    """launch / resume / cancel requests the fake PC received, oldest first: [{"request", "params", "t"}]."""
    return [e for e in helpers.host_events(host, "events", "request") if kind is None or e["request"] == kind]


def setup(bench, fixture="atlas-test", overrides=None, settings=()):
    host, = bench.up(fixture, overrides={fixture: overrides} if overrides else None)
    c = bench.start_client()
    helpers.add_and_pair(c, host)
    helpers.wait_apps(c, host.name)
    for key, value in [("width", SIZE[0]), ("height", SIZE[1]), ("fps", FPS)] + list(settings):
        c.call("setting", action="set", key=key, value=value)
    return host, c


def alive(c, what):
    code = c.proc.poll()
    assert code is None, f"the client exited {what} (exit code {code})"


def wait_home(c, what, timeout=30.0):
    """The session over and the home shown, the client alive all along (an exit is a failure with its code)."""
    deadline = time.time() + timeout
    while True:
        alive(c, what)
        try:
            st = c.state()
        except clientmod.ClientError as e:
            raise AssertionError(f"the command channel broke {what} ({e}); exit code {c.proc.poll()}")
        if not st["session"]["active"] and st["page"] == "HomeView" and not st["pageBusy"]:
            return st
        assert time.time() < deadline, f"the session did not end {what}; state {st}"
        time.sleep(0.3)


def close_dialog(c):
    """The error dialog's OK button (Qt's standard button: its text is Qt's)."""
    for label in ("OK", "Ok"):
        if helpers.find(c, label):
            helpers.click(c, label)
            return
    c.call("key", key="Escape", target="qt")


def menu_click(c, text, qm):
    """Click an entry of the open in-game menu, scrolling its list down to it when it is below the fold."""
    # The list keeps its scroll position from one opening to the next: down, then back up
    for dy in [0] + [-2] * 6 + [2] * 12:
        if dy:
            c.call("wheel", dy=dy, window=qm)
            time.sleep(0.3)
        if helpers.find(c, text, window=qm):
            helpers.quick_menu_click(c, text, qm)
            return
    raise AssertionError(f"no {text!r} in the in-game menu: {helpers.texts(c, window=qm)}")


def menu_entry(c, text):
    """Open the in-game menu and click one of its entries; returns the menu's window index.  A menu still closing
    from the previous entry (it closes a second after) would be toggled shut by the shortcut: wait for it first."""
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    qm = helpers.open_quick_menu(c)
    menu_click(c, text, qm)
    return qm


def wait_stream(c, predicate=lambda s: True, timeout=30.0, what="a stream"):
    deadline = time.time() + timeout
    while True:
        alive(c, f"while waiting for {what}")
        st = c.state()
        s = st["session"]
        if s["streaming"] and s["decoded"] > 0 and predicate(s):
            return st
        assert time.time() < deadline, f"no {what} after {timeout:.0f}s: session {s}"
        time.sleep(0.3)


def stream_runs(c, seconds, what):
    """The stream keeps running for `seconds` (a stale quit request would end it at once)."""
    end = time.time() + seconds
    while time.time() < end:
        alive(c, what)
        s = c.state()["session"]
        assert s["streaming"], f"the stream ended by itself {what}: {s}"
        time.sleep(0.4)


def toasts_since(c, n):
    return c.state()["toasts"][n:]


def wait_toast(c, n, contains, timeout=10.0):
    deadline = time.time() + timeout
    new = []
    while time.time() < deadline:
        new = toasts_since(c, n)
        if any(contains in t for t in new):
            return new
        time.sleep(0.2)
    raise AssertionError(f"no notification containing {contains!r}; shown since: {new}")


class _RECT(ctypes.Structure):
    _fields_ = [("left", ctypes.c_long), ("top", ctypes.c_long), ("right", ctypes.c_long), ("bottom", ctypes.c_long)]


class _POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]


def screen_rects(c):
    """(stream client area, quick menu window) in physical screen pixels, read from Windows itself (this process is
    DPI aware: client.py): independent of how Qt and SDL count their coordinates."""
    u32 = ctypes.windll.user32
    stream = qm = None
    for w in c.windows():
        hwnd = ctypes.c_void_p(int(str(w["hwnd"]), 0))
        if w.get("kind") == "stream":
            r = _RECT()
            u32.GetClientRect(hwnd, ctypes.byref(r))
            p = _POINT(0, 0)
            u32.ClientToScreen(hwnd, ctypes.byref(p))
            stream = (p.x, p.y, r.right, r.bottom)
        elif w.get("role") == "quickmenu":
            r = _RECT()
            u32.GetWindowRect(hwnd, ctypes.byref(r))
            qm = (r.left, r.top, r.right - r.left, r.bottom - r.top)
    return stream, qm


def assert_menu_centred(c, bench):
    stream, qm = screen_rects(c)
    assert stream and qm, f"stream {stream}, menu {qm}"
    sc = (stream[0] + stream[2] / 2, stream[1] + stream[3] / 2)
    mc = (qm[0] + qm[2] / 2, qm[1] + qm[3] / 2)
    dpr = next((w["dpr"] for w in c.windows() if w.get("role") == "quickmenu"), 1)
    bench.note(f"stream client area {stream}, menu {qm} (dpr {dpr}): centres {sc} / {mc}")
    assert abs(sc[0] - mc[0]) <= 3 and abs(sc[1] - mc[1]) <= 3, \
        f"the menu is not centred over the stream: stream {stream}, menu {qm}, dpr {dpr}"


# ---------------------------------------------------------------- S1, S2: host display change

def test_s1_display_change_resumes_without_quitting(bench):
    atlas, c = setup(bench, settings=[("quitAppAfter", True)])
    st = helpers.launch(c, "Bureau")
    first = helpers.host_display_fit(atlas.display, (SIZE[0], SIZE[1], FPS))
    assert (st["session"]["width"], st["session"]["height"], st["session"]["fps"]) == first, st["session"]
    restarts = st["session"]["restarts"]
    n_requests = len(requests(atlas))

    # The PC's display goes ultra-wide: the fitted target changes, the client reconnects to the same session
    new_display = (2560, 1080, 60)
    hosts.set_display(atlas, new_display)
    target = helpers.host_display_fit(new_display, (SIZE[0], SIZE[1], FPS))
    bench.note(f"display {new_display}: target {target}")

    # While it reconnects, the window says so (Android: « Écran du PC modifié : reconnexion… »)
    seen_reconnect = False
    deadline = time.time() + 40
    while True:
        alive(c, "during the reconnection")
        s = c.state()["session"]
        if not seen_reconnect and not s["streaming"]:
            try:
                seen_reconnect = any("reconnexion" in t for t in helpers.texts(c, window=0))
            except clientmod.ClientError:
                pass
        if s["restarts"] > restarts and s["streaming"] and s["decoded"] > 0:
            break
        assert time.time() < deadline, f"no reconnection at the new mode: session {s}, requests " \
                                       f"{[r['request'] for r in requests(atlas)[n_requests:]]}"
        time.sleep(0.15)
    s = c.state()["session"]
    after = requests(atlas)[n_requests:]
    bench.note(f"requests after the change: {[(r['request'], r['params'].get('mode')) for r in after]}")
    assert not [r for r in after if r["request"] == "cancel"], \
        f"the app was quit on the host by a display-change reconnection: {[r['request'] for r in after]}"
    assert [r["request"] for r in after] == ["resume"], \
        f"the reconnection must resume the running session: {[r['request'] for r in after]}"
    assert (s["width"], s["height"], s["fps"]) == target, s
    assert s["restarts"] == restarts + 1, s
    assert seen_reconnect, "nothing said « Écran du PC modifié : reconnexion… » while reconnecting"

    # Settled: no further reconnection
    stream_runs(c, 6, "after the reconnection")
    assert c.state()["session"]["restarts"] == restarts + 1

    # « Déconnecter » now: the stream ended normally, « Quitter l'appli… à la fin » quits the app this time
    menu_entry(c, "Déconnecter")
    wait_home(c, "after « Déconnecter »")
    deadline = time.time() + 10
    while not requests(atlas, "cancel") and time.time() < deadline:
        time.sleep(0.3)
    assert requests(atlas, "cancel"), "« Quitter l'appli sur le PC distant à la fin » did not quit the app"


def test_s2_virtual_display_reconnects_at_most_once(bench):
    # Launched with a virtual display at 1280x800: the fake PC keeps announcing 1920x1080, so the stream is not at
    # the display's fitted mode (1280x720) and the client reconnects once, by a resume, where the fit applies
    setting = (1280, 800)
    atlas, c = setup(bench, settings=[("width", setting[0]), ("height", setting[1]), ("useVirtualDisplay", True)])
    st = helpers.launch(c, "Bureau")
    restarts = st["session"]["restarts"]
    target = helpers.host_display_fit(atlas.display, (setting[0], setting[1], FPS))
    bench.note(f"first stream {st['session']['width']}x{st['session']['height']}, fitted target {target}")
    time.sleep(16)  # a loop reconnects every 5-8 s
    alive(c, "after 16 s of stream")
    s = c.state()["session"]
    kinds = [r["request"] for r in requests(atlas)]
    bench.note(f"after 16 s: restarts {s['restarts'] - restarts}, requests {kinds}, session {s}")
    assert s["restarts"] - restarts <= 1, f"reconnect loop: {s['restarts'] - restarts} reconnections, {kinds}"
    assert kinds.count("launch") == 1, f"a reconnection launched instead of resuming: {kinds}"
    assert s["streaming"] and (s["width"], s["height"]) == target[:2], s


# ---------------------------------------------------------------- S3: failures

def test_s3_refused_launch_and_refused_quit(bench):
    atlas, c = setup(bench)
    hosts.fail_next(atlas, "launch")
    helpers.wait_settled(c, "Bureau")
    c.click("Bureau")
    text = helpers.wait_any_text(c, ["L'hôte a renvoyé une erreur"], timeout=30)
    bench.shot("01-launch-refused")
    wait_home(c, "after a refused launch")
    bench.note(f"error: {text}")
    close_dialog(c)
    helpers.wait_text(c, "L'hôte a renvoyé une erreur", contains=True, gone=True)

    # The next launch works
    helpers.launch(c, "Bureau")
    stream_runs(c, 2, "after a refused launch")

    # The host refuses to quit (another device started the game): said in French, the client stays
    hosts.fail_next(atlas, "cancel")
    qm = menu_entry(c, "Quitter la session")
    helpers.wait_any_text(c, ["Tu veux vraiment quitter ?"])
    helpers.quick_menu_click(c, "Quitter la session", qm)
    found = helpers.wait_any_text(c, ["Le jeu en cours n'a pas été lancé par ce PC"], timeout=30)
    bench.shot("02-quit-refused")
    bench.note(f"quit error: {found}")
    wait_home(c, "after a refused quit")


# ---------------------------------------------------------------- S4, S5: menu and quit requests

def test_s4_host_ends_stream_with_menu_open(bench):
    atlas, c = setup(bench)
    helpers.launch(c, "Bureau")
    qm = helpers.open_quick_menu(c)
    problems = []
    try:
        assert_menu_centred(c, bench)
    except AssertionError as e:
        problems.append(str(e))
    bench.shot("01-menu", window=qm)

    assert hosts.terminate(atlas), "no app running on the fake PC"
    wait_home(c, "after the host ended the stream")
    deadline = time.time() + 5
    while c.state()["quickMenu"] and time.time() < deadline:
        time.sleep(0.2)
    left_open = c.state()["quickMenu"]
    if left_open:
        # What the user would do with a menu left over: it must not take the client down
        idx = helpers.quick_menu_window(c)
        if idx is not None:
            c.cmd("click", text="Déconnecter", window=idx)
        time.sleep(3)
        alive(c, "after « Déconnecter » in a menu left over from an ended stream")
        problems.append("the in-game menu stayed open after the stream ended")

    # Nothing left that ends the next stream (the app ended with the stream: a new launch)
    helpers.wait_text(c, "Reprendre", gone=True, timeout=20)
    try:
        helpers.launch(c, "Bureau")
        stream_runs(c, 4, "after a stream the host ended")
    except AssertionError as e:
        problems.append(str(e))
    assert not problems, "\n".join(problems)


def test_s5_quit_confirmed_twice(bench):
    atlas, c = setup(bench)
    helpers.launch(c, "Bureau")
    qm = menu_entry(c, "Quitter la session")
    helpers.wait_any_text(c, ["Tu veux vraiment quitter ?"])
    c.call("click", text="Quitter la session", window=qm)
    c.cmd("click", text="Quitter la session", window=qm)  # the second one may find the menu gone: fine
    wait_home(c, "after « Quitter la session » twice")
    time.sleep(2)
    alive(c, "after « Quitter la session » twice")
    assert len(requests(atlas, "cancel")) == 1, [r["request"] for r in requests(atlas)]

    helpers.wait_text(c, "Reprendre", gone=True, timeout=20)
    helpers.launch(c, "Bureau")
    stream_runs(c, 4, "after a double « Quitter la session »")


# ---------------------------------------------------------------- S6: clipboard

def test_s6_clipboard(bench):
    atlas, c = setup(bench, settings=[("clipboardSyncEnabled", True), ("clipboardSyncNotifications", True)])
    problems = []

    # Sent at the start of the stream (as Android does), the result notified
    c.call("clipboard", action="set", text="copié avant le stream")
    n = len(c.state()["toasts"])
    helpers.launch(c, "Bureau")
    deadline = time.time() + 10
    while hosts.clipboard(atlas) != "copié avant le stream" and time.time() < deadline:
        time.sleep(0.3)
    if hosts.clipboard(atlas) != "copié avant le stream":
        problems.append(f"not sent at the start of the stream: host has {hosts.clipboard(atlas)!r}")
    try:
        wait_toast(c, n, "Presse-papiers envoyé")
    except AssertionError as e:
        problems.append(f"start: {e}")

    # Fetched on demand: « Récupérer le presse-papiers »
    hosts.clipboard(atlas, "copié sur le PC")
    n = len(c.state()["toasts"])
    menu_entry(c, "Récupérer le presse-papiers")
    deadline = time.time() + 10
    while c.call("clipboard")["text"] != "copié sur le PC" and time.time() < deadline:
        time.sleep(0.3)
    if c.call("clipboard")["text"] != "copié sur le PC":
        problems.append(f"« Récupérer le presse-papiers » did nothing: {c.call('clipboard')['text']!r}")
    try:
        wait_toast(c, n, "Presse-papiers récupéré")
    except AssertionError as e:
        problems.append(f"fetch: {e}")

    # Sent on demand: « Envoyer le presse-papiers » (forced: even what came from the PC)
    c.call("clipboard", action="set", text="envoyé à la main")
    deadline = time.time() + 5
    # a copy made here while the stream runs goes to the PC at once
    while hosts.clipboard(atlas) != "envoyé à la main" and time.time() < deadline:
        time.sleep(0.2)
    if hosts.clipboard(atlas) != "envoyé à la main":
        problems.append(f"a copy made during the stream was not sent: host has {hosts.clipboard(atlas)!r}")
    hosts.clipboard(atlas, "autre chose")
    n = len(c.state()["toasts"])
    menu_entry(c, "Envoyer le presse-papiers")
    deadline = time.time() + 10
    while hosts.clipboard(atlas) != "envoyé à la main" and time.time() < deadline:
        time.sleep(0.3)
    if hosts.clipboard(atlas) != "envoyé à la main":
        problems.append(f"« Envoyer le presse-papiers » did nothing: host has {hosts.clipboard(atlas)!r}")
    try:
        wait_toast(c, n, "Presse-papiers envoyé")
    except AssertionError as e:
        problems.append(f"send: {e}")

    # Fetched when leaving with « Déconnecter » (as Android does)
    hosts.clipboard(atlas, "copié sur le PC en dernier")
    menu_entry(c, "Déconnecter")
    wait_home(c, "after « Déconnecter »")
    deadline = time.time() + 10
    while c.call("clipboard")["text"] != "copié sur le PC en dernier" and time.time() < deadline:
        time.sleep(0.3)
    if c.call("clipboard")["text"] != "copié sur le PC en dernier":
        problems.append(f"not fetched on « Déconnecter »: {c.call('clipboard')['text']!r}")

    # Over: a copy on this PC goes nowhere
    # (the host refuses a client that no longer streams: the attempt shows in the client's log only)
    sets_before = len(helpers.host_events(atlas, "events", "clipboard_set"))
    log_before = len(c.log_text())
    c.call("clipboard", action="set", text="copié après le stream")
    time.sleep(4)
    late = helpers.host_events(atlas, "events", "clipboard_set")[sets_before:]
    tried = [l for l in c.log_text()[log_before:].splitlines() if "actions/clipboard" in l]
    if late or tried:
        problems.append(f"sent to the PC after the stream: {late or tried[:3]}")
    alive(c, "after the clipboard checks")
    bench.note(f"toasts: {c.state()['toasts']}")
    assert not problems, "\n".join(problems)


# ---------------------------------------------------------------- S7: server commands

def test_s7_server_commands(bench):
    atlas, c = setup(bench)
    helpers.launch(c, "Bureau")
    menu_entry(c, "Commandes du PC hôte")
    time.sleep(1)
    log = c.log_text()
    probed = [p for p in ("servercommands", "api/commands", "commands.xml") if p in log]
    assert not probed, f"the client probed guessed endpoints: {probed}"
    helpers.wait_any_text(c, ["Aucune commande de serveur"])
    bench.shot("01-no-commands", window=helpers.quick_menu_window(c))
    c.call("key", key="Escape", target="qt", window=helpers.quick_menu_window(c))
    menu_entry(c, "Déconnecter")
    wait_home(c, "after « Déconnecter »")

    # A host with commands: listed, and run on the host
    bench.restart_host("atlas-test", {"server_cmd": [{"name": "Dire bonjour", "cmd": "echo bonjour",
                                                      "elevated": False}]})
    helpers.wait_computer(c, atlas.name, lambda x: x["online"])
    time.sleep(4)  # the next poll reads the commands from serverinfo
    helpers.launch(c, "Bureau")  # the restarted PC runs nothing
    n = len(c.state()["toasts"])
    qm = menu_entry(c, "Commandes du PC hôte")
    helpers.wait_any_text(c, ["Dire bonjour"])
    bench.shot("02-commands", window=qm)
    helpers.quick_menu_click(c, "Dire bonjour", qm)
    deadline = time.time() + 10
    ran = []
    while not ran and time.time() < deadline:
        ran = [e for e in helpers.host_events(atlas, "commands") if "bonjour" in str(e.get("cmd"))]
        time.sleep(0.3)
    assert ran, f"the command did not run on the host: {helpers.host_events(atlas, 'commands')}"
    wait_toast(c, n, "Dire bonjour")


# ---------------------------------------------------------------- S8, S9: shortcuts, zoom

def test_s8_stream_shortcuts(bench):
    atlas, c = setup(bench, settings=[("micPassthroughEnabled", True)])
    helpers.launch(c, "Bureau")

    # Performance overlay (Ctrl+Alt+Shift+S): in French, with the wording of Android's perf_overlay_*
    c.call("key", key="S", modifiers="Ctrl+Alt+Shift", target="stream")
    st = c.wait_for(lambda s: s["session"]["overlayStats"]["shown"] and s["session"]["overlayStats"]["text"], 10)
    time.sleep(2.5)  # filled at the next stats window
    text = c.state()["session"]["overlayStats"]["text"]
    bench.note(f"overlay: {text!r}")
    for english in ("Video stream", "Decoder", "Incoming frame rate", "Frames dropped", "Average network latency",
                    "Average decoding time", "Rendering frame rate"):
        assert english not in text, f"English in the performance overlay: {english!r} in {text!r}"
    assert "Stream vidéo" in text and "Temps moyen de décodage" in text, text
    c.call("key", key="S", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: not s["session"]["overlayStats"]["shown"], 10)

    # Microphone (Ctrl+Alt+Shift+U): muted, said on screen, then back
    c.wait_for(lambda s: s["session"]["micActive"], 10)
    c.call("key", key="U", modifiers="Ctrl+Alt+Shift", target="stream")
    st = c.wait_for(lambda s: s["session"]["micMuted"], 10)
    assert "Micro coupé" in st["session"]["statusMessage"], st["session"]
    c.call("key", key="U", modifiers="Ctrl+Alt+Shift", target="stream")
    st = c.wait_for(lambda s: not s["session"]["micMuted"], 10)
    assert "Micro coupé" not in st["session"]["statusMessage"], st["session"]

    # Zoom (Ctrl+Alt+Shift+= / 0)
    c.call("key", key="Equals", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: abs(s["session"]["zoom"] - 1.25) < 0.01, 5)
    c.call("key", key="0", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: abs(s["session"]["zoom"] - 1.0) < 0.01, 5)
    stream_runs(c, 1, "after the shortcuts")


def test_s9_zoom_does_not_leak_into_the_next_stream(bench):
    atlas, c = setup(bench)
    helpers.launch(c, "Bureau")
    for _ in range(2):
        c.call("key", key="Equals", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: s["session"]["zoom"] > 1.5, 5)
    menu_entry(c, "Déconnecter")
    wait_home(c, "after « Déconnecter »")
    helpers.launch(c, "Reprendre")  # the app still runs on the PC: join it again
    zoom = c.state()["session"]["zoom"]
    assert abs(zoom - 1.0) < 0.01, f"the new stream starts zoomed in ({zoom}x)"
