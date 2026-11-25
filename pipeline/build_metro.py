"""Build data/metro.json and data/wards.json from the Namma Metro GTFS and the BBMP ward map.

Same maths as the prototype's prep.py (reference/.../scripts/prep.py):
- ride time between two stations = median over weekday trips (includes the dwell at the next stop)
- train gap per station and line = median gap between departures, 8-11 am and 8-10 pm,
  the worse of the two directions
- each ward lists the metro stations within 7 km of its nearest edge (0 m if inside)

Usage: python pipeline/build_metro.py [raw_dir] [out_dir]
"""
import csv
import json
import math
import statistics
import sys
from collections import defaultdict
from pathlib import Path

from shapely.geometry import LineString, Point, shape
from shapely.ops import transform, unary_union

HERE = Path(__file__).resolve().parent
RAW = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "raw"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE.parent / "data"
G = RAW / "bmrcl"
WARDS = RAW / "wards" / "BBMP.geojson"

SERVICE = "weekday"
MORNING = (8 * 3600, 11 * 3600)
EVENING = (20 * 3600, 22 * 3600)
STATION_WARD_RADIUS_M = 7000


def rows(name):
    with open(G / name, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def secs(t):
    h, m, s = map(int, t.split(":"))
    return h * 3600 + m * 60 + s


def headway(times, lo, hi):
    ts = sorted(x for x in times if lo <= x < hi)
    if len(ts) < 3:
        return None
    gaps = [b - a for a, b in zip(ts, ts[1:]) if b > a]
    return round(statistics.median(gaps)) if gaps else None


def build_metro():
    stations, parent = {}, {}
    stop_rows = rows("stops.txt")
    for r in stop_rows:
        if r["location_type"] == "1":
            stations[r["stop_id"]] = dict(id=r["stop_id"], name=r["stop_name"].strip(),
                                          lat=round(float(r["stop_lat"]), 5),
                                          lon=round(float(r["stop_lon"]), 5), lines=set())
    for r in stop_rows:
        if r["parent_station"]:
            parent[r["stop_id"]] = r["parent_station"]
        elif r["location_type"] == "1":
            parent[r["stop_id"]] = r["stop_id"]

    routes = {r["route_id"]: dict(name=r["route_short_name"], color="#" + r["route_color"],
                                  longName=r["route_long_name"]) for r in rows("routes.txt")}
    trips = {r["trip_id"]: r for r in rows("trips.txt") if r["service_id"] == SERVICE}

    st = defaultdict(list)
    for r in rows("stop_times.txt"):
        if r["trip_id"] in trips:
            st[r["trip_id"]].append((int(r["stop_sequence"]), parent[r["stop_id"]],
                                     secs(r["departure_time"]), secs(r["arrival_time"])))

    edge_times = defaultdict(list)
    deps = defaultdict(list)          # (route, direction, station) -> departure seconds
    shape_len = defaultdict(int)
    for tid, seq in st.items():
        seq.sort()
        t = trips[tid]
        rid, d = t["route_id"], t["direction_id"]
        for i, (_, s, dep, _arr) in enumerate(seq):
            stations[s]["lines"].add(rid)
            deps[(rid, d, s)].append(dep)
            if i + 1 < len(seq):
                nxt = seq[i + 1]
                edge_times[(rid, s, nxt[1])].append(nxt[2] - dep)
        shape_len[(rid, t["shape_id"])] = max(shape_len[(rid, t["shape_id"])], len(seq))

    edges = [[rid, a, b, round(statistics.median(v))] for (rid, a, b), v in edge_times.items()]

    hw = defaultdict(dict)
    for (rid, _d, s), times in deps.items():
        pk = headway(times, *MORNING)
        op = headway(times, *EVENING)
        cur = hw[s].get(rid, [None, None])
        hw[s][rid] = [max(filter(None, [cur[0], pk]), default=None),
                      max(filter(None, [cur[1], op]), default=None)]

    shp = defaultdict(list)
    for r in rows("shapes.txt"):
        shp[r["shape_id"]].append((int(r["shape_pt_sequence"]), float(r["shape_pt_lat"]),
                                   float(r["shape_pt_lon"])))
    lines = {}
    for rid, info in routes.items():
        cands = [sid for (r, sid) in shape_len if r == rid]
        best = max(cands, key=lambda sid: len(shp[sid]))
        pts = [(lon, lat) for _, lat, lon in sorted(shp[best])]
        ls = LineString(pts).simplify(0.0002)
        info["path"] = [[round(y, 5), round(x, 5)] for x, y in ls.coords]
        lines[rid] = info

    st_list = [s for s in stations.values() if s["lines"]]
    feed = next(iter(rows("feed_info.txt")), {}).get("feed_version", "")
    return st_list, lines, edges, hw, feed


def build_wards(st_list):
    wj = json.load(open(WARDS, encoding="utf-8"))
    wards, geoms = [], []
    for f in wj["features"]:
        g = shape(f["geometry"]).buffer(0)
        geoms.append(g)
        gs = g.simplify(0.0006, preserve_topology=True)
        polys = [gs] if gs.geom_type == "Polygon" else list(gs.geoms)
        rings = [[[round(y, 4), round(x, 4)] for x, y in p.exterior.coords] for p in polys if p.area > 1e-7]
        c = g.representative_point() if not g.contains(g.centroid) else g.centroid
        name = f["properties"]["KGISWardName"].strip()
        if name.lower().endswith(" ward"):
            name = name[:-5]
        wards.append(dict(no=f["properties"]["KGISWardNo"], name=name,
                          centre=[round(c.y, 5), round(c.x, 5)],
                          areaKm2=round(g.area * 111.32 * 111.32 * 0.974, 2),
                          rings=rings))

    cos = math.cos(math.radians(12.97))
    to_m = lambda x, y, z=None: (x * cos * 111320, y * 110574)  # noqa: E731
    for w, g in zip(wards, geoms):
        gm = transform(to_m, g)
        near = []
        for i, s in enumerate(st_list):
            d = gm.distance(Point(to_m(s["lon"], s["lat"])))
            if d <= STATION_WARD_RADIUS_M:
                near.append([i, round(d)])
        w["metro"] = near

    outline = unary_union(geoms).simplify(0.001)
    op = [outline] if outline.geom_type == "Polygon" else list(outline.geoms)
    outline_rings = [[[round(y, 4), round(x, 4)] for x, y in p.exterior.coords] for p in op if p.area > 1e-5]
    return wards, outline_rings


def main():
    st_list, lines, edges, hw, feed = build_metro()
    wards, outline = build_wards(st_list)
    OUT.mkdir(parents=True, exist_ok=True)
    metro = dict(
        feed=f"BMRCL GTFS, weekday service, version {feed} (unofficial feed by Vonter, ODbL)",
        stations=[dict(s, lines=sorted(s["lines"])) for s in st_list],
        lines=lines,
        edges=edges,
        headways=hw,
    )
    json.dump(metro, open(OUT / "metro.json", "w"), separators=(",", ":"))
    json.dump(dict(source="BBMP wards, DataMeet Municipal_Spatial_Data (CC BY 4.0)",
                   wards=wards, outline=outline),
              open(OUT / "wards.json", "w"), separators=(",", ":"))
    print("stations", len(st_list), "edges", len(edges), "wards", len(wards))


if __name__ == "__main__":
    main()
