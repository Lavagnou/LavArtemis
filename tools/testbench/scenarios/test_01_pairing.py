"""Scenario 1: add a PC by its address and pair it (PIN read on the pairing screen, typed on the host by the
testbench as the user would in LavApollo's web UI), and pair it from an art:// link that carries a passphrase.
Neither pairing leaves its secrets in the client's log: the salt, the OTP hash and the challenges would give the PIN
(and a short passphrase) offline (review of waves 1-4, #5)."""
import pathlib
import re
import time

import helpers
import hosts

ADDRESS = "127.0.0.1:40989"
SECRET_ARGS = ("salt", "otpauth", "clientchallenge", "serverchallengeresp", "clientpairingsecret", "clientcert")


def assert_no_pairing_secrets(log_path, *secrets):
    """The client's log names the pairing arguments but never shows their values."""
    text = pathlib.Path(log_path).read_text(encoding="utf-8", errors="replace")
    assert "phrase=getservercert" in text, f"no pairing request in {log_path}: the check would prove nothing"
    for name in SECRET_ARGS:
        for m in re.finditer(name + r"=([^&\s\"]*)", text):
            assert m.group(1) in ("", "<hidden>"), f"{name}={m.group(1)[:16]}... in the client's log {log_path}"
    for secret in secrets:
        assert secret not in text, f"{secret!r} in the client's log {log_path}"


def test_pairing_add_by_address_opens_pairing(bench):
    """Mes PC › Ajouter un PC › Saisir une adresse… 127.0.0.1:40989 › Ajouter: the new PC's pairing dialog opens by
    itself with a PIN; the PIN given to the host pairs it (state.computers[0].paired) and the home shows the PC."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_by_address(c, ADDRESS)
    deadline = time.time() + 15
    shown = []
    while True:
        shown = helpers.texts(c)
        if any("ce code :" in t for t in shown):
            break
        if "Ce PC est déjà dans la liste" in shown or time.time() > deadline:
            bench.shot("01-no-pairing-dialog")
            raise AssertionError(f"adding a new PC must open its pairing dialog; texts {shown}, "
                                 f"state.computers {helpers.computer(c.state(), atlas.name)}")
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
    home on « Sur Test-Atlas ».  The PIN comes from the dialog that adding the PC by address opens."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    assert c.state()["computers"] == [], "a reset client knows no PC"
    helpers.add_by_address(c, ADDRESS)
    helpers.wait_computer(c, atlas.name, lambda x: x["online"])
    deadline = time.time() + 15
    while not any(t.startswith(f"Sur {atlas.name},") and "ce code :" in t for t in helpers.texts(c)):
        assert time.time() < deadline, f"adding must open the pairing dialog; texts {helpers.texts(c)}"
        time.sleep(0.2)
    pin = helpers.read_pin(c, atlas.name)
    assert len(pin) == 4 and pin.isdigit()
    bench.shot("02-pin")
    assert helpers.give_pin(atlas, pin).get("status") is True
    comp = helpers.wait_computer(c, atlas.name, lambda x: x["paired"])
    assert_no_pairing_secrets(c.log_path)
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
        time.sleep(0.3)
    else:
        bench.shot("03-not-paired")
        raise AssertionError(f"not paired by the link; texts: {helpers.texts(c)}")
    bench.shot("03-paired")
    assert_no_pairing_secrets(c.log_path, passphrase)
    # Never paired by a PIN: the PIN route was not used (the host got no pending pairing to fill)
    assert hosts.pin(atlas, "0000").get("status") is False
    # The CLI pairing ends on its confirmation; the normal app knows the PC afterwards
    bench.restart_client()
    comp = helpers.wait_computer(bench.client, atlas.name, lambda x: x["paired"] and x["online"])
    assert comp["address"] == ADDRESS


def test_pairing_art_link_already_paired(bench):
    """An art:// pairing link for a PC that is already paired: that is a success (Android), « Test-Atlas est
    appairé », and the PC opens on the home of the process the link started."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    passphrase = "lavtestbench"
    link = f"art://{ADDRESS}?pin={hosts.otp(atlas, passphrase)}&passphrase={passphrase}"
    c = bench.restart_client(args=[link])
    helpers.wait_text(c, f"{atlas.name} est appairé", timeout=30)
    bench.shot("04-already-paired")
    helpers.click(c, "OK")
    helpers.wait_page(c, "HomeView")
    helpers.wait_text(c, f"Sur {atlas.name}", timeout=20)
    bench.shot("05-home")


def test_pairing_art_link_expired_code(bench):
    """A link whose code the host no longer has (used, expired, or never made): a French sentence naming the PC,
    never the host's « OTP auth not available. » nor any English."""
    atlas, = bench.up("atlas-test")
    c = bench.start_client(reset=True, args=[f"art://{ADDRESS}?pin=1234&passphrase=lavtestbench"])
    msg = helpers.wait_any_text(c, ["n'est plus valable"], timeout=30)
    bench.shot("06-expired")
    assert msg == (f"Le code du lien n'est plus valable sur {atlas.name} : il ne sert qu'une fois et expire au bout "
                   "de 3 minutes. Crée un nouveau lien dans LavApollo."), msg
    # Not closed here: closing the message quits this pairing process, which the bench counts as a crash
