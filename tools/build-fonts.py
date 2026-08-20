"""Instance + subset the Instrument typefaces into static Android font resources.

Variable fonts are avoided on purpose: static instances use the oldest, simplest
Compose font API (Font(resId, weight)), which is the safest choice given this
tree cannot be compiled here.
"""
import os
import subprocess
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

OUT = sys.argv[1]
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fontcache")
os.makedirs(OUT, exist_ok=True)
os.makedirs(CACHE, exist_ok=True)

RAW = "https://raw.githubusercontent.com/google/fonts/main/ofl/{}"
SOURCES = {
    "spacegrotesk": "spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf",
    "inter": "inter/Inter%5Bopsz,wght%5D.ttf",
}

# Latin text, plus every non-ASCII mark the app actually renders:
#   ·  U+00B7 meta separators      ×  U+00D7 "100 kg × 5"
#   −  U+2212 true minus (steppers)  –—  U+2013/2014 dashes
#   ’“” U+2018-201D smart quotes   →  U+2192 progression arrows
#   ▲▼ U+25B2/25BC trend deltas    ●○ U+25CF/25CB set dots
#   ·  … U+2026 ellipsis           ✓  U+2713 check
UNICODES = (
    "U+0020-007E,U+00A0-00FF,U+2013,U+2014,U+2018-201D,U+2022,U+2026,"
    "U+2192,U+2212,U+25B2,U+25BC,U+25CB,U+25CF,U+2713,U+00B7,U+00D7"
)

TARGETS = [
    # (source, out name, wght, extra axis pins, usWeightClass)
    ("spacegrotesk", "space_grotesk_medium", 500, {}, 500),
    ("spacegrotesk", "space_grotesk_bold", 700, {}, 700),
    ("inter", "inter_regular", 400, {"opsz": 14}, 400),
    ("inter", "inter_medium", 500, {"opsz": 14}, 500),
    ("inter", "inter_semibold", 600, {"opsz": 14}, 600),
]


def fetch(key):
    path = os.path.join(CACHE, key + ".ttf")
    if not os.path.exists(path):
        url = RAW.format(SOURCES[key])
        subprocess.run(["curl", "-sSLf", "--max-time", "120", "-o", path, url], check=True)
    return path


def digit_widths(font, tabular):
    """Advance widths of 0-9, following the tnum substitution when asked."""
    cmap = font.getBestCmap()
    names = [cmap[ord(d)] for d in "0123456789"]
    if tabular:
        mapping = {}
        gsub = font.get("GSUB")
        if gsub is not None:
            for rec in gsub.table.FeatureList.FeatureRecord:
                if rec.FeatureTag != "tnum":
                    continue
                for idx in rec.Feature.LookupListIndex:
                    lookup = gsub.table.LookupList.Lookup[idx]
                    for sub in lookup.SubTable:
                        mapping.update(getattr(sub, "mapping", {}))
        names = [mapping.get(n, n) for n in names]
    hmtx = font["hmtx"]
    return [hmtx[n][0] for n in names]


built = []
for key, name, wght, pins, weight_class in TARGETS:
    src = fetch(key)
    axes = dict(pins)
    axes["wght"] = wght
    font = instancer.instantiateVariableFont(TTFont(src), axes, inplace=False, updateFontNames=False)

    tmp = os.path.join(CACHE, name + ".instanced.ttf")
    font.save(tmp)

    out_path = os.path.join(OUT, name + ".ttf")
    # layout-features='*' is load-bearing: fontTools' default feature list drops
    # 'tnum', which would silently un-align every number in the app.
    subset.main([
        tmp,
        "--unicodes=" + UNICODES,
        "--layout-features=*",
        "--no-hinting",
        "--desubroutinize",
        "--name-IDs=*",
        "--drop-tables+=DSIG",
        "--output-file=" + out_path,
    ])

    out = TTFont(out_path)
    out["OS/2"].usWeightClass = weight_class
    out.save(out_path)

    out = TTFont(out_path)
    has_tnum = any(
        r.FeatureTag == "tnum"
        for r in out["GSUB"].table.FeatureList.FeatureRecord
    ) if out.get("GSUB") else False
    plain = digit_widths(out, tabular=False)
    tab = digit_widths(out, tabular=True)
    built.append((name, os.path.getsize(out_path), has_tnum, len(set(plain)) == 1, len(set(tab)) == 1))

print()
print(f"{'font':26s} {'KB':>6s}  {'tnum':>5s}  {'digits-uniform':>14s}  {'tnum-uniform':>12s}")
ok = True
for name, size, has_tnum, plain_uniform, tab_uniform in built:
    print(f"{name:26s} {size/1024:6.1f}  {str(has_tnum):>5s}  {str(plain_uniform):>14s}  {str(tab_uniform):>12s}")
    if not tab_uniform:
        ok = False
print()
print("TABULAR NUMERALS VERIFIED" if ok else "FAILED: tabular numerals are not uniform width")
sys.exit(0 if ok else 1)
