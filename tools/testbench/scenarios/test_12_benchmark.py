"""Scenario 12: « Benchmark des réglages » (Mes PC › menu d'un PC), against the Benchmark app every fake PC offers
(LavApollo `benchmark_app`, on by default; `"benchmark_app": false` in pc.json takes it away).  Runs are shortened by
the test build only (`LAV_TEST_BENCHMARK="warmup,measure,window[,quitDelayMs]"`, LavArtemis-Qt BenchmarkRunner):
a Quick run takes about half a minute; quitDelayMs holds each quit of the host's app, a window to press « Arrêter »
in.  The report is read from `state.benchmark.report` (the JSON saved in the history, LavApollo docs/benchmark.md).

- unavailable: a PC without the app: the reason in French, « Démarrer » greyed out.
- quick: the report of a Quick run: the baseline records what it streamed (requested = effective: with the codec on
  Auto, the planner, the climb and the confirmation work on the codec really streamed), every run ok, the notes in
  English (translated at display, as Android), the page in French (mode and goal names, never « quick »).
- stop early: « Arrêter » while the host's app is being quit, before any run: the setup page comes back (no blank
  page), with « Démarrer » and a message that says nothing was measured; no stream afterwards.
- stop during restart: « Arrêter » while the scene restarts between a VRR run and a non-VRR one: no stream starts
  afterwards (the launch that the restart was going to make never happens)."""
import json
import re
import time

import helpers

FAST = "1,3,1,0"


def open_benchmark(c, host):
    helpers.host_card_menu(c, host.name)
    helpers.click(c, "Benchmark des réglages")
    helpers.wait_page(c, "BenchmarkView")
    time.sleep(0.5)


def bench_state(c):
    return c.state()["benchmark"]


def requests(host, kind=None):
    return [e for e in helpers.host_events(host, "events")
            if e.get("request") and (kind is None or e.get("request") == kind)]


def wait_message(c, text, timeout=30.0):
    deadline = time.time() + timeout
    while True:
        msg = bench_state(c)["message"]
        if text in msg:
            return msg
        assert time.time() < deadline, f"benchmark message never {text!r}; last {msg!r}"
        time.sleep(0.05)


def wait_finished(c, timeout):
    deadline = time.time() + timeout
    while True:
        b = bench_state(c)
        if not b["running"]:
            return b
        assert time.time() < deadline, f"the benchmark did not finish in {timeout:.0f}s: {b['message']!r}"
        time.sleep(0.5)


def scroll_to_end(c):
    """Wheel the benchmark page to its end (the report is longer than the window), until it stops moving."""
    fl = next(i for i in c.tree() if i["type"] == "Flickable")
    for _ in range(3):
        c.call("wheel", dy=-30, x=fl["x"] + fl["w"] // 2, y=fl["y"] + fl["h"] // 2, target="qt")
        time.sleep(0.3)
    deadline = time.time() + 5
    while any(i.get("moving") for i in c.tree() if i["type"] == "Flickable"):
        assert time.time() < deadline, "the benchmark page keeps moving after a wheel"
        time.sleep(0.2)


def item_enabled(c, text):
    items = [i for i in c.tree() if i.get("text") == text]
    assert items, f"no {text!r} on screen; texts {helpers.texts(c)[-15:]}"
    return items[0]["enabled"]


def test_benchmark_unavailable(bench):
    atlas, = bench.up("atlas-test", overrides={"atlas-test": {"benchmark_app": False}})
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["online"])
    assert comp["benchmarkAppId"] == 0, comp
    open_benchmark(c, atlas)
    msg = helpers.wait_any_text(c, ["ne propose pas le benchmark"])
    bench.shot("01-unavailable")
    assert msg.startswith("Ce PC hôte ne propose pas le benchmark."), msg
    assert item_enabled(c, "Démarrer") is False


