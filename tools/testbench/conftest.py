"""pytest fixtures of the desktop scenarios (tools/testbench/scenarios).

A scenario gets `bench`: a sandbox workdir, the fake PCs it asks for (hosts.up), the test client started from a
wiped "LavArtemis Test" organisation, a captures folder in the run's report, a foreground watch and a
visibility watch (no window of the client may ever be seen by the person using the PC).  Whatever
happens, the client and every test host are stopped at the end (leftovers are swept and reported), and the hosts'
logs and every client launch's log are copied into the report.  Reports: testbench-reports/<date>_<time>/
(report.py), outside git.  LAV_TESTBENCH_ATTEMPT=2 (set by `testbench run --rerun-failed-once`) marks a rerun: its
results are added to the folder's report, its captures and logs go to <scenario>-rerun."""
import ctypes
import ctypes.wintypes as wt
import datetime
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
SELFTEST = HERE / "selftest"  # LAV_TESTBENCH_SELFTEST: the release gate's own check (testbench.py)
ATTEMPT = int(os.environ.get("LAV_TESTBENCH_ATTEMPT", "1"))


def pytest_addoption(parser):
    parser.addoption("--report-dir", default=None, help="report folder (default testbench-reports/<date>_<time>)")


def _is_scenario(item):
    parents = pathlib.Path(str(item.fspath)).resolve().parents
    return SCENARIOS in parents or SELFTEST in parents


class Run:
    def __init__(self, root):
        self.root = pathlib.Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        previous, started = report.load(self.root) if ATTEMPT > 1 else ([], None)
        self.results = previous
        self.started = started or datetime.datetime.now().isoformat(timespec="seconds")
        self.current = {}  # nodeid -> result being built (call phase, then teardown)
        self.flags = {}    # nodeid -> flags set by the bench teardown


_RUN = {}


def _run(config):
    if "run" not in _RUN:
        d = config.getoption("--report-dir")
        if not d:
            d = DEFAULT_REPORTS / datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")
        _RUN["run"] = Run(d)
    return _RUN["run"]


def record(run, nodeid, name, doc, rep):
    """Fold one pytest report (setup, call, teardown) into the scenario's row for this attempt.  The row is opened
    by the first report and closed only by the teardown report, which pytest always sends: a setup failure is the
    row's outcome ("error", phase setup), a teardown failure after a pass makes it an "error", and a test never
    gets a second row."""
    r = run.current.get(nodeid)
    if r is None:
        r = run.current[nodeid] = {
            "id": nodeid, "name": name, "attempt": ATTEMPT, "status": "passed", "phase": "call", "duration": 0.0,
            "reason": "", "error": "", "flags": [], "captures": f"captures/{_bench_name(name)}", "doc": doc}
    r["duration"] = round(r["duration"] + rep.duration, 1)
    xfail = getattr(rep, "wasxfail", None)
    if rep.when == "setup":
        if rep.failed:
            r["status"], r["phase"], r["error"] = "error", "setup", str(rep.longrepr)[-3000:]
        elif rep.skipped:
            r["status"] = "xfail" if xfail is not None else "skipped"
            r["phase"], r["reason"] = "setup", xfail or str(rep.longrepr)[-300:]
    elif rep.when == "call":
        r["status"] = ("xfail" if xfail is not None and rep.skipped else
                       "xpass" if xfail is not None else rep.outcome)
        r["phase"], r["reason"] = "call", xfail or ""
        if rep.failed:
            r["error"] = str(rep.longrepr)[-3000:]
    else:  # teardown: always sent, closes the row
        if rep.failed and r["status"] in ("passed", "xfail", "skipped"):
            r["status"], r["phase"], r["error"] = "error", "teardown", str(rep.longrepr)[-3000:]
        r["flags"] = run.flags.pop(nodeid, [])
        run.results.append(run.current.pop(nodeid))
    return r


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    if _is_scenario(item):
        doc = (item.function.__doc__ or "").strip() if hasattr(item, "function") else ""
        record(_run(item.config), item.nodeid, item.name, doc, outcome.get_result())


