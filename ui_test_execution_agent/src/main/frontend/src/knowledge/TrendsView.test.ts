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
import { axisTicks, innerWeekLabels } from './TrendsView';

describe('innerWeekLabels', () => {
  it('labels every other week of a wide plot, clear of the dates at both ends', () => {
    expect(innerWeekLabels(12, 28, 640)).toEqual([4, 6, 8]);
  });

  it('labels no week between the ends of a plot too narrow for a third date', () => {
    expect(innerWeekLabels(12, 28, 268)).toEqual([]);
  });

  it.each([0, 1, 2])('labels nothing between the ends of %i weeks', (weekCount) => {
    expect(innerWeekLabels(weekCount, 28, 1200)).toEqual([]);
  });
});

describe('axisTicks', () => {
  it.each([
    [1, [0, 1]],
    [3, [0, 1, 2, 3]],
    [5, [0, 2, 4, 6]],
    [8, [0, 2, 4, 6, 8]],
    [17, [0, 5, 10, 15, 20]],
    [40, [0, 10, 20, 30, 40]],
    [123, [0, 50, 100, 150]],
  ])('runs from 0 in even, round steps up to %i', (max, ticks) => {
    expect(axisTicks(max)).toEqual(ticks);
  });
});
