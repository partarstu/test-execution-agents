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
import {
  ArrowDown, ArrowUp, Camera, CircleAlert, Crosshair, Link2Off, ListPlus, Pencil, Plus, Replace, Search, Sparkles, X,
} from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { IconButton } from '../../kit/IconButton';
import { Modal } from '../../kit/Modal';
import { ProcedureList } from '../../kit/ProcedureList';
import { Screenshot } from '../../kit/Screenshot';
import { TextListField } from '../../kit/TextListField';
import { imageUrl } from '../api';
import type { ChildRow, EditorFields, EditorNode, ProcedureHit } from '../Prompt';
import type { ActionCall } from '../useAnswer';
import type { FormState } from './forms';
import type { EditorView } from './navigation';

export interface EditorEnv {
  promptId: string;
  call: ActionCall;
  pending: boolean;
  searchDelayMillis: number;
}

interface Props {
  path: number[];
  form: FormState;
  env: EditorEnv;
  onEdit: (node: EditorNode) => void;
  onOpenChild: (index: number, row: ChildRow) => void;
  onNavigate: (view: EditorView) => void;
  onSuggest: () => void;
  footer: ReactNode;
}

type Lookup = { kind: 'add' } | { kind: 'replace'; index: number } | null;

/** One procedure's fields, its child steps or target element, and its prerequisites and effects. */
export function ProcedureForm({ path, form, env, onEdit, onOpenChild, onNavigate, onSuggest, footer }: Props) {
  const { node, similar } = form;
  const { fields } = node;
  const [lookup, setLookup] = useState<Lookup>(null);
  const [confirmAtomic, setConfirmAtomic] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);
  const [locateFailure, setLocateFailure] = useState<string | null>(null);
  const ids = { description: useId(), info: useId(), expected: useId() };
  const addStep = useRef<HTMLButtonElement>(null);
  const searchedForStep = useRef(false);

  // Closing the step search removes the control that held the focus, so the focus returns to Add a step
  useEffect(() => {
    if (lookup === null && searchedForStep.current) {
      addStep.current?.focus();
    }
    searchedForStep.current = lookup !== null;
  }, [lookup]);

  const setFields = (changes: Partial<EditorFields>) => onEdit({ ...node, fields: { ...fields, ...changes } });
  const setChildren = (children: ChildRow[]) => onEdit({ ...node, children });

  const makeAtomic = (atomic: boolean) => {
    if (atomic && node.children.length > 0) {
      setConfirmAtomic(true);
    } else {
      setFields({ atomic });
    }
  };

  const locate = async () => {
    setLocateFailure(null);
    const resolution = await env.call('locate-element', { description: fields.description, testData: fields.testData });
    if (resolution?.element) {
      onEdit({ ...node, targetElement: resolution.element });
    } else if (resolution) {
      setLocateFailure(resolution.failureReason);
    }
  };

  const linkChild = (hit: ProcedureHit) => {
    const row: ChildRow = {
      id: hit.id, description: hit.description, atomic: hit.atomic, precondition: hit.precondition, effects: [], negatedEffects: [],
      screenshotId: null,
    };
    if (lookup?.kind === 'replace') {
      setChildren(node.children.map((existing, index) => (index === lookup.index ? row : existing)));
    } else {
      setChildren([...node.children, row]);
    }
    setLookup(null);
  };

  const addNewChild = (description: string) => {
    const row: ChildRow = {
      id: null, description, atomic: true, precondition: node.precondition, effects: [], negatedEffects: [], screenshotId: null,
    };
    setLookup(null);
    setChildren([...node.children, row]);
    onOpenChild(node.children.length, row);
  };

  const move = (index: number, offset: number) => {
    const children = [...node.children];
    [children[index], children[index + offset]] = [children[index + offset], children[index]];
    setChildren(children);
  };

  return (
    <div className="procedure-form">
      <div className="form-main">
        <label htmlFor={ids.description}>Description</label>
        <textarea id={ids.description} rows={2} value={fields.description} onChange={(event) => setFields({ description: event.target.value })} />
        {node.id === null && (
          <button type="button" disabled={env.pending} onClick={onSuggest}><Sparkles size={16} />Get AI suggestions</button>
        )}
        <label htmlFor={ids.info}>Additional info for the agent</label>
        <textarea id={ids.info} rows={2} value={fields.additionalInfo ?? ''}
          onChange={(event) => setFields({ additionalInfo: event.target.value })} />
        <div className="toggles">
          <label className="check">
            <input type="checkbox" checked={fields.atomic} onChange={(event) => makeAtomic(event.target.checked)} />
            Atomic: a single UI action
          </label>
          <label className="check">
            <input type="checkbox" checked={fields.optional} onChange={(event) => setFields({ optional: event.target.checked })} />
            Optional: skipped silently when its prerequisites are not met
          </label>
        </div>

        {fields.atomic ? (
          <section className="element-section" aria-label="Target element">
            <h3>Target element</h3>
            {node.targetElement ? (
              <div className="element-card">
                {node.targetElement.screenshotId && (
                  <Screenshot src={imageUrl(env.promptId, node.targetElement.screenshotId)} alt={node.targetElement.name} size="card" />
                )}
                <div>
                  <p className="choice-title">{node.targetElement.name}</p>
                  <p className="muted">{node.targetElement.description}</p>
                </div>
              </div>
            ) : (
              <p className="empty">No element linked. The agent then decides at run time what to act on.</p>
            )}
            {locateFailure && <p className="field-error" role="alert"><CircleAlert size={18} />Locating failed: {locateFailure}</p>}
            <div className="answers">
              <button type="button" className="small" disabled={env.pending} onClick={() => void locate()}><Crosshair size={16} />Locate on screen</button>
              <button type="button" className="small" disabled={env.pending} onClick={() => onNavigate({ kind: 'elementLookup', path })}>
                <Search size={16} />
                Choose a known element
              </button>
              {node.targetElement && (
                <>
                  <button type="button" className="small" disabled={env.pending} onClick={() => onNavigate({ kind: 'elementInfo', path })}>
                    <Pencil size={16} />
                    Edit details
                  </button>
                  <button type="button" className="small" disabled={env.pending} onClick={() => onNavigate({ kind: 'capture', path })}>
                    <Camera size={16} />
                    Replace screenshot
                  </button>
                  <button type="button" className="small quiet" onClick={() => setConfirmUnlink(true)}><Link2Off size={16} />Unlink</button>
                </>
              )}
            </div>
          </section>
        ) : (
          <section className="children-section" aria-label="Child steps">
            <h3>Child steps</h3>
            {node.children.length === 0 && <p className="empty">No child steps yet. A composite procedure needs at least one.</p>}
            <ol className="child-rows">
              {node.children.map((row, index) => (
                <li key={`${row.id ?? 'new'}-${index}`} className="child-row">
                  <div className="choice-text">
                    <span className="choice-title">{row.description}</span>
                    <span className="tags">
                      {row.id === null
                        ? <span className="tag tag-unmet">Not saved yet</span>
                        : <span className="tag">{row.atomic ? 'Atomic' : 'Composite'}</span>}
                      {row.id === null && similar[index] && <span className="tag tag-partly">Similar procedures exist</span>}
                    </span>
                  </div>
                  {row.screenshotId && <Screenshot src={imageUrl(env.promptId, row.screenshotId)} alt={row.description} size="thumb" />}
                  <div className="row-actions">
                    <button type="button" className="small" disabled={env.pending} onClick={() => onOpenChild(index, row)}>
                      <Pencil size={16} />
                      {row.id ? 'Edit' : 'Define'}
                    </button>
                    <button type="button" className="small" disabled={env.pending} onClick={() => setLookup({ kind: 'replace', index })}>
                      <Replace size={16} />
                      Replace
                    </button>
                    <IconButton icon={ArrowUp} label={`Move step ${index + 1} up`} small disabled={index === 0} onClick={() => move(index, -1)} />
                    <IconButton icon={ArrowDown} label={`Move step ${index + 1} down`} small disabled={index === node.children.length - 1}
                      onClick={() => move(index, 1)} />
                    <IconButton icon={X} label={`Remove step ${index + 1}`} small onClick={() => setChildren(node.children.filter((_, at) => at !== index))} />
                  </div>
                </li>
              ))}
            </ol>
            {lookup ? (
              <div className="subview">
                <button type="button" className="back" onClick={() => setLookup(null)}><X size={16} />Close the search</button>
                <ProcedureList
                  search={async (query, showAllTypes) => (await env.call('search-procedures', {
                    query, showAllTypes, procedureId: node.id, precondition: node.precondition,
                    childIds: node.children.flatMap((row) => (row.id ? [row.id] : [])),
                  }))?.hits ?? null}
                  precondition={node.precondition}
                  initialQuery={lookup.kind === 'replace' ? node.children[lookup.index].description : ''}
                  searchDelayMillis={env.searchDelayMillis}
                  pickLabel={lookup.kind === 'replace' ? 'Use instead' : 'Add this step'}
                  onPick={linkChild}
                  onCreate={lookup.kind === 'add' ? addNewChild : undefined}
                  disabled={env.pending}
                />
              </div>
            ) : (
              <button ref={addStep} type="button" disabled={env.pending} onClick={() => setLookup({ kind: 'add' })}><Plus size={16} />Add a step</button>
            )}
          </section>
        )}
      </div>

      <div className="form-side">
        <TextListField
          label="Prerequisites"
          hint="States that must hold before it runs."
          items={fields.prerequisites}
          onChange={(prerequisites) => setFields({ prerequisites })}
          extra={<button type="button" className="small quiet" onClick={() => onNavigate({ kind: 'effectPicker', path })}><ListPlus size={16} />Pick from earlier effects</button>}
        />
        <TextListField
          label="Effects"
          hint="States that hold after it ran."
          items={fields.effects}
          onChange={(effects) => setFields({ effects })}
          extra={!node.precondition && fields.effects.length > 0 && (
            <button type="button" className="small quiet" onClick={() => setFields({ expectedResults: withEffects(fields.expectedResults, fields.effects) })}>
              <ListPlus size={16} />
              Add them to the expected results
            </button>
          )}
        />
        <TextListField
          label="Negated effects"
          hint="States that stop holding after it ran."
          items={fields.negatedEffects}
          onChange={(negatedEffects) => setFields({ negatedEffects })}
        />
        {!node.precondition && (
          <TextListField label="Test data" items={fields.testData} onChange={(testData) => setFields({ testData })} />
        )}
        <label htmlFor={ids.expected}>{node.precondition ? 'Relevant data' : 'Expected results'}</label>
        <textarea id={ids.expected} rows={3} value={fields.expectedResults}
          onChange={(event) => setFields({ expectedResults: event.target.value })} />
      </div>

      <div className="form-footer">{footer}</div>

      <Modal open={confirmAtomic} title="Remove the child steps?" onCancel={() => setConfirmAtomic(false)}>
        <p className="prose">An atomic procedure has no child steps, so its {node.children.length} child steps are removed from it.</p>
        <div className="answers">
          <button type="button" className="danger" onClick={() => {
            setConfirmAtomic(false);
            onEdit({ ...node, children: [], fields: { ...fields, atomic: true } });
          }}>Make it atomic</button>
          <button type="button" data-autofocus onClick={() => setConfirmAtomic(false)}>Keep the steps</button>
        </div>
      </Modal>
      <Modal open={confirmUnlink} title="Unlink the target element?" onCancel={() => setConfirmUnlink(false)}>
        <p className="prose">The element stays in the knowledge; only this procedure no longer targets it.</p>
        <div className="answers">
          <button type="button" className="danger" onClick={() => {
            setConfirmUnlink(false);
            onEdit({ ...node, targetElement: null });
          }}>Unlink</button>
          <button type="button" data-autofocus onClick={() => setConfirmUnlink(false)}>Keep it</button>
        </div>
      </Modal>
    </div>
  );
}

function withEffects(expectedResults: string, effects: string[]): string {
  const missing = effects.map((effect) => effect.trim()).filter((effect) => effect !== '' && !expectedResults.includes(effect));
  return [expectedResults.trim(), ...missing].filter((line) => line !== '').join('\n');
}
