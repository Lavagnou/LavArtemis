"""Sandboxed LavApollo test hosts for the testbench.

Every instance is the test build of sunshine (`<LavApollo>/build/t/sunshine.exe`, `test_host = enabled`)
running as a fake PC: its own appdata (LAVAPOLLO_APPDATA) and ProgramData under `<workdir>/<fixture>/`,
loopback only, ports 40000-44999.  Nothing is ever written outside the workdir and the real LavApollo
service (base port 45989 on this PC) is never contacted, stopped or configured.
"""
import ctypes
import dataclasses
import json
import os
import pathlib
import shutil
import socket
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

import covers

HERE = pathlib.Path(__file__).resolve().parent
FIXTURES = HERE / "fixtures"


B = chr(92)  # backslash


def _resolve_lavapollo():
    root = pathlib.Path(os.environ.get("LAV_TESTBENCH_LAVAPOLLO", HERE.parents[2] / "LavApollo")).resolve()
    low = str(root).lower()
    if (B + "program files") in low or (B+"programdata") in low:
        raise RuntimeError(f"{root}: the test build must never be the installed LavApollo")
    if not (root / "build" / "t").is_dir() or not (root / "src").is_dir():
        raise RuntimeError(f"{root}: no src/ and build/t/ layout (set LAV_TESTBENCH_LAVAPOLLO)")
    return root


_lazy = {}


def __getattr__(name):
    """LAVAPOLLO / SUNSHINE are resolved on first use: importing this module needs no LavApollo checkout."""
    if name == "LAVAPOLLO":
        if "root" not in _lazy:
            _lazy["root"] = _resolve_lavapollo()
        return _lazy["root"]
    if name == "SUNSHINE":
        return __getattr__("LAVAPOLLO") / "build" / "t" / "sunshine.exe"
    raise AttributeError(name)


def lavapollo_root():
    return __getattr__("LAVAPOLLO")


def sunshine_exe():
    return __getattr__("SUNSHINE")


# Popen objects of the instances started by this process (kept so they are waited on, never leaked).
_PROCS = []
PORT_MIN, PORT_MAX = 40000, 44999
# Offsets used by an instance around its base port (base-5 .. base+21).
PORT_SPAN = (-5, 21)
STARTUP_TIMEOUT = 60.0
# The display mode a fake PC announces in serverinfo (CurrentDisplayWidth/Height/RefreshRate, LavApollo
# `test_host_display`): fixed by the fixture, never the monitor of the PC the tests run on.  Same default as LavApollo.
DEFAULT_DISPLAY = (1920, 1080, 60)
MSYS_PATH = "C:\\msys64\\ucrt64\\bin;C:\\msys64\\usr\\bin;"


@dataclasses.dataclass
class Host:
    name: str
    port: int
    pid: "int | None"
    dir: pathlib.Path
    fixture: str = ""
    display: tuple = DEFAULT_DISPLAY  # (width, height, refresh) announced in serverinfo


def default_workdir():
    return pathlib.Path(tempfile.gettempdir()) / "lavtestbench"


def build_jobs():
    return int(os.environ.get("LAV_TESTBENCH_JOBS", "1"))


# ------------------------------------------------------------------ fixtures / files

def available_fixtures():
    return sorted(p.name for p in FIXTURES.iterdir() if (p / "pc.json").is_file())


def load_fixture(name):
    return json.loads((FIXTURES / name / "pc.json").read_text(encoding="utf-8"))


def display_mode(pc):
    """(width, height, refresh) a fake PC announces: pc.json's optional "display" ("2560x1600x120"), else
    DEFAULT_DISPLAY.  Refused unless LavApollo would accept it too (its parse_mode: 1..16384, refresh 1..1000)."""
    text = pc.get("display")
    if not text:
        return DEFAULT_DISPLAY
    parts = str(text).split("x")
    limits = (16384, 16384, 1000)
    if len(parts) != 3 or not all(p.isdigit() and 1 <= int(p) <= m for p, m in zip(parts, limits)):
        raise ValueError(f"{pc.get('name')}: display {text!r} is not WIDTHxHEIGHTxREFRESH")
    return tuple(int(p) for p in parts)


def _slash(p):
    return str(p).replace("\\", "/")


