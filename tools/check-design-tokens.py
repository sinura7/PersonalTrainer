#!/usr/bin/env python3
"""Keep the design system from re-fragmenting.

Every rule here encodes a defect the redesign had to undo, and each one is the kind that
compiles, ships, and is invisible in review:

  raw colour        one screen file held ten hard-coded hexes, so the body map's idea of
                    "intensity" drifted away from the calendar's and nobody could see it
                    from the theme.
  elevation         the app's single `tonalElevation` surface was tinted with the accent by
                    Material's overlay, wearing a lime wash nobody chose. On a near-black
                    field shadows are invisible anyway; depth is the surface ladder.
  raw radius        twenty-one inline corner radii across seven files, nine distinct values,
                    with no scale behind them.
  system font       `FontFamily.Monospace` and `FontFamily.SansSerif` are the two faces the
                    redesign exists to replace.

Usage:  tools/check-design-tokens.py [source-root]
Exit code is the number of violations, so it can gate a build.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kotlin_source import kotlin_files, strip_comments_and_strings  # noqa: E402

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java"

# The theme package defines the tokens, so it is the one place allowed to spell them out.
THEME_DIR = os.path.join("ui", "theme")

RULES = [
    (
        re.compile(r"\bColor\(\s*0x[0-9A-Fa-f]{6,8}\b"),
        "raw colour literal — add a token to ui/theme/Color.kt instead",
        True,
    ),
    (
        re.compile(r"\bRoundedCornerShape\(\s*\d+(?:\.\d+)?\s*\.?d?p?\s*\)"),
        "raw corner radius — use Radius.xs/sm/md/lg or MaterialTheme.shapes",
        True,
    ),
    (
        re.compile(r"\bFontFamily\.(?:Monospace|SansSerif|Serif|Default|Cursive)\b"),
        "system font family — use SpaceGrotesk or Inter",
        True,
    ),
    (
        re.compile(r"\b(?:tonalElevation|shadowElevation)\s*="),
        "elevation — depth is the surface ladder plus a hairline, never a shadow or a tonal tint",
        False,
    ),
]


def main() -> int:
    violations = []
    for path in kotlin_files(ROOT):
        in_theme = THEME_DIR in path.replace("\\", "/").replace("/", os.sep)
        src = strip_comments_and_strings(open(path, encoding="utf-8").read())
        for pattern, message, theme_exempt in RULES:
            if in_theme and theme_exempt:
                continue
            for match in pattern.finditer(src):
                line = src.count("\n", 0, match.start()) + 1
                violations.append((path, line, match.group(0).strip(), message))

    for path, line, text, message in sorted(violations):
        print(f"{path}:{line}  {text}  — {message}")
    print()
    print(f"{len(violations)} design-token violation(s)")
    return len(violations)


if __name__ == "__main__":
    sys.exit(min(main(), 255))
