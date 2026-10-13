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
import { startTransition, useActionState, useRef, useState, useTransition } from 'react';
import { postAnswer, postCountdown, runAction, type CallResult, type CountdownChange } from './api';
import type { ActionName, PromptActions } from './Prompt';

/** Runs a prompt action and resolves with its response, or with null after the frame has shown why it failed. */
export type ActionCall = <N extends ActionName>(
  name: N,
  request: PromptActions[N]['request'],
) => Promise<PromptActions[N]['response'] | null>;

export interface PromptCalls {
  answer: (value: object) => void;
  call: ActionCall;
  /** Holds the countdown, which then waits until the prompt upper bound. */
  hold: () => void;
  /** Freezes the time left of the countdown until `resume`. */
  pause: () => void;
  resume: () => void;
  pending: boolean;
  error: string | null;
}

/**
 * The answer and the actions of one prompt, with one pending state and one rejection message for all of them, so no prompt body
 * handles a 409 or a validation error itself.
 */
export function useAnswer(promptId: string, onUnauthorized: () => void): PromptCalls {
  const [actionError, setActionError] = useState<string | null>(null);
  const [acting, startActing] = useTransition();
  const [answerError, submit, answering] = useActionState(async (_previous: string | null, value: object) => {
    setActionError(null);
    return messageOf(await postAnswer(promptId, value), onUnauthorized);
  }, null);

  const answer = (value: object) => startTransition(() => submit(value));

  const call: ActionCall = (name, request) =>
    new Promise((resolve) => {
      startActing(async () => {
        const result = await runAction(promptId, name, request);
        const message = messageOf(result, onUnauthorized);
        setActionError(message);
        resolve(result.kind === 'ok' ? result.value : null);
      });
    });

  // Sent one after another, so a quick pass of the pointer never resumes before it paused; they leave the controls enabled, and a
  // refusal only means that the countdown had already changed
  const countdownChanges = useRef(Promise.resolve());
  const changeCountdown = (change: CountdownChange) => {
    countdownChanges.current = countdownChanges.current.then(async () => {
      if ((await postCountdown(promptId, change)).kind === 'unauthorized') {
        onUnauthorized();
      }
    });
  };

  return {
    answer, call, hold: () => changeCountdown('hold'), pause: () => changeCountdown('pause'), resume: () => changeCountdown('resume'),
    pending: answering || acting, error: actionError ?? answerError,
  };
}

function messageOf(result: CallResult<unknown>, onUnauthorized: () => void): string | null {
  switch (result.kind) {
    case 'ok':
      return null;
    case 'rejected':
      return result.message;
    case 'unauthorized':
      onUnauthorized();
      return null;
  }
}
