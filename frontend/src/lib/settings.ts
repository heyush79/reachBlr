import type { LatLon, Mode, Net, Office, When } from "../types";

/** Everything the user can choose. Kept in the page address (so a view can be shared) and in localStorage. */
export interface Settings {
  /** index into the preset offices; ignored when `custom` is set */
  office: number;
  /** a spot picked on the map */
  custom: LatLon | null;
  limit: number;
  mode: Mode;
  leg: number;
  when: When;
  net: Net;
  /** "time", "score", or a place-type index as a string */
  colour: string;
  /** place-type indexes that must be within 1 km */
  must: number[];
  sort: "time" | "score";
}

export const DEFAULTS: Settings = {
  office: 0,
  custom: null,
  limit: 45,
  mode: "walk",
  leg: 15,
  when: "morning",
  net: "all",
  colour: "time",
  must: [],
  sort: "time",
};

const STORAGE_KEY = "bcm-settings-v1";

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
const inCity = (lat: number, lon: number) => lat >= 12.6 && lat <= 13.4 && lon >= 77.2 && lon <= 78.0;

/** Reads settings from a query string. Unknown or out-of-range values are dropped or clamped. */
export function fromQuery(qs: string, officeCount: number, placeTypeCount: number): Partial<Settings> {
  const p = new URLSearchParams(qs);
  const out: Partial<Settings> = {};
  const num = (k: string) => (p.has(k) && p.get(k) !== "" ? Number(p.get(k)) : NaN);
  const lat = num("lat"), lon = num("lon");
  if (Number.isFinite(lat) && Number.isFinite(lon) && inCity(lat, lon)) out.custom = [lat, lon];
  const o = num("o");
  if (Number.isInteger(o) && o >= 0 && o < officeCount) out.office = o;
  const limit = num("limit");
  if (Number.isFinite(limit)) out.limit = clamp(Math.round(limit / 5) * 5, 15, 90);
  const leg = num("leg");
  if (Number.isFinite(leg)) out.leg = clamp(Math.round(leg / 5) * 5, 5, 30);
  const mode = p.get("mode");
  if (mode === "walk" || mode === "auto") out.mode = mode;
  const when = p.get("when");
  if (when === "morning" || when === "evening") out.when = when;
  const net = p.get("net");
  if (net === "metro" || net === "all") out.net = net;
  const colour = p.get("colour");
  if (colour === "time" || colour === "score") out.colour = colour;
  else if (colour !== null && /^\d+$/.test(colour) && Number(colour) < placeTypeCount) out.colour = colour;
  const must = p.get("must");
  if (must !== null) {
    out.must = [...new Set(must.split(",").filter(x => /^\d+$/.test(x)).map(Number).filter(k => k < placeTypeCount))];
  }
  const sort = p.get("sort");
  if (sort === "time" || sort === "score") out.sort = sort;
  return out;
}

/** The query string for the page address. Defaults are left out to keep links short. */
export function toQuery(s: Settings): string {
  const p = new URLSearchParams();
  if (s.custom) {
    p.set("lat", s.custom[0].toFixed(5));
    p.set("lon", s.custom[1].toFixed(5));
  } else if (s.office !== DEFAULTS.office) {
    p.set("o", String(s.office));
  }
  if (s.limit !== DEFAULTS.limit) p.set("limit", String(s.limit));
  if (s.mode !== DEFAULTS.mode) p.set("mode", s.mode);
  if (s.leg !== DEFAULTS.leg) p.set("leg", String(s.leg));
  if (s.when !== DEFAULTS.when) p.set("when", s.when);
  if (s.net !== DEFAULTS.net) p.set("net", s.net);
  if (s.colour !== DEFAULTS.colour) p.set("colour", s.colour);
  if (s.must.length) p.set("must", s.must.join(","));
  if (s.sort !== DEFAULTS.sort) p.set("sort", s.sort);
  return p.toString();
}

/** Page address first, then what was saved last time, then defaults. */
export function loadSettings(officeCount: number, placeTypeCount: number): Settings {
  let saved: Partial<Settings> = {};
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) saved = fromQuery(raw, officeCount, placeTypeCount);
  } catch {
    /* private mode or blocked storage */
  }
  const fromUrl = fromQuery(window.location.search, officeCount, placeTypeCount);
  // an office from the address replaces a saved custom spot, and the other way round
  if (fromUrl.custom || fromUrl.office !== undefined) {
    delete saved.custom;
    delete saved.office;
  }
  return { ...DEFAULTS, ...saved, ...fromUrl };
}

export function saveSettings(s: Settings): void {
  const qs = toQuery(s);
  try {
    localStorage.setItem(STORAGE_KEY, qs);
  } catch {
    /* ignore */
  }
  const url = window.location.pathname + (qs ? `?${qs}` : "") + window.location.hash;
  window.history.replaceState(null, "", url);
}

export function officeLocation(s: Settings, offices: Office[]): LatLon {
  if (s.custom) return s.custom;
  const o = offices[s.office] ?? offices[0];
  return [o.lat, o.lon];
}

export function officeName(s: Settings, offices: Office[]): string {
  return s.custom ? "your chosen spot" : (offices[s.office] ?? offices[0]).name;
}

/** Query parameters for /api/reach and /api/trip. */
export function searchParams(s: Settings, offices: Office[]): string {
  const [lat, lon] = officeLocation(s, offices);
  return new URLSearchParams({
    lat: lat.toFixed(5),
    lon: lon.toFixed(5),
    limit: String(s.limit),
    mode: s.mode,
    leg: String(s.leg),
    when: s.when,
    net: s.net,
  }).toString();
}
