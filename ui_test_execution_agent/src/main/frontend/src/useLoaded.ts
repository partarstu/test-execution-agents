/*
 * ui-test-execution-agent - Agent specializing in execution of UI tests.
 * Copyright © 2025-2026 Taras Paruta (partarstu@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import type { Loaded } from './api';

export interface LoadState<T> {
  /** The newest outcome, kept while a newer key or a reload loads; null until the first one arrives. */
  loaded: Loaded<T> | null;
  loading: boolean;
  reload: () => void;
}

/**
 * Reads once per key and again on every reload, aborting a read that a newer key or an unmount supersedes; a view that
 * `<Activity>` shows again keeps what it already read instead of reading it twice.
 */
export function useLoaded<T>(key: string, read: (signal: AbortSignal) => Promise<Loaded<T>>, onUnauthorized: () => void): LoadState<T> {
  const [result, setResult] = useState<{ key: string; loaded: Loaded<T> } | null>(null);
  const [version, setVersion] = useState(0);
  const readKey = useRef<string | null>(null);
  const readNow = useEffectEvent(read);
  const handleUnauthorized = useEffectEvent(onUnauthorized);
  const versionedKey = `${version}:${key}`;

  useEffect(() => {
    if (readKey.current === versionedKey) {
      return;
    }
    const controller = new AbortController();
    void readNow(controller.signal).then((loaded) => {
      if (controller.signal.aborted) {
        return;
      }
      if (loaded.kind === 'unauthorized') {
        handleUnauthorized();
        return;
      }
      readKey.current = versionedKey;
      setResult({ key: versionedKey, loaded });
    });
    return () => controller.abort();
  }, [versionedKey]);

  return { loaded: result?.loaded ?? null, loading: result?.key !== versionedKey, reload: () => setVersion(version + 1) };
}
