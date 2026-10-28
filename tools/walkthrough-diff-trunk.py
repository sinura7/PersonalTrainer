#!/usr/bin/env python3
"""Report-only pixel diff vs the latest trunk walkthrough-screens artifact."""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

try:
    from PIL import Image, ImageChops
except ImportError:  # pragma: no cover - CI installs python3-pil
    Image = None  # type: ignore[misc, assignment]
    ImageChops = None  # type: ignore[misc, assignment]

PATTERN = re.compile(r"^flow(\d{2})_step(\d{2})_(.+)\.png$", re.IGNORECASE)
WORKFLOW = "walkthrough-screens.yml"
REPO = os.environ.get("GITHUB_REPOSITORY", "sinura7/PersonalTrainer")
TOLERANCE = 12  # mean absolute channel delta per changed pixel bucket


def gh_api(path: str) -> dict | list:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("GH_TOKEN required for trunk artifact download")
    request = urllib.request.Request(
        f"https://api.github.com{path}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def download_trunk_baseline(dest: Path) -> bool:
    runs = gh_api(f"/repos/{REPO}/actions/workflows/{WORKFLOW}/runs?branch=trunk&status=success&per_page=5")
    workflow_runs = runs.get("workflow_runs", []) if isinstance(runs, dict) else []
    for run in workflow_runs:
        run_id = run["id"]
        artifacts = gh_api(f"/repos/{REPO}/actions/runs/{run_id}/artifacts")
        for artifact in artifacts.get("artifacts", []):
            if artifact.get("name") != "walkthrough-screens":
                continue
            archive_url = artifact["archive_download_url"]
            token = os.environ["GH_TOKEN"]
            request = urllib.request.Request(
                archive_url,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                },
            )
            zip_path = dest / "baseline.zip"
            with urllib.request.urlopen(request, timeout=120) as response:
                zip_path.write_bytes(response.read())
            with zipfile.ZipFile(zip_path) as zf:
                zf.extractall(dest / "baseline")
            return True
    return False


def diff_images(current: Path, baseline: Path) -> tuple[bool, str]:
    if Image is None:
        return False, "Pillow unavailable; skipped pixel diff"
    with Image.open(current) as cur, Image.open(baseline) as base:
        if cur.size != base.size:
            return True, f"size {cur.size} vs baseline {base.size}"
        cur_rgb = cur.convert("RGB")
        base_rgb = base.convert("RGB")
        diff = ImageChops.difference(cur_rgb, base_rgb)
        bbox = diff.getbbox()
        if bbox is None:
            return False, "identical"
        region = diff.crop(bbox)
        pixels = list(region.getdata())
        if not pixels:
            return False, "identical"
        mean = sum(sum(p) for p in pixels) / (len(pixels) * 3)
        if mean <= TOLERANCE:
            return False, f"within tolerance (mean Δ≈{mean:.1f})"
        return True, f"changed (mean Δ≈{mean:.1f})"


def main() -> int:
    current_root = Path(sys.argv[1] if len(sys.argv) > 1 else "walkthrough-screens")
    current_pngs = {p.name: p for p in current_root.glob("flow*.png")}
    print("## Walkthrough visual diff (report only)")
    print("")
    if not current_pngs:
        print("No current walkthrough PNGs to compare.")
        return 0
    if os.environ.get("GITHUB_REF") == "refs/heads/trunk":
        print("Trunk push: skipping diff against self.")
        return 0
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        try:
            found = download_trunk_baseline(tmp_path)
        except (urllib.error.URLError, RuntimeError, KeyError) as error:
            print(f"Could not load trunk baseline: {error}")
            return 0
        if not found:
            print("No trunk `walkthrough-screens` artifact yet; diff skipped.")
            return 0
        baseline_root = tmp_path / "baseline"
        baseline_pngs = {p.name: p for p in baseline_root.rglob("flow*.png")}
        changed: list[str] = []
        added: list[str] = []
        removed: list[str] = []
        for name, path in sorted(current_pngs.items()):
            if name not in baseline_pngs:
                added.append(name)
                continue
            is_changed, detail = diff_images(path, baseline_pngs[name])
            if is_changed:
                changed.append(f"- `{name}`: {detail}")
        for name in sorted(baseline_pngs):
            if name not in current_pngs:
                removed.append(name)
        print(f"Compared **{len(current_pngs)}** current PNGs to trunk baseline **{len(baseline_pngs)}** PNGs.")
        print("")
        if changed:
            print("### Changed screens")
            print("\n".join(changed))
            print("")
        if added:
            print("### New screens (not on trunk)")
            print("\n".join(f"- `{n}`" for n in added))
            print("")
        if removed:
            print("### Missing vs trunk")
            print("\n".join(f"- `{n}`" for n in removed))
            print("")
        if not changed and not added and not removed:
            print("All screens match trunk within tolerance.")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
