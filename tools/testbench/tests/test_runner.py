"""Unit tests of the scenario runner's pieces that need no GUI: the report (attempts, flakes, flags), the rerun of
failed scenarios, the foreground watch (hook installed and removed, switches classified), the client's log names,
its refusal of a guessed stream size, the leftover-host sweep.  Nothing here starts a client or a host."""
import datetime
import json
import pathlib
import sys
import types

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import client  # noqa: E402
import conftest as tbconf  # noqa: E402
import hosts  # noqa: E402
import report  # noqa: E402
import testbench  # noqa: E402


def res(name, status, attempt=1, flags=(), error=""):
    return {"id": f"scenarios/test_x.py::{name}", "name": name, "attempt": attempt, "status": status,
            "phase": "call", "duration": 1.0, "reason": "", "error": error, "flags": list(flags), "captures": ""}


# ---------------------------------------------------------------- report

def test_outcome_flake_and_failed_ids():
    results = [res("a", "failed", 1, error="E boom"), res("a", "passed", 2),
               res("b", "failed", 1, error="E x"), res("b", "failed", 2, error="E x"),
               res("c", "passed"), res("d", "error", 1, error="E teardown")]
    out = report.outcome(results)
    assert out["scenarios/test_x.py::a"] == ("passed", True)
    assert out["scenarios/test_x.py::b"] == ("failed", False)
    assert out["scenarios/test_x.py::c"] == ("passed", False)
    assert report.failed_ids(results) == ["scenarios/test_x.py::b", "scenarios/test_x.py::d"]


def test_report_md_shows_both_attempts_flake_and_flags(tmp_path, monkeypatch):
    monkeypatch.setattr(report, "repos", lambda: {})
    results = [res("a", "failed", 1, error="AssertionError: boom"), res("a", "passed", 2),
               res("c", "passed", 1, flags=["RETRY x1: RETRY click 'Mes PC'", "FOREGROUND 01:02:03: no window"])]
    md = report.write(tmp_path, results, "2026-11-25T03:00:00", datetime.datetime.now()).read_text(encoding="utf-8")
    assert "**FLAKE**" in md and "1 flaky (passed on rerun)" in md
    assert "| a | 1 | FAIL |" in md and "| a | 2 | PASS (rerun) |" in md
    assert "RETRY x1" in md and "FOREGROUND 01:02:03: no window" in md and "**FLAGS**" in md
    assert json.loads((tmp_path / "report.json").read_text(encoding="utf-8"))["results"][1]["attempt"] == 2
    loaded, started = report.load(tmp_path)
    assert len(loaded) == 3 and started == "2026-11-25T03:00:00"


# ---------------------------------------------------------------- rerun

def test_rerun_failed_once_reruns_only_failed_and_returns_rerun_code(tmp_path, monkeypatch):
    calls = []

    def fake_pytest(args, attempt):
        calls.append((list(args), attempt))
        if attempt == 1:
            (tmp_path / "report.json").write_text(json.dumps({"started": "s", "results": [
                res("a", "failed"), res("b", "passed"), res("c", "xfail")]}), encoding="utf-8")
            return 1
        return 0
    monkeypatch.setattr(testbench, "_pytest", fake_pytest)
    assert testbench.run_suite(report_dir=tmp_path, rerun_failed_once=True) == 0
    assert [a for _args, a in calls] == [1, 2]
    assert calls[1][0][0] == "scenarios/test_x.py::a" and len([x for x in calls[1][0] if "::" in x]) == 1
    assert "--report-dir" in calls[1][0] and str(tmp_path) in calls[1][0]


