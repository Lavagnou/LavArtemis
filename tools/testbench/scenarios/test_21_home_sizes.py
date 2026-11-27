"""Scenario 21: the home never lays a block over another, at any window size (user's report, 2026-11-27).

The user's maximised window (Braise, a session running) had the « Bureau / Playnite Fullscreen » cards drawn over the
Scène: « Reprendre », « Quitter » and the hint line under the cards.  A maximised window on a 16:9 screen is lower
than the prototype's 1280 x 720 scene once the taskbar and the title bar are taken off (1280 x 657 at 150 %,
1536 x 790 at 125 %): Braise anchored the Scène at the top and the bottom block at the bottom, and let them meet.

The main window is resized by the channel (`resize`) to every size below, in each theme (dark), with no session, with
a session (Bureau, left running by « Déconnecter ») and with a long session title (a Playnite game, `state.json` of
the fake PC).  For each layout:
- no two blocks overlap: the focusable items (`fid` of the tree: header buttons, Scène buttons, cards, covers,
  « Tout voir ») and the texts outside them (Scène, « Sur {PC} », captions, hero title, header), the hints bar too;
  measured with the focus on the first item of each row (a cover shows Braise's hero title);
- no text and no focusable item is cut (`clipped`: by the window or a clipping ancestor; `truncated`: elided), the
  card titles excepted (a long app name may end in « … » in a narrow card, as in the prototype);
- no focus ring is cut (`focusrings`), every item of the focus grid in turn (`pagefocus`).
The test client's windows are cloaked, and Qt may then put off a layout to the next frame: a capture forces the
frame before each measure, and a finding counts only if another frame shows it again.
A capture of each layout that fails, and of the user's sizes, goes into the report."""
import time

import helpers
import hosts

THEMES = ("braise", "affiche", "ludique")
# The minimum window (1000 x 600), the prototype's scene (1280 x 720), the user's maximised windows on a 1920 x 1080
# screen (150 %: 1280 x 657 ; 125 %: 1536 x 790), lower, wider and taller ones.  The home is laid out on a stage
# 1600 wide: past 1280 px only the ratio counts (1280 x H / W prototype px high), and Windows caps a window at the
# screen (1541 px wide on a 1920 px screen at 125 %): 1536 x 720 stands for 1920 x 900, 1536 x 816 for 2560 x 1360,
# 1536 x 825 for 1600 x 860
SIZES = [(1000, 600), (1000, 760), (1100, 600), (1280, 600), (1280, 650), (1280, 657), (1280, 690), (1280, 720),
         (1280, 960), (1366, 705), (1536, 720), (1536, 790), (1536, 800), (1536, 816), (1536, 825)]
USER_SIZES = {(1280, 657), (1536, 790)}
LONG_TITLE = "The Legend of Heroes: Trails through Daybreak II"
SLACK = 2  # px: a focused item grows to 1.04, and the scene is scaled


def _overlap(a, b):
    w = min(a["x"] + a["w"], b["x"] + b["w"]) - max(a["x"], b["x"])
    h = min(a["y"] + a["h"], b["y"] + b["h"]) - max(a["y"], b["y"])
    return w > SLACK and h > SLACK


def _inside(a, box):
    return (a["x"] >= box["x"] - SLACK and a["y"] >= box["y"] - SLACK and a["x"] + a["w"] <= box["x"] + box["w"] + SLACK
            and a["y"] + a["h"] <= box["y"] + box["h"] + SLACK)


def _name(i):
    return i.get("fid") or (repr(i["text"][:40]) if i.get("text") else i["type"])


def check_layout(c):
    """What overlaps or is cut on the home as it is now: a list of strings."""
    items = [i for i in c.tree(window=0)]
    layers = [i for i in items if i["type"] in ("HintsBar", "LavToast")]
    bar = [i for i in layers if i["type"] == "HintsBar"]
    toasts = [i for i in layers if i["type"] == "LavToast"]
    boxes = [i for i in items if i.get("fid")]
    texts = [i for i in items if i["type"] == "Text" and i.get("text", "").strip()
             and not any(_inside(i, t) for t in toasts + bar)]
    free = [i for i in texts if not i.get("inFid")]
    blocks = boxes + free + bar
    bad = []
    for n, a in enumerate(blocks):
        for b in blocks[n + 1:]:
            if b.get("inFid") and b["inFid"] == a.get("fid") or a.get("inFid") and a["inFid"] == b.get("fid"):
                continue
            if _overlap(a, b):
                bad.append(f"{_name(a)} over {_name(b)} ({a['x']},{a['y']} {a['w']}x{a['h']} / "
                           f"{b['x']},{b['y']} {b['w']}x{b['h']})")
    card_ids = ("a0", "a1")
    for t in texts:
        if t.get("clipped"):
            bad.append(f"text cut by an edge: {t['text'][:50]!r} ({t['x']},{t['y']} {t['w']}x{t['h']})")
        elif t.get("truncated") and t.get("inFid") not in card_ids:
            bad.append(f"text elided: {t['text'][:50]!r}")
    for b in boxes:
        if b.get("clipped"):
            bad.append(f"{b['fid']} cut by an edge ({b['x']},{b['y']} {b['w']}x{b['h']})")
    return bad


