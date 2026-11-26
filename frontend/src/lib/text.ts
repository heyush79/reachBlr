import type { ReachResponse, WardTime } from "../types";

/** "Purple Line + Bus 500-D", or a note for trips without a ride. */
export function viaText(w: Pick<WardTime, "direct" | "via">): string {
  if (w.direct) return "No bus or metro needed";
  return w.via.length ? w.via.join(" + ") : "Walk";
}

/** "a, b and c" */
export function joinAnd(items: string[]): string {
  if (items.length <= 1) return items.join("");
  return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
}

/** The note under the count: closest metro station and bus stop, in the prototype's words. */
export function nearestNotes(r: ReachResponse, leg: number, mode: "walk" | "auto"): { station: string; bus: string | null } {
  const word = mode === "walk" ? "walk" : "auto or cab ride";
  const n = r.nearestStation;
  const station = n.withinLeg
    ? `about ${Math.round(n.minutes)} min ${word}.`
    : `${(n.metres / 1000).toFixed(1)} km away, more than your ${leg} min ${word} limit.`;
  const b = r.nearestBusStop;
  const bus = b ? `about ${b.walkMinutes} min walk, ${b.routes} route${b.routes === 1 ? "" : "s"} stop there.` : null;
  return { station, bus };
}

/** Distance as the place table shows it: "nearest 450 m" / "nearest 1.2 km" / "none". */
export function nearestText(tens: number): string {
  if (tens < 0) return "none";
  return tens < 100 ? `nearest ${tens * 10} m` : `nearest ${(tens / 100).toFixed(1)} km`;
}
