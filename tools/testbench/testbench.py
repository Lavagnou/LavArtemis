"""Testbench CLI:  python tools/testbench/testbench.py up [fixtures...] | down | logs <pc> [input|commands|events]"""
import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import hosts  # noqa: E402


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
    a = ap.parse_args(argv)
    if a.cmd == "up":
        for h in hosts.up(a.fixtures, a.workdir):
            print(f"{h.name}\tport {h.port}\tpid {h.pid if h.pid else '-'}\t{h.dir}")
    elif a.cmd == "down":
        hosts.down(a.workdir)
    else:
        pc = a.pc if (a.workdir / a.pc).is_dir() else next(
            (f for f in hosts.available_fixtures() if hosts.load_fixture(f)["name"] == a.pc), a.pc)
        f = a.workdir / pc / "testhost" / f"{a.stream}.jsonl"
        print(f.read_text(encoding="utf-8") if f.exists() else "", end="")


if __name__ == "__main__":
    main()
