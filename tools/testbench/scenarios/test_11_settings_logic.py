"""Scenario 11: Réglages, the logic behind the rows (findings-settings of the 2026-11-25 verification pass).

S1 profiles: a new profile is a snapshot of the profile in use (Android), each profile keeps its own values, switching
   and deleting go back to the right ones, all of it after a restart.
S2 bitrate: the bitrate follows the resolution and the frame rate while the user has not moved it (« Suit la
   résolution et les images/s tant que tu ne le changes pas »), « Rétablir » makes it follow again, and leaving PyroWave
   for a codec whose track stops at 800 Mbit/s brings a PyroWave bitrate back onto that track.
S3 custom resolution and frame rate (« Personnalisée… », « Autre… »), kept after a restart.
S4 rows that depend on another one are greyed out and do nothing while it is off.
S5 the settings removed by the triage are forced back to their default at startup.
S6 « Vérifier » (updates): every check ends (twice in a row); a failed check says why in French.
S7 appearance and language: « Clair ou sombre » kept after a restart; the language list covers every language the
   app can store.
S8 every switch of every page (expert mode) flipped through the UI, read again after a restart.

Rows are found by their objectName `set:<key>` (SettingsView2.qml); `tree` gives each one's `row` (label, checked,
value, valueText).  `setting raw` reads what is stored, globally and in each profile, without the fall-through."""
import time

import helpers

LIST_X_MAX = helpers.SETTINGS_LIST_X_MAX
VCC_FORCE_HEVC = 2  # StreamingPreferences::VideoCodecConfig
TOUCH = {"LAV_TEST_TOUCHSCREEN": "1"}  # a fake touchscreen (LavArtemis-Qt systemproperties.cpp, test build)


# ---------------------------------------------------------------- helpers

def get(c, key):
    return c.call("setting", action="get", key=key)["value"]


def put(c, key, value):
    return c.call("setting", action="set", key=key, value=value)["value"]


def raw(c, key):
    return c.call("setting", action="raw", key=key)


def wait_value(c, key, predicate, what, timeout=5.0):
    deadline = time.time() + timeout
    while True:
        v = get(c, key)
        if predicate(v):
            return v
        assert time.time() < deadline, f"{what}: {key} is {v!r}"
        time.sleep(0.2)


def rows_flick(c):
    return next(i for i in c.tree() if i["type"] == "Flickable" and i["x"] > LIST_X_MAX)


