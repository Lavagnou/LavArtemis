"""Offscreen captures of the test client's interface, to compare with the prototype (`testbench.py shots`).

Not a test and never a release gate: `run` keeps its "display off" refusal (code 4).  This only needs no display:
the test client runs on Qt's `offscreen` platform with the software scene graph (nothing is drawn on the real
screen, no window exists for Windows), at a 1.25 scale factor so a 1280 x 720 logical window (design-port-spec
§5.3) gives 1600 x 900 PNGs, the size of the prototype's PC renders.  The fake PCs are the bench's own (hosts.up,
in a workdir of their own, `<tmp>/lavtestbench-shots`), the client its usual "LavArtemis Test" organisation, wiped
first; same isolation as `run` (loopback ports 40000-44999, injected input only).

Captures land in <out>/<screen>/<theme>-<clair|sombre>-<input>.png, the prototype renders' naming
(`v8-shots/<planche>/<thème>-<clair|sombre>-<entrée>.png`).

    python tools/testbench/testbench.py shots <out folder> [--screens home,hosts,...] [--themes braise,...]
"""
import os
import pathlib
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "scenarios"))
import client as clientmod  # noqa: E402
import hosts  # noqa: E402

THEMES = ("braise", "affiche", "ludique")
MODES = (("clair", 1), ("sombre", 2))
OFFSCREEN_ENV = {
    "QT_QPA_PLATFORM": "offscreen",
    "QT_QUICK_BACKEND": "software",
    "QT_SCALE_FACTOR": "1.25",
    # offscreen has no screen of its own: give it the size of a 1600 x 900 desktop at 125 %
    "QT_QPA_OFFSCREEN_SCREEN_SIZE": "1600x900",
}


def qt_platforms_dir():
    """Qt's own plugins/platforms (windeployqt deploys qwindows only): the Qt of scripts/build-test.bat."""
    bat = clientmod.qt_root() / "scripts" / "build-test.bat"
    for line in bat.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().lower().startswith("set qt_bin="):
            d = pathlib.Path(line.split("=", 1)[1].strip()).parent / "plugins" / "platforms"
            if (d / "qoffscreen.dll").exists():
                return d
    raise RuntimeError(f"no qoffscreen.dll next to the Qt of {bat}")


def workdir():
    return hosts.default_workdir().parent / "lavtestbench-shots"


def _set(c, key, value):
    c.call("setting", action="set", key=key, value=value)


def _keys(c, *names):
    for k in names:
        c.call("key", key=k, target="qt")
        time.sleep(0.15)


class Shooter:
    def __init__(self, c, out, themes, inputs):
        self.c, self.out, self.themes, self.inputs = c, pathlib.Path(out), themes, inputs
        self.taken = []

    def themed(self, screen, prepare=None, inputs=None, settle=0.8):
        """One capture per theme x mode x input.  prepare(c, input): puts the screen in the state to capture
        (after the theme change, which rebuilds nothing but may move focus)."""
        for theme in self.themes:
            for mode_name, mode in MODES:
                _set(self.c, "uiTheme", theme)
                _set(self.c, "uiThemeMode", mode)
                for inp in (inputs or self.inputs):
                    if inp == "keys":
                        # the last input is the keyboard: a key that does nothing else (Insert)
                        self.c.call("key", key="Insert", target="qt")
                    if prepare:
                        prepare(self.c, inp)
                    time.sleep(settle)
                    p = self.out / screen / f"{theme}-{mode_name}-{inp}.png"
                    p.parent.mkdir(parents=True, exist_ok=True)
                    self.c.screenshot(p, 0)
                    self.taken.append(p)


SCREENS = {}


def screen(name):
    def deco(f):
        SCREENS[name] = f
        return f
    return deco


@screen("home")
def shot_home(sh, ctx):
    import helpers
    helpers.go_home(sh.c)
    sh.themed("home")


