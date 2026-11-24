"""Scenario 3: Mes PC.  A paired PC switched off shows « Hors ligne » / « Éteint… »; a PC whose LavApollo is too old
says so when opened (« La version du logiciel hôte … (2.1.0.0) »); renaming a PC; forgetting one."""
import time

import helpers
import hosts

NEW_NAME = "Atlas du salon"


def test_hosts_offline_old_version_rename_forget(bench):
    """Three paired PCs.  Test-Eteint is then switched off; Test-Ancien restarts announcing LavApollo 2.1.0.0
    (generation 2).  It is paired first under its real version: the client pairs a generation-2 host with the old
    SHA-1 handshake, which a current LavApollo pretending to be old cannot answer (the real old host could)."""
    atlas, old, off = bench.up("atlas-test", "old-version-test", "offline-test", force_instances=("offline-test",),
                               overrides={"old-version-test": {"appversion": None}})
    c = bench.start_client()
    for h in (atlas, old, off):
        helpers.add_and_pair(c, h)
    hosts.restart(old, bench.workdir, {})  # pc.json again: appversion 2.1.0.0
    bench.watch.ours.add(old.pid)
    helpers.wait_computer(c, old.name, lambda x: x["online"], timeout=30)
    helpers.open_hosts(c)
    for h in (atlas, old, off):
        helpers.wait_text(c, h.name)
    bench.shot("01-three-paired")

    # The PC is switched off: its card says so
    hosts.stop(off, bench.workdir)
    helpers.wait_computer(c, off.name, lambda x: not x["online"], timeout=40)
    helpers.open_hosts(c)
    helpers.wait_text(c, "Hors ligne")
    detail = helpers.wait_any_text(c, ["Éteint ou injoignable.", "Éteint ou en veille"])
    assert detail.startswith("Éteint")
    bench.shot("02-offline")

    # The old LavApollo: opening it is refused with its version
    c.click(old.name)
    msg = helpers.wait_any_text(c, ["La version du logiciel hôte"])
    assert msg == (f"La version du logiciel hôte de {old.name} n'est pas prise en charge (2.1.0.0). "
                   "Mets à jour LavApollo sur ce PC."), msg
    helpers.wait_text(c, "Oups")
    bench.shot("03-old-version")
    c.click("OK")
    helpers.wait_text(c, "Oups", gone=True)
    assert c.state()["page"] == "HostsView", "a refused PC must not open"

    # Rename (the PC keeps its own name; the client shows ours)
    helpers.host_card_menu(c, atlas.name)
    bench.shot("04-menu")
    c.click("Renommer")
    helpers.wait_text(c, "Le nom de ce PC dans LavArtemis. Le PC lui-même garde le sien.")
    c.call("key", key="A", modifiers="Ctrl", target="qt")
    c.call("key", key="Delete", target="qt")
    c.call("text", s=NEW_NAME, target="qt")
    helpers.wait_text(c, NEW_NAME)
    bench.shot("05-rename")
    c.click("Renommer", index=-1)
    helpers.wait_computer(c, NEW_NAME)
    helpers.wait_text(c, NEW_NAME)
    st = c.state()
    assert helpers.computer(st, atlas.name) is None
    bench.shot("06-renamed")

    # Forget the old one: it disappears
    helpers.host_card_menu(c, old.name)
    c.click("Oublier ce PC")
    helpers.wait_text(c, f"Oublier {old.name} ?")
    bench.shot("07-forget")
    c.click("Oublier")
    deadline = time.time() + 15
    while helpers.computer(c.state(), old.name) is not None:
        assert time.time() < deadline, "the forgotten PC is still known"
        time.sleep(0.3)
    helpers.wait_text(c, old.name, gone=True)
    names = sorted(x["name"] for x in c.state()["computers"])
    assert names == sorted([NEW_NAME, off.name]), names
    bench.shot("08-forgotten")
