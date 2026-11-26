"""Scenario 17 (port of the V8 prototype, LavArtemis-Qt branch port-v8): what came after the home.

S1 « Reprendre » / « Rejoindre » (choice 23): another device streams from the PC (LavApollo
   OtherClientsConnected, faked by POST /api/test/session) -> the Scène says « Rejoindre »; nobody -> « Reprendre ».
S2 the in-game menu in tiles, laid out in the menu workshop of 2026-11-26: Reprendre, Déconnecter, Quitter la session,
   Micro (« Micro non envoyé » greyed while the micro is not sent, V8-61), Libérer la souris et le clavier,
   Statistiques de performance, Plus, in that order; no « Quitter Bureau » (only a game inside Playnite has its
   « Quitter {jeu} », scenario 6), none of the retired entries (Envoyer des touches…); « Plus » lists Plein écran /
   fenêtre, Presse-papiers, Commandes du PC hôte, Zoom, Réduire la fenêtre, Capturer le clavier, Comportement de la
   souris, in that order; Zoom goes 100 % <-> 200 %; « Comportement de la souris » switches Bureau <-> Jeu.
S3 Mes PC (V8-36): a card says the PC's type and link (« Tour · réseau filaire », pc.json form_factor / link).
S4 Réglages (V8-48): expert mode off, a page with expert rows ends with « N réglages avancés masqués · activer le
   mode expert », which turns expert mode on; rows saved in the profile carry « ● ».

Written during the night of 2026-11-26 with the display off, first run on 2026-11-26."""
import time

import helpers
import hosts


def test_s1_resume_or_join(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    helpers.wait_text(c, "Reprendre")
    assert not helpers.find(c, "Rejoindre")
    hosts.set_session(atlas, other_clients=1)
    try:
        helpers.wait_text(c, "Rejoindre", timeout=20)  # the next poll
        bench.shot("01-rejoindre")
    finally:
        hosts.set_session(atlas, other_clients=0)
    helpers.wait_text(c, "Rejoindre", gone=True, timeout=20)
    helpers.wait_text(c, "Reprendre")


def test_s2_ingame_menu_tiles(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.launch(c, "Bureau")
    qm = helpers.open_quick_menu(c)
    helpers.assert_quick_menu_tiles(c, qm)
    helpers.wait_quick_text(c, "Plus", qm)
    tiles = ["Reprendre", "Déconnecter", "Quitter la session", "Micro non envoyé", "Libérer la souris et le clavier",
             "Statistiques de performance", "Plus"]
    items = {t: helpers.find(c, t, window=qm) for t in tiles}
    assert all(items.values()), {t: bool(v) for t, v in items.items()}
    order = sorted(tiles, key=lambda t: helpers.reading_order(items[t][0]))
    assert order == tiles, f"tiles in the order {order}"
    shown = helpers.texts(c, window=qm)
    assert not [t for t in shown if t.startswith("Quitter ") and t != "Quitter la session"], \
        f"a « Quitter {{app}} » tile for the Bureau: {shown}"
    retired = [t for t in helpers.QUICK_MENU_RETIRED if t in shown]
    assert not retired, f"retired entries still in the menu: {retired}"
    bench.shot("01-menu", window=qm)

    # Plus: the PC's other entries, in the workshop's order
    helpers.quick_menu_click(c, "Plus", qm)
    helpers.wait_quick_text(c, "Comportement de la souris", qm)
    plus = [t for t in helpers.popup_texts(c)
            if any(t == e or t.startswith(e + " · ") for e in helpers.QUICK_MENU_PLUS)]
    assert [next(e for e in helpers.QUICK_MENU_PLUS if t == e or t.startswith(e + " · ")) for t in plus] \
        == helpers.QUICK_MENU_PLUS, f"« Plus » lists {plus}"
    assert "Zoom · 100 %" in plus, plus
    bench.shot("02-plus", window=qm)
    c.call("key", key="Escape", target="qt", window=qm)
    time.sleep(0.5)

    # Zoom (« Plus »): 200 % then 100 % (PanZoom, as Ctrl+Alt+Shift + / 0)
    helpers.quick_menu_click(c, "Zoom", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    c.wait_for(lambda s: s["session"]["zoom"] == 2, 10)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Plus", qm)
    assert helpers.wait_quick_text(c, "Zoom", qm) == "Zoom · 200 %", helpers.popup_texts(c)
    helpers.quick_menu_click(c, "Zoom", qm)
    c.wait_for(lambda s: s["session"]["zoom"] == 1, 10)

    # Comportement de la souris (« Plus », Ctrl+Alt+Shift+M): Bureau <-> Jeu, said over the stream
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Plus", qm)
    before = helpers.wait_quick_text(c, "Comportement de la souris", qm)
    assert before in ("Comportement de la souris · Bureau", "Comportement de la souris · Jeu"), before
    after = "Jeu" if before.endswith("Bureau") else "Bureau"
    helpers.quick_menu_click(c, "Comportement de la souris", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    c.wait_for(lambda s: f"Comportement de la souris : {after}" in s["session"]["statusMessage"], 10)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Plus", qm)
    assert helpers.wait_quick_text(c, "Comportement de la souris", qm) == f"Comportement de la souris · {after}"
    c.call("key", key="Escape", target="qt", window=qm)
    time.sleep(0.5)
    helpers.quick_menu_click(c, "Reprendre", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)

    # Reprendre closes the menu, the stream goes on
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Reprendre", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    assert c.state()["session"]["streaming"]


def test_s3_hosts_card_type_and_link(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.open_hosts(c)
    helpers.wait_text(c, "Tour · réseau filaire", contains=True, timeout=20)
    bench.shot("01-hosts")
    # the help line follows the input
    assert helpers.find(c, "Clic : ouvrir le PC. Clic droit ou ⋯ : réveiller, benchmark, renommer, oublier.")


def test_s4_settings_hidden_rows_and_profile_dot(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Vidéo")
    more = helpers.wait_text(c, "réglages avancés masqués", contains=True)[0]["text"]
    assert more.endswith("· activer le mode expert"), more
    assert helpers.find(c, "●"), "no row marked as following the profile"
    bench.shot("01-video")
    c.click(more)
    helpers.wait_text(c, "réglages avancés masqués", contains=True, gone=True)
    assert helpers.find(c, "EXPERT")
    bench.shot("02-video-expert")
