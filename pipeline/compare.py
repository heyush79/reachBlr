"""Compare data/ with the prototype's data files and report every difference.

Routing data (metro, wards, bus) should match exactly. Place counts may drift, because
OpenStreetMap changes every day.

Usage: python pipeline/compare.py [data_dir]
"""
import gzip
import json
import sys
from pathlib import Path

import reference_format as ref

DATA = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "data"


def same(name, a, b):
    ok = a == b
    print(f"  {'OK  ' if ok else 'DIFF'} {name}")
    return ok


def main() -> int:
    r_metro, r_wards, r_places = ref.metro_and_wards()
    r_bus = ref.bus()
    metro = json.load(open(DATA / "metro.json"))
    wards = json.load(open(DATA / "wards.json"))
    bus = json.load(gzip.open(DATA / "bus.json.gz"))
    bad = 0

    print("Metro")
    for k in ["stations", "lines", "edges", "headways"]:
        bad += not same(k, metro[k], r_metro[k])

    print("Wards")
    bad += not same("count (243)", len(wards["wards"]), len(r_wards["wards"]))
    for k in ["no", "name", "centre", "areaKm2", "rings", "metro"]:
        bad += not same(k, [w[k] for w in wards["wards"]], [w[k] for w in r_wards["wards"]])
    bad += not same("outline", wards["outline"], r_wards["outline"])

    print("Bus")
    print(f"  stops {len(bus['stops']['name'])} (prototype {len(r_bus['stops']['name'])}), "
          f"patterns {len(bus['patterns'])} (prototype {len(r_bus['patterns'])})")
    # Stops at the exact same spot may be listed in a different order, so compare stops by
    # (lat, lon, name) rather than by their position in the list.
    def key(d, i):
        s = d["stops"]
        return (s["lat"][i], s["lon"][i], s["name"][i])
    n_ours, n_ref = len(bus["stops"]["name"]), len(r_bus["stops"]["name"])
    bad += not same("same set of stops", sorted(key(bus, i) for i in range(n_ours)),
                    sorted(key(r_bus, i) for i in range(n_ref)))
    bad += not same("pattern stop sequences",
                    [[key(bus, s) for s in p["stops"]] for p in bus["patterns"]],
                    [[key(r_bus, s) for s in p["stops"]] for p in r_bus["patterns"]])
    for k in ["label", "gapMorning", "gapEvening", "cum"]:
        bad += not same(f"patterns.{k}", [p[k] for p in bus["patterns"]], [p[k] for p in r_bus["patterns"]])

    pf = DATA / "places.json"
    if pf.exists() and r_places:
        places = json.load(open(pf))
        print(f"Places (data as of {places['base']}, prototype {r_places['base']}; drift is expected)")
        for k, cat in enumerate(places["categories"]):
            a = [w["count"][k] for w in places["wards"]]
            b = [w["count"][k] for w in r_places["wards"]]
            diff = sum(1 for x, y in zip(a, b) if x != y)
            print(f"  {cat['key']:9s} total {sum(a):6d} (prototype {sum(b):6d}), wards that differ {diff:3d}, "
                  f"wards with none {sum(1 for x in a if x == 0):3d} (prototype {sum(1 for x in b if x == 0)})")

    print("\nRouting data matches the prototype." if not bad else f"\n{bad} routing differences.")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
