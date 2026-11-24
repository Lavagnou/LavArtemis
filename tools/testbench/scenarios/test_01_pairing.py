"""Scenario 1: add a PC by its address and pair it (PIN read on the pairing screen, typed on the host by the
testbench as the user would in LavApollo's web UI), and pair it from an art:// link that carries a passphrase."""
import time

import pytest

import bugs
import helpers
import hosts

ADDRESS = "127.0.0.1:40989"


def test_pairing_add_by_address_opens_pairing(bench):
    """Mes PC › Ajouter un PC › Saisir une adresse… 127.0.0.1:40989 › Ajouter: the new PC's pairing dialog opens by
    itself with a PIN; the PIN given to the host pairs it (state.computers[0].paired) and the home shows the PC."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_by_address(c, ADDRESS)
    deadline = time.time() + 15
    shown = []
    while time.time() < deadline:
        shown = helpers.texts(c)
        if any("ce code :" in t for t in shown):
            break
        if "Ce PC est déjà dans la liste" in shown:
            bench.shot("01-already-listed")
            comp = helpers.computer(c.state(), atlas.name)
            bench.note(f"after Ajouter: texts {shown}; state.computers: {comp}")
            assert comp is not None and not comp["paired"], "the PC should be listed, not paired yet"
            pytest.xfail(bugs.ADD_ALREADY_LISTED)
        time.sleep(0.2)
    bench.shot("01-pairing-dialog")
    pin = helpers.read_pin(c, atlas.name)
    assert helpers.give_pin(atlas, pin).get("status") is True
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["paired"])
    assert comp["address"] == ADDRESS and comp["testHost"]
    helpers.wait_page(c, "HomeView")
    helpers.wait_text(c, f"Sur {atlas.name}")
    bench.shot("01-home")


def test_pairing_with_pin(bench):
    """The PIN shown by the client, typed on the host, pairs the PC: state.computers[0].paired, its apps listed, the
    home on « Sur Test-Atlas ».  The PIN comes from the dialog that adding opens, or, when that dialog does not open
    (bugs.ADD_ALREADY_LISTED), from « Ajouter un PC » › « Test-Atlas · 127.0.0.1:40989 » (the PC answers there)."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    assert c.state()["computers"] == [], "a reset client knows no PC"
    helpers.add_by_address(c, ADDRESS)
    helpers.wait_computer(c, atlas.name, lambda x: x["online"])
    deadline = time.time() + 8
    via_menu = False
    while True:
        shown = helpers.texts(c)
        if any(t.startswith(f"Sur {atlas.name},") and "ce code :" in t for t in shown):
            break
        if "Ce PC est déjà dans la liste" in shown or time.time() > deadline:
            via_menu = True
            helpers.open_add_menu(c)
            bench.shot("02-add-menu")
            entry = f"{atlas.name} · {ADDRESS}"
            assert helpers.find(c, entry), f"the add menu should list {entry}"
            c.click(entry)
            break
        time.sleep(0.2)
    bench.note(f"PIN dialog {'from the add menu' if via_menu else 'opened by the add'}")
    pin = helpers.read_pin(c, atlas.name)
    assert len(pin) == 4 and pin.isdigit()
    bench.shot("02-pin")
    assert helpers.give_pin(atlas, pin).get("status") is True
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["paired"])
    assert comp["state"] == "online" and comp["testHost"] and not comp["thisPc"]
    apps = helpers.wait_apps(c, atlas.name)
    assert "Bureau" in apps and "Playnite Fullscreen" in apps
    helpers.wait_page(c, "HomeView")
    helpers.wait_text(c, f"Sur {atlas.name}")
    bench.shot("02-home")


def test_pairing_art_link_with_passphrase(bench):
    """art://127.0.0.1:40989?pin=<OTP>&passphrase=<p>, given to the client the way a link handler would (command line):
    paired without any PIN typed on the host (the host made the OTP for that passphrase)."""
    atlas, = bench.up("atlas-test")
    passphrase = "lavtestbench"
    otp = hosts.otp(atlas, passphrase)
    link = f"art://{ADDRESS}?pin={otp}&passphrase={passphrase}"
    c = bench.start_client(reset=True, args=[link])
    deadline = time.time() + 30
    while time.time() < deadline:
        comp = helpers.computer(c.state(), atlas.name)
        if comp and comp["paired"]:
            break
        if bugs.toolbar_bug_seen(c.log_text()):
            bench.shot("03-cli-pair-stuck")
            pytest.xfail(bugs.TOOLBAR)
        time.sleep(0.3)
    else:
        bench.shot("03-not-paired")
        raise AssertionError(f"not paired by the link; texts: {helpers.texts(c)}")
    bench.shot("03-paired")
    # Never paired by a PIN: the PIN route was not used (the host got no pending pairing to fill)
    assert hosts.pin(atlas, "0000").get("status") is False
    # The CLI pairing ends on its confirmation; the normal app knows the PC afterwards
    bench.restart_client()
    comp = helpers.wait_computer(bench.client, atlas.name, lambda x: x["paired"] and x["online"])
    assert comp["address"] == ADDRESS
