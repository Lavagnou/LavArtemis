"""Static fonts of the Windows installer window (see generate-installer.mjs).

Usage: python -I generate-installer-fonts.py <LavArtemis-Qt>/app/res/fonts out/installer/fonts

The prototype uses Space Grotesk (titles) and Inter (text, buttons). The installer draws some text with
GDI (labels, from the theme) and the rest into images (resvg): both read these files. GDI matches a
font by family name and weight, and the variable fonts (or the Android static instances, whose names say
"Regular" for every weight) are ambiguous to it: each instance here is its own family, style Regular,
weight 400, so a theme Font only names the family. Latin subset (French), OFL: the renamed families keep
the original names out of the menu, as the licence asks of modified versions.
"""
import os
import sys

from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

FONTS = [
    # file, variable source, weight, family name
    ('lav-space-700.ttf', 'SpaceGrotesk.ttf', 700, 'LavInstall Space Bold'),
    ('lav-inter-400.ttf', 'Inter.ttf', 400, 'LavInstall Inter'),
    ('lav-inter-500.ttf', 'Inter.ttf', 500, 'LavInstall Inter Medium'),
    ('lav-inter-700.ttf', 'Inter.ttf', 700, 'LavInstall Inter Bold'),
]
# Basic Latin, Latin-1, and the typographic marks the French strings use.
UNICODES = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + [
    0x0152, 0x0153, 0x2013, 0x2014, 0x2019, 0x201C, 0x201D, 0x2026, 0x2022, 0x00B7, 0x202F, 0x2192, 0x2715,
]

for name, source, weight, family in FONTS:
    font = TTFont(os.path.join(src, source))
    axes = {a.axisTag: a for a in font['fvar'].axes}
    limits = {tag: (weight if tag == 'wght' else axis.defaultValue) for tag, axis in axes.items()}
    font = instantiateVariableFont(font, limits, updateFontNames=False)
    opts = Options()
    opts.layout_features = ['kern', 'liga', 'calt', 'ccmp', 'locl', 'mark', 'mkmk']
    opts.name_IDs = ['*']
    sub = Subsetter(opts)
    sub.populate(unicodes=UNICODES)
    sub.subset(font)
    names = font['name']
    for rec in list(names.names):
        if rec.nameID in (16, 17, 21, 22, 25):
            names.removeNames(nameID=rec.nameID)
    for pid, eid, lid in ((3, 1, 0x409), (1, 0, 0)):
        names.setName(family, 1, pid, eid, lid)
        names.setName('Regular', 2, pid, eid, lid)
        names.setName(f'{family} Regular', 3, pid, eid, lid)
        names.setName(family, 4, pid, eid, lid)
        names.setName(family.replace(' ', ''), 6, pid, eid, lid)
    os2 = font['OS/2']
    os2.usWeightClass = 400
    os2.fsSelection = (os2.fsSelection & ~0b1100001) | 0b1000000  # REGULAR, not BOLD/ITALIC
    font['head'].macStyle = 0
    font.save(os.path.join(out, name))
    print(name, family, os.path.getsize(os.path.join(out, name)))
