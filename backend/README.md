# Backend (Java 21, Spring Boot 3)

The backend loads the prepared transit data from `../data` into memory at startup. That takes about 0.1 s.
It answers commute searches with Dijkstra's shortest-path search; a 90-minute search takes about 10 ms.

## Run

You need Java 21 or newer. The Maven wrapper downloads Maven itself.

```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test                     # all tests
```

## Endpoints

| Endpoint | What it returns |
|---|---|
| `GET /api/reach?lat=&lon=&limit=&mode=&leg=&when=&net=` | minutes to every reachable ward (quickest first), the lines used, the closest station and bus stop |
| `GET /api/trip?...same...&ward=` | the step-by-step trip to one ward, with shapes to draw |
| `GET /api/wards` | the 243 ward outlines as GeoJSON, with each ward's place counts, nearest distances and scores. Browsers re-check it on every visit with its ETag; when it hasn't changed, the reply is a tiny 304 |
| `GET /api/meta` | preset offices, metro lines and stations, place types, data versions |
| `GET /actuator/health` | `{"status":"UP"}` when the app is ready |

Parameters for `/api/reach` and `/api/trip`:

| Parameter | Values | Default |
|---|---|---|
| `lat`, `lon` | office location, in the Bengaluru area | required |
| `limit` | longest commute, 15–90 minutes | 45 |
| `mode` | `walk` or `auto` (auto or cab to and from stops) | `walk` |
| `leg` | longest trip to or from a stop, 5–30 minutes | 15 |
| `when` | `morning` or `evening` (8–10 pm) | `morning` |
| `net` | `metro` (metro only) or `all` (metro and bus) | `all` |

Bad values get a `400` with a [problem details](https://www.rfc-editor.org/rfc/rfc9457) body that names each one:

```json
{"title":"Invalid request","status":400,"errors":{"limit":"must be 15 to 90 minutes"}}
```

## Settings

All settings are in `src/main/resources/application.yml`. Each can be set with an environment variable:

| Variable | Meaning | Default |
|---|---|---|
| `COMMUTE_DATA_DIR` | folder with the prepared data | `../data` |
| `ALLOWED_ORIGINS` | browser origins allowed to call the API from another site (CORS) | `http://localhost:5173,http://localhost:8080` |
| `RIDE_FIX` | `true` keeps two times at every stop, "no ride yet" and "has ridden", so trips that leave from a stop you could have walked to are not lost. `false` matches the prototype exactly | `false` |
| `PORT` | HTTP port | `8080` |

## Code map

- `data/`: reads the pipeline's files into immutable records.
- `routing/`:
  - `TransitGraph` holds the search structures, built once.
  - `Router` is the search. It is a line-by-line port of the prototype's `route()`.
  - `Geo` has the travel-time rules.
  - `JsMath` copies V8's `Math.hypot`, so results match the browser prototype to the last bit.
- `trip/`: `TripBuilder` traces a search back into steps, with the same wording as the prototype.
- `api/`: the endpoints, input checks, error replies, and a small cache of recent searches.

## Tests

| Test | What it checks |
|---|---|
| `GoldenParityTest` | the same answers as the prototype's JavaScript, ward by ward and step by step, for 12 searches (see `tools/golden`) |
| `KnownCasesTest` | the checks from the prototype's notes: ITPL 45 min walking reaches 17 wards by metro and 22 with buses; MG Road reaches 50 and 86; Pattandur Agrahara → Indiranagar is a 27 min metro ride; Manyatha Tech Park → Kalyananagara is about 13 min by bus |
| `RulesTest` | each travel-time rule on a tiny made-up network |
| `RideFixTest` | the optional fix finds missed wards and never makes a ward slower |
| `SpeedTest` | a 90 min search answers in under 300 ms |
| `ApiTest` | the endpoints, input checks, caching headers and CORS |
