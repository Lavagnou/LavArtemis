"""What a user does in the test client, in a few words: open Mes PC, add a PC by address, pair it, read texts.

Every action goes through the command channel (injected events only); every wait polls the client's state or its
tree, never a fixed sleep longer than a UI animation."""
import math
import re
import time

import client as clientmod
import hosts

PIN_RE = re.compile(r"ce code : ((?:\d ?){4})")
# What the helpers read and did, for the scenario's notes (conftest writes it into the report)
TRACE = []


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


def click(c, text, timeout=5.0, **args):
    """c.click, waiting out a popup that is still closing (its modal overlay takes clicks until it is gone)."""
    deadline = time.time() + timeout
    while True:
        try:
            return c.click(text, **args)
        except clientmod.ClientError as e:
            if "modal popup is open" not in str(e) or time.time() > deadline:
                raise
            TRACE.append(f"{time.strftime('%H:%M:%S')} RETRY click {text!r}: a closing popup still covered it")
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
        try:
            click(c, "Mes PC")
        except clientmod.ClientError as e:
            raise AssertionError(f"cannot open Mes PC ({e}); page {st['page']}, texts {texts(c)[-12:]}")
        wait_page(c, "HostsView")


def go_home(c, timeout=15.0):
    """Escape until the home shows (bounded: a page that ignores Escape fails the scenario instead of hanging it)."""
    deadline = time.time() + timeout
    st = c.wait_idle()
    while st["page"] != "HomeView":
        assert time.time() < deadline, f"Escape does not lead home: page {st['page']}, texts {texts(c)[-12:]}"
        c.call("key", key="Escape", target="qt")
        time.sleep(0.4)
        st = c.wait_idle()


def open_add_menu(c):
    open_hosts(c)
    click(c, "Ajouter un PC", index=0)
    wait_text(c, "Saisir une adresse…")


def wait_field_focus(c, timeout=10.0):
    """Wait until a text field takes the keys (a dialog's initial focus comes after its opening animation)."""
    deadline = time.time() + timeout
    while not any(i["type"] == "TextInput" and i.get("focus") for i in c.tree()):
        if time.time() > deadline:
            raise AssertionError(f"no text field takes the keys; texts {texts(c)[-8:]}")
        time.sleep(0.2)
    time.sleep(0.3)


def add_by_address(c, address):
    """Mes PC › Ajouter un PC › Saisir une adresse… › type it › Ajouter."""
    open_add_menu(c)
    c.click("Saisir une adresse…")
    wait_field_focus(c)
    c.call("text", s=address, target="qt")
    wait_text(c, address)  # the field shows it
    c.click("Ajouter")


def read_pin(c, host_name, timeout=10.0):
    """The PIN of the pairing dialog of this PC (« Sur <PC>, ouvre LavApollo › Appairage et entre ce code : 1 2 3 4 »).
    Matched on the PC's name: the previous PC's dialog may still be fading out."""
    deadline = time.time() + timeout
    while True:
        for t in texts(c):
            m = PIN_RE.search(t)
            if m and t.startswith(f"Sur {host_name},"):
                TRACE.append(f"{time.strftime('%H:%M:%S')} PIN read for {host_name}: {t}")
                return m.group(1).replace(" ", "")
        if time.time() > deadline:
            raise AssertionError(f"no PIN on screen; texts: {texts(c)[:30]}")
        time.sleep(0.2)


def give_pin(host, pin, timeout=10.0):
    """Type the PIN on the host, as the user does in LavApollo's web UI after reading it: the client shows the PIN
    before its pairing request reaches the host, so the host may have nothing pending for a moment."""
    deadline = time.time() + timeout
    attempt = 0
    while True:
        attempt += 1
        r = hosts.pin(host, pin)
        TRACE.append(f"{time.strftime('%H:%M:%S')} {'RETRY ' if attempt > 1 else ''}PIN {pin} given to {host.name} "
                     f"(attempt {attempt}): {r}")
        if r.get("status") is True or time.time() > deadline:
            return r
        time.sleep(0.3)


