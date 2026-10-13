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
import { useEffect, useEffectEvent, useId, useState } from 'react';
import { Modal } from '../../kit/Modal';
import { Screenshot } from '../../kit/Screenshot';
import { imageUrl } from '../api';
import type { ElementCard } from '../Prompt';
import type { EditorEnv } from './ProcedureForm';

interface Props {
  procedureDescription: string;
  testData: string[];
  env: EditorEnv;
  onChoose: (element: ElementCard) => void;
  onBack: () => void;
}

/**
 * Picks the element a procedure acts on from the known ones, or creates it from a description and locates it on the screen; a
 * failed creation stays here with its reason, so the description can be refined.
 */
export function ElementLookup({ procedureDescription, testData, env, onChoose, onBack }: Props) {
  const [query, setQuery] = useState<string | null>(null);
  const [elements, setElements] = useState<ElementCard[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<ElementCard | null>(null);
  const queryId = useId();
  const describe = useEffectEvent(() => env.call('select-element', { description: procedureDescription }));
  const search = useEffectEvent((text: string) => env.call('search-elements', { query: text }));
  const delay = useEffectEvent(() => env.searchDelayMillis);

  useEffect(() => {
    let current = true;
    void describe().then((response) => {
      if (current) {
        setQuery(response?.query ?? procedureDescription);
      }
    });
    return () => {
      current = false;
    };
  }, [procedureDescription]);

  useEffect(() => {
    if (query === null) {
      return;
    }
    let current = true;
    const timer = setTimeout(() => {
      void search(query).then((response) => {
        if (current) {
          setElements(response?.elements ?? []);
        }
      });
    }, delay());
    return () => {
      current = false;
      clearTimeout(timer);
    };
  }, [query]);

  const create = async () => {
    setFailure(null);
    const resolution = await env.call('create-and-locate', { description: query ?? '', testData });
    if (resolution?.element) {
      onChoose(resolution.element);
    } else if (resolution) {
      setFailure(resolution.failureReason);
    }
  };

  const remove = async (element: ElementCard) => {
    setDeleting(null);
    if (await env.call('delete-element', { elementId: element.id })) {
      setElements(elements.filter((candidate) => candidate.id !== element.id));
    }
  };

  return (
    <section className="subview" aria-label="Choose the target element">
      <button type="button" className="back" onClick={onBack}><ArrowLeft size={16} />Back to the procedure</button>
      <h3>Choose the target element</h3>
      {failure && (
        <p className="field-error" role="alert">
          Creating the element failed: {failure} Refine the description and create it again, or choose a known element.
        </p>
      )}
      <label htmlFor={queryId}>Element description</label>
      <input id={queryId} type="search" value={query ?? ''} disabled={query === null} autoFocus
        onChange={(event) => setQuery(event.target.value)} />
      {query === null && <p className="list-state">Reading the element out of the procedure description…</p>}
      <ul className="choice-list">
        {elements.map((element) => (
          <li key={element.id} className="choice">
            {element.screenshotId && <Screenshot src={imageUrl(env.promptId, element.screenshotId)} alt={element.name} size="thumb" />}
            <div className="choice-text">
              <span className="choice-title">{element.name}</span>
              <span className="muted">{element.description}</span>
            </div>
            <div className="row-actions">
              <button type="button" disabled={env.pending} onClick={() => onChoose(element)}>Use</button>
              <button type="button" className="quiet" disabled={env.pending} onClick={() => setDeleting(element)}>Delete</button>
            </div>
          </li>
        ))}
      </ul>
      <button type="button" className="primary" disabled={env.pending || !query?.trim()} onClick={() => void create()}>
        Create it from the description and locate it
      </button>
      <Modal open={deleting !== null} title="Delete the element?" onCancel={() => setDeleting(null)}>
        <p className="prose">“{deleting?.name}” is removed from the knowledge for good, together with its screenshot.</p>
        <div className="answers">
          <button type="button" className="danger" onClick={() => deleting && void remove(deleting)}>Delete it</button>
          <button type="button" data-autofocus onClick={() => setDeleting(null)}>Keep it</button>
        </div>
      </Modal>
    </section>
  );
}
