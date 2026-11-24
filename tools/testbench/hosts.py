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
MSYS_PATH = "C:\\msys64\\ucrt64\\bin;C:\\msys64\\usr\\bin;"


@dataclasses.dataclass
class Host:
    name: str
    port: int
    pid: "int | None"
    dir: pathlib.Path
    fixture: str = ""


def default_workdir():
    return pathlib.Path(tempfile.gettempdir()) / "lavtestbench"


def build_jobs():
    return int(os.environ.get("LAV_TESTBENCH_JOBS", "1"))


# ------------------------------------------------------------------ fixtures / files

def available_fixtures():
    return sorted(p.name for p in FIXTURES.iterdir() if (p / "pc.json").is_file())


def load_fixture(name):
    return json.loads((FIXTURES / name / "pc.json").read_text(encoding="utf-8"))


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
    (pn / "library.json").write_text(json.dumps(lib, ensure_ascii=False, indent=2), encoding="utf-8")
    running = bool(pc.get("playnite_running"))
    state = {"version": 1, "playniteRunning": running, "mode": "fullscreen",
             "pid": 4242 if running else 0, "game": None, "updatedAt": "2026-11-24T09:00:00Z"}
    (pn / "state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")


# ------------------------------------------------------------------ processes

_k32 = ctypes.windll.kernel32 if os.name == "nt" else None
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
    return _image_path(pid) is not None


def _is_test_sunshine(pid):
    return _same_exe(_image_path(pid), sunshine_exe())


def _kill(pid):
    if _is_test_sunshine(pid):  # never anything but the test sunshine
        subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
        for _ in range(50):
            if not pid_alive(pid):
                break
            time.sleep(0.1)


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


def up(fixtures, workdir=None):
    workdir = pathlib.Path(workdir or default_workdir())
    workdir.mkdir(parents=True, exist_ok=True)
    kill_previous(workdir)
    ensure_built()
    fixtures = list(fixtures) or available_fixtures()
    pcs = {f: load_fixture(f) for f in fixtures}
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
            result.append(Host(pc["name"], pc["port"], None, pcdir, f))
            continue
        shutil.rmtree(pcdir, ignore_errors=True)
        write_pc_files(pc, f, pcdir)
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
        records.append({"name": pc["name"], "pid": p.pid, "exe": str(sunshine_exe())})
        (workdir / "pids.json").write_text(json.dumps(records))  # written at once: a crash leaves it
        result.append(Host(pc["name"], pc["port"], p.pid, pcdir, f))
    deadline = time.time() + STARTUP_TIMEOUT
    for h in result:
        if h.pid is None:
            continue
        while not _serverinfo_ok(h.port):
            if not pid_alive(h.pid) or time.time() > deadline:
                down(workdir)
                raise RuntimeError(f"{h.name}: no serverinfo on {h.port} (see {h.dir / 'sunshine.log'})")
            time.sleep(0.3)
    return result


def pin(host, pin, name="testbench"):
    """Enter a pairing PIN as the web UI does.  127.0.0.1 explicitly (::1 and localhost are refused)."""
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(f"https://127.0.0.1:{host.port + 1}/api/test/pin",
                                 data=json.dumps({"pin": pin, "name": name}).encode(),
                                 headers={"Content-Type": "application/json"}, method="POST")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ctx))
    return json.loads(opener.open(req, timeout=10).read())
