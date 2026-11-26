import type { ReactNode } from "react";
import type { Step, TripResponse } from "../types";
import type { Palette } from "../lib/colours";
import type { Loadable } from "../lib/hooks";

interface Props {
  wardName: string;
  officeName: string;
  limit: number;
  trip: Loadable<TripResponse>;
  palette: Palette;
  /** shown under the trip, e.g. the place table */
  children?: ReactNode;
}

function dotColour(s: Step, p: Palette): string {
  if (s.type === "METRO" && s.line) return s.line.color;
  if (s.type === "BUS") return p.bus;
  return "var(--soft)";
}

/** The step's sentence with the line or bus name as a coloured chip and the destination in bold. */
function StepText({ step, palette }: { step: Step; palette: Palette }) {
  const chipText = step.type === "METRO" && step.line ? `${step.line.name} Line` : step.type === "BUS" ? step.bus?.label : undefined;
  const chipColour = step.type === "METRO" ? step.line?.color : palette.bus;
  const parts: ReactNode[] = [];
  let rest = step.text;
  if (chipText && rest.includes(chipText)) {
    const i = rest.indexOf(chipText);
    parts.push(rest.slice(0, i));
    const others = step.bus?.otherRouteNumbers;
    parts.push(
      <span
        key="chip"
        className="chip"
        style={{ background: chipColour, color: step.line?.id === "YELLOW" ? "#141821" : "#fff" }}
        title={others?.length ? `Buses on these routes are counted for the wait too: ${others.join(", ")}` : undefined}
      >
        {chipText}
      </span>,
    );
    rest = rest.slice(i + chipText.length);
  }
  const to = step.type === "FINAL" || step.type === "DIRECT" ? undefined : step.to;
  const j = to ? rest.lastIndexOf(` to ${to}`) : -1;
  if (to && j >= 0) {
    parts.push(rest.slice(0, j + 4));
    parts.push(<b key="to">{to}</b>);
    parts.push(rest.slice(j + 4 + to.length));
  } else {
    parts.push(rest);
  }
  return <>{parts}</>;
}

export function WardDetail({ wardName, officeName, limit, trip, palette, children }: Props) {
  const t = trip.data?.trip;
  let body: ReactNode;
  if (trip.error) {
    body = <p className="sub">Could not load the trip: {trip.error}</p>;
  } else if (!trip.data) {
    body = <p className="sub">Finding the trip…</p>;
  } else if (!t) {
    body = <p className="sub">Not reachable with these limits: no stop is close enough on a trip that fits your time.</p>;
  } else {
    body = (
      <>
        <p className="sub">
          About <b>{t.minutes} min</b> from {officeName}.{t.overLimit ? ` That is over your ${limit} min limit.` : ""}
        </p>
        <ol>
          {t.steps.map((s, i) => (
            <li key={i} style={{ ["--c" as string]: dotColour(s, palette) }}>
              <StepText step={s} palette={palette} />
            </li>
          ))}
        </ol>
      </>
    );
  }
  return (
    <div className="card" aria-live="polite">
      <h3>{wardName}</h3>
      {body}
      {children}
    </div>
  );
}
