"""Scenario 4: Réglages.  Every page opened in normal mode, then in expert mode (a capture each); one setting
changed through the page (Audio › Canaux audio › 5.1) is still there after the client restarts."""
import time

import helpers

LIST_X_MAX = 330  # the page list is the left column


def list_flickable(c):
    return next(i for i in c.tree() if i["type"] == "Flickable" and i["x"] < LIST_X_MAX)


def visible_pages(c):
    fl = list_flickable(c)
    return [i for i in c.tree(textOnly=True)
            if i["type"] == "Text" and i["x"] < LIST_X_MAX and i["w"] > 200 and i["h"] >= 30
            and fl["y"] <= i["y"] and i["y"] + i["h"] <= fl["y"] + fl["h"] + 1]


def scroll_list(c, dy):
    fl = list_flickable(c)
    c.call("wheel", dy=dy, x=fl["x"] + fl["w"] // 2, y=fl["y"] + fl["h"] // 2, target="qt")
    time.sleep(0.6)  # the flick settles


def all_pages(c):
    """Every page of the list, top to bottom (the list scrolls when it is longer than the window)."""
    scroll_list(c, 20)
    names = []
    for _ in range(10):
        new = [i["text"] for i in visible_pages(c) if i["text"] not in names]
        if not new:
            break
        names += new
        scroll_list(c, -3)
    scroll_list(c, 20)
    return names


def open_page(c, name):
    scroll_list(c, 20)
    for _ in range(10):
        if any(i["text"] == name for i in visible_pages(c)):
            c.click(name, index=0)
            # the page's own title (right of the list) shows its name
            helpers.wait_any_text(c, [name])
            time.sleep(0.4)
            return
        scroll_list(c, -2)
    raise AssertionError(f"page {name!r} not in the list")


def open_settings(c):
    helpers.go_home(c)
    c.click("Réglages")
    helpers.wait_page(c, "SettingsView2")


def slug(name):
    import unicodedata
    ascii_name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return "".join(ch if ch.isalnum() else "-" for ch in ascii_name.lower())


def page_texts(c):
    """Labels shown on the right side (the open page)."""
    return {i["text"] for i in c.tree(textOnly=True) if i["x"] > LIST_X_MAX and i.get("text")}


def test_settings_pages_and_persistence(bench):
    c = bench.start_client()
    open_settings(c)
    assert c.call("setting", action="get", key="uiExpertMode")["value"] is False

    normal = all_pages(c)
    bench.note(f"normal pages: {normal}")
    assert normal[:3] == ["Profil", "Affichage", "Vidéo"] and "Audio" in normal and "PC" in normal, normal
    normal_texts = {}
    for name in normal:
        open_page(c, name)
        normal_texts[name] = page_texts(c)
        bench.shot(f"normal-{slug(name)}")

    scroll_list(c, 20)  # « Mode expert » heads the list
    c.click("Mode expert")
    time.sleep(0.5)
    assert c.call("setting", action="get", key="uiExpertMode")["value"] is True
    expert = all_pages(c)
    bench.note(f"expert pages: {expert}")
    assert set(normal) <= set(expert), (normal, expert)
    more = {}
    for name in expert:
        open_page(c, name)
        added = page_texts(c) - normal_texts.get(name, set())
        if added:
            more[name] = sorted(added)
        bench.shot(f"expert-{slug(name)}")
    bench.note(f"rows only in expert mode (visible part of each page): {more}")
    assert more, "expert mode shows no setting that normal mode hides"

    # One write through the page, read again after a restart
    assert c.call("setting", action="get", key="audioConfig")["value"] == 0
    open_page(c, "Audio")
    helpers.wait_text(c, "Canaux audio")
    c.click("5.1")
    deadline = time.time() + 5
    while c.call("setting", action="get", key="audioConfig")["value"] != 1:
        assert time.time() < deadline, "Canaux audio › 5.1 was not saved"
        time.sleep(0.2)
    bench.shot("write-audio-5-1")

    c = bench.restart_client()
    assert c.call("setting", action="get", key="audioConfig")["value"] == 1
    assert c.call("setting", action="get", key="uiExpertMode")["value"] is True
    open_settings(c)
    open_page(c, "Audio")
    helpers.wait_text(c, "Canaux audio")
    bench.shot("after-restart-audio")
