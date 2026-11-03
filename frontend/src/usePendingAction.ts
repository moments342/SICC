import { useRef, useState } from "react";

/** Lock synchronously, including clicks delivered before React renders disabled controls. */
export function usePendingAction() {
  const locks = useRef(new Set<string>());
  const [pending, setPending] = useState(new Set<string>());
  async function run(key: string, action: () => Promise<unknown>) {
    if (locks.current.has(key)) return;
    locks.current.add(key); setPending(new Set(locks.current));
    try { await action(); }
    finally { locks.current.delete(key); setPending(new Set(locks.current)); }
  }
  return { pending, run };
}
