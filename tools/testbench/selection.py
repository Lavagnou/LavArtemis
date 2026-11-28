"""Which scenarios a change concerns (user's rule, 2026-11-28: the release gate runs the scenarios of what changed
since the last release, not the whole bench every time).

Two repositories feed the test client: LavArtemis-Qt (the client, at the commit `desktop/` points to) and this one
(the scenarios and the bench).  `changed_files(since)` lists what changed in both between the release tag `since`
and HEAD; `select(files)` maps each file to the scenarios that exercise it, by the table below.

* A scenario file that changed runs.
* What every scenario rests on (the bench's client and fake PCs, the test build's command channel, main.cpp,
  app.pro, the qrc files) runs the whole suite.
* A file of the client's app/ that no rule names runs the whole suite too: an unmapped file is a gap in this
  table, not a file without effect.  Add a rule when that happens.
* The rest (Android, docs, installer, build scripts, the runner's own code, which tests/ covers) runs nothing.

Patterns are fnmatch globs on paths relative to their repository, with / separators; `*` crosses folders."""
import fnmatch
import pathlib
import re
import subprocess

ALL = "ALL"

HOME = ["02", "16", "17", "18", "19", "20", "21"]
SETTINGS = ["04", "11", "14", "19", "20", "22"]
HOSTS = ["01", "03", "07", "09", "12", "19", "20"]
STREAM = ["05", "05b", "10", "12", "18"]
# shared look and widgets: the scenarios that walk the screens
UI = ["02", "03", "04", "16", "17", "18", "19", "20", "21"]

