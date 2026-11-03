import { useCallback, useEffect, useRef, useState } from "react";

export const ROUTE_CHANGE = "sicc:route-change";
export function routePage() { return window.location.hash.replace(/^#\/?/, "").split("?")[0]; }
function matches(page: string) { return routePage() === page || (page === "consulta-publica" && routePage() === ""); }
export function routeQuery(page: string) {
  return matches(page) ? Object.fromEntries(new URLSearchParams(window.location.hash.split("?")[1] ?? "")) : {};
}
export function writeRoute(page: string, query: Record<string, string>, replace = false) {
  const search = new URLSearchParams(Object.entries(query).filter(([, value]) => value)).toString();
  const hash = `#/${page}${search ? `?${search}` : ""}`;
  if (hash === window.location.hash) return;
  window.history[replace ? "replaceState" : "pushState"](null, "", hash);
  window.dispatchEvent(new Event(ROUTE_CHANGE));
}

/** Only explicitly allowed navigation fields enter the URL; form drafts and tokens never do. */
export function useRouteState(page: string, keys: readonly string[]) {
  const allowed = useRef(keys);
  const clean = useCallback((values: Record<string, string>) => Object.fromEntries(Object.entries(values)
    .filter(([key, value]) => allowed.current.includes(key) && typeof value === "string" && value.length > 0 && value.length <= 200)), []);
  const [state, setState] = useState(() => clean(routeQuery(page)));
  const current = useRef(state); current.current = state;
  useEffect(() => {
    const restore = () => {
      if (!matches(page)) return;
      const next = clean(routeQuery(page)); current.current = next;
      setState(previous => JSON.stringify(previous) === JSON.stringify(next) ? previous : next);
    };
    window.addEventListener("popstate", restore); window.addEventListener("hashchange", restore); window.addEventListener(ROUTE_CHANGE, restore);
    return () => { window.removeEventListener("popstate", restore); window.removeEventListener("hashchange", restore); window.removeEventListener(ROUTE_CHANGE, restore); };
  }, [page, clean]);
  const update = useCallback((changes: Record<string, string>, replace = false) => {
    const next = clean({ ...current.current, ...changes }); current.current = next; setState(next);
    if (matches(page)) writeRoute(page, next, replace);
  }, [clean, page]);
  return [state, update] as const;
}
