"""Android TV banners (320 x 180, drawable-xhdpi): tile icon + "LavArtemis" wordmark.

Usage: python -I banner.py <out dir> <SpaceGrotesk.ttf>
Inputs come from gen.mjs (banner-bg*.png, lavartemis*-96.png).
"""
import sys
from PIL import Image, ImageDraw, ImageFont

out, font_path = sys.argv[1], sys.argv[2]


def font(size):
    f = ImageFont.truetype(font_path, size)
    try:
        f.set_variation_by_axes([700])
    except Exception:
        pass
    return f


def banner(bg_name, icon_name, dest, dev):
    im = Image.open(f"{out}/{bg_name}").convert("RGBA")
    icon = Image.open(f"{out}/{icon_name}").convert("RGBA").resize((84, 84), Image.LANCZOS)
    im.alpha_composite(icon, (22, 48))
    d = ImageDraw.Draw(im)
    x = 122
    size = 34
    while size > 18:
        f = font(size)
        if d.textlength("LavArtemis", font=f) <= 320 - x - 14:
            break
        size -= 1
    f = font(size)
    if dev:
        small = font(24)
        d.text((x, 90 - 4), "LavArtemis", font=f, fill="#FFFFFF", anchor="ls")
        d.text((x, 90 + 26), "Dev", font=small, fill="#4ADE80", anchor="ls")
    else:
        d.text((x, 90), "LavArtemis", font=f, fill="#DDE6F5", anchor="lm")
    im.convert("RGB").save(dest)


banner("banner-bg.png", "lavartemis-96.png", f"{out}/atv_banner.png", False)
banner("banner-bg-dev.png", "lavartemis-dev-96.png", f"{out}/atv_banner_dev.png", True)
print("banners ok")
