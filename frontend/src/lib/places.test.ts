import { describe, expect, it } from "vitest";
import { hasNear, mustText, passesMust, scoreFor, scoreName } from "./places";
import type { PlaceType, WardProps } from "../types";

const TYPES: PlaceType[] = [
  { key: "gym", label: "Gyms", shortLabel: "Gym", one: "a gym" },
  { key: "park", label: "Parks", shortLabel: "Park", one: "a park" },
  { key: "school", label: "Schools", shortLabel: "School", one: "a school" },
];

const ward = (count: number[], nearest10m: number[], scores = [10, 20, 30], score = 20): WardProps => ({
  index: 0, no: "1", name: "W", areaKm2: 1, centre: [12.9, 77.6], count, nearest10m, scores, score,
});

describe("hasNear", () => {
  it("counts places inside the ward", () => {
    expect(hasNear(ward([1, 0, 0], [150, -1, -1]), 0)).toBe(true);
  });
  it("counts places within 1 km of the centre", () => {
    expect(hasNear(ward([0, 0, 0], [100, 101, -1]), 0)).toBe(true);
    expect(hasNear(ward([0, 0, 0], [100, 101, -1]), 1)).toBe(false);
    expect(hasNear(ward([0, 0, 0], [100, 101, -1]), 2)).toBe(false);
  });
});

describe("passesMust", () => {
  it("needs every must-have", () => {
    const w = ward([1, 0, 0], [50, 300, 80]);
    expect(passesMust(w, [])).toBe(true);
    expect(passesMust(w, [0, 2])).toBe(true);
    expect(passesMust(w, [0, 1])).toBe(false);
  });
});

describe("scores", () => {
  it("use the needs score unless colouring by one type", () => {
    const w = ward([0, 0, 0], [0, 0, 0], [10, 20, 30], 20);
    expect(scoreFor(w, "time")).toBe(20);
    expect(scoreFor(w, "score")).toBe(20);
    expect(scoreFor(w, "2")).toBe(30);
    expect(scoreName("time", TYPES)).toBe("needs score");
    expect(scoreName("1", TYPES)).toBe("park score");
  });
});

describe("mustText", () => {
  it("reads as a sentence", () => {
    expect(mustText([], TYPES)).toBe("");
    expect(mustText([0, 1, 2], TYPES)).toBe(", with a gym, a park and a school within 1 km");
  });
});