def render_conf(pc, pcdir):
    """sunshine.conf of a fake PC.  Absolute, forward-slash paths: relative ones fall into build/t/config."""
    pcdir = pathlib.Path(pcdir)
    lines = [
        "test_host = enabled",
        f"port = {pc['port']}",
        f"sunshine_name = {pc['name']}",
        f"file_state = {_slash(pcdir / 'sunshine_state.json')}",
        f"file_apps = {_slash(pcdir / 'apps.json')}",
        f"credentials_file = {_slash(pcdir / 'credentials.json')}",
        f"log_path = {_slash(pcdir / 'sunshine.log')}",
    ]
    if pc.get("mac"):
        lines.append(f"test_host_mac = {pc['mac']}")
    if pc.get("appversion"):
        lines.append(f"test_host_appversion = {pc['appversion']}")
    if pc.get("this_pc"):
        lines.append("test_host_this_pc = enabled")
    if pc.get("form_factor"):
        # serverinfo HostFormFactor (tower / mini / laptop), never the SMBIOS of the machine running the bench
        lines.append(f"test_host_form_factor = {pc['form_factor']}")
    if pc.get("link"):
        # serverinfo HostLink (wired / wifi): loopback has neither
        lines.append(f"test_host_link = {pc['link']}")
    # Always written: the scenarios derive the negotiated stream size from the same value
    lines.append("test_host_display = {}x{}x{}".format(*display_mode(pc)))
    if pc.get("benchmark_app") is False:
        # The hidden Benchmark app is on by default (serverinfo BenchmarkAppId): a PC without it
        lines.append("benchmark_app = disabled")
    if pc.get("server_cmd"):
        # Commands of the host PC (in-game menu); run in test mode as logged no-ops (commands.jsonl)
        lines.append("server_cmd = " + json.dumps(pc["server_cmd"], separators=(",", ":")))
    return "\n".join(lines) + "\n"


def write_pc_files(pc, fixture, pcdir):
    pcdir = pathlib.Path(pcdir)
    src = FIXTURES / fixture
    pcdir.mkdir(parents=True, exist_ok=True)
    (pcdir / "sunshine.conf").write_text(render_conf(pc, pcdir), encoding="utf-8")
    shutil.copyfile(src / "apps.json", pcdir / "apps.json")
    pn = pcdir / "pd" / "LavApollo" / "playnite"
    (pn / "covers").mkdir(parents=True, exist_ok=True)
    lib = json.loads((src / "playnite" / "library.json").read_text(encoding="utf-8"))
    for g in lib["games"]:
        if g.get("cover"):
            covers.make_cover(g["name"], pn / "covers" / g["cover"])
        if g.get("background"):
            covers.make_background(g["name"], pn / "backgrounds" / g["background"])
    (pn / "library.json").write_text(json.dumps(lib, ensure_ascii=False, indent=2), encoding="utf-8")
    running = bool(pc.get("playnite_running"))
    state = {"version": 1, "playniteRunning": running, "mode": "fullscreen",
             "pid": 4242 if running else 0, "game": None, "updatedAt": "2026-11-24T09:00:00Z"}
    (pn / "state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")


# ------------------------------------------------------------------ processes

_k32 = ctypes.windll.kernel32 if os.name == "nt" else None
# A kernel32 whose errors ctypes keeps (ctypes.get_last_error): GetLastError() read after a ctypes call is not
# reliable
_k32e = ctypes.WinDLL("kernel32", use_last_error=True) if os.name == "nt" else None
_PROCESS_QUERY_LIMITED = 0x1000


def _image_path(pid):
    """Full exe path of a process, or None when it does not exist / cannot be read."""
    if _k32 is None or not pid:
        return None
    _k32.OpenProcess.restype = ctypes.c_void_p
    _k32.CloseHandle.argtypes = [ctypes.c_void_p]
    _k32.QueryFullProcessImageNameW.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_wchar_p,
                                                ctypes.POINTER(ctypes.c_ulong)]
    h = _k32.OpenProcess(_PROCESS_QUERY_LIMITED, False, int(pid))
    if not h:
        return None
    try:
        buf = ctypes.create_unicode_buffer(1024)
        size = ctypes.c_ulong(1024)
        ok = _k32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size))
        return buf.value if ok else None
    finally:
        _k32.CloseHandle(h)


