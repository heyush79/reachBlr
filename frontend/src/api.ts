import type { Meta, NearbyResponse, ReachResponse, TripResponse, WardsGeoJson } from "./types";

/** Base URL of the backend. Empty = same site (Vite's dev proxy, or nginx in Docker). */
const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? "";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(BASE + path, { signal, headers: { Accept: "application/json" } });
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.errors ? Object.entries(body.errors).map(([k, v]) => `${k} ${v}`).join(", ") : body.detail ?? detail;
    } catch {
      /* not JSON */
    }
    throw new ApiError(detail, res.status);
  }
  return (await res.json()) as T;
}

export const api = {
  meta: () => getJson<Meta>("/api/meta"),
  wards: () => getJson<WardsGeoJson>("/api/wards"),
  reach: (params: string, signal?: AbortSignal) => getJson<ReachResponse>(`/api/reach?${params}`, signal),
  trip: (params: string, ward: number, signal?: AbortSignal) =>
    getJson<TripResponse>(`/api/trip?${params}&ward=${ward}`, signal),
  nearby: (lat: number, lon: number, type: string, signal?: AbortSignal) =>
    getJson<NearbyResponse>(`/api/places/nearby?lat=${lat.toFixed(5)}&lon=${lon.toFixed(5)}&type=${encodeURIComponent(type)}`, signal),
};
