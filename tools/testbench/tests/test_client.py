"""Client tests.  Unit tests use a fake transport (nothing is launched).  The integration tests start the real
test client (LavArtemisTest.exe, org "LavArtemis Test") and check that the user's foreground window never changes.
Run: python -m pytest tools/testbench/tests/test_client.py -v   (-m "not integration" for the unit tests only)"""
import json
import pathlib
import sys
import tempfile

import pytest
from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import client  # noqa: E402


class FakeTransport:
    def __init__(self, replies):
        self.replies = list(replies)  # callables(request_dict) -> bytes chunks or None
        self.sent = []
        self.pending = b""

    def write(self, data):
        req = json.loads(data.decode())
        self.sent.append(req)
        r = self.replies.pop(0)
        self.pending = r(req) if callable(r) else r

    def read_available(self, timeout):
        if self.pending is None:
            return None
        out, self.pending = self.pending, b""
        return out

    def close(self):
        pass


def line(**kw):
    return (json.dumps(kw) + "\n").encode()


def test_request_roundtrip_and_ids():
    t = FakeTransport([lambda r: line(id=r["id"], ok=True, page="HomeView"),
                       lambda r: line(id=r["id"], ok=False, error="nope")])
    ch = client.Channel(t)
    assert ch.request("state")["page"] == "HomeView"
    assert ch.request("click", text="x") == {"id": 2, "ok": False, "error": "nope"}
    assert t.sent[1] == {"id": 2, "cmd": "click", "text": "x"}


def test_reply_split_in_chunks_and_stale_reply_dropped():
    parts = [line(id=99, ok=True), b'{"id": 1, "ok": tr', b'ue, "n": 5}\n']
    t = FakeTransport([None])

    def read(timeout, it=iter(parts)):
        return next(it, None)
    t.read_available = read
    assert client.Channel(t).request("x")["n"] == 5


def test_error_without_id_is_returned():
    t = FakeTransport([line(id=None, ok=False, error="another client is already connected")])
    assert client.Channel(t).request("state")["ok"] is False


def test_timeout_and_closed():
    t = FakeTransport([None])
    with pytest.raises(client.ClientError, match="timeout"):
        client.Channel(t).request("state", timeout=0.1)
    t = FakeTransport([b""])
    t.pending = b""
    t.read_available = lambda timeout: b""
    with pytest.raises(client.ClientError, match="closed"):
        client.Channel(t).request("state")


def test_call_raises_on_refusal_and_wait_for():
    states = iter([{"ok": True, "page": "A", "pageBusy": True}, {"ok": True, "page": "B", "pageBusy": False}])

    class T(FakeTransport):
        def write(self, data):
            self.req = json.loads(data.decode())

        def read_available(self, timeout):
            return line(id=self.req["id"], **next(states))
    c = client.Client(client.Channel(T([])))
    assert c.wait_idle(timeout=5)["page"] == "B"

    t = FakeTransport([lambda r: line(id=r["id"], ok=False, error="covered")])
    with pytest.raises(client.ClientError, match="covered"):
        client.Client(client.Channel(t)).call("click", text="x")


def _fake_qt(tmp_path, monkeypatch):
    root = tmp_path / "qt"
    (root / "app").mkdir(parents=True)
    (root / "moonlight-common-c").mkdir()
    exe = root / "build" / "test-x64" / "deploy" / "LavArtemisTest.exe"
    exe.parent.mkdir(parents=True)
    exe.write_bytes(b"x")
    (root / "lavartemis.pro").write_text("x")
    (root / "app" / "a.cpp").write_text("x")
    monkeypatch.setenv("LAV_TESTBENCH_QT", str(root))
    return root, exe


def test_staleness_mtimes_and_fingerprint(tmp_path, monkeypatch):
    import os
    root, exe = _fake_qt(tmp_path, monkeypatch)
    old, new = 1_000_000_000, 1_000_000_100
    for p in (root / "lavartemis.pro", root / "app" / "a.cpp"):
        os.utime(p, (old, old))
    os.utime(exe, (new, new))
    assert client.sources_newer_than_exe() is False
    # sources outside app/ and non-C++ resources count too
    for rel in ("moonlight-common-c/x.c", "app/shader.frag", "app/logo.svg", "app/m.qml", "app/t.ts"):
        f = root / rel
        f.write_text("x")
        os.utime(f, (new + 50, new + 50))
        assert client.sources_newer_than_exe() is True, rel
        f.unlink()
    # build products and git dirs are ignored
    (root / "app" / "build").mkdir()
    f = root / "app" / "build" / "gen.cpp"
    f.write_text("x")
    os.utime(f, (new + 50, new + 50))
    assert client.sources_newer_than_exe() is False
    # a recorded fingerprint wins over mtimes (no rebuild loop when the exe mtime did not advance)
    os.utime(root / "app" / "a.cpp", (new + 99, new + 99))
    assert client.sources_newer_than_exe() is True
    client.mark_built()
    assert client.sources_newer_than_exe() is False
    (root / "app" / "a.cpp").write_text("changed content")
    assert client.sources_newer_than_exe() is True
    exe.unlink()
    assert client.sources_newer_than_exe() is True