def pytest_sessionfinish(session, exitstatus):
    if "run" in _RUN:
        run = _RUN.pop("run")
        report.write(run.root, run.results, run.started, datetime.datetime.now())


# ---------------------------------------------------------------- foreground watch

_user32 = ctypes.windll.user32
_kernel32 = ctypes.windll.kernel32
EVENT_SYSTEM_FOREGROUND = 0x0003
WINEVENT_OUTOFCONTEXT = 0x0000
WM_QUIT = 0x0012
WINEVENTPROC = ctypes.WINFUNCTYPE(None, wt.HANDLE, wt.DWORD, wt.HWND, wt.LONG, wt.LONG, wt.DWORD, wt.DWORD)
_user32.SetWinEventHook.argtypes = [wt.UINT, wt.UINT, wt.HMODULE, WINEVENTPROC, wt.DWORD, wt.DWORD, wt.UINT]
_user32.SetWinEventHook.restype = wt.HANDLE
_user32.UnhookWinEvent.argtypes = [wt.HANDLE]
_user32.GetMessageW.argtypes = [ctypes.POINTER(wt.MSG), wt.HWND, wt.UINT, wt.UINT]
_user32.PostThreadMessageW.argtypes = [wt.DWORD, wt.UINT, wt.WPARAM, wt.LPARAM]
_user32.GetWindowThreadProcessId.argtypes = [wt.HWND, ctypes.POINTER(wt.DWORD)]
_user32.GetWindowTextW.argtypes = [wt.HWND, wt.LPWSTR, ctypes.c_int]
_user32.GetForegroundWindow.restype = wt.HWND
CRASH_DIALOG_EXES = ("werfault.exe", "werfaultsecure.exe", "wermgr.exe")


def window_pid(hwnd):
    pid = wt.DWORD(0)
    if hwnd:
        _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    return pid.value


def window_exe(hwnd):
    return hosts._image_path(window_pid(hwnd)) or "?" if hwnd else "-"


def window_title(hwnd):
    buf = ctypes.create_unicode_buffer(256)
    if hwnd:
        _user32.GetWindowTextW(hwnd, buf, 256)
    return buf.value


def classify(hwnd, exe):
    """'ours' (the test client or a test host: the scenario fails), 'none' (no foreground window), 'crash' (a
    Windows error-reporting dialog), or 'other' (the user's own windows)."""
    if not hwnd:
        return "none"
    if exe and exe not in ("?", "-"):
        if hosts._same_exe(exe, clientmod.exe_path()) or hosts._same_exe(exe, hosts.sunshine_exe()):
            return "ours"
        if os.path.basename(exe).lower() in CRASH_DIALOG_EXES:
            return "crash"
    return "other"


