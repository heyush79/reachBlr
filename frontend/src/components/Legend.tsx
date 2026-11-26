import type { LegendItem, Palette } from "../lib/colours";

interface Props {
  title?: string;
  items: LegendItem[];
  palette: Palette;
  restLabel: string;
}

export function Legend({ title, items, palette, restLabel }: Props) {
  return (
    <div className="legend" aria-label="Map legend">
      {title && (
        <span>
          <b>{title}</b>
        </span>
      )}
      {items.map(it => (
        <span key={it.label}>
          <i style={{ background: palette.ramp[it.band - 1] }} />
          {it.label}
        </span>
      ))}
      <span>
        <i className="empty-swatch" />
        {restLabel}
      </span>
    </div>
  );
}
