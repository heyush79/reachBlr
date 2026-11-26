// Shapes of the backend's JSON (see backend/README.md).

export type Mode = "walk" | "auto";
export type When = "morning" | "evening";
export type Net = "metro" | "all";
export type LatLon = [number, number];

export interface Office {
  name: string;
  lat: number;
  lon: number;
}

export interface MetroLine {
  id: string;
  name: string;
  color: string;
  path: LatLon[];
}

export interface Station {
  id: string;
  name: string;
  lat: number;
  lon: number;
  lines: string[];
}

export interface PlaceType {
  key: string;
  label: string;
  shortLabel: string;
  one: string;
}

export interface Meta {
  offices: Office[];
  lines: MetroLine[];
  stations: Station[];
  placeTypes: PlaceType[];
  placesAsOf: string | null;
  feeds: Record<string, string | null>;
  rideFix: boolean;
  /** true when the server has a Google Places key, so "Nearby on Google" can be offered */
  googlePlaces: boolean;
}

export interface WardProps {
  index: number;
  no: string;
  name: string;
  areaKm2: number;
  centre: LatLon;
  /** per place type, in the order of Meta.placeTypes: how many inside the ward */
  count?: number[];
  /** per place type: nearest one from the ward centre, in units of 10 m; -1 = none */
  nearest10m?: number[];
  /** per place type: 0-100, how the ward's density ranks among all wards */
  scores?: number[];
  /** everyday needs score: the average of the type scores */
  score?: number;
}

export interface WardFeature {
  type: "Feature";
  id: number;
  properties: WardProps;
  geometry: { type: "MultiPolygon"; coordinates: number[][][][] };
}

export interface WardsGeoJson {
  type: "FeatureCollection";
  features: WardFeature[];
}

export interface WardTime {
  ward: number;
  name: string;
  minutes: number;
  overLimit: boolean;
  direct: boolean;
  via: string[];
}

export interface ReachResponse {
  query: { lat: number; lon: number; limit: number; mode: Mode; leg: number; when: When; net: Net };
  reachable: number;
  wards: WardTime[];
  nearestStation: { name: string; minutes: number; metres: number; withinLeg: boolean };
  nearestBusStop: { name: string; metres: number; walkMinutes: number; routes: number } | null;
  searchMs: number;
}

export type StepType = "ACCESS" | "WALK" | "EXIT_WALK" | "BUS" | "METRO" | "FINAL" | "DIRECT";

export interface Step {
  type: StepType;
  text: string;
  minutes: number;
  waitMinutes?: number;
  to?: string;
  stops?: number;
  line?: { id: string; name: string; color: string };
  bus?: { label: string; otherRoutes: number };
  path: LatLon[];
}

export interface Trip {
  ward: number;
  wardName: string;
  minutes: number;
  overLimit: boolean;
  direct: boolean;
  via: string[];
  steps: Step[];
}

export interface TripResponse {
  ward: number;
  wardName: string;
  trip: Trip | null;
}

export interface NearbyPlace {
  id: string;
  name: string;
  address: string;
  lat: number;
  lon: number;
  distanceMetres: number;
  googleMapsUri: string;
  /** data providers Google says must be shown with this result */
  attributions: { provider: string; providerUri: string }[];
}

export interface NearbyResponse {
  type: string;
  radiusMetres: number;
  places: NearbyPlace[];
  attribution: string;
}
