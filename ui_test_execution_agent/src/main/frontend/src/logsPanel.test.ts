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
import { afterEach, describe, expect, it, vi } from 'vitest';
import { storedLogsOpen, storeLogsOpen, unseenProblems } from './logsPanel';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('the logs panel state', () => {
  it('starts closed and remembers the last choice', () => {
    expect(storedLogsOpen()).toBe(false);

    storeLogsOpen(true);

    expect(storedLogsOpen()).toBe(true);
  });

  it('stays closed and keeps working without storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(() => storeLogsOpen(true)).not.toThrow();
    expect(storedLogsOpen()).toBe(false);
  });
});

describe('unseenProblems', () => {
  const lines = ['10:00 WARN a - slow', '10:01 INFO a - fine', '10:02 ERROR a - gone', '10:03 INFO a - fine'];

  it('counts the warnings and errors from the first unseen line on, wherever the tail starts', () => {
    expect(unseenProblems(lines, 0, 0)).toBe(2);
    expect(unseenProblems(lines, 0, 1)).toBe(1);
    expect(unseenProblems(lines, 10, 13)).toBe(0);
    expect(unseenProblems(lines, 10, 4)).toBe(2);
  });
});
