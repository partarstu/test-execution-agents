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
import { describe, expect, it } from 'vitest';
import { itemCounts, progressText } from './RunProgress';
import type { ItemStatus, TimelineItem } from './types';

const item = (status: ItemStatus): TimelineItem => ({ kind: 'STEP', description: status, status, errorMessage: null, resolution: null });

describe('itemCounts', () => {
  it('counts every item by its status, errors apart from failures', () => {
    const counts = itemCounts(['PASSED', 'PASSED', 'FAILED', 'ERROR', 'RUNNING', 'PENDING'].map((status) => item(status as ItemStatus)));

    expect(counts).toEqual({ passed: 2, failed: 1, errors: 1, running: 1, pending: 1 });
  });
});

describe('progressText', () => {
  it('names every status that has items', () => {
    expect(progressText({ passed: 2, failed: 1, errors: 2, running: 1, pending: 3 }))
      .toBe('2 passed, 1 failed, 2 errors, 1 running, 3 not run yet');
  });

  it('names a single error in the singular', () => {
    expect(progressText({ passed: 1, failed: 0, errors: 1, running: 0, pending: 0 })).toBe('1 passed, 1 error, 0 not run yet');
  });

  it('leaves out the failed, error and running counts while they are zero', () => {
    expect(progressText({ passed: 0, failed: 0, errors: 0, running: 0, pending: 4 })).toBe('0 passed, 4 not run yet');
  });
});
