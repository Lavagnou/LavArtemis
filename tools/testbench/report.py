"""Report of a scenario run: testbench-reports/<date>_<time>/report.md + report.json.

Per scenario and attempt: result, duration, reason of an expected failure, error, flags (retries done by the
helpers, foreground switches to no window or to a crash dialog, leftover processes); captures/<scenario>/*.png;
logs/<scenario>/ (hosts' sunshine.log and testhost/*.jsonl, client logs).  A scenario rerun after a failure
(`testbench run --rerun-failed-once`) keeps both results: a pass on the rerun is shown as a FLAKE, never hidden.
The commits of LavApollo, LavArtemis-Qt and LavArtemis used by the run are recorded, with a flag when a checkout
has uncommitted changes."""
import json
import pathlib
import subprocess

HERE = pathlib.Path(__file__).resolve().parent


def _git(root, *args):
    try:
        r = subprocess.run(["git", "-C", str(root)] + list(args), capture_output=True, text=True, timeout=20)
        return r.stdout.strip() if r.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def repo_state(root):
    root = pathlib.Path(root)
    if not (root / ".git").exists():
        return {"path": str(root), "commit": "", "branch": "", "dirty": None}
    dirty = _git(root, "status", "--porcelain", "--untracked-files=no")
    return {"path": str(root), "commit": _git(root, "rev-parse", "--short=10", "HEAD"),
            "subject": _git(root, "log", "-1", "--format=%s"),
            "branch": _git(root, "rev-parse", "--abbrev-ref", "HEAD"), "dirty": bool(dirty)}


def repos():
    import client
    import hosts
    out = {"LavArtemis": repo_state(HERE.parents[1])}
    try:
        out["LavApollo"] = repo_state(hosts.lavapollo_root())
    except RuntimeError as e:
        out["LavApollo"] = {"error": str(e)}
    out["LavArtemis-Qt"] = repo_state(client.qt_root())
    return out


ICON = {"passed": "PASS", "failed": "FAIL", "error": "ERROR", "skipped": "SKIP", "xfail": "XFAIL", "xpass": "XPASS"}
BAD = ("failed", "error", "xpass")


def load(root):
    """Results already in this report folder (a first attempt, before a rerun)."""
    f = pathlib.Path(root) / "report.json"
    if not f.exists():
        return [], None
    meta = json.loads(f.read_text(encoding="utf-8"))
    return meta.get("results", []), meta.get("started")


def outcome(results):
    """Per scenario id: final status over its attempts, and whether it is a flake (bad, then passed on rerun)."""
    by_id = {}
    for r in results:
        by_id.setdefault(r["id"], []).append(r)
    out = {}
    for rid, rs in by_id.items():
        rs = sorted(rs, key=lambda r: r.get("attempt", 1))
        last = rs[-1]["status"]
        flake = len(rs) > 1 and any(r["status"] in BAD for r in rs[:-1]) and last not in BAD
        out[rid] = (last, flake)
    return out


def failed_ids(results):
    return [rid for rid, (status, _flake) in outcome(results).items() if status in BAD]


def write(root, results, started, finished):
    root = pathlib.Path(root)
    root.mkdir(parents=True, exist_ok=True)
    meta = {"started": str(started), "finished": finished.isoformat(timespec="seconds"),
            "repos": repos(), "results": results}
    (root / "report.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    final = outcome(results)
    counts = {}
    for status, flake in final.values():
        key = "flaky (passed on rerun)" if flake else status
        counts[key] = counts.get(key, 0) + 1
    flagged = [r for r in results if r.get("flags")]
    lines = [f"# Testbench desktop - {str(started)[:16].replace('T', ' ')}", "",
             ("Result: " + ", ".join(f"{v} {k}" for k, v in sorted(counts.items()))) if counts else
             "No scenario ran.", ""]
    if any(flake for _s, flake in final.values()):
        lines += ["**FLAKE**: a scenario failed, then passed when rerun once: see both rows below.", ""]
    if flagged:
        lines += [f"**FLAGS** on {len(flagged)} result(s): retries or foreground events, see the Note column.", ""]
    lines += ["## Builds", ""]
    for name, st in meta["repos"].items():
        if "error" in st:
            lines.append(f"- {name}: {st['error']}")
        else:
            lines.append(f"- {name}: `{st['commit']}` ({st['branch']}){' + uncommitted changes' if st['dirty'] else ''}"
                         f" {st.get('subject', '')}")
    lines += ["", "## Scenarios", "", "| Scenario | Attempt | Result | Time (s) | Note |", "|---|---|---|---|---|"]
    for r in results:
        parts = list(r.get("flags", []))
        if r["reason"]:
            parts.append(r["reason"])
        elif r["error"]:
            parts.append(r["error"].splitlines()[-1][:160])
        status = ICON.get(r["status"], r["status"])
        if r.get("attempt", 1) > 1:
            status += " (rerun)"
        note = "; ".join(parts).replace("|", "/").replace("\n", " ")
        lines.append(f"| {r['name']} | {r.get('attempt', 1)} | {status} | {r['duration']} | {note} |")
    for r in results:
        if r["status"] in BAD:
            lines += ["", f"### {r['name']} (attempt {r.get('attempt', 1)}, {r['phase']})", "", "```", r["error"], "```"]
    lines += ["", "Captures: `captures/<scenario>/`; host and client logs, notes, foreground timeline: "
              "`logs/<scenario>/` (a rerun: `<scenario>-rerun`).", ""]
    (root / "report.md").write_text("\n".join(lines), encoding="utf-8")
    return root / "report.md"