class ForegroundWatch:
    """Every foreground switch, caught by a WinEvent hook (EVENT_SYSTEM_FOREGROUND, out of context, on a thread of
    its own with a message loop): nothing between two samples can be missed.  A switch to a window of the test
    client or of a test host (identified by the exe path, whichever launch it was) fails the scenario; a switch to
    no window or to a crash dialog is flagged in the report; the user's own windows (they keep working during a
    run) are only recorded."""

    def __init__(self):
        self.before = _user32.GetForegroundWindow()
        self.events = []  # (time, hwnd, pid, exe, title, kind)
        self.hooked = False
        self.thread_id = None
        self._ready = threading.Event()
        self._t = threading.Thread(target=self._loop, daemon=True)
        self._t.start()
        self._ready.wait(5)

    def _on_event(self, _hook, _event, hwnd, _obj, _child, _thread, _ms):
        exe = window_exe(hwnd)
        self.events.append((time.strftime("%H:%M:%S"), hwnd or 0, window_pid(hwnd), exe, window_title(hwnd),
                            classify(hwnd, exe)))

    def _loop(self):
        self.thread_id = _kernel32.GetCurrentThreadId()
        self._proc = WINEVENTPROC(self._on_event)  # kept alive as long as the hook
        hook = _user32.SetWinEventHook(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND, None, self._proc, 0, 0,
                                       WINEVENT_OUTOFCONTEXT)
        self.hooked = bool(hook)
        self._ready.set()
        if not hook:
            return
        msg = wt.MSG()
        while _user32.GetMessageW(ctypes.byref(msg), None, 0, 0) > 0:
            _user32.TranslateMessage(ctypes.byref(msg))
            _user32.DispatchMessageW(ctypes.byref(msg))
        _user32.UnhookWinEvent(hook)

    def finish(self):
        if self.thread_id:
            _user32.PostThreadMessageW(self.thread_id, WM_QUIT, 0, 0)
        self._t.join(5)
        return self.before, _user32.GetForegroundWindow()

    def ours(self):
        return [e for e in self.events if e[5] == "ours"]

    def flags(self):
        out = []
        for t, hwnd, pid, exe, title, kind in self.events:
            if kind == "none":
                out.append(f"FOREGROUND {t}: no window (hwnd 0)")
            elif kind == "crash":
                out.append(f"FOREGROUND {t}: crash dialog {os.path.basename(exe)} « {title} »")
        return out


# ---------------------------------------------------------------- visibility watch

EVENT_OBJECT_SHOW = 0x8002
EVENT_OBJECT_REORDER = 0x8004
EVENT_OBJECT_LOCATIONCHANGE = 0x800B
OBJID_WINDOW = 0
GA_ROOT = 2
GWL_EXSTYLE = -20
WS_EX_TOPMOST = 0x8
MONITOR_DEFAULTTONULL = 0
DWMWA_CLOAKED = 14
_dwmapi = ctypes.windll.dwmapi
_dwmapi.DwmGetWindowAttribute.argtypes = [wt.HWND, wt.DWORD, ctypes.c_void_p, wt.DWORD]
WNDENUMPROC = ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
_user32.GetAncestor.argtypes = [wt.HWND, wt.UINT]
_user32.GetAncestor.restype = wt.HWND
_user32.IsWindowVisible.argtypes = [wt.HWND]
_user32.GetWindowRect.argtypes = [wt.HWND, ctypes.POINTER(wt.RECT)]
_user32.GetWindowLongW.argtypes = [wt.HWND, ctypes.c_int]
_user32.MonitorFromRect.argtypes = [ctypes.POINTER(wt.RECT), wt.DWORD]
_user32.MonitorFromRect.restype = wt.HMONITOR
_user32.EnumWindows.argtypes = [WNDENUMPROC, wt.LPARAM]


def windows_visible():
    """LAV_TEST_VISIBLE=1: the test client shows its windows (debugging by eye, LavArtemis-Qt's
    testctl/hiddenwindows.h); the visibility watch then checks nothing."""
    return os.environ.get("LAV_TEST_VISIBLE") == "1"


def window_visibility(hwnd):
    """(rect, seen, topmost) of a shown top-level window, None for a hidden one.  Seen: on a monitor and not cloaked
    (DWMWA_CLOAKED: DWM draws nothing of a cloaked window and the mouse goes through it)."""
    if not _user32.IsWindowVisible(hwnd):
        return None
    r = wt.RECT()
    if not _user32.GetWindowRect(hwnd, ctypes.byref(r)):
        return None
    cloaked = wt.DWORD(0)
    if _dwmapi.DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, ctypes.byref(cloaked), ctypes.sizeof(cloaked)) != 0:
        cloaked.value = 0  # unreadable: counted as not cloaked
    on_monitor = bool(_user32.MonitorFromRect(ctypes.byref(r), MONITOR_DEFAULTTONULL))
    topmost = bool(_user32.GetWindowLongW(hwnd, GWL_EXSTYLE) & WS_EX_TOPMOST)
    return (r.left, r.top, r.right, r.bottom), on_monitor and not cloaked.value, topmost


