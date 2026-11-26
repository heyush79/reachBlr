import type { Office } from "../types";
import type { Settings } from "../lib/settings";
import { Segmented } from "./Segmented";

interface Props {
  offices: Office[];
  settings: Settings;
  placing: boolean;
  onChange: (patch: Partial<Settings>) => void;
  onPlacing: (on: boolean) => void;
}

export function Controls({ offices, settings: s, placing, onChange, onPlacing }: Props) {
  return (
    <>
      <label htmlFor="office">Your office</label>
      <div className="row">
        <select
          id="office"
          value={s.custom ? "custom" : String(s.office)}
          onChange={e => {
            if (e.target.value !== "custom") onChange({ office: Number(e.target.value), custom: null });
          }}
        >
          {offices.map((o, i) => (
            <option key={o.name} value={i}>
              {o.name}
            </option>
          ))}
          <option value="custom" hidden>
            Spot picked on map
          </option>
        </select>
        <button type="button" className="place" aria-pressed={placing} onClick={() => onPlacing(!placing)}>
          Pick on map
        </button>
      </div>

      <Segmented
        label="Travel by"
        value={s.net}
        options={[
          { value: "metro", label: "Metro only" },
          { value: "all", label: "Metro and bus" },
        ]}
        onChange={net => onChange({ net })}
      />

      <label htmlFor="limit">
        Longest commute you accept <span className="val">{s.limit} min</span>
      </label>
      <input
        type="range"
        id="limit"
        min={15}
        max={90}
        step={5}
        value={s.limit}
        onChange={e => onChange({ limit: Number(e.target.value) })}
      />

      <Segmented
        label="Getting to the first stop and from the last one"
        value={s.mode}
        options={[
          { value: "walk", label: "Walk" },
          { value: "auto", label: "Auto or cab" },
        ]}
        onChange={mode => onChange({ mode })}
      />

      <label htmlFor="leg">
        Longest trip to or from a stop <span className="val">{s.leg} min</span>
      </label>
      <input
        type="range"
        id="leg"
        min={5}
        max={30}
        step={5}
        value={s.leg}
        onChange={e => onChange({ leg: Number(e.target.value) })}
      />

      <Segmented
        label="Time of travel"
        value={s.when}
        options={[
          { value: "morning", label: "Weekday morning" },
          { value: "evening", label: "Evening, 8 to 10 pm" },
        ]}
        onChange={when => onChange({ when })}
      />
    </>
  );
}
