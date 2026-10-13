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
import { useEffect, useState } from 'react';

const TICK_MILLIS = 250;
const URGENT_MILLIS = 10_000;

interface Timed {
  receivedAt: number;
  remainingMillis: number;
  paused: boolean;
}

/** Whether the countdown applies, whether it is paused or in its last seconds and the share of it still left. */
export function countdownState(prompt: { countdown: boolean; countdownMillis: number; held: boolean; paused: boolean }, remaining: number) {
  const counting = prompt.countdown && !prompt.held;
  return {
    counting,
    paused: counting && prompt.paused,
    urgent: counting && !prompt.paused && remaining < URGENT_MILLIS,
    fraction: prompt.countdownMillis > 0 ? Math.min(1, remaining / prompt.countdownMillis) : 0,
  };
}

/**
 * The time left at `now`, counted from what the server computed when it sent the prompt and frozen while it is paused; the operator
 * machine's clock never shifts the deadline, and a snapshot, a hold, a pause or a resume restarts the count from its fresh value.
 */
export function remainingAt(prompt: Timed, now: number): number {
  return prompt.paused ? prompt.remainingMillis : Math.max(0, prompt.receivedAt + prompt.remainingMillis - now);
}

/**
 * When the prompt opened on the operator's wall clock, from the time it had waited when the server sent it, so a reload or a
 * reconnect states the same time as the tab that saw it open.
 */
export function openedAtOf(prompt: { receivedAt: number; waitedMillis: number }, wallNow: number, monotonicNow: number): Date {
  return new Date(wallNow - (monotonicNow - prompt.receivedAt) - prompt.waitedMillis);
}

export function formatRemaining(millis: number): string {
  const seconds = Math.ceil(millis / 1_000);
  if (seconds < 60) {
    return `${seconds} s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return seconds % 60 === 0 ? `${minutes} min` : `${minutes} min ${seconds % 60} s`;
  }
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

/** The time left in at most three characters, for the countdown ring: seconds, then minutes, then hours. */
export function formatCompact(millis: number): string {
  const seconds = Math.ceil(millis / 1_000);
  if (seconds < 100) {
    return String(seconds);
  }
  const minutes = Math.round(seconds / 60);
  return minutes < 100 ? `${minutes}m` : `${Math.round(minutes / 60)}h`;
}

/** Re-renders a few times a second with the time left of the prompt. */
export function useRemainingMillis(prompt: Timed): number {
  const [now, setNow] = useState(() => performance.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(performance.now()), TICK_MILLIS);
    return () => clearInterval(timer);
  }, []);
  return remainingAt(prompt, now);
}