def scroll_rows(c, dy):
    """Wheel over the open page, then wait until it stops moving: a Flickable still moving takes the next press to
    stop itself, and the row under it never sees that click."""
    fl = rows_flick(c)
    c.call("wheel", dy=dy, x=fl["x"] + fl["w"] // 2, y=fl["y"] + fl["h"] // 2, target="qt")
    time.sleep(0.3)
    deadline = time.time() + 5
    while rows_flick(c).get("moving"):
        assert time.time() < deadline, "the settings page keeps moving after a wheel"
        time.sleep(0.2)


def rows(c):
    """The rows of the open page now on screen: {key: item} (item["row"] = label, checked, value...)."""
    fl = rows_flick(c)
    out = {}
    for i in c.tree():
        name = i.get("objectName", "")
        if name.startswith("set:") and i["x"] > LIST_X_MAX and i["y"] >= fl["y"] - 1 \
                and i["y"] + i["h"] <= fl["y"] + fl["h"] + 1:
            out[name[4:]] = i
    return out


def row(c, key):
    """Scroll the open page until the row `key` is entirely on screen; returns its tree item."""
    scroll_rows(c, 30)
    seen = None
    for _ in range(25):
        found = rows(c).get(key)
        if found and found["h"] >= 40:
            return found
        now = sorted(rows(c))
        if now == seen and _ > 0:
            break
        seen = now
        scroll_rows(c, -2)
    raise AssertionError(f"no row set:{key} on this page; rows {sorted(rows(c))}")


def anywhere(c, key):
    """The row `key` wherever it is in the tree (partly scrolled out included), or None."""
    return next((i for i in c.tree() if i.get("objectName") == "set:" + key), None)


def list_texts(c, lv):
    """Texts of an open dropdown list: after the ListView in the tree (the popup is drawn over the page, whose
    texts share its rectangle) and inside it."""
    items = c.tree()
    at = next(n for n, i in enumerate(items) if i["type"] == "ListView" and
              (i["x"], i["y"], i["w"], i["h"]) == (lv["x"], lv["y"], lv["w"], lv["h"]))
    return [i for i in items[at + 1:] if i.get("text") and i["type"] == "Text" and inside(i, lv)]


def inside(item, box):
    return box["x"] <= item["x"] and item["x"] + item["w"] <= box["x"] + box["w"] + 1 and \
        box["y"] <= item["y"] and item["y"] + item["h"] <= box["y"] + box["h"] + 1


def choose_segment(c, key, text):
    """Click the segment `text` of a segmented choice row."""
    r = row(c, key)
    seg = [i for i in c.tree(textOnly=True) if i.get("text") == text and inside(i, r)]
    assert seg, f"no segment {text!r} in set:{key}"
    helpers.click_item(c, seg[0])


def open_list(c, key):
    """Open the dropdown of a choice row; returns its list (the ListView that appeared)."""
    r = row(c, key)
    dd = [i for i in c.tree() if i["type"] == "LavDropdown" and inside(i, r)]
    assert dd, f"set:{key} has no dropdown"
    before = {(i["x"], i["y"], i["w"], i["h"]) for i in c.tree() if i["type"] == "ListView"}
    helpers.click_item(c, dd[0])
    deadline = time.time() + 5
    while True:
        new = [i for i in c.tree() if i["type"] == "ListView" and (i["x"], i["y"], i["w"], i["h"]) not in before]
        if new:
            break
        assert time.time() < deadline, f"the list of set:{key} did not open"
        time.sleep(0.2)
    time.sleep(0.5)  # opening animation
    return [i for i in c.tree() if i["type"] == "ListView" and (i["x"], i["y"], i["w"], i["h"]) not in before][-1]


def choose_dropdown(c, key, text):
    """Open the dropdown of a choice row and pick `text` in its list."""
    pop = open_list(c, key)
    for _ in range(15):
        hits = [i for i in list_texts(c, pop) if i["text"] == text]
        if hits:
            helpers.click_item(c, hits[0])
            helpers.wait_text(c, text, gone=False)
            time.sleep(0.5)
            return
        c.call("wheel", dy=-2, x=pop["x"] + pop["w"] // 2, y=pop["y"] + pop["h"] // 2, target="qt")
        time.sleep(0.4)
    raise AssertionError(f"no {text!r} in the list of set:{key}")


def choose(c, key, text):
    """Pick `text` in a choice row, segmented or dropdown."""
    r = row(c, key)
    if [i for i in c.tree() if i["type"] == "LavDropdown" and inside(i, r)]:
        choose_dropdown(c, key, text)
    else:
        choose_segment(c, key, text)


def dropdown_texts(c, key):
    """Every entry of a dropdown row's list (scrolled through), then the list closed."""
    pop = open_list(c, key)
    seen = []
    for _ in range(20):
        new = [i["text"] for i in list_texts(c, pop) if i["text"] not in seen]
        if not new and seen:
            break
        seen += new
        c.call("wheel", dy=-2, x=pop["x"] + pop["w"] // 2, y=pop["y"] + pop["h"] // 2, target="qt")
        time.sleep(0.4)
    c.call("key", key="Escape", target="qt")
    time.sleep(0.5)
    return seen


def slide_to(c, key, fraction):
    """Press the slider of row `key` at `fraction` of its groove."""
    r = row(c, key)
    groove = [i for i in c.tree() if i.get("objectName") == "sliderGroove" and inside(i, r)]
    assert groove, f"set:{key} has no slider"
    g = groove[0]
    c.call("click", x=round(g["x"] + fraction * g["w"]), y=g["y"] + g["h"] // 2)
    time.sleep(0.4)


def profile_dialog(c, name):
    """« Nouveau » / « Renommer » open the name dialog: type the name, confirm."""
    helpers.wait_field_focus(c)
    c.call("text", s=name, target="qt")
    helpers.wait_text(c, name)
    helpers.click(c, "Créer")


def new_profile(c, name):
    before = {p["id"] for p in c.state()["profiles"]}
    helpers.open_settings_page(c, "Profil")
    helpers.click(c, "Nouveau")
    profile_dialog(c, name)
    st = c.wait_for(lambda s: any(p["name"] == name and p["active"] and p["id"] not in before for p in s["profiles"]),
                    timeout=10)
    return st["activeProfileId"]


def stored(raw_value, profile_id):
    """The value a profile holds for a raw key (None when it holds none)."""
    for p in raw_value["profiles"]:
        if p["id"] == profile_id:
            return p["value"] if p["stored"] else None
    raise AssertionError(f"no profile {profile_id} in {raw_value}")


# ---------------------------------------------------------------- S1 profiles

def test_s1_profiles(bench):
    c = bench.start_client()
    helpers.open_settings(c)
    assert c.state()["profiles"] == []

    # The global settings: 30 i/s
    put(c, "fps", 30)
    assert raw(c, "fps")["global"] == 30

    # « Maison », made from the global settings, then 120 i/s in it
    maison = new_profile(c, "Maison")
    assert get(c, "fps") == 30, "a profile made from the global settings starts with them"
    put(c, "fps", 120)
    r = raw(c, "fps")
    assert r["global"] == 30 and stored(r, maison) == 120, r

    # « Copie », made while « Maison » is in use: a snapshot of Maison, not of the global settings
    copie = new_profile(c, "Copie")
    bench.shot("s1-copie-created")
    assert get(c, "fps") == 120, \
        f"a new profile must start from the profile in use (Maison, 120 i/s), not the global settings: {raw(c, 'fps')}"
    r = raw(c, "fps")
    assert stored(r, copie) == 120 and stored(r, maison) == 120 and r["global"] == 30, r
    put(c, "fps", 90)

    # Switching through the « Profil actif » list reads each one's values
    choose_dropdown(c, "activeProfile", "Réglages globaux (aucun profil)")
    c.wait_for(lambda s: s["activeProfileId"] == "", timeout=5)
    assert get(c, "fps") == 30
    choose_dropdown(c, "activeProfile", "Maison")
    c.wait_for(lambda s: s["activeProfileId"] == maison, timeout=5)
    assert get(c, "fps") == 120

    # After a restart: the same profiles, the same one in use, the same values
    c = bench.restart_client()
    st = c.state()
    assert [p["name"] for p in st["profiles"]] == ["Maison", "Copie"] and st["activeProfileId"] == maison, st["profiles"]
    assert get(c, "fps") == 120
    r = raw(c, "fps")
    assert r["global"] == 30 and stored(r, maison) == 120 and stored(r, copie) == 90, r

    # Deleting the profile in use goes back to the global settings
    helpers.open_settings(c)
    choose_dropdown(c, "activeProfile", "Copie")
    c.wait_for(lambda s: s["activeProfileId"] == copie, timeout=5)
    assert get(c, "fps") == 90
    helpers.click(c, "Supprimer")
    helpers.wait_text(c, "Supprimer le profil", contains=True)
    helpers.click(c, "Supprimer", index=-1)
    st = c.wait_for(lambda s: [p["name"] for p in s["profiles"]] == ["Maison"], timeout=5)
    assert st["activeProfileId"] == "" and get(c, "fps") == 30, st


# ---------------------------------------------------------------- S2 bitrate

def test_s2_bitrate(bench):
    c = bench.start_client()
    helpers.open_settings(c)
    put(c, "fps", 60)
    start = get(c, "bitrateKbps")
    assert get(c, "autoAdjustBitrate") is True

    # Auto: the bitrate follows the frame rate, both ways
    helpers.open_settings_page(c, "Affichage")
    choose_segment(c, "fps", "30")
    low = wait_value(c, "bitrateKbps", lambda v: v < start, "60 -> 30 i/s must lower an automatic bitrate")
    choose_segment(c, "fps", "60")
    wait_value(c, "bitrateKbps", lambda v: v == start, "30 -> 60 i/s must give the 60 i/s bitrate back")

    # Moved by hand: kept when the frame rate changes
    helpers.open_settings_page(c, "Vidéo")
    slide_to(c, "bitrateKbps", 0.5)
    mine = wait_value(c, "bitrateKbps", lambda v: v != start, "the slider did not move the bitrate")
    assert get(c, "autoAdjustBitrate") is False
    assert raw(c, "autoadjustbitrate")["global"] in (False, "false"), raw(c, "autoadjustbitrate")
    bench.shot("s2-moved")
    helpers.open_settings_page(c, "Affichage")
    choose_segment(c, "fps", "30")
    wait_value(c, "fps", lambda v: v == 30, "30 i/s")
    time.sleep(0.5)
    assert get(c, "bitrateKbps") == mine, "a bitrate moved by hand must stay when the frame rate changes"

    # « Rétablir »: the recommended bitrate for 30 i/s, and it follows again
    helpers.open_settings_page(c, "Vidéo")
    helpers.click(c, "Rétablir")
    wait_value(c, "bitrateKbps", lambda v: v == low, "« Rétablir » must give the 30 i/s bitrate")
    assert get(c, "autoAdjustBitrate") is True
    helpers.open_settings_page(c, "Affichage")
    choose_segment(c, "fps", "60")
    wait_value(c, "bitrateKbps", lambda v: v == start, "after « Rétablir » the bitrate follows the frame rate again")

    # Leaving PyroWave for HEVC: a PyroWave bitrate (1,5 Gbit/s) set by hand comes back onto HEVC's track (800 Mbit/s)
    put(c, "videoCodecConfig", "VCC_FORCE_PYROWAVE")
    put(c, "autoAdjustBitrate", False)
    put(c, "bitrateKbps", 1500000)
    helpers.open_settings_page(c, "Vidéo")
    choose(c, "videoCodecConfig", "HEVC")
    wait_value(c, "videoCodecConfig", lambda v: v == VCC_FORCE_HEVC, "HEVC chosen")
    bench.shot("s2-left-pyrowave")
    assert get(c, "bitrateKbps") == 800000, \
        f"leaving PyroWave must bring 1,5 Gbit/s onto HEVC's track (800 Mbit/s): {get(c, 'bitrateKbps')}"
    assert get(c, "autoAdjustBitrate") is False


# ---------------------------------------------------------------- S3 custom resolution and frame rate

def type_in_dialog(c, *values):
    """Type into the fields of the open dialog, Tab between them."""
    helpers.wait_field_focus(c)
    for n, v in enumerate(values):
        if n:
            c.call("key", key="Tab", target="qt")
            time.sleep(0.3)
        c.call("text", s=v, target="qt")
        helpers.wait_text(c, v)


def test_s3_custom_resolution_and_frame_rate(bench):
    c = bench.start_client()
    helpers.open_settings(c)
    before = get(c, "bitrateKbps")
    helpers.open_settings_page(c, "Affichage")

    choose(c, "resolution", "Personnalisée…")
    helpers.wait_text(c, "Résolution personnalisée")
    type_in_dialog(c, "1600", "900")
    helpers.click(c, "Appliquer")
    wait_value(c, "width", lambda v: v == 1600, "« Personnalisée… » 1600 × 900")
    assert get(c, "height") == 900
    assert get(c, "bitrateKbps") != before, "an automatic bitrate follows a custom resolution"

    choose(c, "fps", "Autre…")
    helpers.wait_text(c, "Images par seconde personnalisées")
    type_in_dialog(c, "59,94")
    helpers.click(c, "Appliquer")
    wait_value(c, "enableFractionalRefreshRate", lambda v: v is True, "« Autre… » 59,94")
    assert abs(get(c, "customRefreshRate") - 59.94) < 0.001 and get(c, "fps") == 60
    bench.shot("s3-custom")

    c = bench.restart_client()
    assert (get(c, "width"), get(c, "height")) == (1600, 900)
    assert get(c, "enableFractionalRefreshRate") is True and abs(get(c, "customRefreshRate") - 59.94) < 0.001


# ---------------------------------------------------------------- S4 rows that depend on another

def assert_inert(c, key, setting, what):
    """The row is greyed out, and clicking it changes nothing."""
    r = row(c, key)
    assert r["enabled"] is False, f"set:{key} must be greyed out {what}"
    before = get(c, setting)
    helpers.click_item(c, r)
    time.sleep(0.6)
    assert get(c, setting) == before, f"a greyed-out set:{key} changed {setting} {what}"


def click_toggle(c, key, setting, value):
    r = row(c, key)
    if get(c, setting) != value:
        helpers.click_item(c, r)
        wait_value(c, setting, lambda v: v == value, f"set:{key}")


def test_s4_greyed_out_dependencies(bench):
    c = bench.start_client()
    put(c, "uiExpertMode", True)
    helpers.open_settings(c)

    helpers.open_settings_page(c, "Affichage")
    put(c, "framePacing", True)
    click_toggle(c, "enableVsync", "enableVsync", False)
    assert row(c, "framePacing")["row"]["checked"] is False, "frame pacing cannot show on without V-Sync"
    assert_inert(c, "framePacing", "framePacing", "without V-Sync")
    click_toggle(c, "enableVsync", "enableVsync", True)
    r = row(c, "framePacing")
    assert r["enabled"] and r["row"]["checked"] is True, f"frame pacing comes back as it was: {r}"

    click_toggle(c, "useVirtualDisplay", "useVirtualDisplay", False)
    assert_inert(c, "useMultiDisplay", "useMultiDisplay", "without the virtual display")
    click_toggle(c, "useVirtualDisplay", "useVirtualDisplay", True)
    assert row(c, "useMultiDisplay")["enabled"] is True

    helpers.open_settings_page(c, "Audio")
    click_toggle(c, "micPassthroughEnabled", "micPassthroughEnabled", False)
    assert row(c, "micDeviceName")["enabled"] is False, "« Micro utilisé » greyed out without the microphone"

    helpers.open_settings_page(c, "PC")
    click_toggle(c, "clipboardSync", "clipboardSyncEnabled", False)
    assert_inert(c, "clipboardNotifications", "clipboardSyncNotifications", "without the clipboard sync")
    assert row(c, "clipboardMaxSizeMB")["enabled"] is False
    click_toggle(c, "clipboardSync", "clipboardSyncEnabled", True)
    assert row(c, "clipboardNotifications")["enabled"] is True
    bench.shot("s4-pc")


# ---------------------------------------------------------------- S5 removed settings

REMOVED = {"enableHdr": (True, False), "enableResolutionScaling": (True, False), "resolutionScaleFactor": (50, 100),
           "swapMouseButtons": (True, False), "reverseScrollDirection": (True, False),
           "gameOptimizations": (False, True), "rendererSelection": (1, 0), "rendererBackend": (1, 0)}


def test_s5_removed_settings_forced(bench):
    c = bench.start_client()
    known = c.call("setting", action="list")["streaming"]
    keys = [k for k in REMOVED if k in known]
    bench.note(f"removed settings exposed: {keys}")
    assert {"enableHdr", "swapMouseButtons", "reverseScrollDirection", "gameOptimizations"} <= set(keys), keys
    for k in keys:
        put(c, k, REMOVED[k][0])
    c = bench.restart_client()
    wrong = {k: get(c, k) for k in keys if get(c, k) != REMOVED[k][1]}
    assert not wrong, f"settings removed by the triage kept an old value after a restart: {wrong}"


# ---------------------------------------------------------------- S6 update check

UPDATE_SCRIPT = ";".join([
    "none", "none",                     # « Vérifier » twice: each check ends
    "fail:3",                           # QNetworkReply::HostNotFoundError
    "fail:5",                           # OperationCanceledError: what the transfer timeout gives
    "fail:203:404",                     # ContentNotFoundError, HTTP 404
    "available:9.9.9:https://github.invalid/LavArtemis-v9.9.9-win-installer.exe",
    "available:9.9.10",                 # a later release without an installer for this PC
])


def update_row_text(c):
    """Label and description of the update row (its texts, right of the list)."""
    r = [i for i in c.tree(textOnly=True) if i["x"] > LIST_X_MAX and i.get("text")]
    return [i["text"] for i in r]


def check_now(c, expect, what):
    helpers.click(c, "Vérifier")
    deadline = time.time() + 10
    while True:
        shown = update_row_text(c)
        hit = [t for t in shown if expect in t]
        if hit and "Vérification…" not in shown:
            return hit[0]
        assert time.time() < deadline, f"{what}: no {expect!r}; texts {shown}"
        time.sleep(0.2)


def update_dialog_text(c):
    deadline = time.time() + 5
    while True:
        t = [x for x in helpers.texts(c) if x.startswith("LavArtemis 9.9.") and "Appuie sur OK" in x]
        if t:
            return t[0]
        assert time.time() < deadline, f"no update dialog; texts {helpers.texts(c)[-10:]}"
        time.sleep(0.2)


def test_s6_update_check(bench):
    c = bench.start_client(env={"LAV_TEST_UPDATE": UPDATE_SCRIPT})
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Avancé")
    row(c, "autoUpdateEnabled")
    check_now(c, "est à jour.", "first check")
    check_now(c, "est à jour.", "second check in a row")
    t = check_now(c, "La vérification a échoué", "host not found")
    assert "GitHub est injoignable. Vérifie la connexion Internet de ce PC." in t, t
    t = check_now(c, "La vérification a échoué", "timeout")
    assert "GitHub n'a pas répondu à temps. Réessaie plus tard." in t, t
    t = check_now(c, "La vérification a échoué", "HTTP 404")
    assert "GitHub a refusé la demande (erreur HTTP 404). Réessaie plus tard." in t, t
    bench.shot("s6-failed")

    # 9.9.9 with its installer: « Installer » offers to download it; « Ignorer cette version »
    check_now(c, "LavArtemis 9.9.9 est disponible.", "9.9.9")
    helpers.click(c, "Installer")
    text = update_dialog_text(c)
    assert "télécharger et l'installer" in text, text
    helpers.click(c, "Ignorer cette version")
    helpers.wait_text(c, "Chercher une mise à jour maintenant")

    # 9.9.10 without an installer: the page, never 9.9.9's installer
    check_now(c, "LavArtemis 9.9.10 est disponible.", "9.9.10")
    helpers.click(c, "Installer")
    text = update_dialog_text(c)
    bench.shot("s6-no-installer")
    assert "ouvrir la page de téléchargement" in text, \
        f"a release without an installer must open its page, not download an earlier installer: {text!r}"
    c.call("key", key="Escape", target="qt")


# ---------------------------------------------------------------- S7 appearance and language

LANGUAGES = 32  # StreamingPreferences::Language: LANG_AUTO + 31 languages


def test_s7_appearance_and_language(bench):
    c = bench.start_client()
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Apparence")
    choose(c, "themeMode", "Clair")
    deadline = time.time() + 5
    while row(c, "themeMode")["row"]["value"] != 1:
        assert time.time() < deadline, "« Clair » not chosen"
        time.sleep(0.2)

    entries = dropdown_texts(c, "language")
    bench.note(f"languages: {entries}")
    assert len(entries) == LANGUAGES, f"{len(entries)} languages listed, the app stores {LANGUAGES}: {entries}"
    for name in ("Українська", "हिन्दी", "עברית", "Lietuvių", "Eesti", "Esperanto", "Norsk bokmål"):
        assert name in entries, f"{name} missing from {entries}"
    assert not any("Norwegian" in e for e in entries), entries
    choose_dropdown(c, "language", "Deutsch")
    de = wait_value(c, "language", lambda v: v != 0, "« Deutsch »")
    choose_dropdown(c, "language", entries[0])
    wait_value(c, "language", lambda v: v == 0, "back to « Automatique »")
    assert de != 0

    c = bench.restart_client()
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Apparence")
    assert row(c, "themeMode")["row"]["value"] == 1, "« Clair » lost at restart"


# ---------------------------------------------------------------- S8 every switch

def all_pages(c):
    helpers.scroll_settings_list(c, 20)
    names = []
    for _ in range(10):
        new = [i["text"] for i in helpers.settings_pages_visible(c) if i["text"] not in names]
        if not new:
            break
        names += new
        helpers.scroll_settings_list(c, -3)
    helpers.scroll_settings_list(c, 20)
    return names


def toggles_on_screen(c):
    return {k: i for k, i in rows(c).items() if isinstance((i.get("row") or {}).get("checked"), bool)
            and i["h"] >= 40}


def flip_every_toggle(c, done):
    """Every switch of the open page, top to bottom (twice: a switch can show others above it)."""
    for _ in range(2):
        scroll_rows(c, 30)
        last = None
        for _ in range(30):
            while True:
                todo = [(k, i) for k, i in toggles_on_screen(c).items() if k not in done and i["enabled"]]
                if not todo:
                    break
                # Lowest first: a switch that greys others out (V-Sync) sits above them
                key, item = max(todo, key=lambda kv: kv[1]["y"])
                was = item["row"]["checked"]
                helpers.click_item(c, item)
                deadline = time.time() + 5
                while True:
                    now = anywhere(c, key)  # clicked, the row takes the focus and the page may scroll
                    if now and now["row"]["checked"] != was:
                        break
                    assert time.time() < deadline, f"set:{key} did not flip"
                    time.sleep(0.2)
                done[key] = None
                time.sleep(0.5)  # the page may lay out again (rows shown or hidden)
            seen = sorted(rows(c))
            if seen == last:
                break
            last = seen
            scroll_rows(c, -2)


def toggles_state(c):
    """{key: checked} of every switch of the open page."""
    out = {}
    scroll_rows(c, 30)
    last = None
    for _ in range(30):
        out.update({k: i["row"]["checked"] for k, i in toggles_on_screen(c).items()})
        seen = sorted(rows(c))
        if seen == last:
            break
        last = seen
        scroll_rows(c, -2)
    return out


def test_s8_every_switch_kept(bench):
    c = bench.start_client(env=TOUCH)  # the touch rows too
    put(c, "uiExpertMode", True)
    helpers.open_settings(c)
    pages = all_pages(c)
    bench.note(f"pages: {pages}")
    initial, final = {}, {}
    for page in pages:
        if page not in helpers.SETTINGS_PAGE_ROW:
            continue
        helpers.open_settings_page(c, page)
        start = toggles_state(c)
        done = {}
        flip_every_toggle(c, done)
        end = toggles_state(c)
        for key in done:
            initial[key] = start.get(key)
            final[key] = (page, end.get(key))
    bench.note(f"switches flipped: {sorted(final)}")
    assert len(final) >= 25, f"only {len(final)} switches flipped: {sorted(final)}"
    # Frame pacing shows V-Sync AND frame pacing: flipped on, then greyed out (and shown off) by V-Sync turned off
    # below it in the same pass; S4 covers that row
    unchanged = [k for k in final if initial[k] is not None and initial[k] == final[k][1] and k != "framePacing"]
    assert not unchanged, f"switches back where they started: {unchanged}"

    c = bench.restart_client(env=TOUCH)
    helpers.open_settings(c)
    lost = {}
    for page in pages:
        keys = [k for k, (p, _) in final.items() if p == page]
        if not keys:
            continue
        helpers.open_settings_page(c, page)
        now = toggles_state(c)
        for k in keys:
            if k in now and now[k] != final[k][1]:
                lost[k] = (final[k][1], now[k])
    assert not lost, f"switches not kept after a restart (expected, read): {lost}"
