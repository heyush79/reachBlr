import { useEffect, useRef, useState } from "react";
import { MapLibreMap, NavigationControl, setWorkerUrl, type GeoJSONSource, type MapLayerMouseEvent } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
// MapLibre draws in a background worker. Point it at the worker file explicitly: Vite's dev pre-bundling
// moves MapLibre, which breaks the worker path it works out on its own.
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import type { FeatureCollection } from "geojson";
import type { LatLon, Meta, Step, WardsGeoJson } from "../types";
import type { Palette } from "../lib/colours";

// Free OpenStreetMap-based vector tiles: no key, attribution added automatically by MapLibre.
const STYLES = {
  light: "https://tiles.openfreemap.org/styles/positron",
  dark: "https://tiles.openfreemap.org/styles/dark",
};
const NO_WARD = -1;

setWorkerUrl(workerUrl);

export interface MapViewProps {
  meta: Meta;
  wards: WardsGeoJson;
  /** colour band per ward (1 = darkest), or null for not shaded */
  bands: (number | null)[];
  palette: Palette;
  dark: boolean;
  selected: number | null;
  tripSteps: Step[] | null;
  office: LatLon;
  placing: boolean;
  /** change `n` to fly to the ward again */
  focus: { ward: number; n: number } | null;
  tooltip: (ward: number) => string;
  onWardClick: (ward: number) => void;
  onPlace: (point: LatLon) => void;
}

type Bounds = [[number, number], [number, number]];

function boundsOf(coords: number[][][][]): Bounds {
  let w = Infinity, s = Infinity, e = -Infinity, n = -Infinity;
  for (const poly of coords) for (const ring of poly) for (const [lon, lat] of ring) {
    w = Math.min(w, lon); e = Math.max(e, lon); s = Math.min(s, lat); n = Math.max(n, lat);
  }
  return [[w, s], [e, n]];
}

function cityBounds(wards: WardsGeoJson): Bounds {
  return boundsOf(wards.features.flatMap(f => f.geometry.coordinates));
}

const toLonLat = (p: LatLon): [number, number] => [p[1], p[0]];

function tripGeoJson(steps: Step[] | null, palette: Palette) {
  return {
    type: "FeatureCollection" as const,
    features: (steps ?? [])
      .filter(s => s.path.length >= 2)
      .map(s => ({
        type: "Feature" as const,
        properties: {
          kind: s.type === "BUS" || s.type === "METRO" ? "ride" : "walk",
          color: s.type === "BUS" ? palette.bus : s.line?.color ?? palette.ink,
        },
        geometry: { type: "LineString" as const, coordinates: s.path.map(toLonLat) },
      })),
  };
}

function officeGeoJson(p: LatLon) {
  return { type: "Feature" as const, properties: {}, geometry: { type: "Point" as const, coordinates: toLonLat(p) } };
}

