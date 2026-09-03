#!/usr/bin/env python3
"""Keep the design system from re-fragmenting.

Every blocking rule encodes a defect the redesign had to undo, and each one
is the kind that compiles, ships, and is invisible in review.

Advisory families from the 1 September audit are counted against
tools/checker-baselines.toml: they print, and they fail only when the
count grows. Cleaning them is a later packet; letting more of them in
is not.

Usage:  tools/check-design-tokens.py [source-root ...]
Exit code is nonzero on a blocking violation or an advisory over ceiling.
"""
from __future__ import annotations

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from checker_baseline import load as load_baselines, report as report_baseline  # noqa: E402
from kotlin_source import kotlin_files_in, strip_comments_and_strings, xml_files_in  # noqa: E402

ROOTS = sys.argv[1:] or ["app/src/main/java", "app/src/debug/java"]
XML_ROOTS = ["app/src/main/res", "app/src/debug/res"]

# Blocking rules. Family selects the token file that may spell them.
BLOCKING = [
    (
        "hex",
        re.compile(r"\bColor\(\s*0x[0-9A-Fa-f]{6,8}\b"),
        "raw colour literal — add a token to ui/theme/Color.kt instead",
    ),
    (
        "radius",
        re.compile(r"\bRoundedCornerShape\(\s*\d+(?:\.\d+)?\s*\.?d?p?\s*\)"),
        "raw corner radius — use Radius.xs/sm/md/lg or MaterialTheme.shapes",
    ),
    (
        "font",
        re.compile(r"\bFontFamily\.(?:Monospace|SansSerif|Serif|Default|Cursive)\b"),
        "system font family — use SpaceGrotesk or Inter",
    ),
    (
        "elevation",
        re.compile(r"\b(?:tonalElevation|shadowElevation)\s*="),
        "elevation — depth is the surface ladder plus a hairline, never a shadow or a tonal tint",
    ),
]

# Advisory families: audit regexes. Counted, not blocking, until a design packet.
ADVISORY = [
    (
        "alpha_copy",
        re.compile(r"\.copy\(\s*alpha\s*="),
        "alpha copy — use a tokenised colour with its own alpha",
    ),
    (
        "named_color",
        re.compile(
            r"\bColor\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Black|White|Gray|Grey)\b"
        ),
        "named Compose colour — use a palette token",
    ),
    (
        "rounded_percent",
        re.compile(r"\bRoundedCornerShape\([^)]*(?:\.dp|percent\s*=)"),
        "RoundedCornerShape with .dp or percent — use Radius.*",
    ),
    (
        "circle",
        re.compile(r"\bCircleShape\b"),
        "CircleShape — use Radius.full when that token exists",
    ),
    (
        "raw_dp",
        re.compile(r"(?<![\w.])(?:[1-9]\d*)(?:\.\d+)?\.dp\b"),
        "non-zero .dp literal — use Metrics / Radius / Type",
    ),
    (
        "sp_style",
        re.compile(r"\b\d+(?:\.\d+)?\.sp\b|\bfontSize\s*=|\bTextStyle\s*\("),
        "raw sp / fontSize / TextStyle — use InstrumentType",
    ),
    (
        "motion",
        re.compile(r"\b(?:tween|spring|infiniteRepeatable)\s*\("),
        "raw motion spec — use Motion / instrumentTween",
    ),
    (
        "haptic",
        re.compile(r"\bHapticFeedbackType\b|\bLocalHapticFeedback\b"),
        "raw haptic — use the Haptics token",
    ),
    (
        "m3_scheme",
        re.compile(r"\bMaterialTheme\.colorScheme\b"),
        "MaterialTheme.colorScheme — use palette tokens",
    ),
    (
        "stock_icons",
        re.compile(r"\bIcons\.(?:Filled|Default|Rounded|Outlined|Sharp|TwoTone)\b"),
        "Material Icons.* — vendor or use TemperIcons",
    ),
    (
        "shadow",
        re.compile(r"\bshadow\s*\("),
        "shadow() — depth is the surface ladder",
    ),
    (
        "defaults_elevation",
        re.compile(r"Defaults\.\w*[Ee]levation"),
        "stock *Defaults.elevation",
    ),
    (
        "stock_component",
        re.compile(r"\b(?:Switch|FloatingActionButton|NavigationBar|TopAppBar)\s*\("),
        "stock Material silhouette — skin or replace",
    ),
]

