"""Turn the BMRCL GTFS feed and the BBMP ward map into one small JSON file."""
import csv, json, statistics, sys
from collections import defaultdict
from shapely.geometry import shape, mapping
from shapely.ops import unary_union

G = sys.argv[1]          # gtfs folder
WARDS = sys.argv[2]      # ward geojson
OUT = sys.argv[3]

def rows(name):
    with open(f"{G}/{name}", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))

def secs(t):
    h, m, s = map(int, t.split(":"))
    return h * 3600 + m * 60 + s

# stations
parent = {}
stations = {}
for r in rows("stops.txt"):
    if r["location_type"] == "1":
        stations[r["stop_id"]] = dict(id=r["stop_id"], n=r["stop_name"].strip(),
                                      lat=round(float(r["stop_lat"]), 5),
                                      lon=round(float(r["stop_lon"]), 5), lines=set())
for r in rows("stops.txt"):
    if r["parent_station"]:
        parent[r["stop_id"]] = r["parent_station"]
    elif r["location_type"] == "1":
        parent[r["stop_id"]] = r["stop_id"]

routes = {r["route_id"]: dict(name=r["route_short_name"], color="#" + r["route_color"],
                              long=r["route_long_name"]) for r in rows("routes.txt")}
trips = {r["trip_id"]: r for r in rows("trips.txt") if r["service_id"] == "weekday"}

st = defaultdict(list)
for r in rows("stop_times.txt"):
    if r["trip_id"] in trips:
        st[r["trip_id"]].append((int(r["stop_sequence"]), parent[r["stop_id"]], secs(r["departure_time"]), secs(r["arrival_time"])))

edge_times = defaultdict(list)
deps = defaultdict(list)          # (route, dir, station) -> departure seconds
shape_len = defaultdict(int)
for tid, seq in st.items():
    seq.sort()
    t = trips[tid]
    rid, d = t["route_id"], t["direction_id"]
    for i, (_, s, dep, arr) in enumerate(seq):
        stations[s]["lines"].add(rid)
        deps[(rid, d, s)].append(dep)
        if i + 1 < len(seq):
            nxt = seq[i + 1]
            edge_times[(rid, s, nxt[1])].append(nxt[2] - dep)   # ride time incl. dwell at next stop
    shape_len[(rid, t["shape_id"])] = max(shape_len[(rid, t["shape_id"])], len(seq))

edges = [[rid, a, b, round(statistics.median(v))] for (rid, a, b), v in edge_times.items()]

def headway(times, lo, hi):
    ts = sorted(x for x in times if lo <= x < hi)
    if len(ts) < 3:
        return None
    gaps = [b - a for a, b in zip(ts, ts[1:]) if b > a]
    return round(statistics.median(gaps)) if gaps else None

# headway per station per route: take the worse of the two directions' medians
hw = defaultdict(dict)
for (rid, d, s), times in deps.items():
    pk = headway(times, 8 * 3600, 11 * 3600)
    op = headway(times, 20 * 3600, 22 * 3600)
    cur = hw[s].get(rid, [None, None])
    cur = [max(filter(None, [cur[0], pk]), default=None), max(filter(None, [cur[1], op]), default=None)]
    hw[s][rid] = cur

# line drawing: longest shape per route
shp = defaultdict(list)
for r in rows("shapes.txt"):
    shp[r["shape_id"]].append((int(r["shape_pt_sequence"]), float(r["shape_pt_lat"]), float(r["shape_pt_lon"])))
from shapely.geometry import LineString
lines_out = {}
for rid, info in routes.items():
    cands = [sid for (r, sid) in shape_len if r == rid]
    best = max(cands, key=lambda sid: len(shp[sid]))
    pts = [(lon, lat) for _, lat, lon in sorted(shp[best])]
    ls = LineString(pts).simplify(0.0002)
    info["path"] = [[round(y, 5), round(x, 5)] for x, y in ls.coords]
    lines_out[rid] = info

# wards
wj = json.load(open(WARDS))
wards = []
geoms = []
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
    wards.append(dict(n=name, no=f["properties"]["KGISWardNo"], c=[round(c.y, 5), round(c.x, 5)], p=rings,
                      a=round(g.area * 111.32 * 111.32 * 0.974, 2)))

# distance from each station to the nearest edge of each ward (0 if the station is inside), in metres
import math
from shapely.ops import transform
from shapely.geometry import Point
COS = math.cos(math.radians(12.97))
to_m = lambda x, y, z=None: (x * COS * 111320, y * 110574)
st_list = [s for s in stations.values() if s["lines"]]
st_idx = {s["id"]: i for i, s in enumerate(st_list)}
for w, g in zip(wards, geoms):
    gm = transform(to_m, g)
    sd = []
    for s in st_list:
        d = gm.distance(Point(to_m(s["lon"], s["lat"])))
        if d <= 7000:
            sd.append([st_idx[s["id"]], round(d)])
    w["sd"] = sd

outline = unary_union(geoms).simplify(0.001)
op = [outline] if outline.geom_type == "Polygon" else list(outline.geoms)
outline_rings = [[[round(y, 4), round(x, 4)] for x, y in p.exterior.coords] for p in op if p.area > 1e-5]

out = dict(
    stations=[dict(s, lines=sorted(s["lines"])) for s in st_list],
    lines=lines_out, edges=edges, hw=hw, wards=wards, outline=outline_rings,
    feed="BMRCL GTFS, weekday service, version 20260817 (unofficial feed by Vonter)",
)
json.dump(out, open(OUT, "w"), separators=(",", ":"))
print("stations", len(out["stations"]), "edges", len(edges), "wards", len(wards))