def _wait_paired(c, host, timeout=12.0):
    """True once paired; False if the pairing failed (its « Oups » dialog) or did not finish in time."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        comp = computer(c.state(), host.name)
        if comp and comp["paired"]:
            return True
        if find(c, "Oups"):
            TRACE.append(f"{time.strftime('%H:%M:%S')} pairing {host.name} failed: {texts(c)[-3:]}")
            c.click("OK")
            wait_text(c, "Oups", gone=True)
            return False
        time.sleep(0.2)
    TRACE.append(f"{time.strftime('%H:%M:%S')} pairing {host.name} not finished after {timeout:.0f}s")
    return False


def add_and_pair(c, host):
    """Add a running test host by address and pair it (setup of the scenarios; scenario 1 tests pairing itself):
    adding opens the new PC's pairing dialog by itself, the PIN shown goes to the host, the PC must be paired.  No
    retry: the « PIN incorrect » for the right PIN seen before (about one pairing in 16) was a client bug, fixed
    (LavArtemis-Qt 53f946d2, the AES key cut at a zero byte of the hash)."""
    add_by_address(c, f"127.0.0.1:{host.port}")
    deadline = time.time() + 15
    paired = None
    while paired is None:
        shown = texts(c)
        if any(t.startswith(f"Sur {host.name},") and "ce code :" in t for t in shown):
            pin = read_pin(c, host.name)
            assert give_pin(host, pin).get("status") is True
            paired = _wait_paired(c, host)
        else:
            assert "Ce PC est déjà dans la liste" not in shown and time.time() < deadline, \
                f"adding {host.name} did not open its pairing dialog; texts {shown}"
            time.sleep(0.2)
    assert paired, f"{host.name} was not paired with the PIN it was given; trace {TRACE[-4:]}"
    return wait_computer(c, host.name, lambda comp: comp["paired"])


def wait_apps(c, name, timeout=20.0):
    return wait_computer(c, name, lambda comp: comp["apps"], timeout)["apps"]


def host_card_menu(c, name):
    """Right click on a PC's card in Mes PC (its menu: Ouvrir, Benchmark, Renommer, Oublier…)."""
    open_hosts(c)
    item = wait_text(c, name)[0]
    c.call("click", x=item["x"] + item["w"] // 2, y=item["y"] + item["h"] // 2, button="right")
    wait_text(c, "Renommer")


# ---------------------------------------------------------------- settings

SETTINGS_LIST_X_MAX = 330  # the page list is the left column of Réglages


def open_settings(c):
    go_home(c)
    c.click("Réglages")
    wait_page(c, "SettingsView2")


def _settings_list(c):
    return next(i for i in c.tree() if i["type"] == "Flickable" and i["x"] < SETTINGS_LIST_X_MAX)


def settings_pages_visible(c):
    fl = _settings_list(c)
    return [i for i in c.tree(textOnly=True)
            if i["type"] == "Text" and i["x"] < SETTINGS_LIST_X_MAX and i["w"] > 200 and i["h"] >= 30
            and fl["y"] <= i["y"] and i["y"] + i["h"] <= fl["y"] + fl["h"] + 1]


def scroll_settings_list(c, dy):
    fl = _settings_list(c)
    c.call("wheel", dy=dy, x=fl["x"] + fl["w"] // 2, y=fl["y"] + fl["h"] // 2, target="qt")
    time.sleep(0.6)  # the flick settles


# A row that only that page shows, at its top (normal mode): the proof that the page really opened
# (LavArtemis-Qt app/gui/v2/SettingsView2.qml)
SETTINGS_PAGE_ROW = {
    "Profil": "Profil actif",
    "Affichage": "Résolution",
    "Vidéo": "Débit vidéo",
    "Audio": "Canaux audio",
    "Entrées": "Souris pour le bureau à distance",
    "Manettes": "Configurer une manette",
    "Apparence": "Thème",
    "PC": "Quitter l'appli sur le PC distant à la fin",
    "Raccourcis clavier": "Menu en jeu",
    "Avancé": "Chercher les mises à jour au démarrage",
}


def open_settings_page(c, name):
    """Click the page in the list, then wait for the row that only this page shows (right of the list)."""
    row = SETTINGS_PAGE_ROW.get(name)
    assert row, f"no row known to prove that settings page {name!r} opened: add it to SETTINGS_PAGE_ROW"
    scroll_settings_list(c, 20)
    for _ in range(10):
        if any(i["text"] == name for i in settings_pages_visible(c)):
            c.click(name, index=0)
            deadline = time.time() + 10
            while not any(i["x"] > SETTINGS_LIST_X_MAX for i in find(c, row)):
                assert time.time() < deadline, f"page {name!r} did not open: no {row!r}; texts {texts(c)[-15:]}"
                time.sleep(0.2)
            time.sleep(0.4)
            return
        scroll_settings_list(c, -2)
    raise AssertionError(f"settings page {name!r} not in the list")


def set_switch(c, label, key, value):
    """Click a switch row of the open settings page (its label) until the setting `key` is `value`."""
    if c.call("setting", action="get", key=key)["value"] == value:
        return
    wait_settled(c, label)
    c.click(label)
    deadline = time.time() + 5
    while c.call("setting", action="get", key=key)["value"] != value:
        assert time.time() < deadline, f"{label!r} did not set {key} to {value}"
        time.sleep(0.2)


# ---------------------------------------------------------------- stream

def wait_settled(c, text, timeout=10.0):
    """Wait until the item showing `text` keeps the same place over two reads 0.3 s apart.  When the app list
    arrives the home's cards are created first and laid out at the next polish: a tree read in between gives them
    all the same corner (seen: every card and tile at 40,74), and a click computed from it lands on nothing."""
    deadline = time.time() + timeout
    last = None
    while True:
        items = find(c, text)
        rect = (items[0]["x"], items[0]["y"], items[0]["w"], items[0]["h"]) if items else None
        if rect is not None and rect == last:
            return items[0]
        if time.time() > deadline:
            raise AssertionError(f"{text!r} never settled (last {rect})")
        last = rect
        time.sleep(0.3)


def launch(c, title, timeout=40.0):
    """Click a card or a cover of the home, wait for the stream's SDL loop and a first decoded frame."""
    wait_settled(c, title)
    c.click(title)
    deadline = time.time() + timeout
    while True:
        st = c.state()
        sess = st["session"]
        if sess["streaming"] and sess["decoded"] > 0:
            return st
        if time.time() > deadline:
            raise AssertionError(f"no stream after {timeout:.0f}s: page {st['page']}, session {sess}, "
                                 f"texts {texts(c)[:10] if st['page'] else []}")
        time.sleep(0.3)


def host_display_fit(host_display, setting):
    """(width, height, fps) a client negotiates when it does not create the host's display (it joins a session, or
    launches without a virtual display): the « résolution calée » rule of both clients (HostDisplayFit, same
    reference values).  The host display's aspect, at the largest size inside both the setting and that display
    (never upscaled, rounded down to even), at the lower frame rate.  host_display: hosts.Host.display."""
    hw, hh, hr = host_display
    sw, sh, sf = setting
    scale = min(1.0, sw / hw, sh / hh)

    def even(v):
        return max(2, v & ~1)

    return even(math.floor(hw * scale)), even(math.floor(hh * scale)), min(sf, hr) if hr > 0 else sf


def quick_menu_window(c):
    for w in c.windows():
        if w.get("kind") == "qt" and w.get("role") == "quickmenu":
            return w["index"]
    return None


def open_quick_menu(c):
    """Ctrl+Alt+Shift+Backslash on the stream (the desktop's quick-menu combo); returns the menu's window index."""
    c.call("key", key="Backslash", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda s: s.get("quickMenu"), 10)
    deadline = time.time() + 10
    while True:
        idx = quick_menu_window(c)
        if idx is not None and find(c, "Menu rapide", window=idx):
            time.sleep(0.4)  # opening animation
            return idx
        assert time.time() < deadline, "the quick menu did not show"
        time.sleep(0.2)


def assert_quick_menu_footer_clear(c, window):
    """The in-game menu's footer button (« Fermer (Échap) » / « ← Retour ») is below the list, and no visible part of
    a list row lies under it (the tree gives the part of each item left visible by its clipping ancestors)."""
    items = c.tree(window=window)
    footer = max((i for i in items if i.get("text") in ("Fermer (Échap)", "← Retour")),
                 key=lambda i: i["w"] * i["h"], default=None)
    assert footer is not None, f"no footer button in the menu: {[i.get('text') for i in items if i.get('text')]}"
    lv = next((i for i in items if i["type"] == "ListView"), None)
    assert lv is not None, f"no ListView in the menu's tree: {sorted({i['type'] for i in items})}"
    assert lv["y"] + lv["h"] <= footer["y"] + 1, f"the list {lv} reaches under the footer {footer}"
    rows = [i for i in items if i.get("text") and i["type"] == "Text" and i is not footer
            and i["text"] not in ("Fermer (Échap)", "← Retour")
            and lv["x"] <= i["x"] < lv["x"] + lv["w"] and i["y"] >= lv["y"] - 1]
    assert rows, "no list row found"
    under = [i for i in rows if i["y"] < footer["y"] + footer["h"] and i["y"] + i["h"] > footer["y"]
             and i["x"] < footer["x"] + footer["w"] and i["x"] + i["w"] > footer["x"]]
    assert not under, f"rows drawn under the footer {footer}: {under}"
    TRACE.append(f"quick menu: list ends at y={lv['y'] + lv['h']}, footer at y={footer['y']}, {len(rows)} rows clear")


def quick_menu_click(c, text, window):
    c.call("click", text=text, window=window)
    time.sleep(0.4)


def host_events(host, stream, key=None):
    events = hosts.read_log(host, stream)
    return [e for e in events if key is None or key in e]


# ---------------------------------------------------------------- covers

def tiles(c):
    return [i for i in c.tree() if i["type"] == "CoverTile"]


def tile_names(c, shot_path, names, dpr):
    """Name of each cover tile, recognised by its generated colour (covers.py) sampled in a capture: the tile's
    picture has no text item in the tree.  Returns (name, colour distance, tree item) in reading order."""
    import covers
    from PIL import Image
    expected = {n: covers._colour(n) for n in names}
    out = []
    with Image.open(shot_path) as img:
        img = img.convert("RGB")
        for t in sorted(tiles(c), key=lambda i: (i["y"], i["x"])):
            x = int((t["x"] + t["w"] / 2) * dpr)
            y = int((t["y"] + t["h"] * 0.15) * dpr)
            rgb = img.getpixel((x, y))
            best = min(expected, key=lambda n: sum((a - e) ** 2 for a, e in zip(rgb, expected[n])))
            dist = sum((a - e) ** 2 for a, e in zip(rgb, expected[best])) ** 0.5
            out.append((best, round(dist), t))
    return out


def click_item(c, item, button="left"):
    c.call("click", x=item["x"] + item["w"] // 2, y=item["y"] + item["h"] // 2, button=button)


def popup_texts(c):
    """Texts inside the open popup menu (AppMenu), in tree order; [] when none is open."""
    items = c.tree()
    pop = next((i for i in items if i["type"] == "PopupItem" and i.get("objectName") == "AppMenu"), None)
    if pop is None:
        return []
    return [i["text"] for i in items if i.get("text") and i["type"] == "Text"
            and pop["x"] <= i["x"] < pop["x"] + pop["w"] and pop["y"] <= i["y"] < pop["y"] + pop["h"]]


def tile_by_name(c, name):
    """The cover tile of a game, found by opening each tile's menu (its title is the game's name) and closing it:
    works whatever the covers look like (dimmed while a session runs)."""
    for t in sorted(tiles(c), key=lambda i: (i["y"], i["x"])):
        click_item(c, t, "right")
        deadline = time.time() + 5
        shown = []
        while not shown and time.time() < deadline:
            time.sleep(0.2)
            shown = popup_texts(c)
        c.call("key", key="Escape", target="qt")
        deadline = time.time() + 5
        while popup_texts(c) and time.time() < deadline:
            time.sleep(0.2)
        if shown and shown[0] == name:
            return t
    raise AssertionError(f"no cover tile for {name!r}")
