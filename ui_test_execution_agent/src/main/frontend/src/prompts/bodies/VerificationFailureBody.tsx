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
import { Play } from 'lucide-react';
import { Screenshot } from '../../kit/Screenshot';
import { imageUrl } from '../api';
import type { BodyProps } from '../body';

/** A verification that did not hold, with the agent's reason and the screen it judged. */
export function VerificationFailureBody({ prompt, answer, pending }: BodyProps<'verification-failure'>) {
  const { verification, reason, screenshotId } = prompt.payload;
  return (
    <>
      <dl className="facts">
        <dt>Expected</dt>
        <dd className="fact-strong">{verification}</dd>
        <dt>Why it failed</dt>
        <dd>{reason}</dd>
      </dl>
      {screenshotId && <Screenshot src={imageUrl(prompt.id, screenshotId)} alt="The screen the verification judged" />}
      <div className="answers">
        <button type="button" className="primary" autoFocus disabled={pending} onClick={() => answer({})}><Play size={16} />Continue</button>
      </div>
    </>
  );
}
