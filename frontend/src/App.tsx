import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api";
import type { LatLon, Meta, WardsGeoJson } from "./types";
import { DARK, LIGHT, timeBand, timeLegend } from "./lib/colours";
import { useDarkMode, useDebounced, useLoad } from "./lib/hooks";
import { loadSettings, officeLocation, officeName, saveSettings, searchParams, type Settings } from "./lib/settings";
import { nearestNotes } from "./lib/text";
import { Controls } from "./components/Controls";
import { Footer } from "./components/Footer";
import { Legend } from "./components/Legend";
import { MapView } from "./components/MapView";
import { WardDetail } from "./components/WardDetail";
import { WardList } from "./components/WardList";

const loadReach = (params: string, signal: AbortSignal) => api.reach(params, signal);
const loadTrip = (key: string, signal: AbortSignal) => {
  const [params, ward] = key.split("#");
  return api.trip(params, Number(ward), signal);
};

export default function App() {
  const [boot, setBoot] = useState<{ meta: Meta; wards: WardsGeoJson } | { error: string } | null>(null);
  useEffect(() => {
    Promise.all([api.meta(), api.wards()]).then(
      ([meta, wards]) => setBoot({ meta, wards }),
      (e: unknown) => setBoot({ error: e instanceof Error ? e.message : String(e) }),
    );
  }, []);
  return (
    <>
      <header>
        <h1>How far can you live from work in Bengaluru?</h1>
        <p>Pick your office. See every ward you can reach by metro and bus, and what each one has nearby.</p>
      </header>
      {boot === null && <p className="boot">Loading the map…</p>}
      {boot && "error" in boot && (
        <p className="boot">
          The map data did not load ({boot.error}). Please reload the page.
        </p>
      )}
      {boot && "meta" in boot && <CommuteMap meta={boot.meta} wards={boot.wards} />}
      <Footer />
    </>
  );
}

function CommuteMap({ meta, wards }: { meta: Meta; wards: WardsGeoJson }) {
  const [settings, setSettings] = useState<Settings>(() => loadSettings(meta.offices.length, meta.placeTypes.length));
  const [placing, setPlacing] = useState(false);
  const [selected, setSelected] = useState<number | null>(null);
  const [focus, setFocus] = useState<{ ward: number; n: number } | null>(null);
  const [query, setQuery] = useState("");
  const dark = useDarkMode();
  const palette = dark ? DARK : LIGHT;

  useEffect(() => saveSettings(settings), [settings]);
  const change = useCallback((patch: Partial<Settings>) => setSettings(s => ({ ...s, ...patch })), []);

  const params = useDebounced(searchParams(settings, meta.offices), 150);
  const reach = useLoad(params, loadReach);
  const trip = useLoad(selected === null ? null : `${params}#${selected}`, loadTrip);
  const r = reach.data;
  const limit = r?.query.limit ?? settings.limit;

  const byWard = useMemo(() => new Map((r?.wards ?? []).map(w => [w.ward, w])), [r]);
  const ok = useMemo(() => (r?.wards ?? []).filter(w => w.minutes <= limit), [r, limit]);
  const bands = useMemo(() => {
    const b: (number | null)[] = wards.features.map(() => null);
    for (const w of ok) b[w.ward] = timeBand(w.minutes, limit);
    return b;
  }, [ok, limit, wards]);

  const office = officeLocation(settings, meta.offices);
  const officeLabel = officeName(settings, meta.offices);
  const tooltip = useCallback(
    (i: number) => {
      const w = byWard.get(i);
      return `${wards.features[i].properties.name} · ${w ? `${w.minutes} min` : "not reachable"}`;
    },
    [byWard, wards],
  );
  const onPlace = useCallback(
    (p: LatLon) => {
      change({ custom: p });
      setPlacing(false);
    },
    [change],
  );
  const pickFromList = (ward: number) => {
    setSelected(ward);
    setFocus(f => ({ ward, n: (f?.n ?? 0) + 1 }));
  };

  const notes = r ? nearestNotes(r, settings.leg, settings.mode) : null;

  return (
    <div className="app">
      <div className="mapcol">
        <MapView
          meta={meta}
          wards={wards}
          bands={bands}
          palette={palette}
          dark={dark}
          selected={selected}
          tripSteps={trip.data?.trip?.steps ?? null}
          office={office}
          placing={placing}
          focus={focus}
          tooltip={tooltip}
          onWardClick={setSelected}
          onPlace={onPlace}
        />
        <Legend items={timeLegend(limit)} palette={palette} restLabel="longer" />
      </div>

      <aside>
        <Controls offices={meta.offices} settings={settings} placing={placing} onChange={change} onPlacing={setPlacing} />

        <div className="big" aria-live="polite">
          {r ? `${ok.length} of ${wards.features.length}` : " "}
        </div>
        <p className="sub">
          wards within {limit} min of {officeLabel}
          {reach.loading && <span className="updating"> · updating…</span>}
        </p>
        {reach.error && <p className="note error">Could not search: {reach.error}</p>}
        {r && notes && (
          <p className="note">
            Closest metro station: <b>{r.nearestStation.name}</b>, {notes.station}
            {r.nearestBusStop && notes.bus && (
              <>
                <br />
                Closest bus stop: <b>{r.nearestBusStop.name}</b>, {notes.bus}
              </>
            )}
          </p>
        )}

        {selected !== null && (
          <WardDetail
            wardName={wards.features[selected].properties.name}
            officeName={officeLabel}
            limit={limit}
            trip={trip}
            palette={palette}
          />
        )}

        <span className="lab">Reachable wards, quickest first</span>
        <WardList
          rows={ok}
          query={query}
          onQuery={setQuery}
          onPick={pickFromList}
          emptyText="No ward fits these limits. Try a longer commute, a longer trip to the stop, or add buses."
        />
      </aside>
    </div>
  );
}
