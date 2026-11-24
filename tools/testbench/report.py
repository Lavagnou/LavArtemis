"""Report of a scenario run: testbench-reports/<date>_<time>/report.md + report.json.

Per scenario: result, duration, reason of an expected failure, error; captures/<scenario>/*.png; logs/<scenario>/
(hosts' sunshine.log and testhost/*.jsonl, client logs).  The commits of LavApollo, LavArtemis-Qt and LavArtemis
used by the run are recorded, with a flag when a checkout has uncommitted changes."""
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


def write(root, results, started, finished):
    root = pathlib.Path(root)
    root.mkdir(parents=True, exist_ok=True)
    meta = {"started": started.isoformat(timespec="seconds"), "finished": finished.isoformat(timespec="seconds"),
            "repos": repos(), "results": results}
    (root / "report.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    counts = {}
    for r in results:
        counts[r["status"]] = counts.get(r["status"], 0) + 1
    lines = [f"# Testbench desktop - {started:%Y-%m-%d %H:%M}", "",
             "Result: " + ", ".join(f"{v} {k}" for k, v in sorted(counts.items())) if counts else "No scenario ran.",
             "", "## Builds", ""]
    for name, st in meta["repos"].items():
        if "error" in st:
            lines.append(f"- {name}: {st['error']}")
        else:
            lines.append(f"- {name}: `{st['commit']}` ({st['branch']}){' + uncommitted changes' if st['dirty'] else ''}"
                         f" {st.get('subject', '')}")
    lines += ["", "## Scenarios", "", "| Scenario | Result | Time (s) | Note |", "|---|---|---|---|"]
    for r in results:
        note = r["reason"] or (r["error"].splitlines()[-1][:160] if r["error"] else "")
        lines.append(f"| {r['name']} | {ICON.get(r['status'], r['status'])} | {r['duration']} | "
                     f"{note.replace('|', '/')} |")
    for r in results:
        if r["status"] in ("failed", "error"):
            lines += ["", f"### {r['name']} ({r['phase']})", "", "```", r["error"], "```"]
    lines += ["", "Captures: `captures/<scenario>/`; host and client logs: `logs/<scenario>/`.", ""]
    (root / "report.md").write_text("\n".join(lines), encoding="utf-8")
    return root / "report.md"
