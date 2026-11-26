"""Count everyday places per ward from OpenStreetMap, through the Overpass API.

Writes data/places.json: for each ward and each place type, how many are inside the ward and how far
the nearest one is from the ward centre (in units of 10 m). The ward centre is its centroid when that lies
inside the ward, and otherwise a point inside it (the same rule as the app's ward centre). The prototype
always used the centroid, which falls outside 9 oddly shaped wards.

Being a good citizen of the Overpass servers (lessons from the prototype):
- Use the maps.mail.ru mirror. The main server (overpass-api.de) refuses AI-agent traffic, so this
  script never calls it.
- Small requests: the city is split into 9 boxes, each query declares [timeout:25], and a box that
  still fails is split into 4 smaller boxes.
- Up to 3 tries per box with growing pauses, and a pause between every request.
- If a server refuses automated requests (HTTP 403, or says so in its reply), the script stops.
  It does not retry, switch servers, or disguise itself.
- Raw replies are saved in pipeline/raw/osm/, so re-running the counting step needs no network.
  Use --refetch to download again.

Tag rules: the same as the prototype's (checked ward by ward against the prototype's data.json on the
saved replies: gyms, schools and groceries matched in every ward).

Unlike the prototype, places drawn as several pieces (OSM "relations") are counted too, as well as
points (nodes) and outlines (ways). The prototype left them out, which missed about 70 big parks, many
of them lake parks. --like-prototype counts and measures the prototype's way, to reproduce its numbers.

The data is © OpenStreetMap contributors, available under the ODbL. The counts in places.json are
a derived database, so they carry the same licence and credit.

Usage: python pipeline/build_places.py [--refetch] [--like-prototype]
"""
import argparse
import json
import math
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import requests
from shapely.geometry import Point, shape
from shapely.strtree import STRtree

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
CACHE = RAW / "osm"
OUT = HERE.parent / "data"

MIRROR = "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
UA = {"User-Agent": "bengaluru-commute-map-pipeline/1.0 (open-source data script; small, infrequent requests)"}
PAUSE_S = 3
TRIES = 3
MAX_DEPTH = 2          # a box may be split into 4, and each of those into 4 again

# What counts as each place type: a place matches if ANY listed key has one of the listed values.
# A place counts for every type it matches.
CATEGORIES = [
    dict(key="gym", label="Gyms", short="Gym", one="a gym",
         tags={"leisure": ["fitness_centre"], "sport": ["fitness"]}),
    dict(key="park", label="Parks", short="Park", one="a park",
         tags={"leisure": ["park"]}),
    dict(key="health", label="Hospitals and clinics", short="Hospital or clinic", one="a hospital or clinic",
         tags={"amenity": ["hospital", "clinic"]}),
    dict(key="school", label="Schools", short="School", one="a school",
         tags={"amenity": ["school"]}),
    dict(key="grocery", label="Grocery shops", short="Grocery", one="a grocery shop",
         tags={"shop": ["supermarket", "convenience", "greengrocer"]}),
    dict(key="pharmacy", label="Pharmacies", short="Pharmacy", one="a pharmacy",
         tags={"amenity": ["pharmacy"]}),
    dict(key="food", label="Places to eat", short="Place to eat", one="a place to eat",
         tags={"amenity": ["restaurant", "fast_food", "cafe"]}),
]


class Refused(Exception):
    pass


def query_for(box):
    s, w, n, e = box
    bb = f"({s},{w},{n},{e})"
    keys = {}
    for c in CATEGORIES:
        for k, vals in c["tags"].items():
            keys.setdefault(k, set()).update(vals)
    parts = "".join(f'nwr["{k}"~"^({"|".join(sorted(v))})$"]{bb};' for k, v in sorted(keys.items()))
    return f"[out:json][timeout:25];({parts});out tags center;"


def fetch_box(box, name):
    path = CACHE / f"{name}.json"
    if path.exists():
        return json.load(open(path))
    q = query_for(box)
    for attempt in range(1, TRIES + 1):
        time.sleep(PAUSE_S)
        try:
            r = requests.post(MIRROR, data={"data": q}, headers=UA, timeout=60)
        except requests.RequestException as ex:
            print(f"    {name}: try {attempt} failed ({ex.__class__.__name__})")
            time.sleep(10 * attempt)
            continue
        text = r.text[:400].lower()
        if r.status_code == 403 or "automated" in text or "not allowed" in text:
            raise Refused(f"{MIRROR} refused the request (HTTP {r.status_code}). Stopping, as asked.")
        if r.status_code == 200:
            try:
                data = r.json()
            except ValueError:
                data = None
            if data is not None and "remark" not in data:
                json.dump(data, open(path, "w"))
                return data
            remark = (data or {}).get("remark", "reply was not JSON")
            print(f"    {name}: try {attempt}: server remark: {remark[:120]}")
        else:
            print(f"    {name}: try {attempt}: HTTP {r.status_code}")
        time.sleep(10 * attempt)
    return None


def split4(box):
    s, w, n, e = box
    ms, mw = (s + n) / 2, (w + e) / 2
    return [(s, w, ms, mw), (s, mw, ms, e), (ms, w, n, mw), (ms, mw, n, e)]