def test_no_rerun_without_the_option_or_on_success(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(testbench, "_pytest", lambda args, attempt: calls.append(attempt) or 1)
    assert testbench.run_suite(report_dir=tmp_path) == 1 and calls == [1]
    calls.clear()
    monkeypatch.setattr(testbench, "_pytest", lambda args, attempt: calls.append(attempt) or 0)
    assert testbench.run_suite(report_dir=tmp_path, rerun_failed_once=True) == 0 and calls == [1]


# ---------------------------------------------------------------- foreground watch

def test_foreground_hook_installs_and_stops():
    w = tbconf.ForegroundWatch()
    assert w.hooked and w.thread_id
    before, _after = w.finish()
    assert not w._t.is_alive()
    assert before == w.before


def test_foreground_classification(monkeypatch):
    monkeypatch.setattr(client, "exe_path", lambda: pathlib.Path(r"C:\x\LavArtemisTest.exe"))
    monkeypatch.setattr(hosts, "sunshine_exe", lambda: pathlib.Path(r"C:\l\build\t\sunshine.exe"))
    assert tbconf.classify(0, "-") == "none"
    assert tbconf.classify(5, r"c:\x\lavartemistest.exe") == "ours"
    assert tbconf.classify(5, r"C:\l\build\t\sunshine.exe") == "ours"
    assert tbconf.classify(5, r"C:\Windows\System32\WerFault.exe") == "crash"
    assert tbconf.classify(5, r"C:\Program Files\LavApollo\sunshine.exe") == "other"
    assert tbconf.classify(5, "?") == "other"


def test_foreground_flags_from_events():
    w = types.SimpleNamespace(events=[("01:00:00", 0, 0, "-", "", "none"),
                                      ("01:00:01", 7, 9, r"C:\Windows\System32\WerFault.exe", "LavArtemisTest", "crash"),
                                      ("01:00:02", 8, 10, r"C:\a\claude.exe", "Claude", "other")])
    flags = tbconf.ForegroundWatch.flags(w)
    assert len(flags) == 2 and "hwnd 0" in flags[0] and "WerFault.exe" in flags[1]


# ---------------------------------------------------------------- client

def test_client_log_names_per_launch(tmp_path):
    first = client.Client.next_log_path(tmp_path, "reset")
    assert first.name == "client-1-reset.log"
    first.write_text("x")
    second = client.Client.next_log_path(tmp_path)
    assert second.name == "client-2.log"
    second.write_text("x")
    assert client.Client.next_log_path(tmp_path, "args").name == "client-3-args.log"


class _StateOnly:
    def __init__(self, session, settings=None):
        self.session, self.settings = session, settings or {}

    def state(self):
        return {"session": self.session}

    def call(self, name, **args):
        return {"value": self.settings[args["key"]]}


def test_stream_size_never_guessed_during_a_stream():
    c = _StateOnly({"streaming": True, "width": 0, "height": 0}, {"width": 1280, "height": 720})
    with pytest.raises(client.ClientError, match="negotiated"):
        client.Client.stream_size(c)
    assert client.Client.stream_size(_StateOnly({"streaming": True, "width": 1280, "height": 720})) == \
        (1280, 720, "negotiated")
    assert client.Client.stream_size(_StateOnly({"streaming": False}, {"width": 1920, "height": 1080})) == \
        (1920, 1080, "settings")


def test_frame_number_refuses_a_settings_size():
    c = types.SimpleNamespace(screenshot=lambda path, window, size: {"stream_size_source": "settings"})
    with pytest.raises(client.ClientError, match="negotiated"):
        client.Client.frame_number(c, "x.png")


# ---------------------------------------------------------------- hosts

def test_leftover_instances_match_exe_and_workdir(monkeypatch, tmp_path):
    exe = r"C:\l\build\t\sunshine.exe"
    monkeypatch.setattr(hosts, "sunshine_exe", lambda: pathlib.Path(exe))
    rows = [{"ProcessId": 1, "ExecutablePath": exe, "CommandLine": f'"{exe}" "{tmp_path}\\atlas-test\\sunshine.conf"'},
            {"ProcessId": 2, "ExecutablePath": exe, "CommandLine": f'"{exe}" "C:\\other\\run\\sunshine.conf"'},
            {"ProcessId": 3, "ExecutablePath": r"C:\Program Files\LavApollo\sunshine.exe",
             "CommandLine": f"sunshine.exe {tmp_path}"},
            {"ProcessId": 4, "ExecutablePath": None, "CommandLine": None},
            # image path unreadable (access denied): its command line names this run's sandbox, so it is ours
            {"ProcessId": 5, "ExecutablePath": None, "CommandLine": f'sunshine.exe "{tmp_path / "x" / "sunshine.conf"}"'}]
    monkeypatch.setattr(hosts.subprocess, "run",
                        lambda *a, **k: types.SimpleNamespace(stdout=json.dumps(rows), returncode=0))
    assert [pid for pid, _cmd in hosts.leftover_instances(tmp_path)] == [1, 5]


def test_kill_reports_an_unidentified_live_pid(monkeypatch):
    hosts.PROBLEMS.clear()
    monkeypatch.setattr(hosts, "_is_test_sunshine", lambda pid: False)
    monkeypatch.setattr(hosts, "pid_alive", lambda pid: True)
    monkeypatch.setattr(hosts, "_image_path", lambda pid: r"C:\somewhere\else.exe")
    killed = []
    monkeypatch.setattr(hosts.subprocess, "run", lambda *a, **k: killed.append(a))
    hosts._kill(1234)
    assert not killed and hosts.PROBLEMS and "1234" in hosts.PROBLEMS[0]
    hosts.PROBLEMS.clear()