def test_benchmark_quick(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client(env={"LAV_TEST_BENCHMARK": FAST})
    helpers.add_and_pair(c, atlas)
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["online"])
    assert comp["benchmarkAppId"] > 0, comp
    c.call("setting", action="set", key="fps", value=30)
    assert c.call("setting", action="get", key="videoCodecConfig")["value"] == 0, "the codec must be Auto"
    open_benchmark(c, atlas)
    helpers.wait_any_text(c, ["Tes réglages :"])
    bench.shot("01-setup")
    helpers.click(c, "Démarrer")
    b = wait_finished(c, 150)
    report = b["report"]
    bench.note(f"report: {json.dumps(report)[:3000]}")
    runs = report["runs"]
    assert report["mode"] == "quick" and report["measureSeconds"] == 3 and report["warmupSeconds"] == 1, report
    assert runs[0]["role"] == "baseline" and runs[-1]["role"] == "baseline-repeat", [r["role"] for r in runs]

    # The baseline records what it really streamed: Auto has no codec name, and the plan, its climb and its
    # confirmation are made on the codec the baseline ran with (findings B3)
    base = runs[0]
    assert base["requested"] == base["effective"], f"baseline requested {base['requested']} != {base['effective']}"
    assert runs[-1]["requested"] == runs[-1]["effective"], runs[-1]
    for r in runs:
        assert r["status"] == "ok", f"run {r['role']} {r['requested']}: {r['status']} {r['note']!r}"
        assert r["metrics"]["framesRendered"] > 0, r
        assert all(0 <= r["scores"][k] <= 100 for k in ("latency", "smoothness", "balanced")), r
        # Notes are written in English and translated at display, as Android (findings B12)
        assert re.fullmatch(r"[\x20-\x7e]*", r["note"]), f"a note must be stored in English: {r['note']!r}"
    for r in runs[1:-1]:
        assert r["role"] == "candidate", r
        assert r["requested"]["codec"] == base["effective"]["codec"] or r["requested"]["pacing"] == base["effective"]["pacing"], r

    # Saved in the history; the page in French
    assert any(f.endswith(".json") for f in __import__("os").listdir(b["historyDir"])), b["historyDir"]
    st = c.state()
    assert not st["session"]["active"]
    assert st["benchmark"]["message"] == "Benchmark terminé.", st["benchmark"]["message"]
    shown = helpers.texts(c)
    header = next((t for t in shown if t.startswith(f"{atlas.name}, ")), None)
    assert header and "Rapide" in header and "Équilibré" in header, f"report header {header!r}"
    for t in shown:
        assert not re.search(r"\b(quick|full|adaptive|balanced|latency|smoothness)\b", t), f"English on screen: {t!r}"
    bench.shot("02-report")
    scroll_to_end(c)
    helpers.click(c, "Retour")
    helpers.wait_text(c, "Benchmarks précédents")
    history = next((t for t in helpers.texts(c) if "Rapide" in t and "Équilibré" in t), None)
    assert history, f"the history row must name the mode and the goal in French: {helpers.texts(c)[-12:]}"
    bench.shot("03-history")


def test_benchmark_stop_early(bench):
    """Stop while the app already running on the host is quit, before the first run (findings B4)."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client(env={"LAV_TEST_BENCHMARK": "1,3,1,4000"})
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="fps", value=30)
    # An app left running on the host: Bureau, disconnected
    helpers.launch(c, "Bureau")
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Déconnecter", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    helpers.wait_computer(c, atlas.name, lambda x: x["currentGameId"] != 0, timeout=20)
    launches = len(requests(atlas, "launch"))

    open_benchmark(c, atlas)
    helpers.wait_any_text(c, ["Une appli tourne sur le PC hôte"])
    helpers.click(c, "Démarrer")
    wait_message(c, "Fermeture de l'appli")
    helpers.click(c, "Arrêter")
    wait_finished(c, 20)
    time.sleep(5.0)  # past the quit delay: the run that would have followed
    st = c.state()
    bench.shot("01-stopped")
    assert not st["session"]["active"] and st["page"] == "BenchmarkView", st["page"]
    assert len(requests(atlas, "launch")) == launches, "no run may start after « Arrêter »"
    # The setup page, not a blank one, and a message that says what happened
    assert item_enabled(c, "Démarrer") is True
    msg = st["benchmark"]["message"]
    assert msg == "Benchmark arrêté avant le premier passage : rien n'a été mesuré.", msg
    helpers.wait_text(c, msg)
    assert st["benchmark"]["report"]["runs"] == []


def test_benchmark_stop_during_restart(bench):
    """Stop while the scene restarts between the VRR baseline and a non-VRR run (findings B2)."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client(env={"LAV_TEST_BENCHMARK": "1,3,1,4000"})
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="fps", value=30)
    c.call("setting", action="set", key="enableVsync", value=True)
    c.call("setting", action="set", key="enableVrr", value=True)
    open_benchmark(c, atlas)
    helpers.click(c, "Démarrer")
    wait_message(c, "Redémarrage de la scène", timeout=90)
    launches = len(requests(atlas, "launch"))
    helpers.click(c, "Arrêter")
    wait_finished(c, 20)
    deadline = time.time() + 7.0  # the quit delay, and then some: the launch it was holding must never come
    while time.time() < deadline:
        st = c.state()
        assert not st["session"]["active"], "a run started after « Arrêter »"
        time.sleep(0.2)
    bench.shot("01-stopped")
    assert len(requests(atlas, "launch")) == launches, "no launch may reach the host after « Arrêter »"
    report = st["benchmark"]["report"]
    assert report["cancelled"] is True and [r["role"] for r in report["runs"]] == ["baseline"], report
    assert st["benchmark"]["message"] == "Benchmark arrêté. Les mesures déjà faites sont conservées.", \
        st["benchmark"]["message"]
    helpers.wait_text(c, "Retour")
