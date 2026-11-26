# Data pipeline

Python scripts that download the source data and write the compact files in `../data/` that the backend loads at startup.
The files in `data/` are committed, so you only need this when you want to refresh or check the data.

## Run it

```bash
cd pipeline
python3.12 -m venv .venv && . .venv/bin/activate     # Python 3.10-3.13
pip install -r requirements.txt

python fetch.py          # downloads pinned versions into raw/ (about 100 MB, git-ignored)
python build_metro.py    # -> data/metro.json, data/wards.json
python build_bus.py      # -> data/bus.json.gz
python build_places.py   # -> data/places.json (asks the Overpass API; replies are saved in raw/osm/)
python compare.py        # checks data/ against the prototype in ../reference/
```

`compare.py` should end with `Routing data matches the prototype.` Small differences in place counts are normal, because OpenStreetMap is edited every day.

## Sources

Every download is pinned to a git commit and checked against a SHA-256 hash, so the results are repeatable.

| Data | Source | Version | Licence |
|---|---|---|---|
| Namma Metro timetable (GTFS) | [Vonter/bmrcl-gtfs](https://github.com/Vonter/bmrcl-gtfs), unofficial | commit `f402c4c`, 17 Aug 2026 | ODbL 1.0 |
| BMTC bus timetable (GTFS) | [Vonter/bmtc-gtfs](https://github.com/Vonter/bmtc-gtfs), unofficial, built from the Namma BMTC app | commit `4ced54e`, 9 Nov 2026 | ODbL 1.0 |
| 243 BBMP ward boundaries | [DataMeet Municipal_Spatial_Data](https://github.com/datameet/Municipal_Spatial_Data) | commit `0c3a2e3` | CC BY 4.0 |
| Gyms, parks, clinics and so on | [OpenStreetMap](https://www.openstreetmap.org/copyright) through the Overpass API (maps.mail.ru mirror) | date stored in `places.json` | ODbL 1.0, © OpenStreetMap contributors |

GTFS (General Transit Feed Specification) is the standard file format for public transport timetables.

## What each script does

**`build_metro.py`** (same maths as the prototype's `prep.py`)
- Ride time between two stations: the median over weekday trips, including the stop at the next station.
- Train gap at each station and line: the median gap between departures, 8–11 am and 8–10 pm. It takes the worse of the two directions.
- Wards: names, areas, centres and simplified outlines. Each ward lists the metro stations within 7 km of its nearest edge.

**`build_bus.py`** (the prototype's `prep_bus.py`, plus the step that made its in-between files)
- Keeps the trips that run on a Tuesday. "Weekday" in the prototype meant exactly this: it gives the same 5,580 route patterns over 9,394 stops. Change it with `--day`.
- A *pattern* is one exact sequence of stops. Routes with the same sequence are merged, so their buses add up.
- A trip counts for the morning (7–11 am) or evening (7:30–10 pm) by the time it is halfway along. Gap = window length ÷ number of buses.
- Route labels keep the first word of each route name, for example `KBS-1I`, not `KBS-1I BEMLF-KDG`.

**`build_places.py`**
- Counts the places inside each ward, and measures how far the nearest one is from the ward's centroid.
- Its tag rules are the prototype's. Checked ward by ward on the same data, `--like-prototype` reproduces the
  prototype's counts exactly.

| Type | OpenStreetMap tags |
|---|---|
| Gym | `leisure=fitness_centre` or `sport=fitness` |
| Park | `leisure=park` |
| Hospital or clinic | `amenity=hospital` or `amenity=clinic` |
| School | `amenity=school` |
| Grocery | `shop=supermarket`, `shop=convenience` or `shop=greengrocer` |
| Pharmacy | `amenity=pharmacy` |
| Place to eat | `amenity=restaurant`, `amenity=fast_food` or `amenity=cafe` |

Unlike the prototype, it also counts *relations*: places drawn as several pieces, such as many big lake parks. That adds
58 parks inside wards (1,838 instead of 1,780), 11 schools and 7 hospitals or clinics. `--like-prototype` leaves them out.

**Being polite to the Overpass servers.**
- It only uses the maps.mail.ru mirror. The main server (overpass-api.de) refuses AI-agent traffic, so the script never calls it.
- Requests are small: 9 boxes, `[timeout:25]`, and a failing box is split into 4.
- It tries each box up to 3 times, waiting longer each time, and pauses between requests.
- If a server refuses automated requests, the script stops.

**`compare.py`** checks every field against the prototype's files.

**`import_reference.py`** builds `data/` directly from the prototype's files. It's a safety net in case the pinned feeds disappear.

## Output files (`../data/`)

| File | Contents |
|---|---|
| `metro.json` | stations, lines (with drawn paths), ride times between stations (seconds), train gaps per station and line `[morning, evening]` in seconds |
| `wards.json` | 243 wards: number, name, centre, area (km²), simplified outline, nearby metro stations `[station index, metres]`, plus the city outline |
| `bus.json.gz` | bus stops (lat, lon, name) and patterns: label, gaps in minutes (`0` = none in that window), stop indexes, time since the first stop in tenths of a minute |
| `places.json` | the 7 place types and, per ward, the count inside and the nearest distance in units of 10 m |
