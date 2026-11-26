// Colours from the prototype: one blue ramp for time and scores, the metro line colours, and red for buses.

export interface Palette {
  /** ramp from darkest (quickest / best) to lightest */
  ramp: [string, string, string, string, string, string];
  bus: string;
  ink: string;
  casing: string;
  wardLine: string;
  office: string;
}

export const LIGHT: Palette = {
  ramp: ["#0b3d91", "#2160c4", "#4f88d8", "#86afe8", "#bcd3f3", "#e0eafa"],
  bus: "#c8372d",
  ink: "#141821",
  casing: "#ffffff",
  wardLine: "#8a93a3",
  office: "#141821",
};

export const DARK: Palette = {
  ramp: ["#a9ccff", "#7eacf0", "#5a8ad6", "#3f69ad", "#2d4c80", "#22385d"],
  bus: "#ff8a7a",
  ink: "#e8ebf0",
  casing: "#0f1319",
  wardLine: "#5b6270",
  office: "#e8ebf0",
};

/** Upper ends of the time bands, in minutes. */
export const BANDS = [20, 30, 40, 50, 60, 75, 90];

/**
 * Colour band (1 = darkest) for a commute time, or null when over the limit.
 * Same rule as the prototype: 75-90 min shares the lightest colour with 60-75.
 */
export function timeBand(minutes: number, limit: number): number | null {
  if (minutes > limit) return null;
  const i = BANDS.findIndex(b => minutes <= b);
  return Math.min((i < 0 ? BANDS.length : i) + 1, 6);
}

/** Colour band for a 0-100 score: 80+ darkest, under 20 lightest of five. */
export function scoreBand(score: number): number {
  return score >= 80 ? 1 : score >= 60 ? 2 : score >= 40 ? 3 : score >= 20 ? 4 : 5;
}

export interface LegendItem {
  band: number;
  label: string;
}

/** Legend rows for the time colouring, cut at the limit. */
export function timeLegend(limit: number): LegendItem[] {
  return BANDS.filter((_, i) => i === 0 || BANDS[i - 1] < limit).map((b, i) => {
    const lo = i === 0 ? 0 : BANDS[i - 1];
    const hi = Math.min(b, limit);
    return { band: Math.min(i + 1, 6), label: i === 0 ? `under ${hi} min` : `${lo}–${hi} min` };
  });
}

export const SCORE_LEGEND: LegendItem[] = [
  { band: 1, label: "80+" },
  { band: 2, label: "60–79" },
  { band: 3, label: "40–59" },
  { band: 4, label: "20–39" },
  { band: 5, label: "under 20" },
];
