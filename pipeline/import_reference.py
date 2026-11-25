"""Safety net: build data/ straight from the prototype's data.json and bus.json.

Use this only if the pinned feeds can no longer be downloaded. The normal route is
fetch.py -> build_metro.py -> build_bus.py -> build_places.py, which gives the same routing data
(compare.py checks that).

Usage: python pipeline/import_reference.py [out_dir]
"""
import gzip
import json
import sys
from pathlib import Path

import reference_format as ref


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "data"
    out.mkdir(parents=True, exist_ok=True)
    metro, wards, places = ref.metro_and_wards()
    bus = ref.bus()
    json.dump(metro, open(out / "metro.json", "w"), separators=(",", ":"))
    json.dump(wards, open(out / "wards.json", "w"), separators=(",", ":"))
    if places:
        json.dump(places, open(out / "places.json", "w"), separators=(",", ":"))
    with open(out / "bus.json.gz", "wb") as raw_f:
        with gzip.GzipFile(fileobj=raw_f, mode="wb", mtime=0) as f:
            f.write(json.dumps(bus, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
    print(f"wrote {out}: {len(metro['stations'])} stations, {len(wards['wards'])} wards, "
          f"{len(bus['stops']['name'])} bus stops, {len(bus['patterns'])} patterns")


if __name__ == "__main__":
    main()
