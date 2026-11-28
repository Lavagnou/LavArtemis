"""Scenario 22: first launch at the screen's native resolution and refresh rate (user's rule of 2026-11-28), and
« Saisir un débit » gone from Réglages › Vidéo.

S1 a client that never stored a resolution or a frame rate takes those of its screen: the test build fakes the
   screen with LAV_TEST_NATIVE_MODE (never this PC's monitor), and the recommended bitrate follows that mode.  A
   resolution and a frame rate chosen since win over another screen at the next start.
S2 without LAV_TEST_NATIVE_MODE the test build's screen is 1280x720 at 60: the defaults every other scenario counts on.
S3 expert mode, page Vidéo: « Format vidéo (codec) » is there, « Saisir un débit » no longer is."""
import helpers

SCREEN = {"LAV_TEST_NATIVE_MODE": "2560x1440@144"}
OTHER_SCREEN = {"LAV_TEST_NATIVE_MODE": "3840x2160@120"}


def get(c, key):
    return c.call("setting", action="get", key=key)["value"]


def test_s1_first_launch_takes_the_screens_mode(bench):
    c = bench.start_client(env=SCREEN)
    assert (get(c, "width"), get(c, "height"), get(c, "fps")) == (2560, 1440, 144)
    recommended = get(c, "bitrateKbps")
    bench.note(f"native 2560x1440@144: bitrate {recommended} kbps")

    c1080 = bench.start_client(env={"LAV_TEST_NATIVE_MODE": "1920x1080@60"})
    assert (get(c1080, "width"), get(c1080, "height"), get(c1080, "fps")) == (1920, 1080, 60)
    assert get(c1080, "bitrateKbps") < recommended, "the recommended bitrate does not follow the native mode"

    # A choice wins over the screen afterwards
    c = bench.start_client(env=SCREEN)
    for key, value in [("width", 1920), ("height", 1080), ("fps", 60)]:
        c.call("setting", action="set", key=key, value=value)
    c = bench.restart_client(env=OTHER_SCREEN)
    assert (get(c, "width"), get(c, "height"), get(c, "fps")) == (1920, 1080, 60)


def test_s2_test_builds_screen_is_720p60(bench):
    c = bench.start_client()
    assert (get(c, "width"), get(c, "height"), get(c, "fps")) == (1280, 720, 60)


def test_s3_no_typed_bitrate_row(bench):
    c = bench.start_client()
    c.call("setting", action="set", key="uiExpertMode", value=True)
    helpers.open_settings(c)
    helpers.open_settings_page(c, "Vidéo")
    helpers.wait_text(c, "Format vidéo (codec)")
    texts = helpers.texts(c)
    assert "Saisir un débit" not in texts, "« Saisir un débit » is still on the Vidéo page"
    bench.shot("video-expert")
