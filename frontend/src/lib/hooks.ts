import { useEffect, useState } from "react";

/** The value, but only after it has stopped changing for `ms` (so dragging a slider sends one request). */
export function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export interface Loadable<T> {
  data: T | undefined;
  error: string | undefined;
  loading: boolean;
}

/**
 * Loads `key` with `load`, cancelling the previous request when the key changes. Keeps the last good
 * data while the next one loads, so the map does not flash. A null key clears the data.
 */
export function useLoad<T>(key: string | null, load: (key: string, signal: AbortSignal) => Promise<T>): Loadable<T> {
  const [state, setState] = useState<Loadable<T>>({ data: undefined, error: undefined, loading: key !== null });
  useEffect(() => {
    if (key === null) {
      setState({ data: undefined, error: undefined, loading: false });
      return;
    }
    const ctl = new AbortController();
    setState(s => ({ ...s, loading: true }));
    load(key, ctl.signal).then(
      data => setState({ data, error: undefined, loading: false }),
      (e: unknown) => {
        if (!ctl.signal.aborted) setState(s => ({ ...s, error: e instanceof Error ? e.message : String(e), loading: false }));
      },
    );
    return () => ctl.abort();
  }, [key, load]);
  return state;
}

/** True when the system asks for dark mode; follows changes. */
export function useDarkMode(): boolean {
  const query = "(prefers-color-scheme: dark)";
  const [dark, setDark] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = (e: MediaQueryListEvent) => setDark(e.matches);
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return dark;
}
