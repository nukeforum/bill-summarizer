"""Build a compact ZIP→{state, [districts]} JSON for the Android app.

Source: HUD USPS ZIP-CBSA-CD crosswalk (manual download). HUD's "CD" code
is two-digit; "00" represents at-large states and "98" represents
non-voting delegate jurisdictions (DC + territories). Both are normalized
to district 0 in our output for UI uniformity.
"""

from __future__ import annotations

import csv
import json
import sys
from collections import defaultdict
from pathlib import Path


def _normalize_cd(raw: str) -> int:
    s = raw.strip().lstrip("0") or "0"
    n = int(s)
    if n == 98:  # HUD non-voting delegate code
        return 0
    return n


def build(source_csv: Path, output_json: Path) -> None:
    by_zip: dict[str, dict[str, set[int]]] = defaultdict(lambda: {"state": "", "districts": set()})
    with source_csv.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            zip_code = (row.get("ZIP") or "").zfill(5)
            state = (row.get("STATE") or "").upper()
            cd_raw = row.get("CD") or "0"
            if not zip_code or not state:
                continue
            entry = by_zip[zip_code]
            entry["state"] = state
            entry["districts"].add(_normalize_cd(cd_raw))

    output: dict[str, dict[str, object]] = {}
    for zip_code, entry in by_zip.items():
        output[zip_code] = {
            "state": entry["state"],
            "districts": sorted(entry["districts"]),
        }

    output_json.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, separators=(",", ":"))


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: build_zip_crosswalk.py <hud_csv> <out_json>", file=sys.stderr)
        return 2
    build(Path(argv[1]), Path(argv[2]))
    print(f"wrote {argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
