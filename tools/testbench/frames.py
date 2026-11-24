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


def read_frame_number_image(img, stream_size=None, content_rect=None):
    """img: PIL image.  content_rect=(x, y, w, h) in image pixels: the part showing the stream (letterbox cut off)."""
    img = img.convert("RGB")
    if content_rect:
        x, y, w, h = content_rect
        img = img.crop((x, y, x + w, y + h))
    if stream_size and img.size != tuple(stream_size):
        img = img.resize(tuple(stream_size), Image.BOX)
    width, height = img.size
    if height < CELL:
        return 0
    px = img.load()
    value = 0
    for i in range(min(BITS, width // CELL)):
        total = n = 0
        for yy in range(height - 6, height - 2):
            for xx in range(CELL * i + 2, CELL * i + 6):
                r, g, b = px[xx, yy]
                total += (r + g + b) / 3
                n += 1
        if total / n >= 128:
            value |= 1 << i
    return value


def read_frame_number(png_path, stream_size=None, content_rect=None):
    with Image.open(png_path) as img:
        return read_frame_number_image(img, stream_size, content_rect)


def is_increasing(numbers, allow_restart=True):
    """True when the sequence only moves forward; with allow_restart a drop to a small number (capture reinit)
    is accepted once per drop.  Gaps (skipped slots) are always fine."""
    prev = None
    for n in numbers:
        if prev is not None and n <= prev and not (allow_restart and n < prev and n < 1000):
            return False
        prev = n
    return True
