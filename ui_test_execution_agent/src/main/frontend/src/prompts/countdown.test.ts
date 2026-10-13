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
import { countdownState, formatCompact, formatRemaining, openedAtOf, remainingAt } from './countdown';

describe('remainingAt', () => {
  it('counts down from the remaining time the server sent, starting at its arrival', () => {
    const prompt = { receivedAt: 1_000, remainingMillis: 2_000, paused: false };

    expect(remainingAt(prompt, 1_000)).toBe(2_000);
    expect(remainingAt(prompt, 2_500)).toBe(500);
  });

  it('never goes below zero once the deadline has passed', () => {
    expect(remainingAt({ receivedAt: 0, remainingMillis: 2_000, paused: false }, 9_000)).toBe(0);
  });

  it('restarts from the fresh value of a later snapshot or hold', () => {
    const first = { receivedAt: 0, remainingMillis: 2_000, paused: false };
    const held = { receivedAt: 1_500, remainingMillis: 7_200_000, paused: false };

    expect(remainingAt(first, 1_500)).toBe(500);
    expect(remainingAt(held, 1_500)).toBe(7_200_000);
  });

  it('stays at the time left the server froze while the countdown is paused', () => {
    expect(remainingAt({ receivedAt: 0, remainingMillis: 2_000, paused: true }, 9_000)).toBe(2_000);
  });
});

describe('openedAtOf', () => {
  it('dates the opening back by the time the prompt had waited and the time since it arrived', () => {
    const prompt = { receivedAt: 5_000, waitedMillis: 60_000 };

    expect(openedAtOf(prompt, 1_000_000, 7_000).getTime()).toBe(1_000_000 - 2_000 - 60_000);
  });
});

describe('formatRemaining', () => {
  it('shows seconds, rounded up so the last second is never shown as zero', () => {
    expect(formatRemaining(1_200)).toBe('2 s');
    expect(formatRemaining(200)).toBe('1 s');
  });

  it('shows minutes and hours for long waits', () => {
    expect(formatRemaining(90_000)).toBe('1 min 30 s');
    expect(formatRemaining(120_000)).toBe('2 min');
    expect(formatRemaining(7_200_000)).toBe('2 h 0 min');
  });
});

describe('formatCompact', () => {
  it('fits the time left in three characters', () => {
    expect(formatCompact(23_400)).toBe('24');
    expect(formatCompact(99_000)).toBe('99');
    expect(formatCompact(150_000)).toBe('3m');
    expect(formatCompact(7_200_000)).toBe('2h');
  });
});

describe('countdownState', () => {
  const prompt = { countdown: true, countdownMillis: 40_000, held: false, paused: false };

  it('counts a running countdown with the share left, urgent in its last ten seconds', () => {
    expect(countdownState(prompt, 30_000)).toEqual({ counting: true, paused: false, urgent: false, fraction: 0.75 });
    expect(countdownState(prompt, 9_000)).toEqual({ counting: true, paused: false, urgent: true, fraction: 0.225 });
  });

  it('is never urgent while the countdown is held or the prompt has none', () => {
    expect(countdownState({ ...prompt, held: true }, 1_000).urgent).toBe(false);
    expect(countdownState({ ...prompt, countdown: false }, 1_000)).toMatchObject({ counting: false, urgent: false });
  });

  it('is paused but never urgent while the pointer is on the prompt, with the share measured against the whole countdown', () => {
    expect(countdownState({ ...prompt, paused: true }, 4_000)).toEqual({ counting: true, paused: true, urgent: false, fraction: 0.1 });
  });

  it('has no share left without a countdown', () => {
    expect(countdownState({ ...prompt, countdown: false, countdownMillis: 0 }, 0).fraction).toBe(0);
  });
});
