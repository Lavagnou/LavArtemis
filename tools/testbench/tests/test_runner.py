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


@pytest.fixture(autouse=True)
def _display_on(monkeypatch):
    """run_suite refuses with the display off (exit 4): these tests must not depend on the real display."""
    import display
    monkeypatch.setattr(display, "display_state", lambda *a: display.ON)


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

def _fake_runs(tmp_path, calls, second_status):
    def fake_pytest(args, attempt):
        calls.append((list(args), attempt))
        first = [res("a", "failed", error="E boom"), res("b", "passed"), res("c", "xfail")]
        rows = first if attempt == 1 else first + [res("a", second_status, 2, error="E boom")]
        (tmp_path / "report.json").write_text(json.dumps({"started": "s", "results": rows}), encoding="utf-8")
        return 1 if attempt == 1 or second_status != "passed" else 0
    return fake_pytest


def test_rerun_failed_once_reruns_only_failed_a_pass_on_rerun_is_flaky(tmp_path, monkeypatch, capsys):
    calls = []
    monkeypatch.setattr(testbench, "_pytest", _fake_runs(tmp_path, calls, "passed"))
    assert testbench.run_suite(report_dir=tmp_path, rerun_failed_once=True) == testbench.EXIT_FLAKY == 3
    out = capsys.readouterr().out
    assert "FLAKE: scenarios/test_x.py::a" in out and "RESULT: FLAKY (exit 3)" in out and "FAIL:" not in out
    assert [a for _args, a in calls] == [1, 2]
    assert calls[1][0][0] == "scenarios/test_x.py::a" and len([x for x in calls[1][0] if "::" in x]) == 1
    assert "--report-dir" in calls[1][0] and str(tmp_path) in calls[1][0]


def test_rerun_failing_again_fails(tmp_path, monkeypatch, capsys):
    calls = []
    monkeypatch.setattr(testbench, "_pytest", _fake_runs(tmp_path, calls, "failed"))
    assert testbench.run_suite(report_dir=tmp_path, rerun_failed_once=True) == 1
    out = capsys.readouterr().out
    assert "FAIL: scenarios/test_x.py::a" in out and "RESULT: FAILED (exit 1)" in out


def test_verdict_codes():
    ok = [res("a", "passed"), res("b", "xfail")]
    assert testbench.verdict(ok, 0) == (0, [])
    assert testbench.verdict([res("a", "failed")], 1)[0] == 1
    assert testbench.verdict([res("a", "error")], 1)[0] == 1
    assert testbench.verdict([res("a", "failed"), res("a", "passed", 2)], 0) == (3, ["FLAKE: scenarios/test_x.py::a"])
    assert testbench.verdict([], 1)[0] == 1  # pytest failed with no scenario result (collection error)
    assert testbench.verdict([], 2)[0] == 1  # interrupted


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


# ---------------------------------------------------------------- in-game menu footer check

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scenarios"))
import helpers  # noqa: E402


def _menu_tree(last_row_y):
    """The in-game menu as `tree` reports it: title, a ListView ending at y=321, rows every 60 px, the footer
    button at y=336.  last_row_y places the last row (visible part, as the tree clips it)."""
    def item(type_, text, x, y, w, h):
        return {"window": 0, "type": type_, "objectName": "", "text": text, "x": x, "y": y, "w": w, "h": h,
                "focus": False, "enabled": True}
    items = [item("Text", "Envoyer des touches", 89, 20, 323, 41),
             item("ListView", "", 30, 80, 440, 241)]
    for n, y in enumerate([91, 151, 211, 271, last_row_y]):
        items.append(item("Text", f"Raccourci {n}", 162, y, 120, 23))
        items.append(item("Text", "Raccourci intégré", 162, y + 25, 108, 16))
    items += [item("Button", "← Retour", 210, 336, 80, 44), item("IconLabel", "← Retour", 222, 346, 56, 24)]
    return items


class _TreeOnly:
    def __init__(self, items):
        self.items = items

    def tree(self, **args):
        return self.items


def test_footer_check_fails_on_the_unclipped_menu():
    # Before LavArtemis-Qt 09dfbbb8: the ListView ended above the footer but did not clip, so its last row was
    # drawn under « ← Retour » (seen at y=331 in the C3 capture 05-send-keys)
    with pytest.raises(AssertionError, match="rows drawn under the footer"):
        helpers.assert_quick_menu_footer_clear(_TreeOnly(_menu_tree(331)), 0)


def test_footer_check_passes_on_the_clipped_menu():
    # After 09dfbbb8: the list clips, the last visible row ends above the footer (the rest scrolls)
    helpers.assert_quick_menu_footer_clear(_TreeOnly(_menu_tree(291)), 0)


def test_footer_check_fails_when_the_list_reaches_under_the_footer():
    items = _menu_tree(271)
    next(i for i in items if i["type"] == "ListView")["h"] = 300  # ends at 380, under the footer at 336
    with pytest.raises(AssertionError, match="reaches under the footer"):
        helpers.assert_quick_menu_footer_clear(_TreeOnly(items), 0)


# ---------------------------------------------------------------- the report hook (conftest.record)

class _Rep:
    def __init__(self, when, outcome, longrepr="", wasxfail=None):
        self.when, self.outcome, self.duration, self.longrepr = when, outcome, 0.5, longrepr
        self.failed, self.skipped, self.passed = outcome == "failed", outcome == "skipped", outcome == "passed"
        if wasxfail is not None:
            self.wasxfail = wasxfail


def _run_reports(monkeypatch, *reps):
    run = types.SimpleNamespace(current={}, results=[], flags={"n": ["RETRY x1: something"]})
    monkeypatch.setattr(tbconf, "ATTEMPT", 1)
    for r in reps:
        tbconf.record(run, "n", "test_n", "", r)
    return run


