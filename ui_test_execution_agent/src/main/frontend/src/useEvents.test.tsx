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
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { INITIAL_STATE } from './events';
import { installDom } from './testing/dom';
import { useEvents } from './useEvents';

installDom();

class FakeEventSource extends EventTarget {
  static latest: FakeEventSource | null = null;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor() {
    super();
    FakeEventSource.latest = this;
  }

  close() {}

  emit(name: string, data: unknown) {
    this.dispatchEvent(new MessageEvent(name, { data: JSON.stringify(data) }));
  }
}

const PROMPT = {
  id: 'prompt-1', type: 'message', remainingMillis: 60_000, waitedMillis: 0, countdown: false, countdownMillis: 0, held: false, paused: false, hasDefault: true,
  payload: { title: 'Element not found', message: 'The Save button is not on the screen.', severity: 'WARNING', screenshotId: null },
};

function openStreamWithPrompt() {
  const { result } = renderHook(() => useEvents(true, { onDisplaced: vi.fn(), onSignedOut: vi.fn() }));
  act(() => FakeEventSource.latest!.emit('snapshot', { state: { ...INITIAL_STATE, prompt: PROMPT } }));
  expect(result.current.state.prompt?.id).toBe('prompt-1');
  return result;
}

describe('useEvents', () => {
  beforeEach(() => {
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each(['visible', 'hidden'] as const)('closes the prompt the server closed while the page is %s', (visibility) => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(visibility);
    const result = openStreamWithPrompt();

    act(() => FakeEventSource.latest!.emit('prompt-closed', { id: 'prompt-1' }));

    expect(result.current.state.prompt).toBeNull();
  });

  it('keeps a newer prompt open when the close of an older one arrives', () => {
    const result = openStreamWithPrompt();

    act(() => FakeEventSource.latest!.emit('prompt-closed', { id: 'prompt-0' }));

    expect(result.current.state.prompt?.id).toBe('prompt-1');
  });
});
