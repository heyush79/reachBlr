import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import type { LatLon, NearbyResponse, PlaceType } from "../types";

interface Props {
  centre: LatLon;
  wardName: string;
  types: PlaceType[];
}

type State =
  | { kind: "idle" }
  | { kind: "loading"; type: number }
  | { kind: "done"; type: number; data: NearbyResponse }
  | { kind: "error"; type: number; message: string };

const distance = (m: number) => (m < 1000 ? `${Math.round(m / 10) * 10} m` : `${(m / 1000).toFixed(1)} km`);

/**
 * Places near the ward centre from Google Places, shown as a list only. Google's terms do not allow
 * Places results on a non-Google map, so nothing here is drawn on our map. Google is only asked when the
 * visitor picks a type, and nothing is kept.
 */
export function GoogleNearby({ centre, wardName, types }: Props) {
  const [state, setState] = useState<State>({ kind: "idle" });
  const ctl = useRef<AbortController | null>(null);
  useEffect(() => () => ctl.current?.abort(), []);

  const ask = (k: number) => {
    ctl.current?.abort();
    const c = new AbortController();
    ctl.current = c;
    setState({ kind: "loading", type: k });
    api.nearby(centre[0], centre[1], types[k].key, c.signal).then(
      data => setState({ kind: "done", type: k, data }),
      (e: unknown) => {
        if (c.signal.aborted) return;
        const message =
          e instanceof ApiError && e.status === 429
            ? e.message
            : "Google Places did not answer. Please try again later.";
        setState({ kind: "error", type: k, message });
      },
    );
  };

  const current = state.kind === "idle" ? null : state.type;
  return (
    <section className="gnearby" aria-label={`Nearby on Google, within 1 km of the centre of ${wardName}`}>
      <span className="lab">Nearby on Google, within 1 km of the ward centre</span>
      <div className="chips" role="group" aria-label="Look up on Google">
        {types.map((t, k) => (
          <button key={t.key} type="button" aria-pressed={current === k} onClick={() => ask(k)}>
            {t.shortLabel}
          </button>
        ))}
      </div>
      {state.kind === "loading" && <p className="sub gnote">Asking Google…</p>}
      {state.kind === "error" && <p className="sub gnote">{state.message}</p>}
      {state.kind === "done" && (
        <div className="gbox" aria-live="polite">
          {state.data.places.length === 0 ? (
            <p className="sub gnote">Google lists no {types[state.type].label.toLowerCase()} within 1 km.</p>
          ) : (
            <ol className="glist">
              {state.data.places.map(p => (
                <li key={p.id}>
                  <span className="gname">
                    {p.name}
                    {p.address && <small>{p.address}</small>}
                    {p.attributions.length > 0 && (
                      <small>
                        Data:{" "}
                        {p.attributions.map((a, i) => (
                          <span key={i}>
                            {i > 0 && ", "}
                            {a.providerUri ? (
                              <a href={a.providerUri} target="_blank" rel="noopener noreferrer">
                                {a.provider}
                              </a>
                            ) : (
                              a.provider
                            )}
                          </span>
                        ))}
                      </small>
                    )}
                  </span>
                  <span className="gside">
                    <b>{distance(p.distanceMetres)}</b>
                    {p.googleMapsUri && (
                      <a href={p.googleMapsUri} target="_blank" rel="noopener noreferrer">
                        View on Google Maps
                      </a>
                    )}
                  </span>
                </li>
              ))}
            </ol>
          )}
          <p className="gattr">
            Results from{" "}
            <span className="google-maps" translate="no">
              Google Maps
            </span>
          </p>
        </div>
      )}
    </section>
  );
}
