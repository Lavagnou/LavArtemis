"""Drives the LavArtemis test client (LavArtemis-Qt build `CONFIG+=lav_test`, LavArtemisTest.exe).

The client opens a local command channel, the named pipe `\\\\.\\pipe\\lavartemis-test`: one JSON object per line,
`{"id": n, "cmd": ...}` -> `{"id": n, "ok": bool, ...}`, one connection at a time (protocol: LavArtemis-Qt CLAUDE.md,
section "Build de test").  Everything here is non-intrusive: no window activation, no mouse or keyboard automation.
The test client has its own Qt organisation ("LavArtemis Test"); this module never touches LavArtemis settings.
"""
import ctypes
import ctypes.wintypes
import json
import os
import pathlib
import subprocess
import time

import frames

HERE = pathlib.Path(__file__).resolve().parent
PIPE_PATH = "\\\\.\\pipe\\lavartemis-test"
EXE_NAME = "LavArtemisTest.exe"
QT_SOURCE_SUFFIXES = (".cpp", ".h", ".qml", ".qrc", ".pro", ".pri", ".ui", ".js", ".c", ".hpp")

_PROCS = []  # Popen objects of clients started by this process

try:  # physical pixels for every window rect and capture of this process, set once
    ctypes.windll.user32.SetProcessDPIAware()
except (OSError, AttributeError):
    pass


class ClientError(RuntimeError):
    pass


def qt_root():
    return pathlib.Path(os.environ.get("LAV_TESTBENCH_QT", HERE.parents[2] / "LavArtemis-Qt")).resolve()


def exe_path():
    return qt_root() / "build" / "test-x64" / "deploy" / EXE_NAME


def test_jobs():
    return int(os.environ.get("LAV_TEST_JOBS", "2"))


# ------------------------------------------------------------------ line protocol over a transport

class PipeTransport:
    """The real pipe.  One thread: write, then poll PeekNamedPipe so a dead client cannot hang us."""

    def __init__(self, path=PIPE_PATH):
        self.f = open(path, "r+b", buffering=0)
        import msvcrt
        self.handle = msvcrt.get_osfhandle(self.f.fileno())

    def write(self, data):
        self.f.write(data)

    def read_available(self, timeout):
        k32 = ctypes.windll.kernel32
        k32.PeekNamedPipe.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_ulong, ctypes.c_void_p,
                                      ctypes.POINTER(ctypes.c_ulong), ctypes.c_void_p]
        deadline = time.time() + timeout
        while True:
            avail = ctypes.c_ulong(0)
            ok = k32.PeekNamedPipe(self.handle, None, 0, None, ctypes.byref(avail), None)
            if not ok:
                return b""  # broken pipe: the client went away
            if avail.value:
                return os.read(self.f.fileno(), min(avail.value, 1 << 20))
            if time.time() > deadline:
                return None  # timeout
            time.sleep(0.01)

    def close(self):
        try:
            self.f.close()
        except OSError:
            pass


class Channel:
    """JSON-lines request/response over any transport with write(bytes) and read_available(timeout)."""

    def __init__(self, transport):
        self.t = transport
        self.buf = b""
        self.next_id = 1

    def request(self, cmd, timeout=30.0, **args):
        rid = self.next_id
        self.next_id += 1
        line = json.dumps(dict(args, id=rid, cmd=cmd), separators=(",", ":")) + "\n"
        try:
            self.t.write(line.encode("utf-8"))
        except OSError as e:  # the pipe broke: the client is gone or going
            raise ClientError(f"the client closed the channel before {cmd!r}: {e}")
        deadline = time.time() + timeout
        while True:
            while b"\n" in self.buf:
                raw, self.buf = self.buf.split(b"\n", 1)
                if not raw.strip():
                    continue
                reply = json.loads(raw.decode("utf-8"))
                if reply.get("id") in (rid, None):
                    return reply
                # a reply to an older, abandoned request: drop it
            chunk = self.t.read_available(max(0.0, deadline - time.time()))
            if chunk is None:
                raise ClientError(f"timeout waiting for the reply to {cmd!r}")
            if chunk == b"":
                raise ClientError(f"the client closed the channel during {cmd!r}")
            self.buf += chunk


