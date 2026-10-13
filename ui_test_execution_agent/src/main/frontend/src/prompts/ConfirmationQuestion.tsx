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
import { CircleAlert, Hand } from 'lucide-react';
import { useId, useState, type ReactNode } from 'react';
import { PROMPT_ANCHOR_ID } from './anchor';
import { AnswerOptions } from './AnswerOptions';
import { openedAtOf } from './countdown';
import type { PromptOfType } from './Prompt';
import { countdownListeners, WaitingState } from './PromptFrame';
import { promptTitle } from './titles';
import { useAnswer } from './useAnswer';

interface Props {
  prompt: PromptOfType<'confirmation'>;
  onUnauthorized: () => void;
  /** The current action's row, which the question extends and whose whole box the pointer hovers to pause the countdown. */
  children: ReactNode;
}

/**
 * The confirmation before or after an action, answered in the current action of the step instead of in a dialog; the countdown's
 * default proceeds, so Halt is its only answer, and it never takes the focus, so a stray key cannot halt the run.
 */
export function ConfirmationQuestion({ prompt, onUnauthorized, children }: Props) {
  const calls = useAnswer(prompt.id, onUnauthorized);
  const [openedAt] = useState(() => openedAtOf(prompt, Date.now(), performance.now()));
  const titleId = useId();
  return (
    <section id={PROMPT_ANCHOR_ID} tabIndex={-1} className="trow question" aria-labelledby={titleId} aria-busy={calls.pending}
      {...countdownListeners(prompt, calls)}>
      {children}
      <div className="question-body">
        <h3 id={titleId} className="question-title">{promptTitle(prompt)}</h3>
        <WaitingState prompt={prompt} openedAt={openedAt} />
        <AnswerOptions
          autoFocus={false}
          numbered={false}
          disabled={calls.pending}
          options={[{
            id: 'halt', label: 'Halt', hint: 'Stop here and decide how the run goes on', Icon: Hand, tone: 'danger',
            onChoose: () => calls.answer({ decision: 'HALTED' }),
          }]}
        />
        {calls.error && <p className="prompt-error" role="alert"><CircleAlert size={18} />{calls.error}</p>}
      </div>
    </section>
  );
}
