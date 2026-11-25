"""Read the prototype's data.json / bus.json and convert them to this project's data/ format."""
import json
from pathlib import Path

from build_bus import short_label

REF = Path(__file__).resolve().parent.parent / "reference" / "bengaluru-commute-map-prototype" / "prototype"


def metro_and_wards(path: Path = REF / "data.json"):
    d = json.load(open(path, encoding="utf-8"))
    metro = dict(
        feed=d["feed"],
        stations=[dict(id=s["id"], name=s["n"], lat=s["lat"], lon=s["lon"], lines=s["lines"]) for s in d["stations"]],
        lines={k: dict(name=v["name"], color=v["color"], longName=v["long"], path=v["path"]) for k, v in d["lines"].items()},
        edges=d["edges"],
        headways=d["hw"],
    )
    wards = dict(
        source="BBMP wards, DataMeet Municipal_Spatial_Data (CC BY 4.0)",
        wards=[dict(no=w["no"], name=w["n"], centre=w["c"], areaKm2=w["a"], rings=w["p"], metro=w["sd"])
               for w in d["wards"]],
        outline=d["outline"],
    )
    places = None
    if "places" in d:
        pl = d["places"]
        places = dict(
            source=pl["src"], base=pl["base"],
            categories=[dict(key=k, label=pl["label"][i], short=pl["short"][i], one=pl["one"][i])
                        for i, k in enumerate(pl["order"])],
            wards=[dict(no=w["no"], count=w["nb"], nearest10m=w["nr"]) for w in d["wards"]],
        )
    return metro, wards, places


def bus(path: Path = REF / "bus.json"):
    raw = json.load(open(path, encoding="utf-8"))
    n = len(raw["n"])
    lat, lon = [], []
    a = b = 0
    for i in range(n):
        a += raw["s"][2 * i]
        b += raw["s"][2 * i + 1]
        lat.append(round(a / 1e5, 5))
        lon.append(round(b / 1e5, 5))
    pats = []
    for label, gm, ge, ids, cs in raw["p"]:
        stops, cum, x, c = [], [], 0, 0
        for v in ids:
            x += v
            stops.append(x)
        for v in cs:
            c += v
            cum.append(c)
        full = label.replace(" / ", "\u0000").split("\u0000")
        pats.append(dict(label=short_label(label), routes=full, gapMorning=gm, gapEvening=ge, stops=stops, cum=cum))
    return dict(feed=raw["feed"], stops=dict(lat=lat, lon=lon, name=raw["n"]), patterns=pats)
