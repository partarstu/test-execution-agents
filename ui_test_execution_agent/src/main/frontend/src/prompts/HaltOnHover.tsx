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
import type { ReactNode } from 'react';
import { PROMPT_ANCHOR_ID } from './anchor';
import { countdownState, formatCompact, useRemainingMillis } from './countdown';
import { CountdownRing } from './CountdownRing';
import type { PromptOfType } from './Prompt';
import { countdownListeners, waitingOutcome } from './PromptFrame';
import { useAnswer } from './useAnswer';

interface Props {
  prompt: PromptOfType<'confirmation'>;
  onUnauthorized: () => void;
  /** The compact current action's row, which the pointer hovers to pause the countdown. */
  children: ReactNode;
}

/**
 * The confirmation beside the large live view, kept out of the operator's way inside the compact current action: a ring and a line
 * count down, Halt stops the run, and the pointer on the action pauses the countdown; it never takes the focus itself.
 */
export function HaltOnHover({ prompt, onUnauthorized, children }: Props) {
  const calls = useAnswer(prompt.id, onUnauthorized);
  return (
    <section id={PROMPT_ANCHOR_ID} tabIndex={-1} className="trow halt-hover"
      aria-label={prompt.payload.beforeExecution ? 'Halt before this action?' : 'Halt after this action?'} aria-busy={calls.pending}
      {...countdownListeners(prompt, calls)}>
      {children}
      <ActionCountdown prompt={prompt}>
        <button type="button" className="danger small" disabled={calls.pending} onClick={() => calls.answer({ decision: 'HALTED' })}>
          <Hand size={16} aria-hidden="true" />Halt
        </button>
      </ActionCountdown>
      {calls.error && <p className="prompt-error halt-error" role="alert"><CircleAlert size={18} />{calls.error}</p>}
    </section>
  );
}

/** The ring beside the action and, under it, the time left with what then happens next to Halt; it re-renders as the time runs. */
function ActionCountdown({ prompt, children }: { prompt: PromptOfType<'confirmation'>; children: ReactNode }) {
  const remaining = useRemainingMillis(prompt);
  const { counting, urgent, fraction } = countdownState(prompt, remaining);
  return (
    <>
      {counting && <span className="halt-ring"><CountdownRing fraction={fraction} text={formatCompact(remaining)} urgent={urgent} /></span>}
      <div className="halt-row">
        <p role="timer">{waitingOutcome(prompt, remaining)}</p>
        {children}
      </div>
    </>
  );
}
