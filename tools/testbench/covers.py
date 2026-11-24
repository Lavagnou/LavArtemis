"""Generated cover tiles (plain colour + the game name). Never real game art."""
import colorsys
import hashlib
import pathlib

from PIL import Image, ImageDraw, ImageFont

SIZE = (300, 450)


def _colour(name):
    h = int(hashlib.sha1(name.encode("utf-8")).hexdigest()[:6], 16) / 0xFFFFFF
    r, g, b = colorsys.hsv_to_rgb(h, 0.55, 0.55)
    return int(r * 255), int(g * 255), int(b * 255)


def make_cover(name, path):
    img = Image.new("RGB", SIZE, _colour(name))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 34)
    except OSError:
        font = ImageFont.load_default()
    lines, line = [], ""
    for word in name.split():
        trial = (line + " " + word).strip()
        if line and draw.textlength(trial, font=font) > SIZE[0] - 40:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    y = SIZE[1] // 2 - 20 * len(lines)
    for text in lines:
        w = draw.textlength(text, font=font)
        draw.text(((SIZE[0] - w) / 2, y), text, fill=(255, 255, 255), font=font)
        y += 42
    pathlib.Path(path).parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")
