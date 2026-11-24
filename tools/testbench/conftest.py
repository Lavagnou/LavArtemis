"""pytest fixtures of the desktop scenarios (tools/testbench/scenarios).

A scenario gets `bench`: a sandbox workdir, the fake PCs it asks for (hosts.up), the test client started from a
wiped "LavArtemis Test" organisation, a captures folder in the run's report, and a foreground watchdog.  Whatever
happens, the client and every test host are stopped at the end, and the hosts' logs and the client's log are
copied into the report.  Reports: testbench-reports/<date>_<time>/ (report.py), outside git."""
import ctypes
import ctypes.wintypes
import datetime
import json
import os
import pathlib
import shutil
import sys
import tempfile
import threading
import time

import pytest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import client as clientmod  # noqa: E402
import hosts  # noqa: E402
import report  # noqa: E402

DEFAULT_REPORTS = HERE / "testbench-reports"
SCENARIOS = HERE / "scenarios"


def pytest_addoption(parser):
    parser.addoption("--report-dir", default=None, help="report folder (default testbench-reports/<date>_<time>)")


def _is_scenario(item):
    return SCENARIOS in pathlib.Path(str(item.fspath)).resolve().parents


class Run:
    def __init__(self, root):
        self.root = pathlib.Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.results = []
        self.started = datetime.datetime.now()


_RUN = {}


def _run(config):
    if "run" not in _RUN:
        d = config.getoption("--report-dir")
        if not d:
            d = DEFAULT_REPORTS / datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")
        _RUN["run"] = Run(d)
    return _RUN["run"]


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    if not _is_scenario(item):
        return
    run = _run(item.config)
    xfail = getattr(rep, "wasxfail", None)
    if rep.when == "call" or (rep.when == "setup" and (rep.failed or rep.skipped)) or \
            (rep.when == "teardown" and rep.failed):
        status = "xfail" if xfail is not None and rep.skipped else \
                 "xpass" if xfail is not None else rep.outcome
        if rep.when == "teardown":
            status = "error"
        run.results.append({
            "id": item.nodeid, "name": item.name, "status": status, "phase": rep.when,
            "duration": round(rep.duration, 1), "reason": xfail or "",
            "doc": (item.function.__doc__ or "").strip() if hasattr(item, "function") else "",
            "error": str(rep.longrepr)[-3000:] if rep.failed else "",
            "captures": f"captures/{item.name}",
        })


def pytest_sessionfinish(session, exitstatus):
    if "run" in _RUN:
        run = _RUN.pop("run")
        report.write(run.root, run.results, run.started, datetime.datetime.now())


# ---------------------------------------------------------------- foreground watchdog

_user32 = ctypes.windll.user32


def window_pid(hwnd):
    pid = ctypes.wintypes.DWORD(0)
    _user32.GetWindowThreadProcessId(ctypes.c_void_p(hwnd), ctypes.byref(pid))
    return pid.value


def window_exe(hwnd):
    return hosts._image_path(window_pid(hwnd)) or "?"


class ForegroundWatch:
    """Samples the foreground window every 50 ms and keeps the timeline of its changes (window, owner exe).  A
    scenario fails if the foreground ever belongs to the test client or a test host.  The user keeps working while
    the suite runs: a change to one of the user's own windows is recorded in the report, not counted against the
    test client (it never had the foreground)."""

    def __init__(self):
        self.before = _user32.GetForegroundWindow()
        self.ours = set()
        self.seen_ours = []
        self.changes = []
        self.stop_flag = False
        self.t = threading.Thread(target=self._loop, daemon=True)
        self.t.start()

    def _loop(self):
        last = self.before
        while not self.stop_flag:
            h = _user32.GetForegroundWindow()
            if h != last:
                last = h
                self.changes.append((time.strftime("%H:%M:%S"), h, window_exe(h) if h else "-"))
            if h and window_pid(h) in self.ours:
                self.seen_ours.append((time.strftime("%H:%M:%S"), h, window_exe(h)))
            time.sleep(0.05)

    def finish(self):
        self.stop_flag = True
        self.t.join(2)
        return self.before, _user32.GetForegroundWindow()


# ---------------------------------------------------------------- the bench