# ------------------------------------------------------------------ processes

def tasklist_pids():
    out = subprocess.run(["tasklist", "/FI", f"IMAGENAME eq {EXE_NAME}", "/FO", "CSV", "/NH"],
                         capture_output=True, text=True).stdout
    pids = []
    for line in out.splitlines():
        parts = [p.strip('"') for p in line.split('","')]
        if len(parts) > 1 and parts[0].strip('"').lower() == EXE_NAME.lower():
            pids.append(int(parts[1]))
    return pids


def _image_path(pid):
    import hosts
    return hosts._image_path(pid)


def _is_our_exe(pid):
    p = _image_path(pid)
    return p is not None and os.path.normcase(os.path.abspath(p)) == os.path.normcase(str(exe_path()))


SOURCE_DIRS = ("app", "moonlight-common-c", "qmdnsengine", "h264bitstream", "AntiHooking", "pyrowave")
SOURCE_FILES = ("lavartemis.pro", "globaldefs.pri")
SOURCE_SUFFIXES = QT_SOURCE_SUFFIXES + (".hlsl", ".glsl", ".vert", ".frag", ".svg", ".png", ".ts", ".cu", ".cc",
                                        ".cxx", ".inc", ".mk", ".json", ".ttf", ".otf")
SKIP_DIRS = {".git", "build", "debug", "release", "__pycache__"}


def source_fingerprint():
    """Hash of (path, size, mtime) of every source the test client is built from."""
    import hashlib
    h = hashlib.sha256()
    root = qt_root()
    for name in SOURCE_FILES:
        f = root / name
        if f.exists():
            st = f.stat()
            h.update(f"{name}|{st.st_size}|{st.st_mtime_ns}\n".encode())
    for d in SOURCE_DIRS:
        for dirpath, dirs, files in os.walk(root / d):
            dirs[:] = sorted(x for x in dirs if x.lower() not in SKIP_DIRS)
            for f in sorted(files):
                if f.lower().endswith(SOURCE_SUFFIXES):
                    p = os.path.join(dirpath, f)
                    st = os.stat(p)
                    h.update(f"{os.path.relpath(p, root)}|{st.st_size}|{st.st_mtime_ns}\n".encode())
    return h.hexdigest()


def _fingerprint_file():
    return exe_path().parent.parent / ".testbench-built-from"


def mark_built():
    """Record the current sources as the ones the existing exe was built from (use after checking that a newer
    source mtime is a comment-only edit).  CLI: testbench mark-client-built."""
    _fingerprint_file().write_text(source_fingerprint())


def sources_newer_than_exe():
    """True when the exe must be rebuilt.  With a recorded fingerprint of the last build, only a real change of
    the sources counts (an mtime that did not advance can never loop a rebuild); without one, source mtimes are
    compared with the exe's."""
    exe = exe_path()
    if not exe.exists():
        return True
    rec = _fingerprint_file()
    if rec.exists():
        return rec.read_text().strip() != source_fingerprint()
    mt = exe.stat().st_mtime
    root = qt_root()
    for name in SOURCE_FILES:
        if (root / name).exists() and (root / name).stat().st_mtime > mt:
            return True
    for d in SOURCE_DIRS:
        for dirpath, dirs, files in os.walk(root / d):
            dirs[:] = [x for x in dirs if x.lower() not in SKIP_DIRS]
            for f in files:
                if f.lower().endswith(SOURCE_SUFFIXES) and os.stat(os.path.join(dirpath, f)).st_mtime > mt:
                    return True
    return False


def ensure_built():
    if not sources_newer_than_exe():
        return
    env = dict(os.environ, LAV_TEST_JOBS=str(test_jobs()))
    fp = source_fingerprint()  # the sources as they were when the build began
    r = subprocess.run(["cmd", "/c", str(qt_root() / "scripts" / "build-test.bat")], cwd=str(qt_root()), env=env)
    if r.returncode or not exe_path().exists():
        raise ClientError("test client build failed (killed for low memory? Free memory and retry with "
                          "LAV_TEST_JOBS=1; not retrying automatically)")
    _fingerprint_file().write_text(fp)


