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
import type { ActionName, PromptActions } from './Prompt';

/** The outcome of a call to the open prompt; a rejection carries the server's message, which is shown to the operator. */
export type CallResult<T> =
  | { kind: 'ok'; value: T }
  | { kind: 'rejected'; message: string }
  | { kind: 'unauthorized' };

const FAILED_MESSAGE = 'The agent did not answer. Check the connection and try again.';

export function imageUrl(promptId: string, imageId: string): string {
  return `/api/prompts/${encodeURIComponent(promptId)}/images/${encodeURIComponent(imageId)}`;
}

export function postAnswer(promptId: string, answer: object): Promise<CallResult<null>> {
  return post(`/api/prompts/${encodeURIComponent(promptId)}/answer`, answer, () => null);
}

export type CountdownChange = 'hold' | 'pause' | 'resume';

export function postCountdown(promptId: string, change: CountdownChange): Promise<CallResult<null>> {
  return post(`/api/prompts/${encodeURIComponent(promptId)}/${change}`, undefined, () => null);
}

export function runAction<N extends ActionName>(
  promptId: string,
  name: N,
  request: PromptActions[N]['request'],
): Promise<CallResult<PromptActions[N]['response']>> {
  return post(`/api/prompts/${encodeURIComponent(promptId)}/actions/${name}`, request,
    (body) => JSON.parse(body) as PromptActions[N]['response']);
}

async function post<T>(path: string, body: object | undefined, read: (body: string) => T): Promise<CallResult<T>> {
  try {
    const response = await fetch(path, {
      credentials: 'same-origin',
      method: 'POST',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (response.status === 401) {
      return { kind: 'unauthorized' };
    }
    const text = await response.text();
    if (response.ok) {
      return { kind: 'ok', value: read(text) };
    }
    return { kind: 'rejected', message: errorMessage(text) };
  } catch {
    return { kind: 'rejected', message: FAILED_MESSAGE };
  }
}

function errorMessage(body: string): string {
  try {
    const parsed = JSON.parse(body) as { error?: unknown };
    return typeof parsed.error === 'string' ? parsed.error : FAILED_MESSAGE;
  } catch {
    return FAILED_MESSAGE;
  }
}
