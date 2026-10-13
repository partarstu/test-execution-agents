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
import { age, duration } from './format';

// The runner's locale picks the words and the decimal separator, so the checks look at the numbers only
describe('duration', () => {
  it('keeps milliseconds below a second', () => {
    expect(duration(850)).toMatch(/^850\D/);
  });

  it('shows one decimal below a minute', () => {
    expect(duration(2_310)).toMatch(/^2[.,]3\D/);
  });

  it('shows whole minutes and seconds above a minute', () => {
    expect(duration(65_400)).toMatch(/^1\D+5\D/);
    expect(duration(65_400)).not.toMatch(/[.,]\d/);
  });

  it('never goes below zero', () => {
    expect(duration(-5)).toMatch(/^0\D/);
  });
});

describe('age', () => {
  const now = Date.parse('2026-10-04T12:00:00Z');

  it('counts days beyond a day', () => {
    expect(age('2026-10-01T12:00:00Z', now)).toMatch(/3/);
  });

  it('counts minutes within the hour', () => {
    expect(age('2026-10-04T11:55:00Z', now)).toMatch(/5/);
  });
});