def foreground_hwnd():
    return ctypes.windll.user32.GetForegroundWindow()


def capture_window(hwnd, path):
    """PrintWindow (PW_CLIENTONLY | PW_RENDERFULLCONTENT) of a window to a PNG.  Never activates or raises it."""
    from PIL import Image
    user32, gdi32 = ctypes.windll.user32, ctypes.windll.gdi32
    hwnd = ctypes.c_void_p(int(hwnd))
    rect = ctypes.wintypes.RECT()
    user32.GetClientRect(hwnd, ctypes.byref(rect))
    w, h = rect.right - rect.left, rect.bottom - rect.top
    if w <= 0 or h <= 0:
        raise ClientError("window has an empty client area")
    for fn in ("GetWindowDC", "GetDC"):
        getattr(user32, fn).restype = ctypes.c_void_p
    gdi32.CreateCompatibleDC.restype = ctypes.c_void_p
    gdi32.CreateCompatibleBitmap.restype = ctypes.c_void_p
    gdi32.SelectObject.restype = ctypes.c_void_p
    gdi32.CreateCompatibleDC.argtypes = [ctypes.c_void_p]
    gdi32.CreateCompatibleBitmap.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    gdi32.SelectObject.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    gdi32.DeleteObject.argtypes = [ctypes.c_void_p]
    gdi32.DeleteDC.argtypes = [ctypes.c_void_p]
    user32.GetDC.argtypes = [ctypes.c_void_p]
    user32.ReleaseDC.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    user32.PrintWindow.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint]
    screen = user32.GetDC(None)
    mem = gdi32.CreateCompatibleDC(screen)
    bmp = gdi32.CreateCompatibleBitmap(screen, w, h)
    old = gdi32.SelectObject(mem, bmp)
    selected = True
    try:
        if not user32.PrintWindow(hwnd, mem, 1 | 2):
            raise ClientError("PrintWindow failed")

        class BITMAPINFOHEADER(ctypes.Structure):
            _fields_ = [("biSize", ctypes.c_uint32), ("biWidth", ctypes.c_int32), ("biHeight", ctypes.c_int32),
                        ("biPlanes", ctypes.c_uint16), ("biBitCount", ctypes.c_uint16),
                        ("biCompression", ctypes.c_uint32), ("biSizeImage", ctypes.c_uint32),
                        ("biXPelsPerMeter", ctypes.c_int32), ("biYPelsPerMeter", ctypes.c_int32),
                        ("biClrUsed", ctypes.c_uint32), ("biClrImportant", ctypes.c_uint32)]

        # GetDIBits wants the bitmap deselected from any DC (documented contract)
        gdi32.SelectObject(mem, old)
        selected = False
        bi = BITMAPINFOHEADER(ctypes.sizeof(BITMAPINFOHEADER), w, -h, 1, 32, 0, 0, 0, 0, 0, 0)
        buf = ctypes.create_string_buffer(w * h * 4)
        gdi32.GetDIBits.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint, ctypes.c_uint,
                                    ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint]
        if not gdi32.GetDIBits(mem, bmp, 0, h, buf, ctypes.byref(bi), 0):
            raise ClientError("GetDIBits failed")
        img = Image.frombuffer("RGBA", (w, h), buf, "raw", "BGRA", 0, 1).convert("RGB")
    finally:
        if selected:
            gdi32.SelectObject(mem, old)
        gdi32.DeleteObject(bmp)
        gdi32.DeleteDC(mem)
        user32.ReleaseDC(None, screen)
    if img.getextrema() == ((0, 0), (0, 0), (0, 0)):
        raise ClientError("stream window captured black (PrintWindow returned nothing drawn)")
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")
    return {"path": str(path), "width": w, "height": h}


# ------------------------------------------------------------------ the client