/** Adds our sources and layers on top of the basemap (again after every style change). */
function addOverlays(map: MapLibreMap, p: MapViewProps) {
  const { palette } = p;
  const firstLabel = map.getStyle().layers.find(l => l.type === "symbol")?.id;
  const ramp = palette.ramp;

  map.addSource("wards", { type: "geojson", data: p.wards as unknown as FeatureCollection });
  map.addLayer({
    id: "ward-fill",
    type: "fill",
    source: "wards",
    paint: {
      "fill-color": [
        "match", ["coalesce", ["feature-state", "band"], 0],
        1, ramp[0], 2, ramp[1], 3, ramp[2], 4, ramp[3], 5, ramp[4], 6, ramp[5],
        "rgba(255,255,255,0.01)", // not shaded, but still clickable
      ],
      // strong colour for the city view; lighter when zoomed in so streets stay readable
      "fill-opacity": ["interpolate", ["linear"], ["zoom"], 11, 0.72, 14, 0.45],
    },
  }, firstLabel);
  map.addLayer({
    id: "ward-line", type: "line", source: "wards",
    paint: { "line-color": palette.wardLine, "line-width": 0.6, "line-opacity": 0.7 },
  }, firstLabel);
  map.addLayer({
    id: "ward-hover", type: "line", source: "wards", filter: ["==", ["id"], NO_WARD],
    paint: { "line-color": palette.ink, "line-width": 1.5 },
  });
  map.addLayer({
    id: "ward-selected", type: "line", source: "wards", filter: ["==", ["id"], p.selected ?? NO_WARD],
    paint: { "line-color": palette.ink, "line-width": 2.5 },
  });

  map.addSource("metro", {
    type: "geojson",
    data: {
      type: "FeatureCollection",
      features: p.meta.lines.map(l => ({
        type: "Feature", properties: { color: l.color },
        geometry: { type: "LineString", coordinates: l.path.map(toLonLat) },
      })),
    },
  });
  map.addLayer({
    id: "metro-casing", type: "line", source: "metro",
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": palette.casing, "line-width": 7 },
  });
  map.addLayer({
    id: "metro-line", type: "line", source: "metro",
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": ["get", "color"], "line-width": 4 },
  });

  const lineColour = Object.fromEntries(p.meta.lines.map(l => [l.id, l.color]));
  map.addSource("stations", {
    type: "geojson",
    data: {
      type: "FeatureCollection",
      features: p.meta.stations.map(s => ({
        type: "Feature",
        properties: {
          name: s.name.replace("Nadaprabhu Kempegowda Station, ", ""),
          ring: s.lines.length > 1 ? palette.ink : lineColour[s.lines[0]] ?? palette.ink,
          interchange: s.lines.length > 1,
        },
        geometry: { type: "Point", coordinates: [s.lon, s.lat] },
      })),
    },
  });
  map.addLayer({
    id: "stations", type: "circle", source: "stations",
    paint: {
      "circle-radius": ["case", ["get", "interchange"], 5, 3.5],
      "circle-color": palette.casing,
      "circle-stroke-color": ["get", "ring"],
      "circle-stroke-width": ["case", ["get", "interchange"], 3, 2.5],
    },
  });
  map.addLayer({
    id: "station-labels", type: "symbol", source: "stations", minzoom: 12.5,
    layout: {
      "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12,
      "text-anchor": "left", "text-offset": [0.8, 0], "text-optional": true,
    },
    paint: { "text-color": palette.ink, "text-halo-color": palette.casing, "text-halo-width": 1.5 },
  });

  map.addSource("trip", { type: "geojson", data: tripGeoJson(p.tripSteps, palette) });
  map.addLayer({
    id: "trip-walk", type: "line", source: "trip", filter: ["==", ["get", "kind"], "walk"],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": palette.ink, "line-width": 2.5, "line-dasharray": [0.6, 2.2] },
  });
  map.addLayer({
    id: "trip-ride-casing", type: "line", source: "trip", filter: ["==", ["get", "kind"], "ride"],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": palette.casing, "line-width": 9 },
  });
  map.addLayer({
    id: "trip-ride", type: "line", source: "trip", filter: ["==", ["get", "kind"], "ride"],
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": ["get", "color"], "line-width": 5 },
  });

  map.addSource("office", { type: "geojson", data: officeGeoJson(p.office) });
  map.addLayer({
    id: "office-halo", type: "circle", source: "office",
    paint: { "circle-radius": 13, "circle-color": palette.office, "circle-opacity": 0.22 },
  });
  map.addLayer({
    id: "office-dot", type: "circle", source: "office",
    paint: { "circle-radius": 7, "circle-color": palette.office, "circle-stroke-color": palette.casing, "circle-stroke-width": 2.5 },
  });
}