def _same_exe(a, b):
    return a is not None and os.path.normcase(os.path.abspath(a)) == os.path.normcase(os.path.abspath(b))


def pid_alive(pid):
    """True while the process runs, also when its image path cannot be read (access denied): an unreadable
    process is not a dead one."""
    if _k32 is None or not pid:
        return False
    if _image_path(pid) is not None:
        return True
    _k32e.OpenProcess.restype = ctypes.c_void_p
    _k32e.OpenProcess.argtypes = [ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
    _k32e.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
    _k32e.CloseHandle.argtypes = [ctypes.c_void_p]
    h = _k32e.OpenProcess(0x00100000, False, int(pid))  # SYNCHRONIZE
    if h:
        try:
            return _k32e.WaitForSingleObject(h, 0) == 0x102  # WAIT_TIMEOUT: still running
        finally:
            _k32e.CloseHandle(h)
    return ctypes.get_last_error() == 5  # ERROR_ACCESS_DENIED: it exists


def _is_test_sunshine(pid):
    return _same_exe(_image_path(pid), sunshine_exe())


# Problems met while stopping instances (a recorded PID that is alive but not identifiable, an instance that
# survives taskkill): the scenario teardown reports them instead of carrying on silently.
PROBLEMS = []


def _kill(pid):
    if _is_test_sunshine(pid):  # never anything but the test sunshine
        subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
        for _ in range(50):
            if not pid_alive(pid):
                break
            time.sleep(0.1)
        if pid_alive(pid):
            PROBLEMS.append(f"test host pid {pid} still alive 5 s after taskkill")
    elif pid_alive(pid):
        PROBLEMS.append(f"recorded pid {pid} is alive but is not {sunshine_exe()} ({_image_path(pid)}): not killed")


def _cim_sunshine():
    script = ("Get-CimInstance Win32_Process -Filter \"Name='sunshine.exe'\" | "
              "Select-Object ProcessId,ExecutablePath,CommandLine | ConvertTo-Json -Compress")
    r = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True, timeout=60)
    try:
        rows = json.loads(r.stdout or "[]")
    except ValueError:
        return []
    return [rows] if isinstance(rows, dict) else rows


def _is_in_sandbox(cmd, workdir):
    """The command line names a file inside this workdir: the workdir path followed by a separator (a bare prefix
    would also match a sibling such as <workdir>-rerun)."""
    marker = os.path.normcase(str(pathlib.Path(workdir))).rstrip(B + "/")
    low = os.path.normcase(cmd or "")
    return (marker + B) in low or (marker + "/") in low


def leftover_instances(workdir):
    """Test sunshine processes still running on this workdir's sandbox, found by their command line (the sandbox
    conf path), whatever the PID file says.  [(pid, command line)]"""
    out = []
    for row in _cim_sunshine():
        exe, cmd = row.get("ExecutablePath"), row.get("CommandLine") or ""
        # the sandbox's conf path in the command line is ours alone; the exe path must match when readable
        if (exe is None or _same_exe(exe, sunshine_exe())) and _is_in_sandbox(cmd, workdir):
            out.append((row["ProcessId"], cmd))
    return out


def sweep(workdir):
    """Kill the test hosts of this workdir that outlived down() (identified by exe path and sandbox command line);
    returns what was found, for the report."""
    found = leftover_instances(workdir)
    for pid, _cmd in found:
        if _is_test_sunshine(pid):
            _kill(pid)
            continue
        # image path unreadable: only its command line identifies it, so read it again right before the kill (the
        # PID may have been reused since), and kill that process alone (no /T: its tree is not identified)
        again = next((r for r in _cim_sunshine() if r.get("ProcessId") == pid), None)
        if again and _is_in_sandbox(again.get("CommandLine"), workdir):
            subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True)
        else:
            PROBLEMS.append(f"pid {pid} no longer runs this sandbox's sunshine: not killed")
    return found


def kill_previous(workdir):
    f = pathlib.Path(workdir) / "pids.json"
    if f.exists():
        try:
            records = json.loads(f.read_text())
        except ValueError:
            records = []
        for r in records:
            _kill(r.get("pid"))
        f.unlink()