def test_ensure_built_failure_raises_and_records_nothing(tmp_path, monkeypatch):
    root, exe = _fake_qt(tmp_path, monkeypatch)
    client.mark_built()
    (root / "app" / "a.cpp").write_text("changed")

    class R:
        returncode = 1
    calls = []
    monkeypatch.setattr(client.subprocess, "run", lambda *a, **k: calls.append(a) or R())
    with pytest.raises(client.ClientError, match="build failed"):
        client.ensure_built()
    assert len(calls) == 1  # no retry
    assert client.sources_newer_than_exe() is True


def test_foreign_instance_refused_before_build(tmp_path, monkeypatch):
    root, exe = _fake_qt(tmp_path, monkeypatch)
    monkeypatch.setattr(client, "tasklist_pids", lambda: [4242])
    monkeypatch.setattr(client, "_image_path", lambda pid: r"C:\somewhere\else\LavArtemisTest.exe")
    built = []
    monkeypatch.setattr(client, "ensure_built", lambda: built.append(1))
    killed = []
    monkeypatch.setattr(client.subprocess, "run", lambda *a, **k: killed.append(a))
    with pytest.raises(client.ClientError, match="not ours"):
        client.Client.start(tmp_path / "wd")
    assert not built and not killed


def test_own_stale_instance_is_killed_by_pid_and_exe(tmp_path, monkeypatch):
    root, exe = _fake_qt(tmp_path, monkeypatch)
    wd = tmp_path / "wd"
    wd.mkdir()
    (wd / "client.json").write_text(json.dumps({"pid": 777, "exe": str(exe)}))
    monkeypatch.setattr(client, "tasklist_pids", lambda: [777, 888])
    monkeypatch.setattr(client, "_image_path", lambda pid: str(exe) if pid == 777 else r"D:\other\LavArtemisTest.exe")
    killed = []
    monkeypatch.setattr(client.subprocess, "run", lambda args, **k: killed.append(args))
    monkeypatch.setattr(client.time, "sleep", lambda s: None)
    with pytest.raises(client.ClientError, match="888"):
        client.Client._clear_previous(wd)
    assert killed == [["taskkill", "/PID", "777", "/T", "/F"]]  # ours only, never 888


def test_wait_idle_without_page(monkeypatch):
    class T(FakeTransport):
        def write(self, data):
            self.req = json.loads(data.decode())

        def read_available(self, timeout):
            return line(id=self.req["id"], ok=True, page="", pageBusy=False)
    c = client.Client(client.Channel(T([])))
    assert c.wait_idle(timeout=1, page_required=False)["pageBusy"] is False


def test_gamepad_attach_waits_for_pad(monkeypatch):
    seq = iter([{"ok": True}, {"ok": True, "pad": False}, {"ok": True, "pad": True}])

    class T(FakeTransport):
        def write(self, data):
            self.req = json.loads(data.decode())
            self.sent.append(self.req)

        def read_available(self, timeout):
            return line(id=self.req["id"], **next(seq))
    t = T([])
    monkeypatch.setattr(client.time, "sleep", lambda s: None)
    client.Client(client.Channel(t)).gamepad_attach()
    assert t.sent[0]["cmd"] == "gamepad" and t.sent[0]["attach"] is True


@pytest.mark.skip(reason="stream-window capture (PrintWindow on the SDL window, black-capture check, "
                         "frames.read_frame_number with the negotiated stream size) runs on a real stream in "
                         "scenarios/test_05_stream.py (testbench run -k stream)")
def test_stream_window_capture_marker():
    pass


# ---------------------------------------------------------------- integration

@pytest.fixture
def running_client():
    wd = pathlib.Path(tempfile.mkdtemp(prefix="lavtestbench-c-"))
    fg = client.foreground_hwnd()
    c = client.Client.start(wd, reset=False, build=False)  # exe is comment-only stale (see report)
    yield c, wd, fg
    c.stop()


@pytest.mark.integration
def test_start_state_tree_screenshot_stop(running_client, tmp_path):
    c, wd, fg = running_client
    st = c.wait_idle()
    assert st["page"]
    texts = [i.get("text", "") for i in c.tree(textOnly=True)]
    assert any(texts), "the home page should show some text"
    shot = tmp_path / "main.png"
    r = c.screenshot(shot)
    assert r["ok"] and shot.stat().st_size > 1000
    with Image.open(shot) as im:
        assert im.size[0] > 100
    # PrintWindow path on the Qt main window (same code as for the stream window)
    main = [w for w in c.windows() if w.get("kind") == "qt"][0]
    pw = client.capture_window(int(str(main["hwnd"]), 0), tmp_path / "pw.png")
    assert pw["width"] > 100
    assert client.foreground_hwnd() == fg, "the test client must not take the foreground"
    pid = c.proc.pid
    c.stop()
    assert client.tasklist_pids() == [] or pid not in client.tasklist_pids()
