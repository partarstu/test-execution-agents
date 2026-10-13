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
import { countdownState, formatCompact, formatRemaining, useRemainingMillis } from './countdown';
import { CountdownRing } from './CountdownRing';
import type { PromptOfType, ReceivedPrompt } from './Prompt';
import { promptTitle } from './titles';
import './PromptStrip.css';

/**
 * The open prompt seen from another tab: its question or supervised action and the time its countdown leaves, with the way to the
 * run; not a live region, since the prompt is announced when it opens.
 */
export function PromptStrip({ prompt, onAnswer }: { prompt: ReceivedPrompt; onAnswer: () => void }) {
  const remaining = useRemainingMillis(prompt);
  const { counting, paused, urgent, fraction } = countdownState(prompt, remaining);
  const text = prompt.type === 'confirmation' ? confirmationText(prompt, counting && !paused, formatRemaining(remaining)) : {
    label: 'Open question',
    eyebrow: 'The run waits for you',
    title: promptTitle(prompt),
    time: counting ? `${formatRemaining(remaining)} left to answer` : null,
    action: 'Answer',
  };
  return (
    <aside className={urgent ? 'prompt-strip prompt-strip-urgent' : 'prompt-strip'} aria-label={text.label}>
      {counting && <CountdownRing fraction={fraction} text={formatCompact(remaining)} urgent={urgent} />}
      <p className="prompt-strip-text">
        <span className="eyebrow">{text.eyebrow}</span>
        <strong>{text.title}</strong>
        {text.time && <span className="prompt-strip-time">{text.time}</span>}
      </p>
      <button type="button" className="primary small" onClick={onAnswer}>{text.action}</button>
    </aside>
  );
}

// A supervised confirmation goes on by itself when its countdown ends, so it names the action and when the run goes on instead of asking
function confirmationText(prompt: PromptOfType<'confirmation'>, running: boolean, remaining: string) {
  const { beforeExecution, procedureDescription } = prompt.payload;
  return {
    label: 'Supervised action',
    eyebrow: beforeExecution ? 'Up next' : 'Just executed',
    title: procedureDescription,
    time: !running ? 'Waits for your go' : beforeExecution ? `Runs in ${remaining}` : `The run goes on in ${remaining}`,
    action: 'Go to the run',
  };
}