def down(workdir=None):
    """Stop the instances recorded in workdir.  Removes nothing but the PID file."""
    kill_previous(workdir or default_workdir())
    for p in list(_PROCS):
        try:
            p.wait(timeout=5)
        except Exception:
            pass
        if p.poll() is not None:
            _PROCS.remove(p)


# ------------------------------------------------------------------ ports / build

def _port_free(port):
    for kind in (socket.SOCK_STREAM, socket.SOCK_DGRAM):
        s = socket.socket(socket.AF_INET, kind)
        try:
            s.bind(("127.0.0.1", port))
        except OSError:
            return False
        finally:
            s.close()
    return True


def check_ports_free(base):
    busy = [p for p in range(base + PORT_SPAN[0], base + PORT_SPAN[1] + 1) if not _port_free(p)]
    if busy:
        raise RuntimeError(f"ports busy around {base}: {busy} (another process holds them)")


def sources_newer_than_exe():
    """True when the test exe is older than the LavApollo sources (or missing)."""
    if not sunshine_exe().exists():
        return True
    exe = sunshine_exe().stat().st_mtime
    newest = (lavapollo_root() / "CMakeLists.txt").stat().st_mtime
    for root in (lavapollo_root() / "src", lavapollo_root() / "cmake"):
        for dirpath, _d, files in os.walk(root):
            for f in files:
                if f.endswith((".cpp", ".h", ".c", ".cmake", ".txt", ".in")):
                    newest = max(newest, os.stat(os.path.join(dirpath, f)).st_mtime)
    return newest > exe


def ensure_built():
    if not sources_newer_than_exe():
        return
    env = dict(os.environ)
    env["PATH"] = MSYS_PATH + env["PATH"]
    r = subprocess.run(["ninja", "-C", str(lavapollo_root() / "build" / "t"), f"-j{build_jobs()}", "sunshine"], env=env)
    if r.returncode:
        raise RuntimeError("sunshine build failed (out of memory? retry with LAV_TESTBENCH_JOBS=1)")


# ------------------------------------------------------------------ up / pin

# Loopback requests never go through a proxy configured on the machine.
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def _serverinfo_ok(port):
    try:
        return b"LavTestHost" in OPENER.open(f"http://127.0.0.1:{port}/serverinfo", timeout=2).read()
    except (OSError, urllib.error.URLError):
        return False


def _spawn(pc, pcdir):
    """Launch one test sunshine on its sandbox dir; returns the Popen."""
    # The exe imports only Windows system DLLs (checked with objdump), so the child gets a system-only
    # PATH: nothing from the user's PATH (the installed LavApollo, msys64...) can be loaded by accident.
    sysroot = os.environ.get("SystemRoot", "C:" + B + "Windows")
    env = dict(os.environ, LAVAPOLLO_APPDATA=str(pcdir), ProgramData=str(pcdir / "pd"),
               PATH=sysroot + B + "System32;" + sysroot)
    flags = 0x08000000 | 0x00000200  # CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP
    p = subprocess.Popen([str(sunshine_exe()), str(pcdir / "sunshine.conf")], cwd=str(lavapollo_root() / "build" / "t"),
                         env=env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, creationflags=flags)
    _PROCS.append(p)
    return p


def _wait_serverinfo(hosts_, workdir):
    deadline = time.time() + STARTUP_TIMEOUT
    for h in hosts_:
        if h.pid is None:
            continue
        while not _serverinfo_ok(h.port):
            if not pid_alive(h.pid) or time.time() > deadline:
                down(workdir)
                raise RuntimeError(f"{h.name}: no serverinfo on {h.port} (see {h.dir / 'sunshine.log'})")
            time.sleep(0.3)


