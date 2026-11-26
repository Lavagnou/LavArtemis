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


def run(out, screens=None, themes=THEMES, inputs=("mouse",), fixtures=("atlas-test",), keep=False):
    import helpers
    wd = workdir()
    out = pathlib.Path(out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    started = hosts.up(list(fixtures), wd)
    c = None
    try:
        env = dict(OFFSCREEN_ENV, QT_QPA_PLATFORM_PLUGIN_PATH=str(qt_platforms_dir()))
        c = clientmod.Client.start(wd, reset=True, build=True, env=env)
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
        return 0
    finally:
        if c is not None and not keep:
            c.stop()
        if not keep:
            hosts.down(wd)
