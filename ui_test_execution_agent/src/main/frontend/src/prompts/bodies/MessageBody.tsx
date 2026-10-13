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
import { Aspect, type Tone } from '../../kit/Aspect';
import { Screenshot } from '../../kit/Screenshot';
import { imageUrl } from '../api';
import type { BodyProps } from '../body';
import type { Severity } from '../Prompt';

const SEVERITY_LABELS: Record<Severity, string> = { INFO: 'For your information', WARNING: 'Warning', ERROR: 'Error' };

const SEVERITY_TONES: Record<Severity, Tone> = { INFO: 'neutral', WARNING: 'warning', ERROR: 'failed' };

/** Something the operator should know before the run goes on, with the screen it happened on when there is one. */
export function MessageBody({ prompt, answer, pending }: BodyProps<'message'>) {
  const { message, screenshotId, severity } = prompt.payload;
  return (
    <>
      <p className="severity-label"><Aspect tone={SEVERITY_TONES[severity]}>{SEVERITY_LABELS[severity]}</Aspect></p>
      <p className="prose prose-pre">{message}</p>
      {screenshotId && <Screenshot src={imageUrl(prompt.id, screenshotId)} alt="The screen when this happened" />}
      <div className="answers">
        <button type="button" className="primary" autoFocus disabled={pending} onClick={() => answer({})}><Play size={16} />Continue</button>
      </div>
    </>
  );
}
