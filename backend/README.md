# Backend (Java 21, Spring Boot 3)

The backend loads the prepared transit data from `../data` into memory at startup. That takes about 0.1 s.
It answers commute searches with Dijkstra's shortest-path search; a 90-minute search takes about 10 ms.

## Run

You need Java 21 or newer. The Maven wrapper downloads Maven itself.

```bash
./mvnw spring-boot:run          # http://localhost:8081 (another port: PORT=8085 ./mvnw spring-boot:run)
curl "http://localhost:8081/api/reach?lat=12.9855&lon=77.7368"   # ITPL: "reachable": 22
./mvnw test                     # all tests
```

## Endpoints

| Endpoint | What it returns |
|---|---|
| `GET /api/reach?lat=&lon=&limit=&mode=&leg=&when=&net=` | minutes to every reachable ward (quickest first), the lines used, the closest station and bus stop |
| `GET /api/trip?...same...&ward=` | the step-by-step trip to one ward, with shapes to draw |
| `GET /api/wards` | the 243 ward outlines as GeoJSON, with each ward's place counts, nearest distances and scores. Browsers re-check it on every visit with its ETag; when it hasn't changed, the reply is a tiny 304 |
| `GET /api/meta` | preset offices, metro lines and stations, place types, data versions, and whether Google nearby places are on |
| `GET /api/places/nearby?lat=&lon=&type=` | up to 10 places of one type (`gym`, `park`, `health`, `school`, `grocery`, `pharmacy`, `food`) within 1 km, from Google Places, nearest first. Rate limited; `503` when no key is set |
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

## Google nearby places

The ward card can list places from Google Places API (New) Nearby Search. The backend calls Google, so the API key never
reaches the browser.

**Set it up**

1. In Google Cloud Console, create an API key and enable **Places API (New)**.
2. Restrict the key to that one API.
3. Set a daily quota, as a second guard on the bill: APIs & Services → Places API (New) → Quotas.
4. Put the key in `.env` at the repo root (copy `.env.example`), then start the backend with it:
   ```bash
   set -a; . ../.env; set +a
   ./mvnw spring-boot:run
   ```

**Costs.** Each lookup asks only for fields billed as *Nearby Search Pro*: id, name, short address, location, Google Maps
link and attributions. That tier has 5,000 free requests a month, then US$32 per 1,000 (Google's price list, November 2026).
Adding fields such as `places.rating` would move every request to a dearer tier.

**Limits**, settable by environment variable:

- `PLACES_PER_IP_PER_MINUTE` (default 10) per visitor.
- `PLACES_GLOBAL_PER_DAY` (default 500) for the whole site.

Over a limit, the reply is `429` with a `Retry-After` header. Visitors are told apart by IP address, behind a proxy using
`X-Forwarded-For`. A determined visitor could fake that header, but the daily cap still holds.

**Google's rules this follows**

- Nothing from Google is stored. Only place IDs may be kept, and we keep nothing. Replies say `Cache-Control: no-store`.
- Results are a list, never pins on our map: Google does not allow Places results on a non-Google map.
- The list carries the "Google Maps" attribution (Roboto, regular weight, #5E5E5E, not translated, on one line) in the
  same box.
- Each place's own `attributions` (data providers Google says must be shown) are shown with it.
- Each place links to Google Maps.
- Place coordinates are only used to show a distance on the fly, never to test whether a place is inside a ward.

## Settings

All settings are in `src/main/resources/application.yml`. Each can be set with an environment variable:

| Variable | Meaning | Default |
|---|---|---|
| `COMMUTE_DATA_DIR` | folder with the prepared data | `../data` |
| `ALLOWED_ORIGINS` | browser origins allowed to call the API from another site (CORS) | `http://localhost:5180,http://localhost:8090` |
| `GOOGLE_PLACES_API_KEY` | Google Places API (New) key. Empty turns "Nearby on Google" off | empty |
| `PLACES_PER_IP_PER_MINUTE`, `PLACES_GLOBAL_PER_DAY` | rate limits for `/api/places/nearby` | `10`, `500` |
| `RIDE_FIX` | `true` keeps two times at every stop, "no ride yet" and "has ridden", so trips that leave from a stop you could have walked to are not lost. `false` matches the prototype exactly | `false` |
| `PORT` | HTTP port | `8081` for local development; `8080` inside Docker (set in its Dockerfile); hosting platforms set their own |

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
| `NearbyPlacesTest` | the Google proxy against a fake Google server: request, field mask, answer shape, per-visitor limit, errors, the key never leaking |
| `NearbyDailyCapTest` | the site-wide daily cap |
| `PlaceScoresTest` | ward scores match the prototype's `buildPlaces()` |
