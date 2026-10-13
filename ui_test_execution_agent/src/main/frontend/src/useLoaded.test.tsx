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
// @vitest-environment jsdom
import { act, render, renderHook, screen } from '@testing-library/react';
import { Activity } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { Loaded } from './api';
import { installDom } from './testing/dom';
import { useLoaded } from './useLoaded';

installDom();

/** A read whose calls the test resolves one by one. */
function controlledRead() {
  const calls: { signal: AbortSignal; resolve: (loaded: Loaded<string>) => void }[] = [];
  const read = (signal: AbortSignal) => new Promise<Loaded<string>>((resolve) => calls.push({ signal, resolve }));
  return { calls, read };
}

const ok = (data: string): Loaded<string> => ({ kind: 'ok', data });

describe('useLoaded', () => {
  it('aborts the read a newer key supersedes and keeps only the newer outcome', async () => {
    const { calls, read } = controlledRead();
    const { result, rerender } = renderHook(({ key }) => useLoaded(key, read, vi.fn()), { initialProps: { key: 'first' } });

    rerender({ key: 'second' });
    await act(async () => calls[0].resolve(ok('first')));

    expect(calls[0].signal.aborted).toBe(true);
    expect(result.current.loaded).toBeNull();
    expect(result.current.loading).toBe(true);

    await act(async () => calls[1].resolve(ok('second')));

    expect(result.current.loaded).toEqual(ok('second'));
    expect(result.current.loading).toBe(false);
  });

  it('hands a 401 to the unauthorized handler instead of keeping it', async () => {
    const { calls, read } = controlledRead();
    const onUnauthorized = vi.fn();
    const { result } = renderHook(() => useLoaded('key', read, onUnauthorized));

    await act(async () => calls[0].resolve({ kind: 'unauthorized' }));

    expect(onUnauthorized).toHaveBeenCalledOnce();
    expect(result.current.loaded).toBeNull();
  });

  it('reads again on reload and keeps the previous outcome until the new one arrives', async () => {
    const { calls, read } = controlledRead();
    const { result } = renderHook(() => useLoaded('key', read, vi.fn()));
    await act(async () => calls[0].resolve(ok('old')));

    act(() => result.current.reload());

    expect(calls).toHaveLength(2);
    expect(result.current.loaded).toEqual(ok('old'));
    expect(result.current.loading).toBe(true);

    await act(async () => calls[1].resolve(ok('new')));

    expect(result.current.loaded).toEqual(ok('new'));
    expect(result.current.loading).toBe(false);
  });

  it('does not read the same key again when a hidden Activity is shown again', async () => {
    const { calls, read } = controlledRead();
    function Probe() {
      const { loaded } = useLoaded('key', read, vi.fn());
      return <p>{loaded?.kind === 'ok' ? loaded.data : 'loading'}</p>;
    }
    const { rerender } = render(<Activity mode="visible"><Probe /></Activity>);
    await act(async () => calls[0].resolve(ok('read once')));

    rerender(<Activity mode="hidden"><Probe /></Activity>);
    rerender(<Activity mode="visible"><Probe /></Activity>);

    expect(calls).toHaveLength(1);
    expect(screen.getByText('read once')).toBeTruthy();
  });
});