def client_windows_seen():
    """Every shown top-level window of a test client (any launch, identified by the exe path) that the person using
    the PC could see (on a monitor and not cloaked) or that is topmost: [(hwnd, pid, title, rect, seen, topmost)].
    Empty is the rule."""
    found, ours = [], {}

    def each(hwnd, _l):
        pid = window_pid(hwnd)
        if pid not in ours:
            exe = hosts._image_path(pid)
            ours[pid] = bool(exe) and hosts._same_exe(exe, clientmod.exe_path())
        if ours[pid]:
            v = window_visibility(hwnd)
            if v and (v[1] or v[2]):
                found.append((hwnd, pid, window_title(hwnd)) + v)
        return True

    _user32.EnumWindows(WNDENUMPROC(each), 0)
    return found


class VisibilityWatch:
    """Every top-level window of the test client, checked each time it shows, moves or changes place in the Z order
    (WinEvent hook, out of context, on a thread of its own with a message loop), and once more before the client
    stops: one the person using the PC could see (on a monitor and not cloaked) or topmost fails the scenario.
    Not installed with LAV_TEST_VISIBLE=1."""

    def __init__(self):
        self.seen = {}  # hwnd -> (title, last rect)
        self.violations = []  # (time, hwnd, pid, title, rect, seen, topmost)
        self._ours = {}
        self.hooked = False
        self.thread_id = None
        if windows_visible():
            return
        self._ready = threading.Event()
        self._t = threading.Thread(target=self._loop, daemon=True)
        self._t.start()
        self._ready.wait(5)

    def _is_ours(self, pid):
        if pid not in self._ours:
            exe = hosts._image_path(pid)
            self._ours[pid] = bool(exe) and hosts._same_exe(exe, clientmod.exe_path())
        return self._ours[pid]

    def check(self, hwnd):
        pid = window_pid(hwnd)
        if not pid or not self._is_ours(pid):
            return
        v = window_visibility(hwnd)
        if v is None:
            return
        title = window_title(hwnd)
        self.seen[hwnd] = (title, v[0])
        if v[1] or v[2]:
            self.violations.append((time.strftime("%H:%M:%S"), hwnd, pid, title) + v)

    def _on_event(self, _hook, event, hwnd, obj, child, _thread, _ms):
        if obj != OBJID_WINDOW or child != 0 or not hwnd:
            return
        if event not in (EVENT_OBJECT_SHOW, EVENT_OBJECT_REORDER, EVENT_OBJECT_LOCATIONCHANGE):
            return
        if _user32.GetAncestor(hwnd, GA_ROOT) != hwnd:
            return
        self.check(hwnd)

    def _loop(self):
        self.thread_id = _kernel32.GetCurrentThreadId()
        self._proc = WINEVENTPROC(self._on_event)  # kept alive as long as the hook
        hook = _user32.SetWinEventHook(EVENT_OBJECT_SHOW, EVENT_OBJECT_LOCATIONCHANGE, None, self._proc, 0, 0,
                                       WINEVENT_OUTOFCONTEXT)
        self.hooked = bool(hook)
        self._ready.set()
        if not hook:
            return
        msg = wt.MSG()
        while _user32.GetMessageW(ctypes.byref(msg), None, 0, 0) > 0:
            _user32.TranslateMessage(ctypes.byref(msg))
            _user32.DispatchMessageW(ctypes.byref(msg))
        _user32.UnhookWinEvent(hook)

    def sweep(self):
        """A last look at every window of the client, still running (the hook only sees changes)."""
        if windows_visible():
            return
        for v in client_windows_seen():
            self.violations.append((time.strftime("%H:%M:%S"),) + v)

    def finish(self):
        if self.thread_id:
            _user32.PostThreadMessageW(self.thread_id, WM_QUIT, 0, 0)
            self._t.join(5)

    def lines(self):
        if windows_visible():
            return ["LAV_TEST_VISIBLE=1: the windows were shown, not checked"]
        out = [f"{len(self.seen)} window(s) of the test client shown, none seen by the user nor topmost"
               if not self.violations else f"{len(self.violations)} VIOLATION(S)"]
        out += [f"shown {h}: « {t} » last at {r}" for h, (t, r) in self.seen.items()]
        out += [f"VIOLATION {t}: {h} pid {pid} « {title} » rect {r} seen {s} topmost {top}"
                for t, h, pid, title, r, s, top in self.violations]
        return out


