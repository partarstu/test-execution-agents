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
import { ArrowLeft } from 'lucide-react';
import type { ParentSummary } from '../Prompt';

interface Props {
  parents: ParentSummary[];
  pending: boolean;
  onChoose: (parent: ParentSummary) => void;
  onBack: () => void;
}

/** Chooses which of several procedures that contain the edited one to edit instead. */
export function ParentChooser({ parents, pending, onChoose, onBack }: Props) {
  return (
    <section className="subview" aria-label="Choose the parent to edit">
      <button type="button" className="back" onClick={onBack}><ArrowLeft size={16} />Back to the procedure</button>
      <h3>Which parent do you want to edit?</h3>
      <ul className="choice-list">
        {parents.map((parent, index) => (
          <li key={parent.id} className="choice">
            <span className="choice-title">{parent.description}</span>
            <button type="button" autoFocus={index === 0} disabled={pending} onClick={() => onChoose(parent)}>Edit this one</button>
          </li>
        ))}
      </ul>
    </section>
  );
}
