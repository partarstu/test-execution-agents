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
import { Hand } from 'lucide-react';
import { AnswerOptions } from '../AnswerOptions';
import type { BodyProps } from '../body';
import { ItemContext } from './ItemContext';

/** Shows the step the run is about to execute, or has just executed, within the composites it was decomposed from. */
export function ConfirmationBody({ prompt, answer, pending }: BodyProps<'confirmation'>) {
  const { procedureDescription, ancestors, item, beforeExecution } = prompt.payload;
  return (
    <>
      <ItemContext item={item} />
      <dl className="facts">
        <dt>{beforeExecution ? 'Step to execute' : 'Executed step'}</dt>
        <dd className="fact-strong">{procedureDescription}</dd>
        {ancestors.length > 0 && (
          <>
            <dt>Part of</dt>
            <dd>
              <ol className="chain">
                {ancestors.map((ancestor, index) => <li key={index}>{ancestor}</li>)}
              </ol>
            </dd>
          </>
        )}
      </dl>
      <AnswerOptions
        disabled={pending}
        autoFocus={false}
        numbered={false}
        options={[
          {
            id: 'halt', label: 'Halt', hint: 'Stop here and decide how the run goes on', Icon: Hand, tone: 'danger',
            onChoose: () => answer({ decision: 'HALTED' }),
          },
        ]}
      />
    </>
  );
}
