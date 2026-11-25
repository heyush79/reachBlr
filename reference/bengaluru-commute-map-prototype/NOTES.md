# Prototype notes: Bengaluru Commute Map

This folder is a working prototype built in a chat session. Use it as a reference, not as code to copy as-is.

## What is here

- `prototype/index.html` – the full single-page app (map, routing, scores). Plain JavaScript, no build step.
- `prototype/data.json` – metro stations, lines, ride times, train gaps, 243 ward shapes, place counts per ward.
- `prototype/bus.json` – compact BMTC bus route patterns (stops, ride times, bus gaps).
- `scripts/prep.py` – builds data.json from the metro timetable (GTFS) and the ward map.
- `scripts/prep_bus.py` – builds bus.json from the BMTC timetable (GTFS).

## Data sources

| Data | Source | Notes |
|---|---|---|
| Namma Metro timetable | github.com/Vonter/bmrcl-gtfs (`gtfs/bmrcl.zip`) | Unofficial. 3 lines open: Purple, Green, Yellow. Version 17 Aug 2026. |
| BMTC bus timetable | github.com/Vonter/bmtc-gtfs (`gtfs/bmtc.zip`, 93 MB) | Unofficial, built from the Namma BMTC app. Only routes with live tracking. Version 9 Nov 2026. |
| Ward boundaries | github.com/datameet/Municipal_Spatial_Data (`Bangalore/BBMP.geojson`) | 243 BBMP wards. |
| Places (gyms, parks, etc.) | OpenStreetMap through the Overpass API | Many places are missing (139 of 243 wards have no gym mapped). |

GTFS = General Transit Feed Specification, the standard file format for public transport timetables (stops, routes, trip times).

## How travel time is worked out

- **Walking:** 4.8 km/h, along a path 30% longer than a straight line.
- **Auto or cab:** 18 km/h, plus 5 minutes to get one.
- **Metro:**
  - Ride times come from the timetable.
  - The wait is half the gap between trains at that station. Morning uses 8–11 am; evening uses 8–10 pm.
  - Entering a station adds 2 min, leaving adds 1 min, and changing lines adds 3 min plus the wait.
- **Bus:**
  - The timetable assumes about 22.5 km/h at every hour, which ignores traffic. News reports say real speeds are about 10–12 km/h at peak. So bus ride times are multiplied by 1.8 in the morning and 1.4 in the evening.
  - When several routes go from stop A to stop B, their buses are counted together for the wait. Only routes whose ride is at most 1.25 × fastest + 2 min are counted. The route shown to the user is the most frequent one.
  - The wait is half the combined gap, at least 1 min and at most 15 min.
  - Changing between buses means walking up to 250 m between stops. Changing between bus and metro means walking up to 400 m to the station.
- **Search method:** Dijkstra's shortest path over bus stops plus (metro station, line) pairs, stopped at the time limit.
- **Reaching a ward:**
  - A ward counts as reached if you can get to its nearest edge plus a 300 m walk into it.
  - The trip must include at least one bus or metro ride. Walking to a stop and walking away does not count.
  - A walk straight from the office is allowed if it fits the limit.
- **Ward scores:**
  - For each place type: the ward's count per km², ranked against all 243 wards (0–100). A ward with none mapped scores 0.
  - The everyday needs score is the average of the 7 type scores.
  - "Must have within 1 km" keeps a ward if the place type is inside it, or one is within 1 km of its centre.

## Checks we did

- Metro: Pattandur Agrahara → Indiranagar, weekday 8:35 am, is 27 min in the timetable. The page matched.
- Bus: Manyatha Tech Park → Kalyananagara, most routes take 6.7–7.5 min in the timetable. With the 1.8 traffic factor, that's ≈ 12–13.5 min. The page showed 13 min.
- From ITPL within 45 min (walking to stops): 17 wards by metro only, 22 with buses. From MG Road: 50 wards by metro only, 86 with buses.

## Lessons learned

- The main Overpass server (overpass-api.de) refuses requests from AI-agent browsers, so respect that. The maps.mail.ru mirror worked, but only with small requests: split the city into 9 boxes, declare `[timeout:25]`, and split a box into 4 again if it still fails.
- The bus GTFS has many route variants with the same number. Merge identical stop sequences, and show route names by their first word (e.g. "KBS-1I", not "KBS-1I BEMLF-KDG").
- Sort stops by location and store differences between values. That made bus.json 1.56 MB (415 KB compressed) instead of 2.1 MB.
