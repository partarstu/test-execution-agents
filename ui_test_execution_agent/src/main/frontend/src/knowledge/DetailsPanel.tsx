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
import { History, Image, ListTree, LoaderCircle, MousePointerClick } from 'lucide-react';
import { useId } from 'react';
import { duration, localDateTime } from '../format';
import { useLoaded } from '../useLoaded';
import { fetchProcedure } from './api';
import type { ProcedureRef } from './model';
import './DetailsPanel.css';
import { OUTCOME_LABELS, ResolutionExplanation } from './ResolutionExplanation';

export type Selection = { kind: 'procedure' | 'element' | 'test-case'; id: string };

interface Props {
  selection: Selection | null;
  onShowFailures: (procedureId: string) => void;
  onShowLens: (testCaseName: string) => void;
  onOpenElement: (elementId: string) => void;
  onSelectProcedure: (procedureId: string) => void;
  onUnauthorized: () => void;
}

/** What the map's selected node is: a procedure in full, or a way into the element gallery or a test case's lens. */
export function DetailsPanel({ selection, onShowFailures, onShowLens, onOpenElement, onSelectProcedure, onUnauthorized }: Props) {
  if (selection === null) {
    return (
      <section className="details card" aria-label="Details">
        <div className="empty-state">
          <MousePointerClick size={28} />
          <p>Select a node on the map to see what the agent knows about it.</p>
        </div>
      </section>
    );
  }
  switch (selection.kind) {
    case 'element':
      return (
        <section className="details card" aria-label="Details">
          <p>A UI element. Its screenshots and location history are in the element gallery.</p>
          <button type="button" onClick={() => onOpenElement(selection.id)}><Image size={16} />Open in Elements</button>
        </section>
      );
    case 'test-case':
      return (
        <section className="details card" aria-label="Details">
          <h3>{selection.id}</h3>
          <button type="button" onClick={() => onShowLens(selection.id)}><ListTree size={16} />Show the procedures it uses</button>
        </section>
      );
    case 'procedure':
      return (
        <ProcedurePanel key={selection.id} procedureId={selection.id} onShowFailures={onShowFailures}
          onShowLens={onShowLens} onOpenElement={onOpenElement} onSelectProcedure={onSelectProcedure} onUnauthorized={onUnauthorized} />
      );
  }
}

type ProcedurePanelProps = Omit<Props, 'selection'> & { procedureId: string };

function ProcedurePanel({ procedureId, onShowFailures, onShowLens, onOpenElement, onSelectProcedure, onUnauthorized }: ProcedurePanelProps) {
  const { loaded } = useLoaded(procedureId, (signal) => fetchProcedure(procedureId, signal), onUnauthorized);
  const headingId = useId();

  if (loaded === null) {
    return (
      <section className="details card" aria-label="Details" aria-busy="true">
        <p className="loading-note"><LoaderCircle size={18} className="spin" />Loading the procedure…</p>
      </section>
    );
  }
  if (loaded.kind !== 'ok') {
    return (
      <section className="details card" aria-label="Details">
        <p className="alert" role="alert">
          {loaded.kind === 'not-found' ? 'This procedure is no longer in the knowledge graph.' : 'Knowledge graph unavailable.'}
        </p>
      </section>
    );
  }
  const details = loaded.data;
  return (
    <section className="details card" aria-labelledby={headingId}>
      <h3 id={headingId}>{details.description}</h3>
      <p className="details-kind">
        {details.atomic ? 'Atomic procedure' : 'Composite procedure'}
        {details.precondition && ', precondition'}
        {details.optional && ', optional'}
      </p>
      <div className="details-actions">
        <button type="button" className="small" onClick={() => onShowFailures(details.id)}><History size={16} />Failure history</button>
      </div>
      <dl className="details-facts">
        <dt>Successful runs</dt>
        <dd>
          {details.successCount}
          {details.timing && `, last on ${localDateTime(details.timing.lastTimingUpdate)}`}
        </dd>
        {details.timing && (
          <>
            <dt>Average duration</dt>
            <dd>{duration(details.timing.avgExecutionMs)}, verified after {duration(details.timing.avgVerificationDelayMs)}</dd>
          </>
        )}
        <dt>Updated</dt>
        <dd>{localDateTime(details.updatedAt)}</dd>
        {details.target && (
          <>
            <dt>Target element</dt>
            <dd>
              <button type="button" className="link" onClick={() => onOpenElement(details.target!.id)}>{details.target.name}</button>
            </dd>
          </>
        )}
      </dl>
      <PhraseList title="Prerequisites" phrases={details.prerequisites} />
      <PhraseList title="Effects" phrases={details.effects} />
      <PhraseList title="Negated effects" phrases={details.negatedEffects} />
      <PhraseList title="Test data" phrases={details.testData} />
      {details.expectedResults && (
        <section>
          <h4>Expected results</h4>
          <p>{details.expectedResults}</p>
        </section>
      )}
      <RefList title="Parents" refs={details.parents} onSelect={onSelectProcedure} />
      <RefList title="Children, in order" refs={details.children} onSelect={onSelectProcedure} />
      {details.testCases.length > 0 && (
        <section>
          <h4>Used by test cases</h4>
          <ul>
            {details.testCases.map((name) => (
              <li key={name}><button type="button" className="link" onClick={() => onShowLens(name)}>{name}</button></li>
            ))}
          </ul>
        </section>
      )}
      {details.recentAnomalies.length > 0 && (
        <section>
          <h4>Recent anomalies</h4>
          <ul className="details-anomalies">
            {details.recentAnomalies.map((anomaly, index) => (
              <li key={index}>
                <time dateTime={anomaly.occurredAt}>{localDateTime(anomaly.occurredAt)}</time> {anomaly.details}
              </li>
            ))}
          </ul>
        </section>
      )}
      <section>
        <h4>Chosen for</h4>
        {details.chosenFor.length === 0 ? (
          <p className="empty">No recorded run chose this procedure yet.</p>
        ) : (
          <ul className="chosen-for">
            {details.chosenFor.map((view) => (
              <li key={view.resolution.id}>
                <details>
                  <summary>
                    {view.testCaseName}, <time dateTime={view.runStartedAt}>{localDateTime(view.runStartedAt)}</time>:{' '}
                    {OUTCOME_LABELS[view.resolution.outcome]}
                  </summary>
                  <p className="chosen-item">{view.resolution.itemDescription}</p>
                  <ResolutionExplanation resolution={view.resolution} />
                </details>
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  );
}

function PhraseList({ title, phrases }: { title: string; phrases: string[] }) {
  if (phrases.length === 0) {
    return null;
  }
  return (
    <section>
      <h4>{title}</h4>
      <ul>
        {phrases.map((phrase, index) => <li key={index}>{phrase}</li>)}
      </ul>
    </section>
  );
}

function RefList({ title, refs, onSelect }: { title: string; refs: ProcedureRef[]; onSelect: (id: string) => void }) {
  if (refs.length === 0) {
    return null;
  }
  return (
    <section>
      <h4>{title}</h4>
      <ul>
        {refs.map((ref) => (
          <li key={ref.id}><button type="button" className="link" onClick={() => onSelect(ref.id)}>{ref.description}</button></li>
        ))}
      </ul>
    </section>
  );
}
