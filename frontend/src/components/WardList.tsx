import type { WardTime } from "../types";
import { viaText } from "../lib/text";

interface Props {
  rows: WardTime[];
  query: string;
  emptyText: string;
  /** extra text after the lines used, e.g. "needs score 72" */
  extra?: (w: WardTime) => string | null;
  onQuery: (q: string) => void;
  onPick: (ward: number) => void;
}

const MAX_ROWS = 250;

export function WardList({ rows, query, emptyText, extra, onQuery, onPick }: Props) {
  const q = query.trim().toLowerCase();
  const shown = rows.filter(w => !q || w.name.toLowerCase().includes(q)).slice(0, MAX_ROWS);
  return (
    <>
      <label htmlFor="q" className="visually-hidden">
        Search wards
      </label>
      <input
        type="search"
        id="q"
        placeholder="Search a ward, e.g. Indiranagar"
        value={query}
        onChange={e => onQuery(e.target.value)}
      />
      <div className="list">
        {shown.length ? (
          shown.map(w => {
            const more = extra?.(w);
            return (
              <button type="button" key={w.ward} onClick={() => onPick(w.ward)}>
                <span>
                  {w.name}
                  <small>
                    {viaText(w)}
                    {more ? ` · ${more}` : ""}
                  </small>
                </span>
                <b>{w.minutes} min</b>
              </button>
            );
          })
        ) : (
          <p className="empty">{q ? "No reachable ward matches that name." : emptyText}</p>
        )}
      </div>
    </>
  );
}
