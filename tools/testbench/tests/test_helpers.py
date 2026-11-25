"""Tests of the scenario helpers that compute something (nothing is launched)."""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent / "scenarios"))
import helpers  # noqa: E402


def test_host_display_fit_matches_the_clients_reference_values():
    # The reference values of LavArtemis-Qt tests/hostdisplay and Android HostDisplayFitTest
    cases = [
        ((1920, 1080, 60), (2400, 1080, 90), (1920, 1080, 60)),     # host smaller than the setting
        ((2400, 1080, 90), (1920, 1080, 60), (1920, 864, 60)),      # phone session on a laptop
        ((2400, 1080, 90), (1920, 1080, 120), (1920, 864, 90)),     # fps capped by the host
        ((1280, 720, 60), (1920, 1080, 60), (1280, 720, 60)),       # never upscaled
        ((3840, 2160, 60), (1920, 1080, 60), (1920, 1080, 60)),     # 4K into full HD
        ((1366, 768, 60), (1280, 720, 60), (1280, 718, 60)),        # rounded down to even
        ((1920, 1080, 1000), (1920, 1080, 90), (1920, 1080, 90)),   # VRR display keeps the client's fps
    ]
    for host, setting, expected in cases:
        assert helpers.host_display_fit(host, setting) == expected, (host, setting)


def test_scenario_5_expectation_with_the_default_test_display():
    import hosts
    assert helpers.host_display_fit(hosts.DEFAULT_DISPLAY, (1280, 720, 30)) == (1280, 720, 30)
