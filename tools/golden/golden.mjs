// Runs the prototype's own route search (reference/.../prototype/index.html), unchanged, on the
// files in data/, and saves its answers as the "golden" file that the Java tests must match.
//
// The routing functions are cut out of index.html as text (from "const OFFICES" up to the
// ward-score code), minus the few lines that touch the page, and run in a Node sandbox.
//
// Usage: node tools/golden/golden.mjs   (writes backend/src/test/resources/golden/golden.json)
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const html = readFileSync(join(ROOT, "reference/bengaluru-commute-map-prototype/prototype/index.html"), "utf8");
const OUT = join(ROOT, "backend/src/test/resources/golden/golden.json");

// ---- 1. cut the routing code out of the prototype ----
const start = html.indexOf("const OFFICES = [");
const end = html.indexOf("// places: scores from density compared across all wards");
if (start < 0 || end < 0) throw new Error("index.html markers not found");
let code = html.slice(start, end);
const svgStart = code.indexOf("function buildSvg() {");
const svgEnd = code.indexOf("// Dijkstra over bus stops");
code = code.slice(0, svgStart) + code.slice(svgEnd);          // drawing code, not needed
code = code
  .split("\n")
  .filter(l => !/^const \$ = |^const svg = |^try \{ Object\.assign\(st, JSON\.parse\(localStorage/.test(l))
  .join("\n");

// ---- 2. turn data/ back into the prototype's in-memory shapes ----
const metro = JSON.parse(readFileSync(join(ROOT, "data/metro.json"), "utf8"));
const wards = JSON.parse(readFileSync(join(ROOT, "data/wards.json"), "utf8"));
const bus = JSON.parse(gunzipSync(readFileSync(join(ROOT, "data/bus.json.gz"))).toString("utf8"));

const D = {
  stations: metro.stations.map(s => ({ id: s.id, n: s.name, lat: s.lat, lon: s.lon, lines: s.lines })),
  lines: Object.fromEntries(Object.entries(metro.lines).map(([k, v]) => [k, { name: v.name, color: v.color, long: v.longName, path: v.path }])),
  edges: metro.edges,
  hw: metro.headways,
  wards: wards.wards.map(w => ({ n: w.name, no: w.no, c: w.centre, p: w.rings, a: w.areaKm2, sd: w.metro })),
  outline: wards.outline,
};
const s = [];
let pa = 0, po = 0;
for (let i = 0; i < bus.stops.lat.length; i++) {
  const a = Math.round(bus.stops.lat[i] * 1e5), b = Math.round(bus.stops.lon[i] * 1e5);
  s.push(a - pa, b - po); pa = a; po = b;
}
const delta = arr => arr.map((v, i) => (i ? v - arr[i - 1] : v));
const RAW = { s, n: bus.stops.name, p: bus.patterns.map(p => [p.label, p.gapMorning, p.gapEvening, delta(p.stops), delta(p.cum)]) };

// ---- 3. the queries ----
// office: index into the prototype's OFFICES list, or [lat, lon]
const QUERIES = [
  { id: "itpl-45-walk-morning-all", office: 0, limit: 45, mode: "walk", leg: 15, when: 0, net: "all" },
  { id: "itpl-45-walk-morning-metro", office: 0, limit: 45, mode: "walk", leg: 15, when: 0, net: "metro" },
  { id: "mgroad-45-walk-morning-all", office: 7, limit: 45, mode: "walk", leg: 15, when: 0, net: "all" },
  { id: "mgroad-45-walk-morning-metro", office: 7, limit: 45, mode: "walk", leg: 15, when: 0, net: "metro" },
  { id: "manyata-60-auto20-evening-all", office: 1, limit: 60, mode: "auto", leg: 20, when: 1, net: "all" },
  { id: "ecity-90-walk30-morning-all", office: 6, limit: 90, mode: "walk", leg: 30, when: 0, net: "all" },
  { id: "koramangala-30-walk10-evening-all", office: 8, limit: 30, mode: "walk", leg: 10, when: 1, net: "all" },
  { id: "golflinks-20-walk5-morning-all", office: 5, limit: 20, mode: "walk", leg: 5, when: 0, net: "all" },
  { id: "rrnagar-75-auto30-morning-all", office: 10, limit: 75, mode: "auto", leg: 30, when: 0, net: "all" },
  { id: "hsr-90-walk15-evening-metro", office: 9, limit: 90, mode: "walk", leg: 15, when: 1, net: "metro" },
  { id: "bellandur-90-walk15-morning-all", office: 2, limit: 90, mode: "walk", leg: 15, when: 0, net: "all" },
  { id: "custom-yeshwanthpur-50-walk15-morning-all", office: [13.0237, 77.5503], limit: 50, mode: "walk", leg: 15, when: 0, net: "all" },
];

// ---- 4. run the prototype code ----
const harness = `
D = __D;
buildMetro();
B = buildBus(__RAW);
const __results = [];
for (const q of __Q) {
  st.office = typeof q.office === "number" ? q.office : 0;
  st.custom = typeof q.office === "number" ? null : q.office;
  st.limit = q.limit; st.mode = q.mode; st.leg = q.leg; st.when = q.when; st.net = q.net;
  const t0 = performance.now();
  last = route();
  const ms = performance.now() - t0;
  const wards = res.map(r => r && {
    t: r.t,
    direct: !!r.direct,
    via: r.direct ? [] : steps(r).lines,
    steps: steps(r).out.map(x => x.h.replace(/<[^>]+>/g, "")),
  });
  __results.push({
    ...q,
    office: officeLoc(),
    reachable: res.filter(r => r && r.t <= st.limit).length,
    nearestStation: { name: last.nearest.s.n, minutes: last.nearest.m, metres: last.nearest.d },
    nearestBusStop: useBus() && last.nearBus ? { name: B.names[last.nearBus.i], metres: last.nearBus.d, patterns: B.sp[last.nearBus.i].length / 2 } : null,
    ms,
    wards,
  });
}
__OUT = __results;
`;
const ctx = { __D: D, __RAW: RAW, __Q: QUERIES, __OUT: null, performance, console };
vm.createContext(ctx);
vm.runInContext(code + "\n" + harness, ctx, { filename: "prototype-routing.js" });

mkdirSync(dirname(OUT), { recursive: true });
const doc = {
  note: "Generated by tools/golden/golden.mjs from the prototype's route() and steps(), run on data/. Do not edit by hand.",
  queries: ctx.__OUT,
};
writeFileSync(OUT, JSON.stringify(doc));
for (const r of ctx.__OUT) {
  console.log(`${r.id.padEnd(44)} reachable ${String(r.reachable).padStart(3)}  (${r.ms.toFixed(0)} ms)`);
}