def up(fixtures, workdir=None, force_instances=(), overrides=None):
    """Start the fixtures' fake PCs.  force_instances: fixtures run even though their pc.json says
    "instance": false (offline-test: started to be paired, then stopped with stop() to go offline).
    overrides: {fixture: {pc.json key: value or None to drop it}} for this run only."""
    workdir = pathlib.Path(workdir or default_workdir())
    workdir.mkdir(parents=True, exist_ok=True)
    kill_previous(workdir)
    ensure_built()
    fixtures = list(fixtures) or available_fixtures()
    pcs = {f: load_fixture(f) for f in fixtures}
    for f in force_instances:
        pcs[f]["instance"] = True
    for f, changes in (overrides or {}).items():
        for k, v in changes.items():
            if v is None:
                pcs[f].pop(k, None)
            else:
                pcs[f][k] = v
    for f, pc in pcs.items():
        base = pc["port"]
        if not (PORT_MIN <= base + PORT_SPAN[0] and base + PORT_SPAN[1] <= PORT_MAX):
            raise RuntimeError(f"{f}: port {base} outside {PORT_MIN}-{PORT_MAX}")
        if pc.get("instance", True):
            check_ports_free(base)
        elif not _port_free(base):
            raise RuntimeError(f"{f}: port {base} should stay closed but is in use")
    result, records = [], []
    for f, pc in pcs.items():
        pcdir = workdir / f
        if not pc.get("instance", True):
            result.append(Host(pc["name"], pc["port"], None, pcdir, f, display_mode(pc)))
            continue
        shutil.rmtree(pcdir, ignore_errors=True)
        write_pc_files(pc, f, pcdir)
        p = _spawn(pc, pcdir)
        records.append({"name": pc["name"], "pid": p.pid, "exe": str(sunshine_exe())})
        (workdir / "pids.json").write_text(json.dumps(records))  # written at once: a crash leaves it
        result.append(Host(pc["name"], pc["port"], p.pid, pcdir, f, display_mode(pc)))
    _wait_serverinfo(result, workdir)
    return result


def restart(host, workdir=None, changes=None):
    """Restart one PC of this run with some pc.json values changed (e.g. its announced appversion), keeping its
    sandbox: same identity, same pairings, same logs."""
    workdir = pathlib.Path(workdir or default_workdir())
    stop(host, workdir)
    pc = load_fixture(host.fixture)
    for k, v in (changes or {}).items():
        if v is None:
            pc.pop(k, None)
        else:
            pc[k] = v
    deadline = time.time() + 15
    while True:  # the killed instance's sockets go away a moment after it
        try:
            check_ports_free(pc["port"])
            break
        except RuntimeError:
            if time.time() > deadline:
                raise
            time.sleep(0.5)
    (host.dir / "sunshine.conf").write_text(render_conf(pc, host.dir), encoding="utf-8")
    p = _spawn(pc, host.dir)
    f = workdir / "pids.json"
    records = json.loads(f.read_text()) if f.exists() else []
    records.append({"name": pc["name"], "pid": p.pid, "exe": str(sunshine_exe())})
    f.write_text(json.dumps(records))
    host.pid = p.pid
    host.display = display_mode(pc)
    _wait_serverinfo([host], workdir)
    return host


def pin(host, pin, name="testbench"):
    """Enter a pairing PIN as the web UI does, at 127.0.0.1 explicitly.  LavApollo accepts only the peer 127.0.0.1
    (it checks the peer address after name resolution; ::1, 127.0.0.2 and anything else get a 403), so never
    `localhost`, which may resolve to ::1 first."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(f"https://127.0.0.1:{host.port + 1}/api/test/pin",
                                 data=json.dumps({"pin": pin, "name": name}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ctx))
    return json.loads(opener.open(req, timeout=10).read())


def _test_request(host, path, body=None):
    """A control route of the fake PC (`/api/test/*`, LavApollo test host mode: 127.0.0.1 only, no login), like
    pin().  GET without a body, POST with one."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"https://127.0.0.1:{host.port + 1}{path}", data=data,
                                 headers={"Content-Type": "application/json"},
                                 method="GET" if body is None else "POST")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ctx))
    return json.loads(opener.open(req, timeout=10).read())


def set_display(host, mode):
    """Change the display mode the fake PC announces from now on, as when its display changes mid-session:
    (width, height, refresh), or None for the fixture's own.  host.display follows."""
    text = "" if mode is None else "{}x{}x{}".format(*mode)
    r = _test_request(host, "/api/test/display", {"mode": text})
    if not r.get("status"):
        raise RuntimeError(f"{host.name}: display change refused ({r})")
    host.display = tuple(mode) if mode is not None else display_mode(load_fixture(host.fixture))
    return r


