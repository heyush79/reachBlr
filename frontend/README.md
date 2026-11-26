# Frontend (React + TypeScript + Vite + MapLibre GL JS)

The map and controls. All routing happens in the backend; this app only draws the answers.

## Run

Start the backend first (`cd ../backend && ./mvnw spring-boot:run`), then:

```bash
npm install
npm run dev        # http://localhost:5173 (the dev server forwards /api to localhost:8080)
npm test           # unit tests (Vitest)
npm run lint
npm run build      # type check + production build into dist/
```

If port 5173 is taken, use `npx vite --port 5180`.

`VITE_API_BASE` sets the backend's address when it lives on another site, e.g.
`VITE_API_BASE=https://api.example.com npm run build`. Left empty, the app calls `/api` on its own site.

## Basemap

Tiles come from [OpenFreeMap](https://openfreemap.org): free, no API key, no limit on map views, and based on
OpenStreetMap. The app uses the `positron` style in light mode and `dark` in dark mode.

- The credit, "OpenFreeMap © OpenMapTiles Data from OpenStreetMap", is required. MapLibre adds it on the map
  automatically, so the attribution control must stay on.
- The public service promises no uptime. If it ever goes away, the fallback is self-hosted tiles
  (OpenFreeMap publishes weekly downloads), or another OpenStreetMap-based style URL in `src/components/MapView.tsx`.

## Code map

- `src/App.tsx`: loads the offices, lines and ward shapes, holds the settings, and asks the backend for searches and trips.
- `src/components/MapView.tsx`: the MapLibre map.
  - Ward shading uses *feature state* (MapLibre's way to recolour shapes without re-sending them).
  - Metro lines, stations, the trip and the office are separate layers.
  - Our layers are added back after a light/dark style swap.
- `src/lib/settings.ts`: settings in the page address (so a view can be shared) and in localStorage. Bad values from a
  hand-edited link are dropped or clamped.
- `src/lib/colours.ts`: the time bands and colours from the prototype.
- `src/lib/hooks.ts`: waits for sliders to settle, and cancels outdated requests.
