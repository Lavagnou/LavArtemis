"""Tests of the sandboxed test hosts.  Run:  python -m pytest tools/testbench/tests/test_hosts.py -v
(from the repo root; needs `pip install -r tools/testbench/requirements.txt`).

Unit tests launch nothing.  The `integration` tests start the real test build of sunshine
(LavApollo build/t/sunshine.exe, test_host = enabled) on loopback ports 40000-44999 only, and check that
the user's real LavApollo service (port 45989) and its config are untouched."""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import hosts  # noqa: E402

REAL_SERVICE = "http://127.0.0.1:45989/serverinfo"
REAL_DIRS = [pathlib.Path(r"C:\Program Files\LavApollo\config"), pathlib.Path(r"C:\ProgramData\LavApollo")]
# Unreadable or self-changing files: the live history db (+ wal/shm), the rtss overrides the real service
# rewrites during a stream, and its locked log. They cannot witness anything.
EXCLUDED = ("lavapollo_history.db", "rtss_overrides.json", "sunshine.log")


def fingerprint():
    out = {}
    for root in REAL_DIRS:
        if not root.is_dir():
            continue
        for dirpath, _dirs, files in os.walk(root):
            for f in files:
                if f.startswith(EXCLUDED):
                    continue
                p = pathlib.Path(dirpath) / f
                try:
                    st = p.stat()
                except OSError:
                    continue
                out[str(p)] = (st.st_size, st.st_mtime_ns)
    return hashlib.sha256(json.dumps(sorted(out.items())).encode()).hexdigest()


def real_service_reachable():
    try:
        urllib.request.urlopen(REAL_SERVICE, timeout=3).read(1)
        return True
    except urllib.error.HTTPError:
        return True
    except OSError:
        return False


# ---------------------------------------------------------------- unit tests (nothing is launched)

def test_fixture_names():
    assert sorted(hosts.available_fixtures()) == [
        "atlas-test", "cynisca-test", "offline-test", "old-version-test", "this-pc-test"]


def test_ports_in_client_range_and_not_overlapping():
    spans = []
    for name in hosts.available_fixtures():
        pc = hosts.load_fixture(name)
        base = pc["port"]
        assert 40000 <= base - 5 and base + 21 <= 44999, name
        spans.append((base - 5, base + 21, name))
    spans.sort()
    for a, b in zip(spans, spans[1:]):
        assert a[1] < b[0], f"{a[2]} overlaps {b[2]}"


def test_conf_generation(tmp_path):
    pc = hosts.load_fixture("atlas-test")
    conf = hosts.render_conf(pc, tmp_path)
    lines = dict(l.split(" = ", 1) for l in conf.splitlines() if " = " in l)
    assert lines["test_host"] == "enabled"
    assert lines["port"] == "40989"
    assert lines["sunshine_name"] == "Test-Atlas"
    assert lines["test_host_mac"] == pc["mac"]
    assert "test_host_this_pc" not in lines and "test_host_appversion" not in lines
    for key in ("file_state", "file_apps", "credentials_file", "log_path"):
        assert pathlib.Path(lines[key]).is_absolute() and "\\" not in lines[key]
        assert lines[key].startswith(str(tmp_path).replace("\\", "/"))


def test_this_pc_and_old_version_tags(tmp_path):
    conf = hosts.render_conf(hosts.load_fixture("this-pc-test"), tmp_path)
    assert "test_host_this_pc = enabled" in conf
    conf = hosts.render_conf(hosts.load_fixture("old-version-test"), tmp_path)
    assert "test_host_appversion = 2.1.0.0" in conf


def test_offline_has_no_instance():
    assert hosts.load_fixture("offline-test").get("instance", True) is False


def test_playnite_files_and_covers(tmp_path):
    hosts.write_pc_files(hosts.load_fixture("atlas-test"), "atlas-test", tmp_path)
    pd = tmp_path / "pd" / "LavApollo" / "playnite"
    lib = json.loads((pd / "library.json").read_text(encoding="utf-8"))
    assert [g["name"] for g in lib["games"]][:1] == ["Nebula Drift"]
    for g in lib["games"]:
        assert (pd / "covers" / g["cover"]).is_file()
    assert json.loads((pd / "state.json").read_text())["playniteRunning"] is True
    assert (tmp_path / "apps.json").is_file()


def test_only_test_exe_is_killable(tmp_path):
    # A pid recorded for some other executable (this python) must never be killed.
    me = os.getpid()
    (tmp_path / "pids.json").write_text(json.dumps([{"name": "x", "pid": me, "exe": "C:/nowhere/sunshine.exe"}]))
    hosts.kill_previous(tmp_path)  # would kill us if it trusted the file
    assert os.getpid() == me


def test_default_jobs(monkeypatch):
    monkeypatch.delenv("LAV_TESTBENCH_JOBS", raising=False)
    assert hosts.build_jobs() == 1
    monkeypatch.setenv("LAV_TESTBENCH_JOBS", "2")
    assert hosts.build_jobs() == 2


# ---------------------------------------------------------------- integration (launches test sunshine)

@pytest.fixture
def workdir():
    d = pathlib.Path(tempfile.mkdtemp(prefix="lavtestbench-t-"))
    yield d
    hosts.down(d)
    shutil.rmtree(d, ignore_errors=True)


def serverinfo(port):
    return urllib.request.urlopen(f"http://127.0.0.1:{port}/serverinfo", timeout=5).read().decode()


@pytest.mark.integration
def test_up_atlas_is_isolated(workdir):
    assert real_service_reachable(), "the real service should answer on 45989 on this PC"
    before = fingerprint()
    started = hosts.up(["atlas-test"], workdir)
    assert [h.name for h in started] == ["Test-Atlas"]
    info = serverinfo(40989)
    assert "LavTestHost" in info and "Test-Atlas" in info
    hosts.down(workdir)
    assert fingerprint() == before
    assert real_service_reachable()
    # nothing of ours outside the workdir: pids gone, ports closed
    with pytest.raises(OSError):
        serverinfo(40989)


@pytest.mark.integration
def test_up_after_hard_kill(workdir):
    h = hosts.up(["atlas-test"], workdir)[0]
    subprocess.run(["taskkill", "/PID", str(h.pid), "/T", "/F"], capture_output=True)
    assert (workdir / "pids.json").exists()  # stale PID file left behind
    again = hosts.up(["atlas-test"], workdir)
    assert "LavTestHost" in serverinfo(again[0].port)


@pytest.mark.integration
def test_up_kills_surviving_instance_of_previous_run(workdir):
    first = hosts.up(["atlas-test"], workdir)[0]
    second = hosts.up(["atlas-test"], workdir)[0]  # previous run's instance still alive
    assert second.pid != first.pid
    assert not hosts.pid_alive(first.pid)


@pytest.mark.integration
def test_offline_has_no_listener_and_pin(workdir):
    started = hosts.up(["offline-test", "atlas-test"], workdir)
    assert [h.name for h in started] == ["Test-Eteint", "Test-Atlas"] or len(started) == 2
    with pytest.raises(OSError):
        serverinfo(44500)
    r = hosts.pin(next(h for h in started if h.name == "Test-Atlas"), "1234", "x")
    assert r.get("status") is False  # no pairing pending