class Client:
    def __init__(self, channel=None, proc=None, workdir=None):
        self.channel = channel
        self.proc = proc
        self.workdir = pathlib.Path(workdir) if workdir else None
        self.log_path = None

    # -- lifecycle
    @staticmethod
    def next_log_path(workdir):
        n = 1
        while (pathlib.Path(workdir) / f"client-{n}.log").exists():
            n += 1
        return pathlib.Path(workdir) / f"client-{n}.log"

    def log_text(self):
        """The client's log so far (its stderr)."""
        p = self.log_path
        return p.read_text(encoding="utf-8", errors="replace") if p and p.exists() else ""

    @classmethod
    def start(cls, workdir, reset=False, build=True, timeout=40.0, args=None):
        """Launch the client.  args: extra command-line arguments (an art:// link, `pair host --pin ...`), passed
        exactly as a shell or the OS link handler would."""
        workdir = pathlib.Path(workdir)
        workdir.mkdir(parents=True, exist_ok=True)
        cls._clear_previous(workdir)  # first: a foreign instance must refuse before any (long) build
        if build:
            ensure_built()
        elif not exe_path().exists():
            raise ClientError(f"{exe_path()} does not exist")
        # The client logs to stderr when it is given one (main.cpp, LOG_TO_FILE only for an unspecified handle):
        # its log lands in the workdir, never in %TEMP%\LavArtemis-*.log next to the real app's logs.
        log = cls.next_log_path(workdir)
        with open(log, "wb") as logf:
            argv = [str(exe_path())] + ([] if reset else [str(a) for a in (args or [])])  # args after the reset
            proc = subprocess.Popen(argv, cwd=str(exe_path().parent),
                                    stdin=subprocess.DEVNULL, stdout=logf, stderr=logf,
                                    creationflags=0x08000000 | 0x00000200)  # CREATE_NO_WINDOW | NEW_PROCESS_GROUP
        _PROCS.append(proc)
        (workdir / "client.json").write_text(json.dumps({"pid": proc.pid, "exe": str(exe_path()), "log": str(log)}))
        c = cls(None, proc, workdir)
        c.log_path = log
        c._connect(timeout)
        if reset:
            # `reset` wipes the "LavArtemis Test" org only (the client refuses any other) and quits: relaunch.
            try:
                c.cmd("reset", timeout=30)
            except ClientError:
                pass
            c._wait_exit(30)
            return cls.start(workdir, reset=False, build=False, timeout=timeout, args=args)
        return c

    @staticmethod
    def _clear_previous(workdir):
        f = workdir / "client.json"
        ours = None
        if f.exists():
            try:
                ours = json.loads(f.read_text()).get("pid")
            except ValueError:
                ours = None
            f.unlink()
        for pid in tasklist_pids():
            if pid == ours and _is_our_exe(pid):
                subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True)
                time.sleep(0.5)
            else:
                raise ClientError(f"another {EXE_NAME} (pid {pid}) is running and is not ours: not starting")

    def _connect(self, timeout):
        deadline = time.time() + timeout
        last = None
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise ClientError(f"the test client exited at startup (code {self.proc.returncode})")
            try:
                self.channel = Channel(PipeTransport())
                return
            except OSError as e:  # pipe not there yet (2), or busy (231)
                last = e
                time.sleep(0.2)
        raise ClientError(f"no command channel after {timeout:.0f}s: {last}")

    def _wait_exit(self, timeout):
        try:
            self.proc.wait(timeout=timeout)
            return True
        except subprocess.TimeoutExpired:
            return False

    def stop(self):
        if self.proc is None:
            return
        try:
            if self.channel and self.proc.poll() is None:
                self.channel.request("quit", timeout=10)
        except (ClientError, OSError, ValueError):
            pass
        if self.channel:
            self.channel.t.close()
            self.channel = None
        if not self._wait_exit(40) and _is_our_exe(self.proc.pid):
            subprocess.run(["taskkill", "/PID", str(self.proc.pid), "/T", "/F"], capture_output=True)
            self._wait_exit(10)
        if self.proc in _PROCS:
            _PROCS.remove(self.proc)
        if self.workdir and (self.workdir / "client.json").exists():
            (self.workdir / "client.json").unlink()
        self.proc = None

    # -- commands
    def cmd(self, name, timeout=30.0, **args):
        """Send one command, return the reply dict (`ok` false on a refusal; transport problems raise)."""
        return self.channel.request(name, timeout=timeout, **args)

    def call(self, name, **args):
        r = self.cmd(name, **args)
        if not r.get("ok"):
            raise ClientError(f"{name}: {r.get('error')}")
        return r

    def state(self):
        return self.call("state")

    def wait_for(self, predicate, timeout=20.0, interval=0.2):
        """Poll `state` until predicate(state) is truthy; returns that state, raises on timeout."""
        deadline = time.time() + timeout
        st = None
        while time.time() < deadline:
            st = self.state()
            if predicate(st):
                return st
            time.sleep(interval)
        raise ClientError(f"timeout waiting for a state condition; last state: {json.dumps(st)[:400]}")

    def wait_idle(self, timeout=20.0, page_required=True):
        """Wait for a finished page transition (state.pageBusy false); page_required=False for a stream, where
        there may be no page."""
        return self.wait_for(lambda s: (s.get("page") or not page_required) and not s.get("pageBusy"), timeout)

    def gamepad_attach(self, timeout=5.0):
        """Attach the virtual pad (stream only) and wait until the client has opened it: a button pressed
        earlier would be lost."""
        self.call("gamepad", attach=True)
        self.wait_for(lambda s: s.get("pad"), timeout)
        time.sleep(0.5)

    def tree(self, **args):
        self.wait_idle()
        return self.call("tree", **args)["items"]

    def click(self, text=None, **args):
        self.wait_idle()
        if text is not None:
            args["text"] = text
        return self.call("click", **args)

    def windows(self):
        return self.call("windows")["windows"]

    def stream_size(self):
        """(width, height, source) of the stream the host encodes.  source "negotiated": state.session's
        negotiated size (after HostDisplayFit and every other adjustment, test build only); "settings": the
        requested width/height, used only while no stream runs (a frame read with it may be wrong)."""
        sess = self.state().get("session") or {}
        if sess.get("width") and sess.get("height"):
            return (int(sess["width"]), int(sess["height"]), "negotiated")
        try:
            return (int(self.call("setting", action="get", key="width")["value"]),
                    int(self.call("setting", action="get", key="height")["value"]), "settings")
        except (ClientError, KeyError, ValueError):
            raise ClientError("unknown stream size")

    def screenshot(self, path, window=0, stream_size=None):
        """Qt window (index) by the channel.  window="stream": PrintWindow on the stream window; the reply also
        carries `stream_size` and `content_rect` (aspect fit of the stream in the captured window, bars cut
        off) so frames.read_frame_number(path, stream_size, content_rect) needs nothing by hand."""
        path = str(pathlib.Path(path).resolve())
        if window == "stream":
            for w in self.windows():
                if w.get("kind") == "stream":
                    info = capture_window(int(str(w["hwnd"]), 0), path)
                    if stream_size:
                        size, source = tuple(stream_size), "given"
                    else:
                        *size, source = self.stream_size()
                        size = tuple(size)
                    info["stream_size"] = size
                    info["stream_size_source"] = source
                    info["content_rect"] = frames.fit_rect(info["width"], info["height"], *size)
                    return info
            raise ClientError("no stream window")
        self.wait_idle()
        return self.call("screenshot", path=path, window=window)

    def frame_number(self, path, stream_size=None, retries=3):
        """Strict frame number of the stream window as it is now (capture, then read).  A capture taken while
        the window repaints can be unreadable: up to `retries` more captures on FrameReadError; the last error
        names the stream size used and where it came from."""
        last = None
        for attempt in range(retries + 1):
            info = self.screenshot(path, "stream", stream_size)
            try:
                return frames.read_frame_number(info["path"], info["stream_size"], info["content_rect"])
            except frames.FrameReadError as e:
                last = frames.FrameReadError(f"{e} (stream size {info['stream_size'][0]}x{info['stream_size'][1]} "
                                             f"from {info['stream_size_source']}, window {info['width']}x"
                                             f"{info['height']}, attempt {attempt + 1}/{retries + 1})")
                time.sleep(0.1)
        raise last