# ---------------------------------------------------------------- the bench

def _bench_name(test_name):
    return test_name + ("-rerun" if ATTEMPT > 1 else "")


class Bench:
    def __init__(self, name, run, workdir):
        self.name = name
        self.run = run
        self.workdir = workdir
        self.captures = run.root / "captures" / name
        self.captures.mkdir(parents=True, exist_ok=True)
        self.hosts = {}
        self.client = None
        self.watch = ForegroundWatch()
        self.visibility = VisibilityWatch()
        self.notes = []
        self.flags = []

    # -- hosts
    def up(self, *fixtures, force_instances=(), overrides=None):
        for h in hosts.up(list(fixtures), self.workdir, force_instances=force_instances, overrides=overrides):
            self.hosts[h.fixture] = h
        return [self.hosts[f] for f in fixtures]

    def host(self, fixture):
        return self.hosts[fixture]

    def restart_host(self, fixture, changes=None):
        """Restart one PC with pc.json values changed (None drops a key), keeping its sandbox (identity, pairings)."""
        return hosts.restart(self.hosts[fixture], self.workdir, changes or {})

    def stop_host(self, fixture):
        hosts.stop(self.hosts[fixture], self.workdir)

    # -- client
    def start_client(self, reset=True, args=None, env=None):
        """The client, from a wiped organisation unless reset=False.  A client still running is stopped first:
        never two clients on the command pipe.  env: extra environment (LAV_TEST_TOUCHSCREEN=1)."""
        if self.client is not None and self.client.proc is not None:
            self.client.stop()
        self.client = clientmod.Client.start(self.workdir, reset=reset, build=True, args=args, env=env)
        self.client.wait_idle(timeout=30)
        return self.client

    def restart_client(self, args=None, env=None):
        """Quit and relaunch the client, settings kept (no reset); args: command-line arguments (an art:// link)."""
        return self.start_client(reset=False, args=args, env=env)

    def expect_client_exit(self, timeout=30.0):
        """The client is expected to quit by itself (a command-line launch that ends): wait for it and return its
        exit code; it then no longer counts as having died during the scenario."""
        c = self.client
        assert c is not None and c.proc is not None, "no client running"
        assert c._wait_exit(timeout), f"the client did not quit within {timeout:.0f}s"
        code = c.proc.returncode
        if c.channel:
            c.channel.t.close()
            c.channel = None
        if c.proc in clientmod._PROCS:
            clientmod._PROCS.remove(c.proc)
        if c.workdir and (c.workdir / "client.json").exists():
            (c.workdir / "client.json").unlink()
        c.proc = None
        return code

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
            self.visibility.sweep()  # while the client still runs
        except Exception as e:  # noqa: BLE001 -- the teardown must go on
            errors.append(f"visibility sweep: {e}")
        if self.client is not None and self.client.proc is not None and self.client.proc.poll() is not None:
            errors.append(f"the test client died during the scenario (exit code {self.client.proc.returncode}); "
                          "see its log")
        try:
            if self.client:
                self.client.stop()
        except Exception as e:  # noqa: BLE001 -- the hosts must still go down
            errors.append(f"client stop: {e}")
        try:
            hosts.down(self.workdir)
        except Exception as e:  # noqa: BLE001
            errors.append(f"hosts down: {e}")
        # Whatever the PID files said: nothing of this scenario may outlive it
        try:
            for pid, cmd in hosts.sweep(self.workdir):
                errors.append(f"leftover test host killed: pid {pid} ({cmd})")
        except Exception as e:  # noqa: BLE001
            errors.append(f"leftover sweep: {e}")
        for pid in clientmod.tasklist_pids():
            if clientmod._is_our_exe(pid):
                clientmod.subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
                errors.append(f"leftover test client killed: pid {pid}")
            else:  # another build's, or unreadable: never killed, but the run is not clean
                errors.append(f"a {clientmod.EXE_NAME} that is not this test build (or unreadable) runs: pid {pid} "
                              f"({clientmod._image_path(pid)})")
        errors += hosts.PROBLEMS
        hosts.PROBLEMS.clear()
        logs = self.run.root / "logs" / self.name
        logs.mkdir(parents=True, exist_ok=True)
        for f, h in self.hosts.items():
            for src in [h.dir / "sunshine.log"] + sorted((h.dir / "testhost").glob("*.jsonl")):
                if src.exists():
                    shutil.copyfile(src, logs / f"{f}-{src.name}")
        for src in sorted(self.workdir.glob("client-*.log")):  # every launch, the reset one included
            shutil.copyfile(src, logs / src.name)
        # What the helpers and the client did, retries included: notes; every retry is also flagged in report.md
        trace = list(clientmod.RETRIES)
        clientmod.RETRIES.clear()
        try:
            import helpers as scenario_helpers  # scenarios/ is on sys.path while scenarios run
            trace += scenario_helpers.TRACE
            scenario_helpers.TRACE.clear()
        except ImportError:
            pass
        self.notes += trace
        retries = [t for t in trace if "RETRY" in t]
        if retries:
            self.flags.append(f"RETRY x{len(retries)}: " + " / ".join(r[:120] for r in retries[:3]))
        if self.notes:
            (logs / "notes.txt").write_text("\n".join(self.notes), encoding="utf-8")
        return errors


