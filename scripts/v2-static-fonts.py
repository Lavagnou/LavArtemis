"""
Static weights of the v2 interface's variable fonts, for Android 7.1 and older.

Android applies the `wght` axis of a variable font (res/font/v2_<family>_<weight>.xml,
fontVariationSettings) from API 26 only. Below that the font's default instance shows for every
weight -- Archivo's is 600, Bricolage's 800 (and its display optical size), Space Grotesk's 300 --
so the body text came out heavy or light and bold was faked. V2Theme loads these instances from
assets/fonts/ on API < 26 instead: a regular (400) and a bold (700) per family, plus the display
weight of Affiche and Ludique, Latin only (French and the symbols the screens draw) to keep the
APK small (about 700 KB for the eleven files).

    python scripts/v2-static-fonts.py      (fontTools 4.x)

Regenerate when a font of res/font changes. Licences: assets/licenses/fonts (OFL).
"""
import os

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "res", "font")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts")

# family file -> name used by V2Theme
FAMILIES = {
    "v2_inter.ttf": "inter",
    "v2_space_grotesk.ttf": "space",
    "v2_archivo.ttf": "archivo",
    "v2_bricolage.ttf": "bricolage",
}
# A regular and a bold, plus the display weight of the theme that uses the family as its title
# font (Affiche: Archivo 900, Ludique: Bricolage 800)
WEIGHTS = {"inter": (400, 700), "space": (400, 700), "archivo": (400, 700, 900), "bricolage": (400, 700, 800)}

# Basic Latin, Latin-1, Latin Extended-A, punctuation, currency, arrows, shapes (▲ ▼ ★ ●)
UNICODES = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + [0x152, 0x153, 0x178, 0x2C6, 0x2DC]
    + list(range(0x2000, 0x2070)) + [0x20AC, 0x2122, 0x2212, 0x2264, 0x2265, 0x2260, 0x221E]
    + list(range(0x2190, 0x2200)) + list(range(0x25A0, 0x2600)) + [0x2605, 0x2606, 0x2713, 0x2715]
)


def build(src_name, family, weight):
    font = TTFont(os.path.join(SRC, src_name))
    location = {}
    for axis in font["fvar"].axes:
        if axis.axisTag == "wght":
            location["wght"] = max(axis.minValue, min(axis.maxValue, weight))
        else:
            # Same as on API 26+, where only wght is set: the other axes stay at their default
            location[axis.axisTag] = axis.defaultValue
    static = instantiateVariableFont(font, location, updateFontNames=False)
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    sub = subset.Subsetter(options)
    sub.populate(unicodes=UNICODES)
    sub.subset(static)
    # Bold for the regular/bold style bits, so a Typeface knows its weight
    os2 = static["OS/2"]
    os2.usWeightClass = weight
    path = os.path.join(OUT, "v2_%s_%d.ttf" % (family, weight))
    static.save(path)
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    for src_name, family in FAMILIES.items():
        for weight in WEIGHTS[family]:
            path = build(src_name, family, weight)
            print("%-40s %6d bytes" % (os.path.relpath(path, ROOT), os.path.getsize(path)))


if __name__ == "__main__":
    main()
