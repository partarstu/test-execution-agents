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
import { Save } from 'lucide-react';
import { useId, useState } from 'react';
import type { BodyProps } from '../body';
import type { ErrorCategory } from '../Prompt';

const CATEGORY_LABELS: Record<ErrorCategory, string> = {
  TERMINATION_BY_USER: 'Stopped by the operator',
  TRANSIENT_TOOL_ERROR: 'Passing tool error',
  NON_RETRYABLE_ERROR: 'Error that a retry will not fix',
  TIMEOUT: 'Took too long',
  UNKNOWN: 'Unknown',
};

/** A short note on why a step failed and how to avoid it, which the agent consults the next time; typing holds the countdown. */
export function FailureContextBody({ prompt, answer, pending, hold }: BodyProps<'failure-context'>) {
  const [category, setCategory] = useState(prompt.payload.category);
  const [symptom, setSymptom] = useState(prompt.payload.symptom);
  const [resolution, setResolution] = useState('');
  const [held, setHeld] = useState(prompt.held);
  const ids = { category: useId(), symptom: useId(), resolution: useId() };

  const holdOnFirstInput = () => {
    if (!held) {
      setHeld(true);
      hold();
    }
  };

  return (
    <form
      className="form"
      onSubmit={(event) => {
        event.preventDefault();
        answer({ choice: 'SUBMIT', category, symptom, resolution });
      }}
      onInput={holdOnFirstInput}
    >
      <p className="prose">The agent reads this note the next time this step fails.</p>
      <label htmlFor={ids.category}>What kind of failure</label>
      <select id={ids.category} value={category} onChange={(event) => setCategory(event.target.value as ErrorCategory)}>
        {prompt.payload.categories.map((value) => <option key={value} value={value}>{CATEGORY_LABELS[value]}</option>)}
      </select>
      <label htmlFor={ids.symptom}>What happened</label>
      <input id={ids.symptom} type="text" value={symptom} onChange={(event) => setSymptom(event.target.value)} />
      <label htmlFor={ids.resolution}>How to avoid it</label>
      <textarea id={ids.resolution} rows={4} value={resolution} onChange={(event) => setResolution(event.target.value)} data-autofocus />
      <div className="answers">
        <button type="submit" className="primary" disabled={pending}><Save size={16} />Save the note</button>
        <button type="button" disabled={pending} onClick={() => answer({ choice: 'DISMISS' })}>Skip</button>
      </div>
    </form>
  );
}
