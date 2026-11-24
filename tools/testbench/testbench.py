"""Testbench CLI:  python tools/testbench/testbench.py up [fixtures...] | down | logs <pc> [input|commands|events]"""
import argparse
import json
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
    s = sub.add_parser("client", help="one-shot: send one command to a running test client, print the reply")
    s.add_argument("command")
    s.add_argument("args", nargs="?", default="{}", help="JSON object of arguments")
    s = sub.add_parser("shot", help="screenshot of the running test client's main window (absolute PNG path)")
    s.add_argument("path", type=pathlib.Path)
    s.add_argument("--window", default="0", help="Qt window index, or 'stream'")
    a = ap.parse_args(argv)
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
