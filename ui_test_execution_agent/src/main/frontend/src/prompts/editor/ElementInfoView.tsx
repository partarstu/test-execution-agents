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
import { useId, useState } from 'react';
import type { ElementCard } from '../Prompt';
import type { EditorEnv } from './ProcedureForm';

interface Props {
  element: ElementCard;
  env: EditorEnv;
  onSaved: (element: ElementCard) => void;
  onBack: () => void;
}

/** Edits how the agent describes and finds an element; saving stores it in the knowledge right away. */
export function ElementInfoView({ element, env, onSaved, onBack }: Props) {
  const [info, setInfo] = useState(element);
  const ids = { name: useId(), description: useId(), location: useId(), page: useId() };

  const save = async () => {
    const saved = await env.call('update-element-info', {
      elementId: element.id, name: info.name, description: info.description, locationDetails: info.locationDetails,
      pageSummary: info.pageSummary, dataDependent: info.dataDependent,
    });
    if (saved) {
      onSaved(saved);
    }
  };

  return (
    <section className="subview" aria-label="Edit the element details">
      <button type="button" className="back" onClick={onBack}><ArrowLeft size={16} />Back without saving</button>
      <h3>Element details</h3>
      <form className="form" onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}>
        <label htmlFor={ids.name}>Name</label>
        <input id={ids.name} type="text" value={info.name} autoFocus onChange={(event) => setInfo({ ...info, name: event.target.value })} />
        <label htmlFor={ids.description}>Description</label>
        <textarea id={ids.description} rows={3} value={info.description}
          onChange={(event) => setInfo({ ...info, description: event.target.value })} />
        <label htmlFor={ids.location}>Where it is on the page</label>
        <textarea id={ids.location} rows={2} value={info.locationDetails}
          onChange={(event) => setInfo({ ...info, locationDetails: event.target.value })} />
        <label htmlFor={ids.page}>The page it is on</label>
        <input id={ids.page} type="text" value={info.pageSummary} onChange={(event) => setInfo({ ...info, pageSummary: event.target.value })} />
        <label className="check">
          <input type="checkbox" checked={info.dataDependent} onChange={(event) => setInfo({ ...info, dataDependent: event.target.checked })} />
          Its look depends on the test data
        </label>
        <div className="answers">
          <button type="submit" className="primary" disabled={env.pending}>Save the details</button>
        </div>
      </form>
    </section>
  );
}