class Bench:
    def __init__(self, name, run, workdir):
        self.name = name
        self.run = run
        self.workdir = workdir
        self.captures = run.root / "captures" / name
        self.captures.mkdir(parents=True, exist_ok=True)
        self.hosts = {}
        self.client = None
        self.clients_logs = []
        self.watch = ForegroundWatch()
        self.notes = []

    # -- hosts
    def up(self, *fixtures, force_instances=(), overrides=None):
        for h in hosts.up(list(fixtures), self.workdir, force_instances=force_instances, overrides=overrides):
            self.hosts[h.fixture] = h
            if h.pid:
                self.watch.ours.add(h.pid)
        return [self.hosts[f] for f in fixtures]

    def host(self, fixture):
        return self.hosts[fixture]

    # -- client
    def start_client(self, reset=True, args=None):
        self.client = clientmod.Client.start(self.workdir, reset=reset, build=True, args=args)
        self.watch.ours.add(self.client.proc.pid)
        self.clients_logs.append(self.client.log_path)
        self.client.wait_idle(timeout=30)
        return self.client

    def restart_client(self, args=None):
        """Quit and relaunch the client, settings kept (no reset)."""
        self.client.stop()
        return self.start_client(reset=False, args=args)

    # -- captures
    def shot(self, label, window=0):
        """Capture into captures/<scenario>/<label>.png.  Qt windows: after the page settled and a popup's
        opening animation (0.3 s), so the capture shows what the assertions read."""
        p = self.captures / f"{label}.png"
        if window == "stream":
            return self.client.screenshot(p, "stream")
        time.sleep(0.3)
        return self.client.screenshot(p, window)

    def note(self, text):
        self.notes.append(text)

    # -- teardown
    def close(self):
        errors = []
        try:
            if self.client:
                self.client.stop()
        except Exception as e:  # noqa: BLE001 -- the hosts must still go down
            errors.append(f"client stop: {e}")
        try:
            hosts.down(self.workdir)
        except Exception as e:  # noqa: BLE001
            errors.append(f"hosts down: {e}")
        logs = self.run.root / "logs" / self.name
        logs.mkdir(parents=True, exist_ok=True)
        for f, h in self.hosts.items():
            for src in [h.dir / "sunshine.log"] + sorted((h.dir / "testhost").glob("*.jsonl")):
                if src.exists():
                    shutil.copyfile(src, logs / f"{f}-{src.name}")
        for i, p in enumerate(self.clients_logs, 1):
            if p and pathlib.Path(p).exists():
                shutil.copyfile(p, logs / f"client-{i}.log")
        try:
            import helpers as scenario_helpers  # scenarios/ is on sys.path while scenarios run
            self.notes += scenario_helpers.TRACE
            scenario_helpers.TRACE.clear()
        except ImportError:
            pass
        if self.notes:
            (logs / "notes.txt").write_text("\n".join(self.notes), encoding="utf-8")
        return errors


@pytest.fixture
def bench(request):
    run = _run(request.config)
    name = request.node.name
    workdir = pathlib.Path(tempfile.gettempdir()) / "lavtestbench-run" / name
    if workdir.exists():
        hosts.kill_previous(workdir)
        shutil.rmtree(workdir, ignore_errors=True)
    workdir.mkdir(parents=True, exist_ok=True)
    b = Bench(name, run, workdir)
    yield b
    errors = b.close()
    before, after = b.watch.finish()
    fg = [f"before: {before} ({window_exe(before)})"] + [f"{t} -> {h} ({exe})" for t, h, exe in b.watch.changes]
    fg.append(f"after: {after} ({window_exe(after)})")
    logs = run.root / "logs" / name
    logs.mkdir(parents=True, exist_ok=True)
    (logs / "foreground.txt").write_text(chr(10).join(fg), encoding="utf-8")
    assert not b.watch.seen_ours, f"a test window took the foreground: {b.watch.seen_ours[:3]}"
    assert not (after and window_pid(after) in b.watch.ours), "the foreground ends on a test window"
    assert not errors, "; ".join(errors)
    assert not clientmod.tasklist_pids(), "a LavArtemisTest.exe is still running"
