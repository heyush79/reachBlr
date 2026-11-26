import { describe, expect, it } from "vitest";
import { scoreBand, timeBand, timeLegend } from "./colours";

describe("timeBand", () => {
  it("uses the prototype's bands", () => {
    expect(timeBand(12, 90)).toBe(1);
    expect(timeBand(20, 90)).toBe(1);
    expect(timeBand(21, 90)).toBe(2);
    expect(timeBand(45, 90)).toBe(4);
    expect(timeBand(60, 90)).toBe(5);
    expect(timeBand(70, 90)).toBe(6);
    expect(timeBand(90, 90)).toBe(6);
  });
  it("is empty over the limit", () => {
    expect(timeBand(46, 45)).toBeNull();
    expect(timeBand(45, 45)).toBe(4);
  });
});

describe("timeLegend", () => {
  it("stops at the limit", () => {
    expect(timeLegend(45).map(i => i.label)).toEqual(["under 20 min", "20–30 min", "30–40 min", "40–45 min"]);
  });
  it("covers the whole range at 90 min", () => {
    const l = timeLegend(90);
    expect(l).toHaveLength(7);
    expect(l[6]).toEqual({ band: 6, label: "75–90 min" });
  });
  it("has one row for short limits", () => {
    expect(timeLegend(15)).toEqual([{ band: 1, label: "under 15 min" }]);
  });
});

describe("scoreBand", () => {
  it("has five steps", () => {
    expect([95, 80, 79, 60, 40, 20, 19, 0].map(scoreBand)).toEqual([1, 1, 2, 2, 3, 4, 5, 5]);
  });
});
