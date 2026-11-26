import { describe, expect, it } from "vitest";
import { DEFAULTS, fromQuery, officeLocation, searchParams, startSettings, toQuery, type Settings } from "./settings";

const OFFICES = [
  { name: "ITPL, Whitefield", lat: 12.9855, lon: 77.7368 },
  { name: "MG Road", lat: 12.9716, lon: 77.596 },
];

describe("page address", () => {
  it("leaves defaults out", () => {
    expect(toQuery(DEFAULTS)).toBe("");
  });

  it("round-trips every setting", () => {
    const s: Settings = { ...DEFAULTS, office: 1, limit: 60, mode: "auto", leg: 20, when: "evening", net: "metro", colour: "3", must: [0, 5], sort: "score" };
    expect({ ...DEFAULTS, ...fromQuery(toQuery(s), 2, 7) }).toEqual(s);
  });

  it("keeps a picked spot", () => {
    const s: Settings = { ...DEFAULTS, custom: [12.93456, 77.61234] };
    expect(fromQuery(toQuery(s), 2, 7).custom).toEqual([12.93456, 77.61234]);
  });

  it("drops or clamps bad values", () => {
    const got = fromQuery("o=9&limit=500&leg=1&mode=bike&when=night&net=x&colour=99&must=1,a,42,1&lat=28.6&lon=77.2", 2, 7);
    expect(got).toEqual({ limit: 90, leg: 5, must: [1] });
  });

  it("rounds to the slider steps", () => {
    expect(fromQuery("limit=47&leg=12", 2, 7)).toEqual({ limit: 45, leg: 10 });
  });
});

describe("search parameters", () => {
  it("send the office and every choice", () => {
    const p = new URLSearchParams(searchParams({ ...DEFAULTS, office: 1 }, OFFICES));
    expect(Object.fromEntries(p)).toEqual({
      lat: "12.97160", lon: "77.59600", limit: "45", mode: "walk", leg: "15", when: "morning", net: "all",
    });
  });

  it("prefer a picked spot", () => {
    expect(officeLocation({ ...DEFAULTS, custom: [12.9, 77.6] }, OFFICES)).toEqual([12.9, 77.6]);
  });
});

describe("starting settings", () => {
  it("use what was saved when the address is plain", () => {
    expect(startSettings("o=1&must=0,5", "", 2, 7)).toEqual({ ...DEFAULTS, office: 1, must: [0, 5] });
  });
  it("show a shared link exactly, ignoring what was saved", () => {
    expect(startSettings("must=0,5&net=metro", "?o=1", 2, 7)).toEqual({ ...DEFAULTS, office: 1 });
  });
  it("treat a link with only bad values as a link too", () => {
    expect(startSettings("must=0,5", "?mode=bike", 2, 7)).toEqual(DEFAULTS);
  });
  it("start from defaults the first time", () => {
    expect(startSettings(null, "", 2, 7)).toEqual(DEFAULTS);
  });
});
