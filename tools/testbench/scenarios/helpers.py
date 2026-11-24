"""What a user does in the test client, in a few words: open Mes PC, add a PC by address, pair it, read texts.

Every action goes through the command channel (injected events only); every wait polls the client's state or its
tree, never a fixed sleep longer than a UI animation."""
import re
import time

import client as clientmod
import hosts

PIN_RE = re.compile(r"ce code : ((?:\d ?){4})")


def texts(c, window=None):
    args = {"textOnly": True}
    if window is not None:
        args["window"] = window
    return [i.get("text", "") for i in c.tree(**args) if i.get("text")]


def find(c, text, contains=False, window=None):
    """Items whose text is `text` (or contains it)."""
    args = {"textOnly": True}
    if window is not None:
        args["window"] = window
    return [i for i in c.tree(**args) if i.get("text") and
            ((text in i["text"]) if contains else i["text"] == text)]


def wait_text(c, text, contains=False, timeout=15.0, gone=False):
    """Wait until an item shows `text` (or no longer shows it with gone=True); returns the matching items."""
    deadline = time.time() + timeout
    while True:
        items = find(c, text, contains)
        if bool(items) != gone:
            return items
        if time.time() > deadline:
            raise AssertionError(f"{'still' if gone else 'never'} shown: {text!r}; texts: {texts(c)[:40]}")
        time.sleep(0.2)


def wait_any_text(c, candidates, timeout=15.0):
    deadline = time.time() + timeout
    while True:
        shown = texts(c)
        for t in shown:
            for cand in candidates:
                if cand in t:
                    return t
        if time.time() > deadline:
            raise AssertionError(f"none of {candidates} shown; texts: {shown[:40]}")
        time.sleep(0.2)


def wait_page(c, page, timeout=15.0):
    return c.wait_for(lambda s: s.get("page") == page and not s.get("pageBusy"), timeout)


def computer(state, name):
    for comp in state.get("computers", []):
        if comp["name"] == name:
            return comp
    return None


def wait_computer(c, name, predicate=lambda comp: True, timeout=20.0):
    st = c.wait_for(lambda s: computer(s, name) is not None and predicate(computer(s, name)), timeout)
    return computer(st, name)


def open_hosts(c):
    """Mes PC, from wherever the app is (home: its pill)."""
    st = c.wait_idle()
    if st["page"] != "HostsView":
        c.click("Mes PC")
        wait_page(c, "HostsView")


def go_home(c):
    st = c.wait_idle()
    while st["page"] != "HomeView":
        c.call("key", key="Escape", target="qt")
        time.sleep(0.4)
        st = c.wait_idle()


def open_add_menu(c):
    open_hosts(c)
    c.click("Ajouter un PC", index=0)
    wait_text(c, "Saisir une adresse…")


def add_by_address(c, address):
    """Mes PC › Ajouter un PC › Saisir une adresse… › type it › Ajouter."""
    open_add_menu(c)
    c.click("Saisir une adresse…")
    deadline = time.time() + 10
    while not any(i["type"] == "TextInput" and i.get("focus") for i in c.tree()):
        if time.time() > deadline:
            raise AssertionError("the address field never took the keys")
        time.sleep(0.2)
    c.call("text", s=address, target="qt")
    wait_text(c, address)  # the field shows it
    c.click("Ajouter")


def read_pin(c, timeout=10.0):
    deadline = time.time() + timeout
    while True:
        for t in texts(c):
            m = PIN_RE.search(t)
            if m:
                return m.group(1).replace(" ", "")
        if time.time() > deadline:
            raise AssertionError(f"no PIN on screen; texts: {texts(c)[:30]}")
        time.sleep(0.2)


def give_pin(host, pin, timeout=10.0):
    """Type the PIN on the host, as the user does in LavApollo's web UI after reading it: the client shows the PIN
    before its pairing request reaches the host, so the host may have nothing pending for a moment."""
    deadline = time.time() + timeout
    while True:
        r = hosts.pin(host, pin)
        if r.get("status") is True or time.time() > deadline:
            return r
        time.sleep(0.3)


def pair_from_add_menu(c, host):
    """Mes PC › Ajouter un PC › <name · address> (an online PC not paired yet) › the PIN shown goes to the host,
    as the user types it in LavApollo's web UI."""
    open_add_menu(c)
    c.click(f"{host.name} · 127.0.0.1:{host.port}")
    pin = read_pin(c)
    r = give_pin(host, pin)
    assert r.get("status") is True, f"the host refused the PIN {pin}: {r}"
    return wait_computer(c, host.name, lambda comp: comp["paired"])


def add_and_pair(c, host):
    """Add a running test host by address and pair it (through the add menu: see scenario 1 for the direct path)."""
    add_by_address(c, f"127.0.0.1:{host.port}")
    wait_computer(c, host.name, lambda comp: comp["online"])
    st = c.wait_idle()
    if any("ce code :" in t for t in texts(c)):  # the pairing dialog opened by itself
        pin = read_pin(c)
        assert give_pin(host, pin).get("status") is True
        return wait_computer(c, host.name, lambda comp: comp["paired"])
    time.sleep(0.5)
    return pair_from_add_menu(c, host)


def wait_apps(c, name, timeout=20.0):
    return wait_computer(c, name, lambda comp: comp["apps"], timeout)["apps"]


def host_card_menu(c, name):
    """Right click on a PC's card in Mes PC (its menu: Ouvrir, Benchmark, Renommer, Oublier…)."""
    open_hosts(c)
    item = wait_text(c, name)[0]
    c.call("click", x=item["x"] + item["w"] // 2, y=item["y"] + item["h"] // 2, button="right")
    wait_text(c, "Renommer")


def menu_entry(c, label):
    c.click(label)


def no_client_left():
    return not clientmod.tasklist_pids()
