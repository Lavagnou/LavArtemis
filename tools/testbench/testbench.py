"""Testbench CLI:  python tools/testbench/testbench.py run [--suite desktop] [-k expr] | up [fixtures...] | down |
logs <pc> [input|commands|events] | client <cmd> [json] | shot <path> | mark-client-built"""
import argparse
import json
import os
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import hosts  # noqa: E402


def _pytest(args, attempt):
    """pytest in a child process (a second session in the same process would keep the first one's modules)."""
    here = pathlib.Path(__file__).resolve().parent
    env = dict(os.environ, LAV_TESTBENCH_ATTEMPT=str(attempt))
    cmd = [sys.executable, "-m", "pytest", "-v", "-p", "no:cacheprovider", "-rxXs"] + args
    return subprocess.run(cmd, cwd=str(here), env=env).returncode


EXIT_OK, EXIT_FAILED, EXIT_FLAKY = 0, 1, 3


def verdict(results, pytest_code):
    """(exit code, summary lines) of a run.  0: every scenario passed on its first attempt (xfail counts as
    expected); 1: a scenario still fails (after its rerun, if any), or pytest failed without a scenario result
    (collection error, interruption); 3 (FLAKY): nothing fails any more but a scenario passed only on its rerun.
    A flake fails the release gate too: it must be looked at, never shipped over."""
    import report
    final = report.outcome(results)
    failed = sorted(rid for rid, (status, _f) in final.items() if status in report.BAD)
    flaky = sorted(rid for rid, (_s, flake) in final.items() if flake)
    lines = [f"FAIL: {rid}" for rid in failed] + [f"FLAKE: {rid}" for rid in flaky]
    if failed:
        return EXIT_FAILED, lines
    if pytest_code not in (0, 1) or (pytest_code == 1 and not results):
        return EXIT_FAILED, lines + [f"FAIL: pytest exit code {pytest_code} without a failed scenario"]
    if flaky:
        return EXIT_FLAKY, lines
    return EXIT_OK, lines


def run_suite(keyword=None, report_dir=None, rerun_failed_once=False, extra=()):
    """The desktop suite; with rerun_failed_once, the failed scenarios run once more into the same report (attempt
    2).  Exit code: see verdict() (0 all passed first time, 1 failure, 3 flaky).  A summary block (FAIL / FLAKE
    lines) is printed at the end, besides report.md."""
    import datetime
    import report
    here = pathlib.Path(__file__).resolve().parent
    stamp = datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")
    report_dir = pathlib.Path(report_dir) if report_dir else here / "testbench-reports" / stamp
    args = [str(here / "scenarios"), "--report-dir", str(report_dir)] + (["-k", keyword] if keyword else [])
    code = _pytest(args + list(extra), 1)
    results, _started = report.load(report_dir)
    if code != 0 and rerun_failed_once:
        failed = report.failed_ids(results)
        if failed:
            print(f"testbench: rerunning {len(failed)} failed scenario(s) once: {failed}", flush=True)
            code = _pytest(failed + ["--report-dir", str(report_dir)] + list(extra), 2)
            results, _started = report.load(report_dir)
    exit_code, lines = verdict(results, code)
    name = {EXIT_OK: "PASSED", EXIT_FAILED: "FAILED", EXIT_FLAKY: "FLAKY"}[exit_code]
    print("", "=== testbench summary ===", *lines, f"RESULT: {name} (exit {exit_code}), report {report_dir}",
          sep="\n", flush=True)
    return exit_code


def main(argv=None):
    ap = argparse.ArgumentParser(prog="testbench")
    ap.add_argument("--workdir", type=pathlib.Path, default=hosts.default_workdir())
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("up")
    s.add_argument("fixtures", nargs="*")
    sub.add_parser("down")
    s = sub.add_parser("logs")
    s.add_argument("pc", help="fixture name (atlas-test) or displayed name (Test-Atlas)")
    s.add_argument("stream", nargs="?", default="events", choices=["input", "commands", "events"])
    s = sub.add_parser("client", help="one-shot: send one command to a running test client, print the reply")
    s.add_argument("command")
    s.add_argument("args", nargs="?", default="{}", help="JSON object of arguments")
    s = sub.add_parser("shot", help="screenshot of the running test client's main window (absolute PNG path)")
    s.add_argument("path", type=pathlib.Path)
    s.add_argument("--window", default="0", help="Qt window index, or 'stream'")
    s = sub.add_parser("run", help="run the scenario suite (pytest), report in testbench-reports/<date>_<time>/")
    s.add_argument("--suite", default="desktop", choices=["desktop"])
    s.add_argument("-k", dest="keyword", default=None, help="pytest -k expression (a scenario's name)")
    s.add_argument("--report-dir", default=None)
    s.add_argument("--rerun-failed-once", action="store_true",
                   help="rerun each failed scenario once (the release gate uses it); both results stay in the "
                        "report, a pass on the rerun is shown as a FLAKE")
    s.add_argument("pytest_args", nargs="*", help="extra pytest arguments (after --)")
    sub.add_parser("mark-client-built", help="record the current sources as the ones the client exe was built "
                                             "from (after checking the newer mtimes are comment-only edits)")
    a = ap.parse_args(argv)
    if a.cmd == "run":
        sys.exit(run_suite(a.keyword, a.report_dir, a.rerun_failed_once, a.pytest_args))
    if a.cmd == "mark-client-built":
        import client
        client.mark_built()
        return
    if a.cmd in ("client", "shot"):
        import client
        c = client.Client(client.Channel(client.PipeTransport()))
        if a.cmd == "client":
            print(json.dumps(c.cmd(a.command, **json.loads(a.args))))
        else:
            w = a.window if a.window == "stream" else int(a.window)
            print(json.dumps(c.screenshot(a.path, w)))
        c.channel.t.close()
        return
    if a.cmd == "up":
        bad = [f for f in a.fixtures if f not in hosts.available_fixtures()]
        if bad:
            ap.error(f"unknown fixture(s) {bad}; available: {hosts.available_fixtures()}")
        for h in hosts.up(a.fixtures, a.workdir):
            print(f"{h.name}\tport {h.port}\tpid {h.pid if h.pid else '-'}\t{h.dir}")
    elif a.cmd == "down":
        hosts.down(a.workdir)
    else:
        pc = next((f for f in hosts.available_fixtures()
                   if a.pc in (f, hosts.load_fixture(f)["name"])), None)
        if pc is None:
            ap.error(f"unknown PC {a.pc!r}; available: {hosts.available_fixtures()}")
        f = a.workdir / pc / "testhost" / f"{a.stream}.jsonl"
        print(f.read_text(encoding="utf-8") if f.exists() else "", end="")


if __name__ == "__main__":
    main()
