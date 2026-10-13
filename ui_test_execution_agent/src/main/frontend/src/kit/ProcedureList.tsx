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
import { Plus } from 'lucide-react';
import { useEffect, useEffectEvent, useId, useState } from 'react';
import type { ProcedureHit } from '../prompts/Prompt';
import { Modal } from './Modal';
import { Popover } from './Popover';
import './ProcedureList.css';

interface Props {
  /** Searches with the query and the type filter; null when the search failed, which the prompt frame reports. */
  search: (query: string, showAllTypes: boolean) => Promise<ProcedureHit[] | null>;
  /** The type of the item the procedure is picked for; the other type is hidden until asked for and confirmed when picked. */
  precondition: boolean;
  initialQuery: string;
  searchDelayMillis: number;
  pickLabel: string;
  onPick: (hit: ProcedureHit) => void;
  disabled: boolean;
  /** Offers to create a new procedure from the typed description when nothing found fits. */
  onCreate?: (description: string) => void;
}

const typeName = (precondition: boolean) => (precondition ? 'precondition' : 'test step');

/** A searchable list of procedures with their type, atomicity and prerequisite coverage, searched as the operator types. */
export function ProcedureList({ search, precondition, initialQuery, searchDelayMillis, pickLabel, onPick, disabled, onCreate }: Props) {
  const [query, setQuery] = useState(initialQuery);
  const [showAllTypes, setShowAllTypes] = useState(false);
  const [hits, setHits] = useState<ProcedureHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [crossTypePick, setCrossTypePick] = useState<ProcedureHit | null>(null);
  const searchId = useId();
  const find = useEffectEvent((text: string, allTypes: boolean) => search(text, allTypes));
  const delayFor = useEffectEvent((text: string) => (text === initialQuery ? 0 : searchDelayMillis));

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => {
      setSearching(true);
      void find(query, showAllTypes).then((found) => {
        if (current) {
          setHits(found ?? []);
          setSearching(false);
        }
      });
    }, delayFor(query));
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [query, showAllTypes]);

  const pick = (hit: ProcedureHit) => (hit.precondition === precondition ? onPick(hit) : setCrossTypePick(hit));

  return (
    <div className="procedure-list">
      <div className="procedure-search">
        <label htmlFor={searchId}>Search procedures</label>
        <input id={searchId} type="search" value={query} onChange={(event) => setQuery(event.target.value)} autoComplete="off" autoFocus />
        <Popover label={showAllTypes ? 'All types' : `Only ${typeName(precondition)}s`}>
          <label className="check">
            <input type="checkbox" checked={showAllTypes} onChange={(event) => setShowAllTypes(event.target.checked)} />
            Also show {typeName(!precondition)}s
          </label>
        </Popover>
      </div>
      <p className="list-state" aria-live="polite">
        {searching ? 'Searching…' : query.trim() === '' ? 'Type a description to search.' : `${hits.length} found`}
      </p>
      <ul className="choice-list">
        {hits.map((hit) => (
          <li key={hit.id} className="choice">
            <div className="choice-text">
              <span className="choice-title">{hit.description}</span>
              <span className="tags">
                <span className="tag">{hit.precondition ? 'Precondition' : 'Test step'}</span>
                <span className="tag">{hit.atomic ? 'Atomic' : 'Composite'}</span>
                <PrerequisiteTag hit={hit} />
              </span>
            </div>
            <button type="button" className="small" onClick={() => pick(hit)} disabled={disabled}>
              {pickLabel}
            </button>
          </li>
        ))}
      </ul>
      {onCreate && query.trim() !== '' && (
        <button type="button" disabled={disabled} onClick={() => onCreate(query.trim())}>
          <Plus size={16} />
          Add “{query.trim()}” as a new step
        </button>
      )}
      {crossTypePick && (
        <Modal open title="Use a procedure of the other type?" onCancel={() => setCrossTypePick(null)}>
          <p className="prose">
            “{crossTypePick.description}” is a {typeName(crossTypePick.precondition)}, but a {typeName(precondition)} is being executed.
          </p>
          <div className="answers">
            <button type="button" className="primary" onClick={() => {
              setCrossTypePick(null);
              onPick(crossTypePick);
            }}>
              Use it anyway
            </button>
            <button type="button" data-autofocus onClick={() => setCrossTypePick(null)}>Keep looking</button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function PrerequisiteTag({ hit }: { hit: ProcedureHit }) {
  if (hit.totalPrerequisites === 0) {
    return <span className="tag">No prerequisites</span>;
  }
  const level = hit.satisfiedPrerequisites === hit.totalPrerequisites ? 'met'
    : hit.satisfiedPrerequisites === 0 ? 'unmet' : 'partly';
  return (
    <span className={`tag tag-${level}`}>
      {hit.satisfiedPrerequisites} of {hit.totalPrerequisites} prerequisites met
    </span>
  );
}
