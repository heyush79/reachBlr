"""Build data/bus.json.gz from the BMTC bus GTFS.

Steps (same as the prototype's prep_bus.py, plus the step that made its st.pkl / trips.pkl):
1. Keep trips that run on one weekday (Tuesday by default; this matches the prototype exactly:
   5,580 patterns over 9,394 stops).
2. A pattern = one exact sequence of stops. Trips with the same sequence are merged, so their
   buses add up and the gap between buses gets shorter.
3. A trip is counted in the morning window (7-11 am) or evening window (7:30-10 pm) by the time
   it is halfway along its route. Gap = window length / number of buses.
4. Ride time along a pattern = median of the counted trips' times since the first stop.

Output (gzipped JSON):
  stops:    {lat: [...], lon: [...], name: [...]}  sorted in 1 km snake rows (keeps nearby stops together)
  patterns: [{label, routes, gapMorning, gapEvening, stops: [stop index], cum: [tenths of a minute]}]
            gap 0 = no buses in that window

Usage: python pipeline/build_bus.py [--day tuesday] [raw_dir] [out_dir]
"""
import argparse
import gzip
import json
import re
from pathlib import Path

import numpy as np
import pandas as pd

HERE = Path(__file__).resolve().parent
MW = (7 * 3600, 11 * 3600)        # morning window (4 h)
EW = (19.5 * 3600, 22 * 3600)     # evening window (2.5 h)


def secs(col: pd.Series) -> pd.Series:
    p = col.str.split(":", expand=True).astype(int)
    return p[0] * 3600 + p[1] * 60 + p[2]


def short_label(label: str) -> str:
    """'KBS-1I BEMLF-KDG / 500-D' -> 'KBS-1I / 500-D' (same rule as the prototype's browser code)."""
    names = []
    for x in re.sub(r" \+\d+$", "", label).split(" / "):
        first = x.strip().split(" ")[0]
        if first not in names:
            names.append(first)
    return " / ".join(names[:3]) + (f" +{len(names) - 3}" if len(names) > 3 else "")


def load(raw: Path, day: str):
    g = raw / "bmtc"
    cal = pd.read_csv(g / "calendar.txt", dtype=str)
    services = set(cal[cal[day].astype(int) == 1].service_id)
    trips = pd.read_csv(g / "trips.txt", dtype=str, usecols=["route_id", "service_id", "trip_id"])
    trips = trips[trips.service_id.isin(services)]
    st = pd.read_csv(g / "stop_times.txt", dtype=str,
                     usecols=["trip_id", "departure_time", "stop_id", "stop_sequence"])
    st = st[st.trip_id.isin(set(trips.trip_id))].copy()
    st["t"] = secs(st.departure_time)
    st["stop_sequence"] = st.stop_sequence.astype(int)
    routes = pd.read_csv(g / "routes.txt", dtype=str)
    stops = pd.read_csv(g / "stops.txt", dtype={"stop_id": str, "stop_name": str})
    feed = pd.read_csv(g / "feed_info.txt", dtype=str).feed_version.iloc[0]
    return st, trips, routes, stops, feed


def build(st, trips, routes, stops):
    rname = dict(zip(routes.route_id, routes.route_short_name.astype(str)))
    t2r = dict(zip(trips.trip_id, trips.route_id))
    st = st.sort_values(["trip_id", "stop_sequence"])

    pat = {}
    for tid, g in st.groupby("trip_id", sort=False):
        seq = tuple(g.stop_id.tolist())
        if len(seq) < 2:
            continue
        t = g.t.to_numpy()
        t0 = t[0]
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
    ll = stops.set_index("stop_id")[["stop_lat", "stop_lon"]].to_dict("index")

    def snake(s):
        la, lo = ll[s]["stop_lat"], ll[s]["stop_lon"]
        row = int(la / 0.01)
        # stop_id breaks ties so the order is the same on every run
        return (row, lo if row % 2 == 0 else -lo, s)

    used.sort(key=snake)
    idx = {s: i for i, s in enumerate(used)}
    sdf = stops.set_index("stop_id").loc[used]

    out_p = []
    for seq, v in keep.items():
        cum = np.median(np.vstack(v["cum"]), axis=0)
        cum = np.maximum.accumulate(cum)
        names = sorted(v["names"], key=lambda r: -v["names"][r])
        label = " / ".join(names[:3]) + (f" +{len(names) - 3}" if len(names) > 3 else "")
        out_p.append(dict(
            label=short_label(label),
            routes=names,
            gapMorning=round(240 / v["m"], 1) if v["m"] else 0,
            gapEvening=round(150 / v["e"], 1) if v["e"] else 0,
            stops=[idx[s] for s in seq],
            cum=[int(round(c / 6)) for c in cum],
        ))

    stops_out = dict(
        lat=[round(int(round(x * 1e5)) / 1e5, 5) for x in sdf.stop_lat],
        lon=[round(int(round(x * 1e5)) / 1e5, 5) for x in sdf.stop_lon],
        name=[str(n).strip() for n in sdf.stop_name],
    )
    return stops_out, out_p


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--day", default="tuesday",
                    choices=["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"])
    ap.add_argument("raw", nargs="?", default=str(HERE / "raw"))
    ap.add_argument("out", nargs="?", default=str(HERE.parent / "data"))
    a = ap.parse_args()
    st, trips, routes, stops, feed = load(Path(a.raw), a.day)
    stops_out, pats = build(st, trips, routes, stops)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    doc = dict(feed=f"BMTC GTFS, weekday ({a.day}) service, version {feed} (unofficial feed by Vonter, ODbL)",
               stops=stops_out, patterns=pats)
    # mtime=0 keeps the gzip bytes identical between runs
    with open(out / "bus.json.gz", "wb") as raw_f:
        with gzip.GzipFile(fileobj=raw_f, mode="wb", mtime=0) as f:
            f.write(json.dumps(doc, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
    gm = [p["gapMorning"] for p in pats if p["gapMorning"]]
    print("stops", len(stops_out["name"]), "patterns", len(pats), "median morning gap", np.median(gm), "min")


if __name__ == "__main__":
    main()