@screen("home-session")
def shot_home_session(sh, ctx):
    """The home with a session running on the PC: Bureau launched, then « Déconnecter » (Ctrl+Alt+Shift+Q), the
    app keeps running on the fake PC.  The stream itself is a real SDL window of the test client (non-activating,
    as in `run`)."""
    import helpers
    helpers.go_home(sh.c)
    try:
        helpers.launch(sh.c, ctx.get("launch", "Bureau"), timeout=40)
    except AssertionError as e:
        print(f"shots: home-session skipped, no stream ({e})")
        return
    sh.c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    sh.c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    time.sleep(3.0)  # the next poll says the app still runs
    sh.themed("home-session")


@screen("library")
def shot_library(sh, ctx):
    import helpers
    helpers.go_home(sh.c)
    sh.c.click("Tout voir →", index=-1)
    helpers.wait_page(sh.c, "LibraryView")
    time.sleep(2.0)
    sh.themed("library")
    helpers.go_home(sh.c)


@screen("details")
def shot_details(sh, ctx):
    """Details of the first recent game: its options (keyboard: Menu), « Afficher les détails »."""
    import helpers
    helpers.go_home(sh.c)
    _keys(sh.c, "Up", "Up", "Up", "Down", "Down", "Menu")
    helpers.wait_text(sh.c, "Afficher les détails")
    sh.c.click("Afficher les détails")
    helpers.wait_page(sh.c, "DetailsView")
    time.sleep(1.5)
    sh.themed("details")
    helpers.go_home(sh.c)


@screen("options")
def shot_options(sh, ctx):
    """Options of the first recent game: the centred dialog (keyboard: Menu)."""
    import helpers
    helpers.go_home(sh.c)

    def prepare(c, inp):
        if helpers.find(c, "Afficher les détails"):  # the dialog of the previous capture
            c.call("key", key="Escape", target="qt")
            helpers.wait_text(c, "Afficher les détails", gone=True)
        _keys(c, "Up", "Up", "Up", "Down", "Down", "Menu")
        helpers.wait_text(c, "Afficher les détails")
    sh.themed("options", prepare=prepare, inputs=["keys"])
    sh.c.call("key", key="Escape", target="qt")
    helpers.wait_text(sh.c, "Afficher les détails", gone=True)


@screen("hosts")
def shot_hosts(sh, ctx):
    import helpers
    helpers.open_hosts(sh.c)
    sh.themed("hosts")
    helpers.go_home(sh.c)


@screen("settings")
def shot_settings(sh, ctx):
    import helpers
    helpers.open_settings(sh.c)
    sh.themed("settings")
    helpers.go_home(sh.c)


def run(out, screens=None, themes=THEMES, inputs=("mouse",), fixtures=("atlas-test",), keep=False, build=True):
    import helpers
    wd = workdir()
    out = pathlib.Path(out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    started = hosts.up(list(fixtures), wd)
    c = None
    try:
        env = dict(OFFSCREEN_ENV, QT_QPA_PLATFORM_PLUGIN_PATH=str(qt_platforms_dir()))
        c = clientmod.Client.start(wd, reset=True, build=build, env=env)
        c.wait_idle(timeout=30)
        for h in started:
            if h.pid:
                helpers.add_and_pair(c, h)
        helpers.go_home(c)
        if started:
            helpers.wait_apps(c, started[0].name)
        time.sleep(4.0)  # covers are fetched from the host, then fade in
        sh = Shooter(c, out, themes, inputs)
        ctx = {"hosts": started}
        for name in (screens or list(SCREENS)):
            SCREENS[name](sh, ctx)
        print(f"shots: {len(sh.taken)} captures in {out}")
        # QML warnings of the client (a broken binding shows here before it shows in a capture)
        warnings = sorted({ln.split(" - ", 1)[-1] for ln in c.log_text().splitlines() if "qrc:/" in ln})
        for w in warnings[:40]:
            print("client:", w)
        return 0
    finally:
        if c is not None and not keep:
            c.stop()
        if not keep:
            hosts.down(wd)
