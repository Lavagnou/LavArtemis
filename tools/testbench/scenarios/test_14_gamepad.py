"""Scenario 14: a gamepad SDL does not know (the channel's `joystick` command: a generic HID joystick, 4 axes,
8 buttons, 1 hat, no mapping) gets a guessed layout (Réglages › Manettes › « Deviner la disposition des manettes
inconnues », on by default), and the app says so every time, not only the first: the mapper shows « Disposition
devinée » after « Actualiser » too, and every stream launch warns (findings B1).  Turning the guess off takes effect
at once, without a restart: the device is « Non reconnue » and a launch warns that it has no layout (findings B11).
A mapping saved in the mapper with guessing off is used at once too: « Reconnue », no warning (review of waves 1-4, #1:
it stayed an ignored guess until the next start).
The joystick is never opened by the stream (test build: only the channel's pad is)."""
import time

import helpers

NAME = "LavArtemis Test Joystick"
GUESSED = "Disposition devinée — peut-être fausse"
UNMAPPED = "Non reconnue — inutilisable avant assignation"
RECOGNIZED = "Reconnue"
GUESS_WARNING = f"Disposition des boutons devinée pour {NAME}"
NO_LAYOUT_WARNING = "Une manette branchée n'a pas de disposition"


def mapper_status(c):
    """The status line under the joystick's name in the mapper's device list."""
    shown = helpers.texts(c)
    assert NAME in shown, f"{NAME} not in the mapper: {shown}"
    return shown[shown.index(NAME) + 1]


def open_mapper(c):
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Manettes")
    row = helpers.wait_settled(c, "Assigner les boutons d’une manette")
    ouvrir = [i for i in helpers.find(c, "Ouvrir") if abs(i["y"] - row["y"]) < 60]
    assert ouvrir, "no « Ouvrir » next to « Assigner les boutons d’une manette »"
    helpers.click_item(c, ouvrir[0])
    helpers.wait_page(c, "GamepadMapper")
    helpers.wait_text(c, NAME)


def launch_warnings(c, title="Bureau"):
    st = helpers.launch(c, title)
    warnings = st["session"]["launchWarnings"]
    # Quit the session: the next launch is a new one (warnings come with a launch)
    qm = helpers.open_quick_menu(c)
    helpers.quick_menu_click(c, "Quitter la session", qm)
    helpers.wait_any_text(c, [helpers.QUIT_CONFIRM])
    helpers.quick_menu_click(c, "Quitter la session", qm)
    c.wait_for(lambda s: not s["session"]["active"] and s["page"] == "HomeView", 30)
    helpers.wait_computer(c, "Test-Atlas", lambda x: x["currentGameId"] == 0, timeout=20)
    return warnings


def test_guessed_gamepad(bench):
    atlas, = bench.up("atlas-test")
    c = bench.start_client()
    helpers.add_and_pair(c, atlas)
    helpers.wait_apps(c, atlas.name)
    c.call("setting", action="set", key="fps", value=30)
    j = c.call("joystick", attach=True, kind="generic", axes=4, buttons=8, hats=1)
    assert j["gameController"] is False, f"the generic joystick must be unknown to SDL: {j}"

    # The mapper says it guessed, and still says so after « Actualiser » (a second applyMappings)
    open_mapper(c)
    assert mapper_status(c) == GUESSED, mapper_status(c)
    bench.shot("01-mapper-guessed")
    helpers.click(c, "Actualiser")
    time.sleep(0.5)
    assert mapper_status(c) == GUESSED, f"after « Actualiser »: {mapper_status(c)}"

    # Every launch says it guessed
    helpers.go_home(c)
    for attempt in (1, 2):
        warnings = launch_warnings(c)
        bench.note(f"launch {attempt}: {warnings}")
        assert any(w.startswith(GUESS_WARNING) for w in warnings), f"launch {attempt}: {warnings}"

    # Guessing turned off: effective at once
    # (written as Réglages writes it: ArtemisSettings, saved; the row itself is scenario 11's)
    c.call("setting", action="set", key="genericGamepadFallbackEnabled", value=False)
    open_mapper(c)
    assert mapper_status(c) == UNMAPPED, f"guessing off: {mapper_status(c)}"
    bench.shot("02-mapper-off")
    helpers.go_home(c)
    warnings = launch_warnings(c)
    bench.note(f"launch, guessing off: {warnings}")
    assert not any(w.startswith(GUESS_WARNING) for w in warnings), warnings
    assert any(w.startswith(NO_LAYOUT_WARNING) for w in warnings), warnings

    # And back on
    c.call("setting", action="set", key="genericGamepadFallbackEnabled", value=True)
    open_mapper(c)
    assert mapper_status(c) == GUESSED, f"guessing on again: {mapper_status(c)}"

    # Guessing off again, and the user saves a mapping in the mapper (it starts from the guess SDL still has):
    # a real mapping, used at once
    c.call("setting", action="set", key="genericGamepadFallbackEnabled", value=False)
    open_mapper(c)
    assert mapper_status(c) == UNMAPPED, f"guessing off again: {mapper_status(c)}"
    helpers.click(c, NAME)
    helpers.wait_text(c, "Enregistrer")
    helpers.click(c, "Enregistrer")
    # the page of the prototype (V8-46): the devices stay on the left, the toast says it is saved
    helpers.wait_text(c, "Disposition enregistrée")
    helpers.wait_text(c, NAME)
    bench.shot("03-saved-guessing-off")
    assert mapper_status(c) == RECOGNIZED, f"a mapping saved with guessing off: {mapper_status(c)}"
    helpers.go_home(c)
    warnings = launch_warnings(c)
    bench.note(f"launch, saved mapping, guessing off: {warnings}")
    assert not any(w.startswith(NO_LAYOUT_WARNING) for w in warnings), warnings
    assert not any(w.startswith(GUESS_WARNING) for w in warnings), warnings
    c.call("joystick", detach=True)
