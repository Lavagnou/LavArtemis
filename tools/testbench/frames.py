"""Reads the frame number the test host stamps in its synthetic picture.

Layout (LavApollo src/benchmark_scene_formula.h, keep in sync): 32 cells of 8x8 stream pixels in the bottom-left
corner, cell i = bit i (LSB leftmost), white = 1, black = 0.  A cell reads 1 when the mean of (R+G+B)/3 over its
centre 4x4 pixels (cols 8i+2..8i+5, rows h-6..h-3) is >= 128.  Coordinates are in STREAM pixels: a screenshot of a
window of another size must be scaled to the stream size first (pass stream_size).

Frame numbers restart at 0 whenever the host reinitialises its capture, and a slot skipped on the host leaves a gap:
use is_increasing() / monotonic checks that allow both, never n+1 equality."""
from PIL import Image

CELL = 8
BITS = 32


class FrameReadError(ValueError):
    """The picture cannot carry a trustworthy frame number (too small, ambiguous cells, garbage)."""


# Strict-read sanity rules (cheap, tested): a real stamped cell is pure black or white, so the mean of its 4x4
# centre sits near 0 or 255 and its pixels are nearly uniform.  H.264 smears but stays well outside the band.
AMBIGUOUS_LOW, AMBIGUOUS_HIGH = 64, 192
MAX_CELL_SPREAD = 96  # max - min of the 16 centre pixels of one cell


def fit_rect(window_w, window_h, stream_w, stream_h):
    """(x, y, w, h) of the stream picture inside a window of another shape: aspect fit, centred
    (letterbox bars above/below, pillarbox bars left/right)."""
    scale = min(window_w / stream_w, window_h / stream_h)
    w, h = max(1, round(stream_w * scale)), max(1, round(stream_h * scale))
    return ((window_w - w) // 2, (window_h - h) // 2, w, h)


def read_frame_number_image(img, stream_size=None, content_rect=None, strict=True, fit=True):
    """img: PIL image of the stream or of a window showing it.

    stream_size=(w, h): the stream resolution; the image is scaled to it before sampling (cells are in stream
    pixels).  Unless content_rect=(x, y, w, h) is given, the part showing the stream is the aspect fit of the
    stream inside the image (fit=True), so letterbox/pillarbox bars are cut off automatically.
    strict (default): raises FrameReadError when the sampled region is under 256x8 pixels, when a cell's mean is
    ambiguous (64-192) or its pixels are not uniform; strict=False reads whatever fits (missing cells = 0)."""
    img = img.convert("RGB")
    if content_rect is None and stream_size and fit:
        content_rect = fit_rect(img.size[0], img.size[1], *stream_size)
    if content_rect:
        x, y, w, h = content_rect
        img = img.crop((x, y, x + w, y + h))
    if stream_size and img.size != tuple(stream_size):
        img = img.resize(tuple(stream_size), Image.BOX)
    width, height = img.size
    if strict and (width < CELL * BITS or height < CELL):
        raise FrameReadError(f"region {width}x{height} is smaller than {CELL * BITS}x{CELL} stream pixels")
    if height < CELL:
        return 0
    px = img.load()
    value = 0
    for i in range(min(BITS, width // CELL)):
        total = n = 0
        lo, hi = 255, 0
        for yy in range(height - 6, height - 2):
            for xx in range(CELL * i + 2, CELL * i + 6):
                r, g, b = px[xx, yy]
                v = (r + g + b) / 3
                total += v
                n += 1
                lo, hi = min(lo, v), max(hi, v)
        mean = total / n
        if strict and (AMBIGUOUS_LOW < mean < AMBIGUOUS_HIGH or hi - lo > MAX_CELL_SPREAD):
            raise FrameReadError(f"cell {i} is not black or white (mean {mean:.0f}, spread {hi - lo:.0f}): "
                                 "not a frame-number strip")
        if mean >= 128:
            value |= 1 << i
    return value


def read_frame_number(png_path, stream_size=None, content_rect=None, strict=True, fit=True):
    with Image.open(png_path) as img:
        return read_frame_number_image(img, stream_size, content_rect, strict, fit)


def is_increasing(numbers, allow_equal=True, allow_restart=True, restart_below=1000):
    """True when the sequence only moves forward.  Gaps (skipped host slots) are always fine.
    allow_equal: two screenshots may show the same frame (default, sampling faster than the stream).
    allow_restart: the host restarts numbering at 0 when it reinitialises its capture; a drop to a number below
    `restart_below` (about 16 s at 60 fps after the reinit) counts as such a restart, any other drop is a failure."""
    prev = None
    for n in numbers:
        if prev is not None:
            if n == prev:
                if not allow_equal:
                    return False
            elif n < prev and not (allow_restart and n < restart_below):
                return False
        prev = n
    return True