TOKEN_FILE = {
    "hex": {"Color.kt", "Theme.kt"},  # Theme.kt binds the scheme; Color.kt owns hexes
    "radius": {"Shape.kt"},
    "font": {"Type.kt"},
    "elevation": set(),
    "alpha_copy": {"Color.kt"},
    "named_color": {"Color.kt", "Theme.kt"},
    "rounded_percent": {"Shape.kt"},
    "circle": {"Shape.kt"},
    "raw_dp": {"Metrics.kt", "Shape.kt", "LogLoopScale.kt", "Type.kt"},
    "sp_style": {"Type.kt", "LogLoopScale.kt"},
    "motion": {"Motion.kt"},
    "haptic": {"Haptics.kt"},
    "m3_scheme": {"Theme.kt", "ThemeGallery.kt"},
    "stock_icons": set(),
    "shadow": set(),
    "defaults_elevation": set(),
    "stock_component": set(),
}


def basename(path: str) -> str:
    return os.path.basename(path.replace("\\", "/"))


def exempt(path: str, family: str) -> bool:
    return basename(path) in TOKEN_FILE.get(family, set())


def scan_kotlin(path: str, src: str, rules, bucket: dict) -> list:
    findings = []
    for family, pattern, message in rules:
        if exempt(path, family):
            continue
        for match in pattern.finditer(src):
            if family == "raw_dp":
                line_start = src.rfind("\n", 0, match.start()) + 1
                line = src[line_start:src.find("\n", match.start())]
                if re.match(r"\s*(?:private\s+)?va[lr]\s+", line):
                    continue
            line_no = src.count("\n", 0, match.start()) + 1
            bucket[family] = bucket.get(family, 0) + 1
            findings.append((path, line_no, match.group(0).split("\n")[0].strip(), message, family))
    return findings


def scan_xml(path: str, text: str, bucket: dict) -> list:
    findings = []
    for match in re.finditer(r"#[0-9A-Fa-f]{3,8}", text):
        line_no = text.count("\n", 0, match.start()) + 1
        bucket["xml_hex"] = bucket.get("xml_hex", 0) + 1
        findings.append((path, line_no, match.group(0), "XML hex — notification/status assets only", "xml_hex"))
    for match in re.finditer(r'fontFamily\s*=\s*"monospace"', text):
        line_no = text.count("\n", 0, match.start()) + 1
        bucket["xml_monospace"] = bucket.get("xml_monospace", 0) + 1
        findings.append(
            (path, line_no, match.group(0), "XML monospace — notification clock is the retired system face", "xml_monospace"),
        )
    return findings


def main() -> int:
    blocking: list = []
    advisory: list = []
    advisory_counts: dict[str, int] = {family: 0 for family, _, _ in ADVISORY}
    advisory_counts["xml_hex"] = 0
    advisory_counts["xml_monospace"] = 0

    for path in kotlin_files_in(ROOTS):
        src = strip_comments_and_strings(open(path, encoding="utf-8").read())
        blocking.extend(scan_kotlin(path, src, BLOCKING, {}))
        advisory.extend(scan_kotlin(path, src, ADVISORY, advisory_counts))

    for path in xml_files_in(XML_ROOTS):
        text = open(path, encoding="utf-8").read()
        advisory.extend(scan_xml(path, text, advisory_counts))

    for path, line, text, message, _family in sorted(blocking):
        print(f"{path}:{line}  {text}  — {message}")

    print()
    print(f"{len(blocking)} blocking design-token violation(s)")
    print(f"{len(advisory)} advisory design-token hit(s)")

    baselines = load_baselines()
    growth = []
    over = set()
    for family in sorted(advisory_counts):
        msg = report_baseline("tokens", family, advisory_counts[family], baselines)
        if msg:
            growth.append(msg)
            over.add(family)
    if over:
        print()
        for path, line, text, message, family in sorted(advisory):
            if family in over:
                print(f"{path}:{line}  {text}  — advisory {family}: {message}")

    if blocking or growth:
        for item in growth:
            print(item)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(min(main(), 255))
