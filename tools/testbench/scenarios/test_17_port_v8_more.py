"""Scenario 17 (port of the V8 prototype, LavArtemis-Qt branch port-v8): what came after the home.

S1 « Reprendre » / « Rejoindre » (choice 23): another device streams from the PC (LavApollo
   OtherClientsConnected, faked by POST /api/test/session) -> the Scène says « Rejoindre »; nobody -> « Reprendre ».
S2 the in-game menu in tiles (V8-26): Reprendre, the 9 entries, Plus; « Micro non envoyé » greyed while the micro is
   not sent (V8-61); « Plus » lists Capturer le clavier and Plein écran / fenêtre; Zoom goes 100 % <-> 200 %.
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
    assert helpers.find(c, "Micro non envoyé", window=qm), helpers.texts(c, window=qm)
    assert helpers.find(c, "Quitter Bureau", window=qm), helpers.texts(c, window=qm)
    bench.shot("01-menu", window=qm)

    # Plus: the PC's other entries
    helpers.quick_menu_click(c, "Plus", qm)
    helpers.wait_quick_text(c, "Capturer le clavier", qm)
    assert helpers.find(c, "Plein écran / fenêtre", contains=True, window=qm)
    bench.shot("02-plus", window=qm)
    c.call("key", key="Escape", target="qt", window=qm)
    time.sleep(0.5)

    # Zoom: 200 % then 100 % (PanZoom, as Ctrl+Alt+Shift + / 0)
    helpers.quick_menu_click(c, "Zoom", qm)
    c.wait_for(lambda s: not s.get("quickMenu"), 10)
    c.wait_for(lambda s: s["session"]["zoom"] == 2, 10)
    qm = helpers.open_quick_menu(c)
    assert helpers.find(c, "200 %", window=qm)
    helpers.quick_menu_click(c, "Zoom", qm)
    c.wait_for(lambda s: s["session"]["zoom"] == 1, 10)

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
