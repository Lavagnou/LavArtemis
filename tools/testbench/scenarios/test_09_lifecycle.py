"""Scenario 9: the life of a PC on Mes PC and the home.  Its pairing removed on the host (it is offered for pairing
again, never « Hors ligne » forever), forgotten (the home moves to another PC, nothing of it is left), switched off
(its cards leave the home), a second PC paired (the home shows it), a menu that keeps its PC while the list moves,
pairing errors in French naming the PC, one pairing at a time, the address forms of « Ajouter un PC »."""
import time

import helpers
import hosts


def _home_on(c, name, timeout=20.0):
    """The home shows this PC with its cards (« Sur <PC> » and the Bureau card)."""
    helpers.wait_page(c, "HomeView", timeout)
    helpers.wait_text(c, f"Sur {name}", timeout=timeout)
    helpers.wait_text(c, "Bureau", timeout=timeout)


def _error_text(c, timeout=20.0):
    """The text of the « Oups » dialog, once it shows; the dialog is closed."""
    helpers.wait_text(c, "Oups", timeout=timeout)
    shown = helpers.texts(c)
    i = shown.index("Oups")
    text = next(t for t in shown[i + 1:] if t not in ("OK", "Oups"))
    return text


def _back_home(c):
    """The header's back button of Mes PC (a mouse click): after a dialog opened from a card's menu, the keyboard
    focus is lost (wave 2 of the verification pass), so Escape is not used here."""
    for attempt in range(3):
        st = c.wait_idle()
        if st["page"] == "HomeView":
            return
        time.sleep(0.5)  # a dialog that just closed still fades out
        btns = [i for i in c.tree() if i["type"] == "RoundIconButton"]
        helpers.TRACE.append(f"back buttons on {st['page']}: {btns}")
        if btns:
            helpers.click_item(c, min(btns, key=lambda i: (i["y"], i["x"])))
        try:
            helpers.wait_page(c, "HomeView", timeout=5)
            return
        except Exception:  # noqa: BLE001 -- retried, then reported below
            pass
    raise AssertionError(f"the back button of Mes PC does not lead home; texts {helpers.texts(c)[-10:]}")


def _close_error(c):
    helpers.click(c, "OK")
    helpers.wait_text(c, "Oups", gone=True)


def _assert_french(text):
    assert "<" not in text and "status_code" not in text, f"raw XML shown: {text!r}"
    for english in ("Pairing", "failed", "error", "Error", "PIN", "Please", "host"):
        assert english not in text, f"English shown ({english}): {text!r}"


