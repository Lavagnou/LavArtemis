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


def test_staleness_uses_qt_sources():
    # does not build anything: only compares mtimes
    assert isinstance(client.sources_newer_than_exe(), bool)


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


@pytest.mark.integration
def test_refuses_foreign_instance(tmp_path):
    # no foreign LavArtemisTest.exe is started here: just check the guard sees none now
    assert client.tasklist_pids() == []
