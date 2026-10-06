import { useCallback, useEffect, useState } from "react";
import { request } from "./api";

/** A failed read is never represented by a successful, empty collection. */
export function useResource<T>(path: string | null, token?: string, revision = 0) {
  const [data, setData] = useState<T>();
  const [loading, setLoading] = useState(path !== null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const reload = useCallback(() => setAttempt(value => value + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setData(undefined);
    setError("");
    setLoading(path !== null);
    if (path !== null) {
      void request<T>(path, { signal: controller.signal }, token)
        .then(value => { if (!controller.signal.aborted) setData(value); })
        .catch(cause => {
          if (!controller.signal.aborted) setError((cause as Error).message);
        })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }
    return () => controller.abort();
  }, [path, token, revision, attempt]);

  return { data, loading, error, reload };
}
