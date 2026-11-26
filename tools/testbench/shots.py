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
        [--covers <folder>]

`--covers <folder>`: the fake PCs take the prototype's data instead of the bench's fixtures, to compare like with
like: the PCs, games, hours, last plays, installed state, hidden game and running session that
`<folder>/shots-data.json` describes (format: `covers_fixtures()`), with the images of that folder as covers,
backgrounds and app covers.  Those are real game art: the folder and its data file stay out of git, and nothing of
them is written anywhere but the shots' work dir.  The first PC's session (« depuis 42 min ») is started by the
client, then left running: every screen shows it, as the prototype does.
"""
import datetime
import json
import uuid
import os
import pathlib
import shutil
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
    # the « update » screen: the first « Vérifier » finds the prototype's next version (never the startup check)
    "LAV_TEST_UPDATE": "available:0.7.0-dev.8:https://github.invalid/LavArtemis-v0.7.0-dev.8-win-installer.exe",
    # the « bench-results » screen: short runs (warm-up, measure, window in seconds), as scenario 12
    "LAV_TEST_BENCHMARK": "1,3,1,0",
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


@screen("home-join")
def shot_home_join(sh, ctx):
    """V8-03: another device streams from the PC (serverinfo OtherClientsConnected): « Rejoindre »."""
    import helpers
    host = ctx["hosts"][0]
    hosts.set_session(host, 1, 60 * _session_minutes(ctx))
    try:
        helpers.go_home(sh.c)
        helpers.wait_text(sh.c, "Rejoindre", contains=True, timeout=15)
        sh.themed("home-join")
    finally:
        hosts.set_session(host, 0, 60 * _session_minutes(ctx))


@screen("home-session")
def shot_home_session(sh, ctx):
    """The home with a session running on the PC: Bureau launched, then « Déconnecter » (Ctrl+Alt+Shift+Q), the
    app keeps running on the fake PC.  The stream itself is a real SDL window of the test client (non-activating,
    as in `run`)."""
    import helpers
    if ctx.get("data"):
        return  # --covers: every screen already has the prototype's session
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
    """Details of a recent game: its options (keyboard: Menu), « Afficher les détails ».  --covers: the fifth recent
    (Split Fiction, the prototype's V8-67), under the Scène of the running session; else the first."""
    import helpers
    helpers.go_home(sh.c)
    if ctx.get("data"):
        _keys(sh.c, "Up", "Up", "Up", "Up", "Down", "Down", "Down", "Right", "Right", "Right", "Right", "Menu")
    else:
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
        # the first recent (--covers: under the Scène of the running session, one row more)
        down = ["Down"] * (3 if ctx.get("data") else 2)
        _keys(c, "Up", "Up", "Up", "Up", *down, "Menu")
        helpers.wait_text(c, "Afficher les détails")
    sh.themed("options", prepare=prepare, inputs=["keys"])
    sh.c.call("key", key="Escape", target="qt")
    helpers.wait_text(sh.c, "Afficher les détails", gone=True)


def _composite(menu_png, backdrop, out_png):
    """The in-game menu is a transparent window over the stream: put its capture over a backdrop (the stream
    capture when there is one, else the session game's background, else a dark grey), at the prototype's size."""
    from PIL import Image
    menu = Image.open(menu_png).convert("RGBA")
    size = (1600, 900)
    base = None
    if backdrop and pathlib.Path(backdrop).exists():
        try:
            base = Image.open(backdrop).convert("RGBA")
            # cover crop, as the prototype's object-fit: cover
            r = max(size[0] / base.width, size[1] / base.height)
            base = base.resize((round(base.width * r), round(base.height * r)))
            left, top = (base.width - size[0]) // 2, (base.height - size[1]) // 2
            base = base.crop((left, top, left + size[0], top + size[1]))
        except OSError:
            base = None
    if base is None:
        base = Image.new("RGBA", size, (40, 40, 44, 255))
    base.alpha_composite(menu.resize(size))
    base.convert("RGB").save(out_png)


@screen("ingame")
def shot_ingame(sh, ctx):
    """The in-game menu (V8-26, 28): the session resumed from the home, Ctrl+Alt+Shift+\\, the menu window
    captured over the session game's background (the stream of the fake PC is a test pattern)."""
    import helpers
    data = ctx.get("data") or {}
    session = ((data.get("pcs") or [{}])[0].get("session") or {})
    backdrop = None
    if session.get("game") and data.get("folder"):
        g = next((g for g in data.get("games", []) if g["name"] == session["game"]), {})
        if g.get("background") or g.get("cover"):
            backdrop = pathlib.Path(data["folder"]) / (g.get("background") or g.get("cover"))
    helpers.go_home(sh.c)
    resume = "Reprendre" if helpers.find(sh.c, "Reprendre") else None
    try:
        if resume:
            helpers.launch(sh.c, resume, timeout=40)
        else:
            helpers.launch(sh.c, ctx.get("launch", "Bureau"), timeout=40)
    except AssertionError as e:
        print(f"shots: ingame skipped, no stream ({e})")
        return
    try:
        for theme in sh.themes:
            for mode_name, mode in MODES:
                _set(sh.c, "uiTheme", theme)
                _set(sh.c, "uiThemeMode", mode)
                for inp in sh.inputs:
                    idx = helpers.open_quick_menu(sh.c)
                    if inp == "keys":
                        sh.c.call("key", key="Right", target="qt", window=idx)
                        sh.c.call("key", key="Left", target="qt", window=idx)
                    time.sleep(0.8)
                    raw = sh.out / "ingame" / f"_menu-{theme}-{mode_name}-{inp}.png"
                    raw.parent.mkdir(parents=True, exist_ok=True)
                    sh.c.screenshot(raw, idx)
                    out = sh.out / "ingame" / f"{theme}-{mode_name}-{inp}.png"
                    _composite(raw, backdrop, out)
                    sh.taken.append(out)
                    # « Plus » (V8-28, entries of the menu workshop): its dialog over the menu
                    if inp == "mouse":
                        helpers.quick_menu_click(sh.c, "Plus", idx)
                        helpers.wait_quick_text(sh.c, "Comportement de la souris", idx)
                        time.sleep(0.8)
                        raw = sh.out / "ingame" / f"_plus-{theme}-{mode_name}.png"
                        sh.c.screenshot(raw, idx)
                        out = sh.out / "ingame" / f"plus-{theme}-{mode_name}.png"
                        _composite(raw, backdrop, out)
                        sh.taken.append(out)
                        # « Annuler » closes the dialog only (the menu stays), « Reprendre » the menu
                        helpers.quick_menu_click(sh.c, "Annuler", idx)
                        helpers.quick_menu_click(sh.c, "Reprendre", idx)
                    else:
                        sh.c.call("key", key="Escape", target="qt", window=idx)
                    sh.c.wait_for(lambda s: not s.get("quickMenu"), 10)
    finally:
        sh.c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
        sh.c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
        time.sleep(2.0)


@screen("segue")
def shot_segue(sh, ctx):
    """The segue while the stream connects (V8-71 « Reprise sur ATLAS-PC… » with --covers, else V8-24
    « Lancement sur … »): « Reprendre » (or the first card), the capture as soon as the segue page is up, then
    « Déconnecter ».  One launch per theme x mode: the segue lasts only as long as the connection."""
    import helpers
    for theme in sh.themes:
        for mode_name, mode in MODES:
            _set(sh.c, "uiTheme", theme)
            _set(sh.c, "uiThemeMode", mode)
            helpers.go_home(sh.c)
            # « Reprendre », « REPRENDRE → » (Affiche), else the first card
            resume = [t for t in helpers.texts(sh.c) if t.lower().startswith("reprendre")]
            title = resume[0] if resume else ctx.get("launch", "Bureau")
            helpers.wait_settled(sh.c, title)
            sh.c.click(title)
            deadline = time.time() + 15
            while sh.c.state()["page"] != "StreamSegue" and time.time() < deadline:
                time.sleep(0.05)
            time.sleep(0.4)  # the cover and the background load asynchronously
            p = sh.out / "segue" / f"{theme}-{mode_name}-mouse.png"
            p.parent.mkdir(parents=True, exist_ok=True)
            sh.c.screenshot(p, 0)
            sh.taken.append(p)
            try:
                deadline = time.time() + 40
                while not sh.c.state()["session"]["streaming"]:
                    if time.time() > deadline:
                        raise AssertionError("no stream")
                    time.sleep(0.3)
            except AssertionError as e:
                print(f"shots: segue, no stream ({e})")
                sh.c.call("key", key="Escape", target="qt")
                helpers.go_home(sh.c)
                continue
            st = sh.c.state()
            toasts = st.get("session", {}).get("toasts") or st.get("toasts")
            if toasts:
                print(f"shots: segue, toasts over the stream: {toasts}")
            sh.c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
            sh.c.wait_for(lambda s: s["page"] == "HomeView" and not s["session"]["active"], 30)
            time.sleep(2.0)


def _open_bench(sh, ctx):
    import helpers
    if sh.c.state()["page"] == "BenchmarkView":
        return
    helpers.open_hosts(sh.c)
    helpers.host_card_menu(sh.c, ctx["hosts"][0].name)
    helpers.click(sh.c, "Benchmark des réglages")
    helpers.wait_page(sh.c, "BenchmarkView")
    time.sleep(1.0)


@screen("bench")
def shot_bench(sh, ctx):
    """V8-43: the benchmark's setup page of the first PC (with --covers: an app runs there, the warning shows)."""
    _open_bench(sh, ctx)
    sh.themed("bench")


@screen("bench-results")
def shot_bench_results(sh, ctx):
    """V8-45: a Quick run (shortened by LAV_TEST_BENCHMARK, test build only), then its ranked results.  It quits
    what runs on the PC: after the screens that need the session."""
    import helpers
    _open_bench(sh, ctx)
    helpers.click(sh.c, "Lancer le benchmark")
    sh.c.wait_for(lambda s: s["benchmark"]["running"], 20)
    try:
        sh.c.wait_for(lambda s: not s["benchmark"]["running"] and s["page"] == "BenchmarkView", 300, interval=1.0)
    except Exception as e:  # noqa: BLE001 - a capture tool: say it and go on
        print(f"shots: bench-results, the benchmark did not finish ({e})")
        return
    time.sleep(1.5)
    sh.themed("bench-results")
    helpers.go_home(sh.c)


@screen("mapper")
def shot_mapper(sh, ctx):
    """V8-46: Réglages › Manettes › « Assigner les boutons d’une manette », with the test build's virtual joystick
    (4 axes, 8 buttons, 1 hat: SDL does not know it, LavArtemis guesses its layout); a few controls assigned."""
    import helpers
    sh.c.call("joystick", attach=True, kind="generic", axes=4, buttons=8, hats=1)
    helpers.open_settings(sh.c)
    helpers.open_settings_page(sh.c, "Manettes")
    row = helpers.wait_settled(sh.c, "Assigner les boutons d’une manette")
    ouvrir = [i for i in helpers.find(sh.c, "Ouvrir") if abs(i["y"] - row["y"]) < 60]
    helpers.click_item(sh.c, ouvrir[0])
    helpers.wait_page(sh.c, "GamepadMapper")
    time.sleep(1.0)
    sh.themed("mapper")
    helpers.go_home(sh.c)


@screen("update")
def shot_update(sh, ctx):
    """V8-13: « Vérifier » in Réglages › Avancé finds 0.7.0-dev.8 (LAV_TEST_UPDATE, test build only), then the
    home's « Mise à jour » opens the theme's dialog.  Last of the screens: the home keeps its « Mise à jour »."""
    import helpers
    helpers.open_settings(sh.c)
    helpers.open_settings_page(sh.c, "Avancé")
    helpers.click(sh.c, "Vérifier")
    helpers.wait_text(sh.c, "est disponible", contains=True, timeout=10)
    helpers.go_home(sh.c)

    def prepare(c, inp):
        if helpers.find(c, "Ignorer cette version"):  # the dialog of the previous capture
            c.call("key", key="Escape", target="qt")
            helpers.wait_text(c, "Ignorer cette version", gone=True)
        # the header's button: its MouseArea covers its text, click at the text's place
        item = next(i for i in c.tree() if i.get("text") == "Mise à jour")
        helpers.click_item(c, item)
        helpers.wait_text(c, "Ignorer cette version")
    sh.themed("update", prepare=prepare)
    sh.c.call("key", key="Escape", target="qt")


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
    helpers.open_settings_page(sh.c, "Vidéo")  # the prototype's V8-48
    sh.themed("settings")
    helpers.go_home(sh.c)


# Same game ids as gen_fixtures.py (LavApollo Link's are GUIDs)
_NS = uuid.UUID("5d0c1c00-0000-4000-8000-000000000000")


def _gid(name):
    return str(uuid.uuid5(_NS, name)).upper()


def _png(src, dst):
    """LavApollo serves an app's image-path only as PNG."""
    from PIL import Image
    Image.open(src).convert("RGB").save(dst, "PNG")
    return dst


def covers_fixtures(folder, wd):
    """Fixture folders (absolute paths: hosts.up() takes them as fixture names) built in the work dir from
    <folder>/shots-data.json:
        {"pcs": [{"name", "form_factor", "link", "games": bool, "offline": bool,
                  "session": {"app", "game", "minutes"}}],
         "hidden": [game names],
         "games": [{"name", "hours", "installed", "days_ago" (null = never played), "cover", "background"}],
         "app_covers": {"Bureau": file, "Playnite Fullscreen": file}}
    Image names are files of <folder>.  Returns (fixture paths, data)."""
    folder = pathlib.Path(folder).resolve()
    data = json.loads((folder / "shots-data.json").read_text(encoding="utf-8"))
    data["folder"] = str(folder)
    root = wd / "covers-fixtures"
    shutil.rmtree(root, ignore_errors=True)
    now = datetime.datetime.now(datetime.timezone.utc)
    games = []
    for g in data.get("games", []):
        e = {"id": _gid(g["name"]), "name": g["name"], "source": "Steam", "platform": "PC (Windows)",
             "favorite": False, "playtime": int(g.get("hours", 0) * 3600), "installed": bool(g.get("installed", True)),
             "lastActivity": None if g.get("days_ago") is None else
             (now - datetime.timedelta(days=g["days_ago"], minutes=5)).strftime("%Y-%m-%dT%H:%M:%SZ")}
        if g.get("cover"):
            # LavApollo Link writes PNG covers: converted, in the work dir
            imgs = root / "img"
            imgs.mkdir(parents=True, exist_ok=True)
            e["cover"] = e["id"] + ".png"
            e["coverFrom"] = str(_png(folder / g["cover"], imgs / e["cover"]))
        if g.get("background"):
            e["background"], e["backgroundFrom"] = e["id"] + ".jpg", str(folder / g["background"])
        games.append(e)
    paths = []
    for i, pc in enumerate(data["pcs"]):
        d = root / f"pc{i}"
        (d / "playnite").mkdir(parents=True)
        conf = {"name": pc["name"], "port": 40989 + 1000 * i, "mac": f"02:00:00:00:a8:0{i + 1}",
                "playnite_running": bool(pc.get("games")), "form_factor": pc.get("form_factor"),
                "link": pc.get("link")}
        if pc.get("offline"):
            conf["instance"] = False
        (d / "pc.json").write_text(json.dumps({k: v for k, v in conf.items() if v is not None}), encoding="utf-8")
        apps = [{"name": "Bureau", "cmd": ""}]
        if pc.get("games"):
            apps.append({"name": "Playnite Fullscreen", "cmd": "Playnite.FullscreenApp.exe --hidesplashscreen"})
        for k, a in enumerate(apps):
            art = data.get("app_covers", {}).get(a["name"])
            if art:
                a["image-path"] = _png(folder / art, d / f"app{k}.png").as_posix()
        (d / "apps.json").write_text(json.dumps({"env": {}, "apps": apps}, indent=2), encoding="utf-8")
        lib = {"version": 1, "exportedAt": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
               "games": games if pc.get("games") else []}
        (d / "playnite" / "library.json").write_text(json.dumps(lib, ensure_ascii=False, indent=2), encoding="utf-8")
        paths.append(str(d))
    return paths, data


def start_covers_session(c, host, session):
    """The prototype's running session on the shown PC: its app launched by the client (a real stream), the
    Playnite game written in the fake state.json, both started `minutes` ago, then « Déconnecter »: the app keeps
    running on the PC."""
    import helpers
    helpers.go_home(c)
    helpers.launch(c, session["app"], timeout=40)
    if session.get("game"):
        hosts.set_playnite_game(host, _gid(session["game"]), session["game"], 60 * session.get("minutes", 0))
    hosts.set_session(host, 0, 60 * session.get("minutes", 0))
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    time.sleep(3.0)  # the next poll says the app still runs


def hide_games(c, names):
    """Hide games as the user would: their options in the library, « Masquer »."""
    import helpers
    for name, index in names:
        helpers.go_home(c)
        c.click("Tout voir →", index=-1)
        helpers.wait_page(c, "LibraryView")
        time.sleep(1.0)
        try:
            # covers show their image, not their name: reach the game with the keys (Braise: 6 columns)
            _keys(c, *(["Down"] * (index // 6) + ["Right"] * (index % 6) + ["Menu"]))
            helpers.wait_text(c, "Masquer")
            c.click("Masquer")
            time.sleep(0.5)
        except Exception as e:  # noqa: BLE001 - a capture without the hidden game is still useful
            print(f"shots: could not hide {name} ({e})")
    helpers.go_home(c)


def _session_minutes(ctx):
    data = ctx.get("data") or {}
    return ((data.get("pcs") or [{}])[0].get("session") or {}).get("minutes", 0)


def run(out, screens=None, themes=THEMES, inputs=("mouse",), fixtures=("atlas-test",), keep=False, build=True,
        covers=None):
    import helpers
    wd = workdir()
    out = pathlib.Path(out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    data = None
    offline = ()
    if covers:
        wd.mkdir(parents=True, exist_ok=True)
        fixtures, data = covers_fixtures(covers, wd)
        offline = tuple(f for f, pc in zip(fixtures, data["pcs"]) if pc.get("offline"))
    started = hosts.up(list(fixtures), wd, force_instances=offline)
    c = None
    try:
        env = dict(OFFSCREEN_ENV, QT_QPA_PLATFORM_PLUGIN_PATH=str(qt_platforms_dir()))
        c = clientmod.Client.start(wd, reset=True, build=build, env=env)
        c.wait_idle(timeout=30)
        # The first PC last: the home shows the PC paired last
        for h in reversed(started):
            if h.pid:
                helpers.add_and_pair(c, h)
        for h in started:
            if h.fixture in offline:
                hosts.stop(h, wd)
        helpers.go_home(c)
        if started:
            helpers.wait_apps(c, started[0].name)
        time.sleep(4.0)  # covers are fetched from the host, then fade in
        sh = Shooter(c, out, themes, inputs)
        ctx = {"hosts": started, "data": data}
        if data:
            if data.get("hidden"):
                # position in the library's « Tous » (most recently played first, never played last)
                order = sorted(data["games"], key=lambda g: (g.get("days_ago") is None, g.get("days_ago") or 0))
                names = [g["name"] for g in order]
                _set(c, "uiTheme", "braise")
                hide_games(c, [(n, names.index(n)) for n in data["hidden"] if n in names])
            if data["pcs"][0].get("session"):
                start_covers_session(c, started[0], data["pcs"][0]["session"])
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