def fail_next(host, what):
    """Make the fake PC's next `launch` / `resume` fail (503), or its next `cancel` leave the app running while
    answering success (what a host does for a game another device started)."""
    r = _test_request(host, "/api/test/fail", {"what": what})
    if not r.get("status"):
        raise RuntimeError(f"{host.name}: fail_next({what}) refused ({r})")
    return r


def terminate(host):
    """The streamed app exits on the fake PC: the host ends the stream (graceful termination).  True when an app
    was running."""
    return bool(_test_request(host, "/api/test/terminate", {}).get("status"))


def clipboard(host, text=None):
    """The fake PC's clipboard: returns it, or sets it (as if copied on the PC) when `text` is given."""
    if text is None:
        return _test_request(host, "/api/test/clipboard").get("text", "")
    _test_request(host, "/api/test/clipboard", {"text": text})
    return text


def stop(host, workdir=None):
    """Stop one instance of this run (the PC goes offline); the others keep running."""
    workdir = pathlib.Path(workdir or default_workdir())
    if host.pid is None:
        return
    _kill(host.pid)
    f = workdir / "pids.json"
    if f.exists():
        records = [r for r in json.loads(f.read_text()) if r.get("pid") != host.pid]
        f.write_text(json.dumps(records))
    for p in list(_PROCS):
        if p.pid == host.pid:
            try:
                p.wait(timeout=5)
            except Exception:
                pass
            _PROCS.remove(p)
    host.pid = None


def read_log(host, stream):
    """Events of <appdata>/testhost/<stream>.jsonl (input, commands, events) as dicts, oldest first."""
    f = pathlib.Path(host.dir) / "testhost" / f"{stream}.jsonl"
    if not f.exists():
        return []
    out = []
    for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if line:
            try:
                out.append(json.loads(line))
            except ValueError:
                pass  # a line being written
    return out


def _web_opener():
    import http.cookiejar
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ctx),
                                       urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def _web_post(opener, host, path, body):
    req = urllib.request.Request(f"https://127.0.0.1:{host.port + 1}{path}", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with opener.open(req, timeout=10) as r:
        data = r.read()
    return json.loads(data) if data.strip() else {}


WEB_USER, WEB_PASSWORD = "testbench", "testbench-password"


def _web_login(host):
    """A web UI session on the fake PC (its sandboxed credentials, set on first use), as otp() logs in."""
    opener = _web_opener()
    try:
        _web_post(opener, host, "/api/password", {"newUsername": WEB_USER, "newPassword": WEB_PASSWORD,
                                                  "confirmNewPassword": WEB_PASSWORD})
    except urllib.error.HTTPError:
        pass  # already set by an earlier call (then only our own credentials can be there)
    _web_post(opener, host, "/api/login", {"username": WEB_USER, "password": WEB_PASSWORD})
    return opener


def unpair_all(host):
    """Remove every paired client on the fake PC, as the user does in LavApollo's web UI (Appairage › Tout
    désappairer): POST /api/clients/unpair-all, logged in like otp().  The client's certificate is then unknown to the
    host: its HTTPS requests get a 401."""
    r = _web_post(_web_login(host), host, "/api/clients/unpair-all", {})
    if not r.get("status"):
        raise RuntimeError(f"{host.name}: unpair-all refused ({r})")
    return r


def otp(host, passphrase, device_name="testbench"):
    """A one-time pairing PIN for `passphrase` (Apollo OTP pairing, what an art://host?pin=&passphrase= link
    carries), asked the way the web UI does: credentials set on the fresh fake PC (they live in its sandboxed
    credentials file), login, POST /api/otp.  127.0.0.1 explicitly, like pin()."""
    opener = _web_opener()
    try:
        _web_post(opener, host, "/api/password", {"newUsername": WEB_USER, "newPassword": WEB_PASSWORD,
                                                  "confirmNewPassword": WEB_PASSWORD})
    except urllib.error.HTTPError:
        pass  # already set by an earlier call (then only our own credentials can be there)
    _web_post(opener, host, "/api/login", {"username": WEB_USER, "password": WEB_PASSWORD})
    r = _web_post(opener, host, "/api/otp", {"passphrase": passphrase, "deviceName": device_name})
    if not r.get("status") or not r.get("otp"):
        raise RuntimeError(f"{host.name}: no OTP ({r})")
    return str(r["otp"])
