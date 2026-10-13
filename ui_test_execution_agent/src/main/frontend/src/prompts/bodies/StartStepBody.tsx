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
import { OctagonX, Play } from 'lucide-react';
import { useState } from 'react';
import { Modal } from '../../kit/Modal';
import type { BodyProps } from '../body';

const ABORT = -1;

/** Picks the test step the run starts from; starting later skips the preconditions and the earlier steps. */
export function StartStepBody({ prompt, answer, pending }: BodyProps<'start-step'>) {
  const [selected, setSelected] = useState(0);
  const [confirmAbort, setConfirmAbort] = useState(false);
  const steps = prompt.payload.steps;
  return (
    <>
      <p className="prose">Starting from the first step also runs every precondition first.</p>
      <div className="answers start-step-answers">
        <button type="button" className="primary" autoFocus disabled={pending} onClick={() => answer({ stepIndex: selected })}>
          <Play size={16} />
          Start from step {selected + 1}
        </button>
        {selected !== 0 && (
          <button type="button" disabled={pending} onClick={() => answer({ stepIndex: 0 })}>Start from the beginning</button>
        )}
        <button type="button" className="danger" disabled={pending} onClick={() => setConfirmAbort(true)}><OctagonX size={16} />Abort the run</button>
      </div>
      <table className="steps">
        <thead>
          <tr>
            <th scope="col">Step</th>
            <th scope="col">Action</th>
            <th scope="col">Test data</th>
            <th scope="col">Expected result</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step, index) => (
            <tr key={index} className={index === selected ? 'selected' : undefined} onClick={() => setSelected(index)}>
              <td>
                <label className="check">
                  <input type="radio" name="start-step" checked={index === selected} onChange={() => setSelected(index)} />
                  {index + 1}
                </label>
              </td>
              <td>{step.description}</td>
              <td>{step.testData.join(', ')}</td>
              <td>{step.expectedResults ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Modal open={confirmAbort} title="Abort the run?" onCancel={() => setConfirmAbort(false)}>
        <p className="prose">No step is executed, and the orchestrator receives an aborted run.</p>
        <div className="answers">
          <button type="button" className="danger" onClick={() => answer({ stepIndex: ABORT })}>Abort the run</button>
          <button type="button" data-autofocus onClick={() => setConfirmAbort(false)}>Keep it</button>
        </div>
      </Modal>
    </>
  );
}