def check_rings(c):
    bad = []
    for r in c.call("focusrings", window=0)["rings"]:
        if r.get("cutBy"):
            cut = r["cutBy"]
            where = "window" if not isinstance(cut, dict) else                 f"{cut.get('type')} {cut.get('x')},{cut.get('y')} {cut.get('w')}x{cut.get('h')}"
            bad.append(f"ring ({r['x']},{r['y']} {r['w']}x{r['h']}) cut by {where}")
    return bad


def check_focus(c, bench, fid, layout):
    """The focus on `fid` (the page's grid), what is wrong there: its ring, and the whole layout if asked.  A cloaked
    window may put off its layout to its next frame (a Row then shows both cards at x = 0): a capture forces the
    frame before measuring, and a finding counts only if it is still there after another one."""
    c.call("pagefocus", fid=fid)
    found = None
    for attempt in range(2):
        c.screenshot(bench.workdir / "render.png")
        time.sleep(0.25)  # the focused item grows to 1.04 in 150 ms
        bad = [f"[focus {fid}] {b}" for b in check_rings(c) + (check_layout(c) if layout else [])]
        if not bad:
            return []
        found = bad
    return found


def sweep(c, bench, state):
    failures = []
    for theme in THEMES:
        c.call("setting", action="set", key="uiTheme", value=theme)
        time.sleep(0.6)
        for w, h in SIZES:
            size = c.call("resize", w=w, h=h)
            label = f"{state}-{theme}-{w}x{h}"
            assert (size["w"], size["h"]) == (w, h), f"{label}: the window is {size['w']} x {size['h']}"
            time.sleep(0.5)
            c.call("key", key="Left", target="qt")  # keyboard input: the rings and the hints bar show
            time.sleep(0.3)
            grid = c.state()["pageState"]["grid"]
            bad = []
            # every item of the grid for its ring; the whole layout with the focus on the first item of each row
            # (a cover shows Braise's hero title)
            for row in grid:
                for n, fid in enumerate(row):
                    bad += check_focus(c, bench, fid, layout=n == 0)
            bad = list(dict.fromkeys(bad))
            if bad or (w, h) in USER_SIZES:
                c.call("pagefocus", fid=grid[1][0])
                bench.shot(label)
            if bad:
                bench.note(f"{label}: " + "; ".join(bad))
                failures.append(f"{label}: " + "; ".join(bad[:6]) + (f" (+{len(bad) - 6})" if len(bad) > 6 else ""))
    c.call("resize", w=1280, h=720)
    c.call("setting", action="set", key="uiTheme", value="braise")
    time.sleep(0.6)
    return failures


def test_home_every_size(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    helpers.go_home(c)
    helpers.wait_text(c, helpers.HOME_LIBRARY_TITLE)
    c.call("setting", action="set", key="uiThemeMode", value=2)
    time.sleep(0.6)

    failures = sweep(c, bench, "idle")

    # A session: the Bureau, left running by « Déconnecter » (Ctrl+Alt+Shift+Q)
    helpers.launch(c, "Bureau")
    c.call("key", key="Q", modifiers="Ctrl+Alt+Shift", target="stream")
    c.wait_for(lambda st: st["page"] == "HomeView" and not st["session"]["active"], 30)
    helpers.wait_text(c, "Reprendre")
    time.sleep(3.5)  # the « Déconnecté » toast goes
    failures += sweep(c, bench, "session")

    # A long session title: a Playnite game runs on the PC (its name is the Scène's title; the home reads it when
    # the session changes: the client is restarted, settings and pairing kept)
    hosts.set_playnite_game(atlas, "6F1D2C3B-0000-4000-8000-00000000A11E", LONG_TITLE)
    helpers.wait_computer(c, atlas.name, lambda x: x["playniteGameName"] == LONG_TITLE, timeout=20)
    c = bench.restart_client()
    helpers.go_home(c)
    helpers.wait_text(c, LONG_TITLE, timeout=30)
    time.sleep(1)
    failures += sweep(c, bench, "long")

    assert not failures, f"{len(failures)} layouts overlap or cut:\n" + "\n".join(failures)