@pytest.fixture
def bench(request):
    run = _run(request.config)
    name = _bench_name(request.node.name)
    workdir = pathlib.Path(tempfile.gettempdir()) / "lavtestbench-run" / name
    setup_notes = []
    if workdir.exists():  # an earlier run of this scenario: whatever it left goes first
        hosts.kill_previous(workdir)
        setup_notes += [f"before the scenario: {p}" for p in hosts.PROBLEMS]
        hosts.PROBLEMS.clear()
        setup_notes += [f"before the scenario: leftover test host killed, pid {pid}" for pid, _c in hosts.sweep(workdir)]
        shutil.rmtree(workdir, ignore_errors=True)
    workdir.mkdir(parents=True, exist_ok=True)
    b = Bench(name, run, workdir)
    b.flags += setup_notes
    assert b.watch.hooked, "the foreground hook (SetWinEventHook) could not be installed"
    assert b.visibility.hooked or windows_visible(), "the visibility hook (SetWinEventHook) could not be installed"
    try:
        yield b
    finally:
        errors = b.close()
        before, after = b.watch.finish()
        fg = [f"before: {before} ({window_exe(before)})"]
        fg += [f"{t} -> {h} pid {pid} ({exe}) « {title} » [{kind}]" for t, h, pid, exe, title, kind in b.watch.events]
        fg.append(f"after: {after} ({window_exe(after)})")
        logs = run.root / "logs" / name
        logs.mkdir(parents=True, exist_ok=True)
        (logs / "foreground.txt").write_text(chr(10).join(fg), encoding="utf-8")
        b.visibility.finish()
        (logs / "visibility.txt").write_text(chr(10).join(b.visibility.lines()), encoding="utf-8")
        end_flags = [] if after else ["FOREGROUND end of scenario: no window (hwnd 0)"]
        run.flags[request.node.nodeid] = b.flags + b.watch.flags() + end_flags
    assert not b.watch.ours(), f"a test window took the foreground: {b.watch.ours()[:3]}"
    assert not b.visibility.violations, \
        f"a test window could be seen by the user, or was topmost: {b.visibility.lines()[-3:]}"
    assert not errors, "; ".join(errors)