# (pattern, scenarios) in LavArtemis-Qt.  Every rule that matches adds its scenarios.
QT_RULES = [
    # what everything rests on
    ("app/testctl/*", ALL), ("app/main.cpp", ALL), ("app/app.pro", ALL), ("app/*.qrc", ALL),
    ("lavartemis.pro", ALL), ("app/path.*", ALL), ("app/utils.h", ALL), ("app/wm.cpp", ALL),
    ("app/gui/main.qml", ALL), ("app/gui/v2/qmldir", UI),
    # screens
    ("app/gui/v2/Home*.qml", HOME), ("app/gui/v2/Stage.qml", HOME), ("app/gui/v2/AppCard.qml", HOME),
    ("app/gui/v2/Cover*.qml", HOME + ["06"]), ("app/gui/v2/FitTitle.qml", HOME), ("app/gui/v2/NoGamesCard.qml", HOME),
    ("app/gui/v2/Live*.qml", HOME), ("app/gui/v2/HeaderButton.qml", HOME), ("app/gui/v2/ThemeLogo.qml", HOME),
    ("app/gui/v2/UpdateButton.qml", HOME), ("app/gui/v2/HomeTile.qml", HOME),
    ("app/gui/v2/LibraryView.qml", ["02", "06", "16", "19", "20"]),
    ("app/gui/v2/DetailsView.qml", ["17", "19", "20"]),
    ("app/gui/v2/HostsView.qml", HOSTS),
    ("app/gui/v2/SettingsView2.qml", SETTINGS), ("app/gui/v2/Setting*.qml", SETTINGS),
    ("app/gui/v2/LavSwitch.qml", SETTINGS), ("app/gui/v2/LavDropdown.qml", SETTINGS),
    ("app/gui/v2/PyroWaveCalibration.qml", ["04", "11"]),
    ("app/gui/v2/SegueScreen.qml", ["05", "10", "13", "18"]),
    ("app/gui/*Segue.qml", ["05", "10", "13", "18"]),
    ("app/gui/QuickMenu.qml", ["05", "05b", "10", "18"]),
    ("app/gui/BenchmarkView.qml", ["12"]),
    ("app/gui/CliPair.qml", ["01", "13"]),
    ("app/gui/GamepadMapper.qml", ["14"]), ("app/gui/ControllerDiagram.qml", ["14"]),
    ("app/gui/GamepadControl.qml", ["14"]), ("app/gui/sdlgamepadmapper.*", ["14"]),
    ("app/gui/sdlgamepadkeynavigation.*", ["14", "20"]),
    ("app/gui/appmodel.*", ["02", "06", "15", "16", "17"]), ("app/gui/appkindmodel.*", ["02", "16"]),
    ("app/gui/computermodel.*", ["03", "09", "16"]),
    ("app/gui/Navigable*.qml", UI), ("app/gui/ErrorMessageDialog.qml", UI),
    # the shared pieces: every screen
    *[(f"app/gui/v2/{name}.qml", UI) for name in (
        "Theme", "FocusRing", "HoverDecor", "HintsBar", "LavButton", "LavDialog", "LavIcon", "LavOptions", "LavPill",
        "LavPopup", "LavTextField", "LavToast", "DashedRect", "PageHeader", "RoundIconButton", "SectionTitle",
        "MouseMoves")],
    ("app/languages/*", UI),
    # engine
    ("app/backend/benchmark/*", ["12"]),
    ("app/backend/nvhttp.*", ["01", "02", "03", "06", "08", "09", "15"]),
    ("app/backend/nvcomputer.*", ["01", "02", "03", "06", "07", "09", "15"]),
    ("app/backend/nvapp.*", ["02", "06", "15"]),
    ("app/backend/computermanager.*", ["01", "03", "06", "07", "09", "15"]),
    ("app/backend/computerseeker.*", ["03", "09", "13"]), ("app/backend/nvaddress.*", ["01", "08"]),
    ("app/backend/nvpairingmanager.*", ["01", "09"]), ("app/backend/otppairingmanager.*", ["01", "13"]),
    ("app/backend/identitymanager.*", ["01", "09"]), ("app/backend/testguard.*", ["08"]),
    ("app/backend/boxartmanager.*", ["02", "15"]), ("app/backend/clipboardmanager.*", ["10"]),
    ("app/backend/servercommandmanager.*", ["05", "10"]), ("app/backend/serverpermissions.*", ["05", "10"]),
    ("app/backend/keymacromanager.*", ["05b"]), ("app/backend/quickmenumanager.*", ["05", "10", "18"]),
    ("app/backend/hostdisplayfit.*", ["05", "10"]),
    ("app/backend/librarycounttext.*", ["02", "16"]), ("app/backend/playtimetext.*", ["02", "16"]),
    ("app/backend/systemproperties.*", ["05", "22"]), ("app/backend/richpresencemanager.*", ["05"]),
    ("app/backend/networkbuffers.*", ["05"]),
    # no scenario: the updater talks to GitHub, which the test client may never reach
    ("app/backend/autoupdatechecker.*", []), ("app/backend/updaterules.*", []),
    ("app/backend/artemis*", []), ("app/backend/test_otppairing.cpp", []),
    ("app/settings/mapping*", ["14"]), ("app/SDL_GameControllerDB*", ["14"]),
    ("app/settings/streamingpreferences.*", ["04", "05", "11", "22"]),
    ("app/settings/artemissettings.*", ["04", "05", "11"]),
    ("app/settings/profilemanager.*", ["11"]), ("app/settings/channelmigration.*", ["11"]),
    ("app/settings/compatfetcher.*", ["03"]),
    ("app/cli/*", ["13"]), ("app/cli/pair.*", ["01"]),
    ("app/streaming/*", STREAM), ("app/streaming/input/*", ["05b", "14"]),
    ("app/streaming/streamutils.*", ["22"]), ("app/shaders/*", ["05"]), ("app/diagnostics/*", ["05"]),
    ("app/masterhook*", []), ("app/test_otp_pairing.cpp", []),
    ("moonlight-common-c*", STREAM),
    # the rest of app/ that has no runtime effect on Windows
    ("app/deploy/*", []), ("app/res/*", UI), ("app/Moonlight.app/*", []), ("app/Info.plist*", []),
    ("app/*.ico", []), ("app/*.icns", []), ("app/*.png", []), ("app/*.manifest", []), ("app/*.ttf", []),
    ("app/version.txt", []), ("app/qt_qt5.conf", []), ("app/SDL_compat.h", ALL),
    ("app/windowsvblankvirtualization.h", STREAM),
]

