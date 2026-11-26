"""Scenario 18 (port of the V8 prototype, LavArtemis-Qt branch port-v8, third pass).

S1 the segue and the stream toasts (V8-24, 71): launching Bureau says « Connecté à <PC> » over the stream; back home
   after « Déconnecter » the home says « Déconnecté : la session continue sur le PC » (choice 150); « Reprendre »
   says « Session reprise ».
S2 the statistics on one line (prototype statsText): Ctrl+Alt+Shift+S, then a line « W × H · N fps · codec · … ».
S3 Mes PC: the PCs that answer first (Cynisca, stopped, goes after Test-Atlas although its name comes first).
S4 the update dialog (V8-13): « Vérifier » finds a version (LAV_TEST_UPDATE), « Installer » opens the theme's
   dialog with « Télécharger et installer », « Ignorer cette version », « Plus tard ».

Written on 2026-11-26 with the display off: never run yet (port-notes.md)."""
import time

import helpers


def _toasts(c):
    return c.state().get("toasts") or []


def test_s1_segue_toasts(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    c.wait_for(lambda st: f"Connecté à {atlas.name}" in (st.get("toasts") or []), 10)
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    helpers.wait_text(c, "Déconnecté : la session continue sur le PC")
    bench.shot("01-disconnected")
    resume = next(t for t in helpers.texts(c) if t.lower().startswith("reprendre"))
    before = len(_toasts(c))
    helpers.launch(c, resume)
    c.wait_for(lambda st: "Session reprise" in (st.get("toasts") or [])[before:], 10)
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)


def test_s2_stats_one_line(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    c.call("key", key="S", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: s["session"]["overlayStats"]["shown"] and s["session"]["overlayStats"]["text"], 10)
    time.sleep(2.5)
    text = c.state()["session"]["overlayStats"]["text"].strip()
    bench.note(f"overlay: {text!r}")
    assert "\n" not in text, text
    parts = text.split(" · ")
    assert " × " in parts[0] and parts[1].endswith(" fps"), parts
    assert any(p.startswith("décodage ") and p.endswith(" ms") for p in parts), parts


def test_s3_hosts_online_first(bench):
    atlas, salon = bench.up("atlas-test", "cynisca-test", force_instances=("cynisca-test",))
    c = bench.start_client()
    helpers.add_and_pair(c, salon)
    helpers.add_and_pair(c, atlas)
    bench.stop_host("cynisca-test")
    helpers.open_hosts(c)
    helpers.wait_text(c, "Hors ligne", timeout=60)
    names = [t for t in helpers.texts(c) if t in (atlas.name, salon.name)]
    bench.shot("01-online-first")
    assert names.index(atlas.name) < names.index(salon.name), names


def test_s4_update_dialog(bench):
    c = bench.start_client(env={"LAV_TEST_UPDATE": "available:9.9.9:https://github.invalid/LavArtemis-v9.9.9-win-installer.exe"})
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Avancé")
    helpers.click(c, "Vérifier")
    helpers.wait_text(c, "LavArtemis 9.9.9 est disponible.")
    helpers.click(c, "Installer")
    helpers.wait_text(c, "Ignorer cette version")
    shown = helpers.popup_texts(c)
    bench.shot("01-update")
    for t in ("LavArtemis 9.9.9 est disponible", "Télécharger et installer", "Plus tard"):
        assert t in shown, shown
    helpers.click(c, "Plus tard")
    helpers.wait_text(c, "Ignorer cette version", gone=True)
