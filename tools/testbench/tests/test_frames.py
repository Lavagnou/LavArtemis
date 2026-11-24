"""Frame-number reader.  Unit tests use a Python re-implementation of the host layout; the cross-check test
compiles LavApollo's own render_bgra (header-only) with the msys64 g++ and reads the real picture."""
import os
import pathlib
import subprocess
import sys

import pytest
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import frames  # noqa: E402


def stamp(width, height, number, noise=(40, 90, 160)):
    """Python rendering of the layout in benchmark_scene_formula.h: coloured background + 32 cells."""
    img = Image.new("RGB", (width, height), noise)
    d = ImageDraw.Draw(img)
    for i in range(32):
        v = 255 if (number >> i) & 1 else 0
        d.rectangle([8 * i, height - 8, 8 * i + 7, height - 1], fill=(v, v, v))
    return img


@pytest.mark.parametrize("n", [0, 1, 2, 3, 12345, 2 ** 31 - 1, 2 ** 31, 2 ** 31 + 12345, 2 ** 32 - 1])
def test_reads_numbers(n):
    assert frames.read_frame_number_image(stamp(1920, 1080, n)) == n


@pytest.mark.parametrize("window", [(1280, 720), (960, 540), (2560, 1440), (801, 451)])
@pytest.mark.parametrize("n", [0, 7, 2 ** 31 + 5, 2 ** 32 - 1])
def test_scaled_screenshot(window, n):
    shot = stamp(1920, 1080, n).resize(window, Image.BILINEAR)  # what a window of another size shows
    assert frames.read_frame_number_image(shot, stream_size=(1920, 1080)) == n


def test_letterboxed_window_needs_content_rect():
    n = 0xA5A5A5A5
    shot = Image.new("RGB", (1000, 800), (0, 0, 0))
    shot.paste(stamp(1920, 1080, n).resize((1000, 562), Image.BILINEAR), (0, 119))  # 16:9 picture centred in a 1000x800 window
    assert frames.read_frame_number_image(shot, stream_size=(1920, 1080), content_rect=(0, 119, 1000, 562)) == n


def test_small_image_missing_cells_read_zero():
    assert frames.read_frame_number_image(stamp(64, 64, 0xFF)) == 0xFF  # 8 cells fit
    assert frames.read_frame_number_image(Image.new("RGB", (100, 4), (255, 255, 255))) == 0


def test_png_roundtrip(tmp_path):
    p = tmp_path / "f.png"
    stamp(1280, 720, 99).save(p)
    assert frames.read_frame_number(p) == 99


def test_increasing_allows_gaps_and_restart():
    assert frames.is_increasing([5, 6, 9, 10])
    assert frames.is_increasing([100, 101, 0, 1, 5])
    assert not frames.is_increasing([5, 5])
    assert not frames.is_increasing([5000, 4000])


GPP = pathlib.Path(r"C:\msys64\ucrt64\bin\g++.exe")


@pytest.mark.skipif(not GPP.exists(), reason="msys64 g++ not available")
@pytest.mark.parametrize("n", [0, 1, 1234567, 2 ** 31 + 3, 2 ** 32 - 1])
def test_cross_check_with_host_render(tmp_path, n):
    import hosts
    src = hosts.lavapollo_root() / "src"
    exe = tmp_path / "export_frame.exe"
    env = dict(os.environ, PATH=str(GPP.parent) + os.pathsep + os.environ["PATH"])
    r = subprocess.run([str(GPP), "-std=c++20", "-O1", f"-I{src}", str(HERE / "export_frame.cpp"), "-o", str(exe)],
                       capture_output=True, text=True, env=env)
    assert r.returncode == 0, r.stderr
    w, h = 1920, 1080
    raw = tmp_path / "f.raw"
    subprocess.run([str(exe), str(w), str(h), str(n), str(raw)], check=True, env=env)
    img = Image.frombuffer("RGBA", (w, h), raw.read_bytes(), "raw", "BGRA", 0, 1)
    assert frames.read_frame_number_image(img) == (n & 0xFFFFFFFF)
    # and seen through a 1280x720 window
    assert frames.read_frame_number_image(img.resize((1280, 720), Image.BILINEAR), stream_size=(w, h)) == n
