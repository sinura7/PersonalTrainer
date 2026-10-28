#!/usr/bin/env python3
"""Write index.md for walkthrough-screens PNG artifacts (flowNN_stepNN_name.png)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

FLOW_TITLES = {
    "01": "Cold start intro",
    "02": "Onboarding preview accept",
    "03": "Main tabs (Home, Plan, Progress, Body)",
    "04": "Live strength workout",
    "05": "Live cardio",
    "06": "Past mixed save",
    "07": "Body map and measurements",
    "08": "Settings units and backup",
    "09": "Large font live workout",
}

PATTERN = re.compile(r"^flow(\d{2})_step(\d{2})_(.+)\.png$", re.IGNORECASE)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "walkthrough-screens")
    root.mkdir(parents=True, exist_ok=True)
    pngs = sorted(p for p in root.glob("flow*.png") if p.is_file())
    lines = [
        "# Walkthrough screens",
        "",
        f"Total captures: **{len(pngs)}**",
        "",
        "| File | Flow | Step | Screen |",
        "| --- | --- | --- | --- |",
    ]
    for path in pngs:
        match = PATTERN.match(path.name)
        if not match:
            lines.append(f"| `{path.name}` | — | — | (unparsed name) |")
            continue
        flow_id, step_id, label = match.groups()
        flow_title = FLOW_TITLES.get(flow_id, f"Flow {flow_id}")
        lines.append(
            f"| `{path.name}` | {flow_id} — {flow_title} | {step_id} | {label.replace('-', ' ')} |"
        )
    (root / "index.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"walkthrough-build-index: wrote {root / 'index.md'} ({len(pngs)} pngs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
