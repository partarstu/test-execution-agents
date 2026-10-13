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
import type { AnswerOf, PromptOfType, PromptType } from './Prompt';
import type { ActionCall } from './useAnswer';

/** What the frame hands the body of a prompt: the prompt, its typed answer, its actions and whether a call is running. */
export interface BodyProps<T extends PromptType> {
  prompt: PromptOfType<T>;
  answer: (value: AnswerOf<T>) => void;
  call: ActionCall;
  pending: boolean;
  searchDelayMillis: number;
  /** Holds the countdown, e.g. once the operator starts typing, so a slow typist is never cut off. */
  hold: () => void;
}
