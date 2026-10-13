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
import { useEffect, useEffectEvent } from 'react';
import { isTypingTarget } from '../shortcuts';
import { PROMPT_ANCHOR_ID } from './anchor';

/**
 * Whether a digit pressed on `target` may answer the open prompt: only from inside the prompt or with the focus nowhere in
 * particular, never from a field, and never from a dialog the prompt opened on top of itself, such as a confirmation.
 */
export function mayAnswerFrom(target: EventTarget | null): boolean {
  const anchor = document.getElementById(PROMPT_ANCHOR_ID);
  if (!anchor || isTypingTarget(target)) {
    return false;
  }
  if (target === document.body || target === null) {
    return true;
  }
  if (!(target instanceof Element) || !anchor.contains(target)) {
    return false;
  }
  const dialog = target.closest('dialog');
  return dialog === null || dialog.contains(anchor);
}

/** Lets the keys 1 to 9 choose the prompt's answers in their order. */
export function useAnswerKeys(answers: (() => void)[], enabled: boolean): void {
  const onKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (!enabled || event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey || !/^[1-9]$/.test(event.key)) {
      return;
    }
    const answer = answers[Number(event.key) - 1];
    if (answer && mayAnswerFrom(event.target)) {
      event.preventDefault();
      answer();
    }
  });

  useEffect(() => {
    const listener = (event: KeyboardEvent) => onKeyDown(event);
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, []);
}
