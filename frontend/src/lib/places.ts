import type { PlaceType, WardProps } from "../types";
import { joinAnd } from "./text";

/** At least one inside the ward, or one within 1 km of its centre (same rule as the prototype). */
export function hasNear(w: WardProps, k: number): boolean {
  const count = w.count?.[k] ?? 0;
  const near = w.nearest10m?.[k] ?? -1;
  return count > 0 || (near >= 0 && near <= 100);
}

/** True if the ward has every must-have type within 1 km. */
export function passesMust(w: WardProps, must: number[]): boolean {
  return must.every(k => hasNear(w, k));
}

/** The score the map and list use: the everyday needs score, or one type's score when colouring by that type. */
export function scoreFor(w: WardProps, colour: string): number {
  if (colour === "time" || colour === "score") return w.score ?? 0;
  return w.scores?.[Number(colour)] ?? 0;
}

/** "needs score" or "gym score", for the list. */
export function scoreName(colour: string, types: PlaceType[]): string {
  if (colour === "time" || colour === "score") return "needs score";
  return `${types[Number(colour)]?.shortLabel.toLowerCase() ?? "type"} score`;
}

/** ", with a gym and a park within 1 km" */
export function mustText(must: number[], types: PlaceType[]): string {
  if (!must.length) return "";
  return `, with ${joinAnd(must.map(k => types[k]?.one ?? ""))} within 1 km`;
}