# (pattern, scenarios) in LavArtemis, under tools/testbench/.  A scenario file names itself (see select()).
BENCH_RULES = [
    ("tools/testbench/conftest.py", ALL), ("tools/testbench/client.py", ALL), ("tools/testbench/hosts.py", ALL),
    ("tools/testbench/frames.py", ALL), ("tools/testbench/covers.py", ALL), ("tools/testbench/fixtures/*", ALL),
    ("tools/testbench/gen_fixtures.py", ALL), ("tools/testbench/pytest.ini", ALL),
    ("tools/testbench/scenarios/helpers.py", ALL), ("tools/testbench/scenarios/conftest.py", ALL),
]

SCENARIOS = pathlib.Path(__file__).resolve().parent / "scenarios"


def scenario_files():
    """{"05b": "test_05b_stream_input.py", ...} of the scenarios on disk."""
    out = {}
    for p in sorted(SCENARIOS.glob("test_*.py")):
        m = re.match(r"test_(\d+[a-z]?)_", p.name)
        if m:
            out[m.group(1)] = p.name
    return out


def select(qt_files=(), bench_files=()):
    """(ALL or a sorted list of scenario numbers, reasons): reasons = [(file, what it runs)], one per file."""
    known = scenario_files()
    picked, everything, reasons = set(), False, []

    def add(path, rules, prefix):
        nonlocal everything
        hits = [s for pattern, s in rules if fnmatch.fnmatch(path, pattern)]
        if not hits:
            return False
        if any(s == ALL for s in hits):
            everything = True
            reasons.append((prefix + path, "tout le banc"))
        else:
            got = sorted({n for s in hits for n in s if n in known})
            picked.update(got)
            reasons.append((prefix + path, ", ".join(got) if got else "aucun scénario"))
        return True

    for path in qt_files:
        if not add(path, QT_RULES, "LavArtemis-Qt/"):
            if path.startswith("app/"):
                everything = True
                reasons.append(("LavArtemis-Qt/" + path, "tout le banc (fichier absent de selection.py)"))
            else:
                reasons.append(("LavArtemis-Qt/" + path, "aucun scénario"))
    for path in bench_files:
        m = re.match(r"tools/testbench/scenarios/test_(\d+[a-z]?)_.*\.py$", path)
        if m:
            if m.group(1) in known:
                picked.add(m.group(1))
                reasons.append((path, m.group(1)))
            continue
        if not add(path, BENCH_RULES, ""):
            reasons.append((path, "aucun scénario"))
    if everything:
        return ALL, reasons
    return sorted(picked, key=lambda n: (int(re.match(r"\d+", n).group()), n)), reasons


def _git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True, text=True, check=True).stdout


def last_release_tag(repo):
    """The latest release tag of this channel that HEAD contains: a version with -dev. looks for the last -dev. tag,
    a stable one for the last tag without it (app/build.gradle's lavVersionName says which)."""
    gradle = (pathlib.Path(repo) / "app" / "build.gradle").read_text(encoding="utf-8")
    version = re.search(r'def lavVersionName = "([^"]+)"', gradle).group(1)
    args = ["describe", "--tags", "--abbrev=0", "--match", "v*-dev.*"] if "-dev." in version else \
           ["describe", "--tags", "--abbrev=0", "--match", "v*", "--exclude", "*-dev.*"]
    return _git(repo, *args, "HEAD").strip()


def changed_files(repo, qt_repo, since):
    """(qt files, bench files) changed between the tag `since` and HEAD (LavArtemis-Qt: between the commits that
    `desktop/` points to at each), plus this repo's uncommitted testbench edits."""
    old_qt = _git(repo, "rev-parse", f"{since}:desktop").strip()
    new_qt = _git(repo, "rev-parse", "HEAD:desktop").strip()
    qt = _git(qt_repo, "diff", "--name-only", f"{old_qt}..{new_qt}").split()
    bench = set(_git(repo, "diff", "--name-only", f"{since}..HEAD", "--", "tools/testbench").split())
    bench |= set(_git(repo, "diff", "--name-only", "HEAD", "--", "tools/testbench").split())
    return qt, sorted(bench)


def targets(numbers):
    """pytest paths of the scenario numbers."""
    known = scenario_files()
    return [str(SCENARIOS / known[n]) for n in numbers]
