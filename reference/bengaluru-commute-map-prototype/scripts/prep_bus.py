"""Turn BMTC GTFS into compact route patterns for a browser.

A pattern = one exact sequence of stops. Routes with the same sequence are merged
(their buses add up, so the gap between buses gets shorter).
Output bus.json:
  s:  flat [lat, lon, ...] as ints (degrees * 1e5), each the difference from the previous stop
  n:  stop names
  p:  [[names, gapMorningMin|0, gapEveningMin|0, [stop idx, as differences], [ride time in tenths of a minute, as differences]], ...]
"""
import json, sys
import pandas as pd, numpy as np

D = sys.argv[1]   # folder with st.pkl, trips.pkl and gtfs/
OUT = sys.argv[2]
st = pd.read_pickle(f"{D}/st.pkl")
trips = pd.read_pickle(f"{D}/trips.pkl")
routes = pd.read_csv(f"{D}/gtfs/routes.txt")
stops = pd.read_csv(f"{D}/gtfs/stops.txt")
rname = dict(zip(routes.route_id, routes.route_short_name.astype(str)))
t2r = dict(zip(trips.trip_id, trips.route_id))

st = st.sort_values(["trip_id", "stop_sequence"])
MW = (7 * 3600, 11 * 3600)        # morning window for counting buses (4 h)
EW = (19.5 * 3600, 22 * 3600)     # evening window (2.5 h)

pat = {}   # seq tuple -> dict
for tid, g in st.groupby("trip_id", sort=False):
    seq = tuple(g.stop_id.tolist())
    if len(seq) < 2:
        continue
    t = g.t.to_numpy()
    t0 = t[0]
    # count a trip in a window by the time it is halfway along its route
    mid = (t[0] + t[-1]) / 2
    p = pat.setdefault(seq, dict(names={}, m=0, e=0, cum=[]))
    r = rname[t2r[tid]]
    p["names"][r] = p["names"].get(r, 0) + 1
    if MW[0] <= mid < MW[1]:
        p["m"] += 1
        p["cum"].append(t - t0)
    elif EW[0] <= mid < EW[1]:
        p["e"] += 1
        if len(p["cum"]) < 3:
            p["cum"].append(t - t0)

keep = {k: v for k, v in pat.items() if v["m"] or v["e"]}
print("patterns", len(pat), "running in our windows", len(keep))

used = list({s for k in keep for s in k})
_ll = stops.set_index("stop_id")[["stop_lat", "stop_lon"]].to_dict("index")
def _key(s):
    la, lo = _ll[s]["stop_lat"], _ll[s]["stop_lon"]
    row = int(la / 0.01)
    return (row, lo if row % 2 == 0 else -lo)   # snake order by 1 km rows
used.sort(key=_key)
idx = {s: i for i, s in enumerate(used)}
sdf = stops.set_index("stop_id").loc[used]
flat = []
pla = plo = 0
for la, lo in zip(sdf.stop_lat, sdf.stop_lon):
    a, b = int(round(la * 1e5)), int(round(lo * 1e5))
    flat += [a - pla, b - plo]
    pla, plo = a, b

out_p = []
for seq, v in keep.items():
    cum = np.median(np.vstack(v["cum"]), axis=0)
    cum = np.maximum.accumulate(cum)
    names = sorted(v["names"], key=lambda r: -v["names"][r])
    label = " / ".join(names[:3]) + (f" +{len(names) - 3}" if len(names) > 3 else "")
    gm = round(240 / v["m"], 1) if v["m"] else 0
    ge = round(150 / v["e"], 1) if v["e"] else 0
    ids = [idx[s] for s in seq]
    cs = [int(round(c / 6)) for c in cum]
    out_p.append([label, gm, ge, [ids[0]] + [b - a for a, b in zip(ids, ids[1:])], [cs[0]] + [b - a for a, b in zip(cs, cs[1:])]])

names = [str(n).strip() for n in sdf.stop_name]
json.dump(dict(s=flat, n=names, p=out_p, feed="BMTC GTFS, weekday service, version 20261109 (unofficial feed by Vonter)"),
          open(OUT, "w"), separators=(",", ":"), ensure_ascii=False)
gm = [p[1] for p in out_p if p[1]]
print("pattern routes by morning gap: <=15 min", sum(1 for g in gm if g <= 15), "<=30", sum(1 for g in gm if g <= 30), "of", len(gm))
print("stops", len(used), "patterns kept", len(out_p), "median morning gap", np.median(gm), "min")
print("stop-visits", sum(len(p[3]) for p in out_p))
