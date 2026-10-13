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
import type { GraphHealthReport } from './health/GraphHealthReport';
import type { Displaced, SessionInfo } from './types';

export type SessionProbe =
  | { kind: 'active'; info: SessionInfo }
  | { kind: 'none'; displaced: Displaced | null }
  | { kind: 'unreachable' };

export type LoginResult = { kind: 'ok'; info: SessionInfo } | { kind: 'rejected' } | { kind: 'rate-limited' } | { kind: 'failed' };

/** One read's outcome; 401 sends the panel back to the login view, as on every other call. */
export type Loaded<T> =
  | { kind: 'ok'; data: T }
  | { kind: 'not-found' }
  | { kind: 'unavailable' }
  | { kind: 'unauthorized' };

const SAME_ORIGIN: RequestInit = { credentials: 'same-origin' };

/** Tells a missing login from a restarting server, which the event stream cannot do because EventSource hides the status. */
export async function probeSession(): Promise<SessionProbe> {
  try {
    const response = await fetch('/api/session', SAME_ORIGIN);
    if (response.ok) {
      return { kind: 'active', info: (await response.json()) as SessionInfo };
    }
    if (response.status === 401) {
      const body = await response.text();
      return { kind: 'none', displaced: body ? (JSON.parse(body) as Displaced) : null };
    }
    return { kind: 'unreachable' };
  } catch {
    return { kind: 'unreachable' };
  }
}

export async function login(password: string): Promise<LoginResult> {
  try {
    const response = await fetch('/api/login', {
      ...SAME_ORIGIN,
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password }),
    });
    if (response.ok) {
      return { kind: 'ok', info: (await response.json()) as SessionInfo };
    }
    if (response.status === 429) {
      return { kind: 'rate-limited' };
    }
    return response.status === 401 ? { kind: 'rejected' } : { kind: 'failed' };
  } catch {
    return { kind: 'failed' };
  }
}

export async function logout(): Promise<void> {
  try {
    await fetch('/api/logout', { ...SAME_ORIGIN, method: 'POST' });
  } catch {
    // The session ends with the page either way; the server drops it at its idle timeout
  }
}

export async function load<T>(path: string, signal?: AbortSignal): Promise<Loaded<T>> {
  try {
    const response = await fetch(path, { ...SAME_ORIGIN, signal });
    if (response.ok) {
      return { kind: 'ok', data: (await response.json()) as T };
    }
    if (response.status === 401) {
      return { kind: 'unauthorized' };
    }
    return response.status === 404 ? { kind: 'not-found' } : { kind: 'unavailable' };
  } catch {
    return { kind: 'unavailable' };
  }
}

export const fetchHealth = (signal?: AbortSignal) => load<GraphHealthReport>('/api/health/graph', signal);