export function MapView(props: MapViewProps) {
  const container = useRef<HTMLDivElement>(null);
  const tip = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const latest = useRef(props);
  const [styleVersion, setStyleVersion] = useState(0);

  useEffect(() => {
    latest.current = props;
  });

  // create the map once
  useEffect(() => {
    const p = latest.current;
    const map = new MapLibreMap({
      container: container.current!,
      style: p.dark ? STYLES.dark : STYLES.light,
      bounds: cityBounds(p.wards),
      fitBoundsOptions: { padding: 16 },
      attributionControl: { compact: true },
      dragRotate: false,
      pitchWithRotate: false,
    });
    map.touchZoomRotate.disableRotation();
    map.addControl(new NavigationControl({ showCompass: false }), "top-right");
    map.on("style.load", () => {
      addOverlays(map, latest.current);
      setStyleVersion(v => v + 1);
    });

    const hideTip = () => {
      if (tip.current) tip.current.style.display = "none";
      if (map.getLayer("ward-hover")) map.setFilter("ward-hover", ["==", ["id"], NO_WARD]);
    };
    map.on("mousemove", "ward-fill", (e: MapLayerMouseEvent) => {
      const f = e.features?.[0];
      if (!f || typeof f.id !== "number" || !tip.current) return;
      map.setFilter("ward-hover", ["==", ["id"], f.id]);
      if ((e.originalEvent as PointerEvent).pointerType === "touch") return;
      tip.current.textContent = latest.current.tooltip(f.id);
      tip.current.style.display = "block";
      const w = container.current!.clientWidth;
      tip.current.style.left = `${Math.min(e.point.x + 14, w - tip.current.offsetWidth - 8)}px`;
      tip.current.style.top = `${e.point.y + 14}px`;
    });
    map.on("mouseleave", "ward-fill", hideTip);
    map.on("dragstart", hideTip);
    map.on("click", e => {
      if (latest.current.placing) {
        latest.current.onPlace([+e.lngLat.lat.toFixed(5), +e.lngLat.lng.toFixed(5)]);
        return;
      }
      const f = map.queryRenderedFeatures(e.point, { layers: ["ward-fill"] })[0];
      if (f && typeof f.id === "number") latest.current.onWardClick(f.id);
    });
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // light/dark basemap: swapping the style drops our layers; "style.load" adds them back
  const firstStyle = useRef(true);
  useEffect(() => {
    if (firstStyle.current) {
      firstStyle.current = false;
      return;
    }
    mapRef.current?.setStyle(props.dark ? STYLES.dark : STYLES.light);
  }, [props.dark]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleVersion || !map.getSource("wards")) return;
    props.bands.forEach((band, i) => map.setFeatureState({ source: "wards", id: i }, { band: band ?? 0 }));
  }, [props.bands, styleVersion]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !styleVersion || !map.getLayer("ward-selected")) return;
    map.setFilter("ward-selected", ["==", ["id"], props.selected ?? NO_WARD]);
  }, [props.selected, styleVersion]);

  useEffect(() => {
    const src = mapRef.current?.getSource("trip") as GeoJSONSource | undefined;
    if (styleVersion && src) src.setData(tripGeoJson(props.tripSteps, latest.current.palette));
  }, [props.tripSteps, styleVersion]);

  useEffect(() => {
    const src = mapRef.current?.getSource("office") as GeoJSONSource | undefined;
    if (styleVersion && src) src.setData(officeGeoJson(props.office));
  }, [props.office, styleVersion]);

  useEffect(() => {
    const canvas = mapRef.current?.getCanvas();
    if (canvas) canvas.style.cursor = props.placing ? "crosshair" : "";
  }, [props.placing, styleVersion]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !props.focus) return;
    const f = latest.current.wards.features[props.focus.ward];
    if (f) map.fitBounds(boundsOf(f.geometry.coordinates), { padding: 60, maxZoom: 14, duration: 600 });
  }, [props.focus]);

  return (
    <div className="mapwrap">
      <div
        ref={container}
        className="map"
        role="region"
        aria-label="Map of Bengaluru wards shaded by commute time by metro and bus"
      />
      <div ref={tip} className="tip" />
      {props.placing && <div className="hint">Tap the map where your office is</div>}
    </div>
  );
}
