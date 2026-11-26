interface Props<T extends string> {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
}

/** A row of two or more buttons where exactly one is pressed. */
export function Segmented<T extends string>({ label, value, options, onChange }: Props<T>) {
  return (
    <>
      <span className="lab">{label}</span>
      <div className="seg" role="group" aria-label={label}>
        {options.map(o => (
          <button key={o.value} type="button" aria-pressed={o.value === value} onClick={() => onChange(o.value)}>
            {o.label}
          </button>
        ))}
      </div>
    </>
  );
}