def test_record_setup_failure_is_one_error_row(monkeypatch):
    run = _run_reports(monkeypatch, _Rep("setup", "failed", "E host did not start"), _Rep("teardown", "passed"))
    assert len(run.results) == 1 and not run.current
    r = run.results[0]
    assert (r["status"], r["phase"]) == ("error", "setup") and "host did not start" in r["error"]
    assert r["flags"] == ["RETRY x1: something"]


def test_record_call_failure_then_clean_teardown(monkeypatch):
    run = _run_reports(monkeypatch, _Rep("setup", "passed"), _Rep("call", "failed", "E assert 1 == 2"),
                       _Rep("teardown", "passed"))
    assert [(r["status"], r["phase"]) for r in run.results] == [("failed", "call")]
    assert run.results[0]["duration"] == 1.5


def test_record_teardown_failure_after_a_pass_is_an_error(monkeypatch):
    run = _run_reports(monkeypatch, _Rep("setup", "passed"), _Rep("call", "passed"),
                       _Rep("teardown", "failed", "E leftover test host killed"))
    assert [(r["status"], r["phase"]) for r in run.results] == [("error", "teardown")]
    assert "leftover" in run.results[0]["error"]


def test_record_call_failure_keeps_its_error_over_a_teardown_failure(monkeypatch):
    run = _run_reports(monkeypatch, _Rep("setup", "passed"), _Rep("call", "failed", "E first"),
                       _Rep("teardown", "failed", "E second"))
    assert [(r["status"], r["phase"]) for r in run.results] == [("failed", "call")]
    assert "first" in run.results[0]["error"]


def test_record_pass_and_xfail(monkeypatch):
    run = _run_reports(monkeypatch, _Rep("setup", "passed"), _Rep("call", "passed"), _Rep("teardown", "passed"))
    assert run.results[0]["status"] == "passed"
    run = _run_reports(monkeypatch, _Rep("setup", "passed"), _Rep("call", "skipped", wasxfail="PRODUCT BUG: x"),
                       _Rep("teardown", "passed"))
    assert (run.results[0]["status"], run.results[0]["reason"]) == ("xfail", "PRODUCT BUG: x")


# ---------------------------------------------------------------- sandbox matching, footer without a list

def test_sandbox_match_needs_a_separator(tmp_path):
    wd = tmp_path / "test_x"
    assert hosts._is_in_sandbox(f'sunshine.exe "{wd / "atlas-test" / "sunshine.conf"}"', wd)
    assert not hosts._is_in_sandbox(f'sunshine.exe "{tmp_path / "test_x-rerun" / "a" / "sunshine.conf"}"', wd)
    assert not hosts._is_in_sandbox(None, wd)


def test_footer_check_without_a_list_fails_clearly():
    items = [i for i in _menu_tree(271) if i["type"] != "ListView"]
    with pytest.raises(AssertionError, match="no ListView"):
        helpers.assert_quick_menu_footer_clear(_TreeOnly(items), 0)


# ---------------------------------------------------------------- gate: exit codes, display, selftest

def test_verdict_skipped_scenario_fails_the_gate():
    code, lines = testbench.verdict([res("a", "passed"), res("b", "skipped")], 0)
    assert code == 1 and lines == ["FAIL: scenarios/test_x.py::b"]


def test_verdict_nonzero_pytest_code_without_failed_row_is_1():
    for pytest_code in (1, 2, 3, 4, 5):
        code, lines = testbench.verdict([res("a", "passed")], pytest_code)
        assert code == 1 and "pytest exit code" in lines[-1]
    assert testbench.verdict([res("a", "xfail")], 0)[0] == 0


def test_display_off_refuses_before_anything_runs(monkeypatch, tmp_path, capsys):
    import display
    monkeypatch.delenv("LAV_TESTBENCH_SELFTEST", raising=False)
    monkeypatch.setattr(display, "display_state", lambda *a: display.OFF)
    monkeypatch.setattr(testbench, "_pytest", lambda *a: pytest.fail("pytest must not start"))
    assert testbench.run_suite(report_dir=tmp_path) == testbench.EXIT_DISPLAY_OFF == 4
    assert "écran éteint" in capsys.readouterr().err
    assert not (tmp_path / "report.json").exists()


def test_display_unknown_or_on_is_not_refused():
    import display
    assert display.OFF == 0 and display.ON == 1


@pytest.mark.parametrize("mode,code", [("pass", 0), ("fail", 1), ("flaky", 3)])
def test_selftest_hook_drives_the_gate_without_gui(mode, code, monkeypatch, tmp_path, capsys):
    import display
    monkeypatch.setenv("LAV_TESTBENCH_SELFTEST", mode)
    monkeypatch.setattr(display, "display_state", lambda *a: pytest.fail("selftest must not check the display"))
    assert testbench.run_suite(report_dir=tmp_path / "r", rerun_failed_once=True) == code
    assert f"(exit {code})" in capsys.readouterr().out


def test_selftest_rejects_unknown_mode(monkeypatch, tmp_path):
    monkeypatch.setenv("LAV_TESTBENCH_SELFTEST", "bogus")
    assert testbench.run_suite(report_dir=tmp_path) == 1


def test_display_unknown_runs_with_a_stderr_line(monkeypatch, tmp_path, capsys):
    import display
    monkeypatch.delenv("LAV_TESTBENCH_SELFTEST", raising=False)
    monkeypatch.setattr(display, "display_state", lambda *a: None)
    monkeypatch.setattr(testbench, "_pytest", lambda args, attempt: 0)
    assert testbench.run_suite(report_dir=tmp_path) == 0
    assert "display state unknown" in capsys.readouterr().err