def test_pairing_removed_on_host(bench):
    """Paired, then « Tout désappairer » in LavApollo: the client's certificate is refused (HTTPS 401).  The PC must
    stay online and become not paired: it leaves the home (« Aucun PC appairé ») and the cards of Mes PC, shows in
    « Ajouter un PC » with its address, and adding it again by address opens its pairing dialog."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    _home_on(c, atlas.name)
    bench.shot("01-paired")

    hosts.unpair_all(atlas)
    comp = helpers.wait_computer(c, atlas.name, lambda x: not x["paired"], timeout=30)
    assert comp["online"], f"a PC that answers is online, not paired: {comp}"
    helpers.wait_text(c, "Aucun PC appairé")
    helpers.wait_text(c, "Bureau", gone=True)
    bench.shot("02-home-unpaired")

    helpers.open_hosts(c)
    helpers.wait_text(c, "Hors ligne", gone=True, timeout=3)
    # Its card left Mes PC; « Ajouter un PC » offers it
    assert not helpers.find(c, atlas.name), f"an unpaired PC has no card: {helpers.texts(c)}"
    helpers.click(c, "Ajouter un PC", index=0)
    entry = helpers.wait_text(c, f"{atlas.name} · 127.0.0.1:40989")
    bench.shot("03-add-menu")
    c.call("key", key="Escape", target="qt")
    helpers.wait_text(c, f"{atlas.name} · 127.0.0.1:40989", gone=True)

    # Added again by its address (known to the client, its pinned certificate refused): its pairing dialog opens
    helpers.add_by_address(c, "127.0.0.1:40989")
    pin = helpers.read_pin(c, atlas.name, timeout=15)
    bench.shot("04-pair-again")
    assert helpers.give_pin(atlas, pin).get("status") is True
    helpers.wait_computer(c, atlas.name, lambda x: x["paired"] and x["online"], timeout=20)
    _home_on(c, atlas.name)
    bench.shot("05-paired-again")
    assert entry


def test_second_pc_then_forget_both(bench):
    """Pairing a second PC shows it on the home (Android does).  Forgetting the PC the home shows moves the home to
    the other one, with its own cards; forgetting the last one leaves « Aucun PC appairé ».  Nothing of a forgotten PC
    is left (state, cards), and the client lives on (it read the deleted PC before)."""
    atlas, cyn = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    _home_on(c, atlas.name)
    helpers.add_and_pair(c, cyn)
    _home_on(c, cyn.name)
    bench.shot("01-second-pc-shown")

    # Forget the PC the home shows: the home moves to the other one
    helpers.host_card_menu(c, cyn.name)
    helpers.click(c, "Oublier ce PC")
    helpers.wait_text(c, f"Oublier {cyn.name} ?")
    helpers.click(c, "Oublier")
    c.wait_for(lambda s: helpers.computer(s, cyn.name) is None, 15)
    helpers.wait_text(c, cyn.name, gone=True)
    _back_home(c)
    _home_on(c, atlas.name)
    assert not helpers.find(c, f"Sur {cyn.name}")
    bench.shot("02-forgot-shown-pc")

    # Forget the last one
    helpers.host_card_menu(c, atlas.name)
    helpers.click(c, "Oublier ce PC")
    helpers.wait_text(c, f"Oublier {atlas.name} ?")
    helpers.click(c, "Oublier")
    c.wait_for(lambda s: s["computers"] == [], 15)
    _back_home(c)
    helpers.wait_text(c, "Aucun PC appairé")
    helpers.wait_text(c, "Bureau", gone=True)
    bench.shot("03-none-left")
    time.sleep(4.0)  # a poll that was under way when the PC was forgotten comes back: nothing may use it
    assert c.proc.poll() is None, "the client died after forgetting a PC"
    assert c.state()["computers"] == []


def test_switched_off_pc_leaves_the_home(bench):
    """The PC the home shows switches off: its cards go, « Test-Atlas est hors ligne » with « Réveiller »; the wake
    packets cannot leave in the test build, which the toast says (Android's v2_wake_failed).  Back on: the cards
    come back."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    _home_on(c, atlas.name)

    bench.stop_host("atlas-test")
    helpers.wait_text(c, f"{atlas.name} est hors ligne", timeout=40)
    helpers.wait_text(c, "Bureau", gone=True)
    assert not helpers.find(c, f"Sur {atlas.name}"), "no card section for an offline PC"
    bench.shot("01-offline")

    helpers.click(c, "Réveiller")
    helpers.wait_text(c, f"Le réveil de {atlas.name} n'a pas pu être envoyé.")
    assert not helpers.find(c, f"Réveil envoyé à {atlas.name}")
    bench.shot("02-wake-failed")

    bench.restart_host("atlas-test")
    _home_on(c, atlas.name, timeout=40)
    bench.shot("03-back-online")


def test_menu_keeps_its_pc_while_the_list_moves(bench):
    """« Oublier Test-Atlas ? » open, then Test-Atlas renames itself Test-Zeta (its LavApollo restarted with another
    name): the PCs swap rows in the list.  « Oublier » forgets that PC, not the one now at its old row."""
    atlas, cyn = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.add_and_pair(c, cyn)
    helpers.host_card_menu(c, atlas.name)
    helpers.click(c, "Oublier ce PC")
    helpers.wait_text(c, f"Oublier {atlas.name} ?")
    bench.restart_host("atlas-test", {"name": "Test-Zeta"})
    helpers.wait_computer(c, "Test-Zeta", lambda x: x["online"], timeout=30)
    bench.shot("01-renamed-under-the-dialog")
    helpers.click(c, "Oublier")
    c.wait_for(lambda s: helpers.computer(s, "Test-Zeta") is None, 15)
    names = [x["name"] for x in c.state()["computers"]]
    assert names == [cyn.name], f"the dialog's PC must be forgotten, not the one at its old row: {names}"


def test_pairing_errors_and_one_pairing_at_a_time(bench):
    """A wrong PIN: « Le code entré sur Test-Atlas n'est pas le bon… ».  « C'est fait » only closes the dialog: the
    pairing goes on, and asking again shows the same dialog and code (one pairing at a time).  The PC stops during
    the pairing: a French message that names it, never the host's XML nor an English exception."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_by_address(c, "127.0.0.1:40989")
    pin = helpers.read_pin(c, atlas.name, timeout=15)
    wrong = "1111" if pin != "1111" else "2222"
    assert helpers.give_pin(atlas, wrong).get("status") is True
    text = _error_text(c)
    bench.shot("01-wrong-pin")
    assert text == f"Le code entré sur {atlas.name} n'est pas le bon. Recommence : un nouveau code s'affichera.", text
    _close_error(c)

    # Pair again from « Ajouter un PC »: a new code; « C'est fait » closes the dialog, the pairing goes on
    helpers.click(c, "Ajouter un PC", index=0)
    helpers.wait_text(c, f"{atlas.name} · 127.0.0.1:40989")
    time.sleep(0.4)  # the menu's opening animation
    helpers.click(c, f"{atlas.name} · 127.0.0.1:40989")
    pin2 = helpers.read_pin(c, atlas.name)
    helpers.click(c, "C'est fait : le code est entré")
    deadline = time.time() + 5
    while any("ce code :" in t for t in helpers.texts(c)):
        assert time.time() < deadline, "the pairing dialog did not close"
        time.sleep(0.2)
    helpers.click(c, "Ajouter un PC", index=0)
    helpers.wait_text(c, f"{atlas.name} · 127.0.0.1:40989")
    time.sleep(0.4)  # the menu's opening animation
    helpers.click(c, f"{atlas.name} · 127.0.0.1:40989")
    pin3 = helpers.read_pin(c, atlas.name)
    bench.shot("02-same-pairing")
    assert pin3 == pin2, f"asking again must show the pairing under way ({pin2}), not start another ({pin3})"

    # The PC stops while it waits for the code
    bench.stop_host("atlas-test")
    text = _error_text(c, timeout=30)
    bench.shot("03-host-stopped")
    assert atlas.name in text, text
    _assert_french(text)
    _close_error(c)
    assert not helpers.computer(c.state(), atlas.name)["paired"]


def test_add_by_address_forms(bench):
    """« Ajouter un PC » › Saisir une adresse…: an unreadable address is said at once (Android's v2_add_invalid);
    `localhost:<port>` reaches the PC; the same PC added again is « déjà dans la liste »."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    for bad in ("pc du salon!", "127.0.0.1/apps"):
        helpers.add_by_address(c, bad)
        text = _error_text(c, timeout=5)
        assert text == (f"« {bad} » n'est pas une adresse valide. Saisis une adresse IP ou un nom, avec le port s'il "
                        "le faut (192.168.1.24:47989)."), text
        _close_error(c)
    bench.shot("01-invalid")
    helpers.add_by_address(c, "localhost:40989")
    pin = helpers.read_pin(c, atlas.name, timeout=15)
    assert helpers.give_pin(atlas, pin).get("status") is True
    helpers.wait_computer(c, atlas.name, lambda x: x["paired"])
    helpers.add_by_address(c, "127.0.0.1:40989")
    helpers.wait_text(c, "Ce PC est déjà dans la liste")
    bench.shot("02-already-listed")
    assert len(c.state()["computers"]) == 1


def _stored_uuids(c):
    return [h["uuid"] for h in c.call("hoststore", action="list")["hosts"]]


def _wait_stored(c, uuid, present, timeout=15.0):
    deadline = time.time() + timeout
    while (uuid in _stored_uuids(c)) != present:
        assert time.time() < deadline, f"{uuid} {'never stored' if present else 'still stored'}: {_stored_uuids(c)}"
        time.sleep(0.3)


def test_forgotten_pc_stored_again_by_another_process(bench):
    """A PC forgotten here, then paired again by another LavArtemis process (an art:// link: there is no single
    instance), which stores it: this process takes it in at its next save instead of dropping it (review of waves
    1-4, #2: a forgotten uuid was kept for the whole process and dropped from the stored list at every save, so the
    other process's pairing was lost).  The other process is played by the channel's `hoststore`: it puts the PC's
    stored entry back, as that process's save would (the bench runs one client at a time)."""
    atlas, cyn = bench.up("atlas-test", "cynisca-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.add_and_pair(c, cyn)
    uuid = helpers.wait_computer(c, atlas.name, lambda x: x["paired"])["uuid"]
    _wait_stored(c, uuid, True)
    c.call("hoststore", action="keep", uuid=uuid)

    # Forgotten here: out of the stored list
    helpers.host_card_menu(c, atlas.name)
    helpers.click(c, "Oublier ce PC")
    helpers.wait_text(c, f"Oublier {atlas.name} ?")
    helpers.click(c, "Oublier")
    c.wait_for(lambda s: helpers.computer(s, atlas.name) is None, 15)
    _wait_stored(c, uuid, False)

    # Another process pairs it again and stores it; then this one saves (a rename of the other PC)
    c.call("hoststore", action="restore")
    helpers.host_card_menu(c, cyn.name)
    c.click("Renommer")
    helpers.wait_field_focus(c)
    c.call("key", key="A", modifiers="Ctrl", target="qt")
    c.call("key", key="Delete", target="qt")
    c.call("text", s="Cynisca renommé", target="qt")
    c.click("Renommer", index=-1)
    helpers.wait_computer(c, "Cynisca renommé")

    # Taken in: in the list here, still stored after that save, and there after a restart
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["paired"], timeout=20)
    assert comp["uuid"] == uuid, comp
    bench.shot("01-taken-in")
    time.sleep(2.0)  # the save that took it in is written
    assert uuid in _stored_uuids(c), f"dropped from the stored list: {_stored_uuids(c)}"
    c = bench.restart_client()
    helpers.wait_computer(c, atlas.name, lambda x: x["paired"], timeout=20)