def fetch_all(bbox):
    s, w, n, e = bbox
    boxes = []
    for i in range(3):
        for j in range(3):
            boxes.append((f"box{i}{j}", (round(s + (n - s) * i / 3, 5), round(w + (e - w) * j / 3, 5),
                                         round(s + (n - s) * (i + 1) / 3, 5), round(w + (e - w) * (j + 1) / 3, 5))))
    elements = {}

    def run(name, box, depth):
        print(f"  {name} {box}")
        data = fetch_box(box, name)
        if data is None:
            if depth >= MAX_DEPTH:
                raise RuntimeError(f"{name} kept failing even after splitting; try again later.")
            print(f"    {name}: splitting into 4")
            for k, sub in enumerate(split4(box)):
                run(f"{name}_{k}", sub, depth + 1)
            return
        for el in data.get("elements", []):
            elements[(el["type"], el["id"])] = el

    for name, box in boxes:
        run(name, box, 0)
    return list(elements.values())


def point_of(el):
    if el["type"] == "node":
        return el["lat"], el["lon"]
    c = el.get("center")
    return (c["lat"], c["lon"]) if c else None


def classify(el):
    tags = el.get("tags", {})
    return [k for k, c in enumerate(CATEGORIES)
            if any(tags.get(t) in vals for t, vals in c["tags"].items())]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--refetch", action="store_true", help="ignore saved replies and download again")
    ap.add_argument("--like-prototype", action="store_true",
                    help="count the prototype's way, to reproduce its numbers: leave out OSM relations, "
                         "and measure nearest places from the centroid even when it falls outside the ward")
    a = ap.parse_args()
    count_relations = not a.like_prototype

    wards = json.load(open(OUT / "wards.json"))["wards"]
    geo = json.load(open(RAW / "wards" / "BBMP.geojson", encoding="utf-8"))
    geoms = [shape(f["geometry"]).buffer(0) for f in geo["features"]]
    assert len(geoms) == len(wards)

    if a.refetch and CACHE.exists():
        for f in CACHE.glob("*.json"):
            f.unlink()
    CACHE.mkdir(parents=True, exist_ok=True)

    minx = min(g.bounds[0] for g in geoms)
    miny = min(g.bounds[1] for g in geoms)
    maxx = max(g.bounds[2] for g in geoms)
    maxy = max(g.bounds[3] for g in geoms)
    pad = 0.06   # about 6 km, so "nearest" also sees places just outside the city
    bbox = (round(miny - pad, 4), round(minx - pad, 4), round(maxy + pad, 4), round(maxx + pad, 4))

    print("Fetching from", MIRROR)
    try:
        elements = fetch_all(bbox)
    except Refused as ex:
        print(ex)
        return 2
    print("elements", len(elements))

    pts = [[] for _ in CATEGORIES]
    for el in elements:
        if el["type"] == "relation" and not count_relations:
            continue
        p = point_of(el)
        if not p:
            continue
        for k in classify(el):
            pts[k].append(p)

    cos = math.cos(math.radians(12.97))
    tree = STRtree(geoms)
    count = [[0] * len(CATEGORIES) for _ in wards]
    nearest = [[-1] * len(CATEGORIES) for _ in wards]
    for k, plist in enumerate(pts):
        for la, lo in plist:
            for wi in tree.query(Point(lo, la), predicate="within"):
                count[wi][k] += 1
        if not plist:
            continue
        xs = [lo * cos * 111320 for la, lo in plist]
        ys = [la * 110574 for la, lo in plist]
        for wi, g in enumerate(geoms):
            # the centroid, or a point inside the ward where the centroid falls outside it (9 wards)
            c = g.centroid
            if not a.like_prototype and not g.contains(c):
                c = g.representative_point()
            cx, cy = c.x * cos * 111320, c.y * 110574
            d = min(math.hypot(x - cx, y - cy) for x, y in zip(xs, ys))
            nearest[wi][k] = round(d / 10)

    base = max((f.stat().st_mtime for f in CACHE.glob("*.json")), default=time.time())
    osm_base = None
    for f in CACHE.glob("*.json"):
        ts = json.load(open(f)).get("osm3s", {}).get("timestamp_osm_base")
        if ts and (osm_base is None or ts < osm_base):
            osm_base = ts
    out = dict(
        source="OpenStreetMap via Overpass API (maps.mail.ru mirror). © OpenStreetMap contributors, ODbL.",
        base=osm_base or datetime.fromtimestamp(base, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        relations=count_relations,
        nearestFrom="centroid" if a.like_prototype else "ward centre (centroid, or a point inside the ward if it falls outside)",
        categories=[{k: c[k] for k in ("key", "label", "short", "one", "tags")} for c in CATEGORIES],
        wards=[dict(no=w["no"], count=count[i], nearest10m=nearest[i]) for i, w in enumerate(wards)],
    )
    json.dump(out, open(OUT / "places.json", "w"), separators=(",", ":"))
    for k, c in enumerate(CATEGORIES):
        print(f"  {c['key']:9s} {len(pts[k]):6d} places, wards with none: {sum(1 for r in count if r[k] == 0)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
