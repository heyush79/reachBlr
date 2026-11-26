import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api";
import type { LatLon, Meta, WardsGeoJson } from "./types";
import { DARK, LIGHT, SCORE_LEGEND, scoreBand, timeBand, timeLegend } from "./lib/colours";
import { useDarkMode, useDebounced, useLoad } from "./lib/hooks";
import { loadSettings, officeLocation, officeName, saveSettings, searchParams, type Settings } from "./lib/settings";
import { mustText, passesMust, scoreFor, scoreName } from "./lib/places";
import { nearestNotes } from "./lib/text";
import { Controls } from "./components/Controls";
import { Footer } from "./components/Footer";
import { GoogleNearby } from "./components/GoogleNearby";
import { Legend } from "./components/Legend";
import { MapView } from "./components/MapView";
import { PlacesTable } from "./components/PlacesTable";
import { Segmented } from "./components/Segmented";
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
      <Footer placesAsOf={boot && "meta" in boot ? boot.meta.placesAsOf : null} />
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

  const props = useMemo(() => wards.features.map(f => f.properties), [wards]);
  const { colour, must, sort } = settings;
  const byWard = useMemo(() => new Map((r?.wards ?? []).map(w => [w.ward, w])), [r]);
  // within the limit and with every must-have; quickest first (as the backend sends them) or best score first
  const ok = useMemo(() => {
    const rows = (r?.wards ?? []).filter(w => w.minutes <= limit && passesMust(props[w.ward], must));
    if (sort === "score") rows.sort((a, b) => scoreFor(props[b.ward], colour) - scoreFor(props[a.ward], colour) || a.minutes - b.minutes);
    return rows;
  }, [r, limit, props, must, sort, colour]);
  const bands = useMemo(() => {
    const b: (number | null)[] = wards.features.map(() => null);
    for (const w of ok) b[w.ward] = colour === "time" ? timeBand(w.minutes, limit) : scoreBand(scoreFor(props[w.ward], colour));
    return b;
  }, [ok, limit, wards, colour, props]);

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
        {colour === "time" ? (
          <Legend items={timeLegend(limit)} palette={palette} restLabel={must.length ? "longer or missing a must-have" : "longer"} />
        ) : (
          <Legend
            title={colour === "score" ? "Everyday needs score" : meta.placeTypes[Number(colour)]?.label}
            items={SCORE_LEGEND}
            palette={palette}
            restLabel="not in reach"
          />
        )}
      </div>

      <aside>
        <Controls
          offices={meta.offices}
          placeTypes={meta.placeTypes}
          settings={settings}
          placing={placing}
          onChange={change}
          onPlacing={setPlacing}
        />

        <div className="big" aria-live="polite">
          {r ? `${ok.length} of ${wards.features.length}` : " "}
        </div>
        <p className="sub">
          wards within {limit} min of {officeLabel}
          {mustText(must, meta.placeTypes)}
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
          >
            <PlacesTable ward={props[selected]} types={meta.placeTypes} />
            {meta.googlePlaces && meta.placeTypes.length > 0 && (
              <GoogleNearby
                key={selected}
                centre={props[selected].centre}
                wardName={props[selected].name}
                types={meta.placeTypes}
              />
            )}
          </WardDetail>
        )}

        <Segmented
          label="Reachable wards"
          value={sort}
          options={[
            { value: "time", label: "Quickest first" },
            { value: "score", label: "Best for daily needs" },
          ]}
          onChange={s => change({ sort: s })}
        />
        <WardList
          rows={ok}
          query={query}
          onQuery={setQuery}
          onPick={pickFromList}
          extra={meta.placeTypes.length ? w => `${scoreName(colour, meta.placeTypes)} ${scoreFor(props[w.ward], colour)}` : undefined}
          emptyText={
            must.length
              ? "No ward fits these limits and must-haves. Try fewer must-haves or a longer commute."
              : "No ward fits these limits. Try a longer commute, a longer trip to the stop, or add buses."
          }
        />
      </aside>
    </div>
  );
}
