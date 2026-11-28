"""Unit tests of selection.py: which scenarios a change runs (the release gate's choice)."""
import pathlib
import subprocess
import sys

import pytest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import selection  # noqa: E402


def test_a_settings_change_runs_the_settings_scenarios_only():
    picked, reasons = selection.select(["app/gui/v2/SettingsView2.qml"])
    assert picked == ["04", "11", "14", "19", "20", "22"]
    assert reasons == [("LavArtemis-Qt/app/gui/v2/SettingsView2.qml", "04, 11, 14, 19, 20, 22")]


def test_a_changed_scenario_runs_itself():
    picked, _ = selection.select([], ["tools/testbench/scenarios/test_05b_stream_input.py"])
    assert picked == ["05b"]


def test_what_everything_rests_on_runs_the_whole_suite():
    for qt, bench in ((["app/testctl/testcontrol.cpp"], []), (["app/main.cpp"], []),
                      ([], ["tools/testbench/client.py"]), ([], ["tools/testbench/scenarios/helpers.py"]),
                      ([], ["tools/testbench/fixtures/atlas-test/pc.json"])):
        assert selection.select(qt, bench)[0] == selection.ALL, (qt, bench)


def test_an_unmapped_client_file_runs_the_whole_suite():
    picked, reasons = selection.select(["app/gui/v2/BrandNewScreen.qml"])
    assert picked == selection.ALL
    assert "absent de selection.py" in reasons[0][1]


def test_docs_android_and_the_runner_run_nothing():
    picked, reasons = selection.select(["CLAUDE.md", "wix/LavArtemis/Product.wxs", "app/backend/updaterules.cpp"],
                                       ["tools/testbench/README.md", "tools/testbench/testbench.py",
                                        "tools/testbench/selection.py", "tools/testbench/tests/test_runner.py"])
    assert picked == []
    assert len(reasons) == 7 and all(what == "aucun scénario" for _p, what in reasons)


def test_the_order_is_numeric():
    picked, _ = selection.select(["app/gui/QuickMenu.qml", "app/gui/v2/HostsView.qml"])
    assert picked == ["01", "03", "05", "05b", "07", "09", "10", "12", "18", "19", "20"]


def test_every_rule_names_scenarios_that_exist():
    known = selection.scenario_files()
    for pattern, scenarios in selection.QT_RULES + selection.BENCH_RULES:
        if scenarios != selection.ALL:
            missing = [n for n in scenarios if n not in known]
            assert not missing, f"{pattern}: no scenario {missing}"


QT = pathlib.Path(__file__).resolve().parents[4] / "LavArtemis-Qt"


@pytest.mark.skipif(not (QT / ".git").exists(), reason="no LavArtemis-Qt clone next to LavArtemis")
def test_every_file_of_the_client_has_a_rule():
    """A new file of app/ needs a rule (or it runs the whole bench at every release): this lists them."""
    files = subprocess.run(["git", "-C", str(QT), "ls-files", "app"], capture_output=True, text=True,
                           check=True).stdout.split()
    unmapped = [f for f in files if "absent de selection.py" in selection.select([f])[1][0][1]]
    assert not unmapped, f"no rule in selection.py for {unmapped}"


def test_the_gate_with_nothing_concerned_passes_without_running(monkeypatch):
    import display
    import testbench
    monkeypatch.setattr(display, "display_state", lambda *a: display.ON)
    monkeypatch.setattr(testbench, "_changed_targets", lambda since: [])
    monkeypatch.setattr(testbench, "_pytest", lambda *a: pytest.fail("pytest must not run"))
    assert testbench.run_suite(changed_since="last-release") == testbench.EXIT_OK


def test_the_gate_runs_the_selected_scenarios(monkeypatch, tmp_path):
    import display
    import testbench
    monkeypatch.setattr(display, "display_state", lambda *a: display.ON)
    monkeypatch.setattr(display, "kept_awake", lambda: __import__("contextlib").nullcontext())
    monkeypatch.setattr(testbench, "_changed_targets", lambda since: ["scenarios/test_22_native_display.py"])
    ran = []
    monkeypatch.setattr(testbench, "_pytest", lambda args, attempt: ran.append(args) or 0)
    testbench.run_suite(report_dir=tmp_path, changed_since="last-release")
    assert ran and ran[0][0] == "scenarios/test_22_native_display.py"


def test_changes_that_cannot_be_listed_fail_the_gate(monkeypatch):
    import testbench
    monkeypatch.setattr(selection, "last_release_tag", lambda repo: (_ for _ in ()).throw(
        subprocess.CalledProcessError(128, "git", stderr="no tag")))
    assert testbench._changed_targets("last-release", out=lambda m: None) is None
