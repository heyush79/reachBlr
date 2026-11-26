import { describe, expect, it } from "vitest";
import { joinAnd, nearestNotes, nearestText, viaText } from "./text";
import type { ReachResponse } from "../types";

describe("viaText", () => {
  it("names the lines used", () => {
    expect(viaText({ direct: false, via: ["Purple Line", "Bus 500-D"] })).toBe("Purple Line + Bus 500-D");
    expect(viaText({ direct: true, via: [] })).toBe("No bus or metro needed");
  });
});

describe("joinAnd", () => {
  it("joins like a sentence", () => {
    expect(joinAnd(["a gym"])).toBe("a gym");
    expect(joinAnd(["a gym", "a park"])).toBe("a gym and a park");
    expect(joinAnd(["a gym", "a park", "a school"])).toBe("a gym, a park and a school");
  });
});

describe("nearestNotes", () => {
  const r = {
    nearestStation: { name: "Pattandur Agrahara", minutes: 4.2, metres: 259, withinLeg: true },
    nearestBusStop: { name: "ITPL", metres: 231, walkMinutes: 4, routes: 1 },
  } as ReachResponse;
  it("uses the prototype's wording", () => {
    expect(nearestNotes(r, 15, "walk")).toEqual({ station: "about 4 min walk.", bus: "about 4 min walk, 1 route stop there." });
  });
  it("says when the station is too far", () => {
    const far = { ...r, nearestStation: { ...r.nearestStation, metres: 3456, withinLeg: false }, nearestBusStop: null };
    expect(nearestNotes(far, 15, "auto")).toEqual({ station: "3.5 km away, more than your 15 min auto or cab ride limit.", bus: null });
  });
});

describe("nearestText", () => {
  it("uses metres below 1 km", () => {
    expect(nearestText(45)).toBe("nearest 450 m");
    expect(nearestText(123)).toBe("nearest 1.2 km");
    expect(nearestText(-1)).toBe("none");
  });
});
