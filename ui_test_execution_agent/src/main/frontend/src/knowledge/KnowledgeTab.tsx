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
import { LoaderCircle, Waypoints } from 'lucide-react';
import { Activity, lazy, Suspense, useId, useState } from 'react';
import { ErrorBoundary } from '../kit/ErrorBoundary';
import { Meter } from '../kit/Meter';
import { TabList, tabPanelProps } from '../kit/Tabs';
import type { RunState } from '../types';
import { useLoaded } from '../useLoaded';
import { fetchSummary } from './api';
import { KNOWLEDGE_VIEWS, type KnowledgeView } from './views';
import './knowledge.css';

const FocusView = lazy(() => import('./FocusView').then((module) => ({ default: module.FocusView })));
const ElementsView = lazy(() => import('./ElementsView').then((module) => ({ default: module.ElementsView })));
const BrainView = lazy(() => import('./BrainView').then((module) => ({ default: module.BrainView })));
const TrendsView = lazy(() => import('./TrendsView').then((module) => ({ default: module.TrendsView })));
const FailuresView = lazy(() => import('./FailuresView').then((module) => ({ default: module.FailuresView })));

export type { KnowledgeView } from './views';

/** A node another view asks the map or the gallery to open; `seq` tells two requests for the same node apart. */
export interface FocusRequest {
  kind: 'procedure' | 'element';
  id: string;
  seq: number;
}

/** A view another tab or the command palette asks to show; `seq` tells two requests for the same view apart. */
export interface ViewRequest {
  view: KnowledgeView;
  seq: number;
}

const TAB_PREFIX = 'knowledge';

interface Props {
  run: RunState;
  requestedFocus: FocusRequest | null;
  requestedView: ViewRequest | null;
  onUnauthorized: () => void;
}

/** What the knowledge graph holds: a summary, then five read-only views that each start from a question. */
export function KnowledgeTab({ run, requestedFocus, requestedView, onUnauthorized }: Props) {
  const [view, setView] = useState<KnowledgeView>('map');
  const [opened, setOpened] = useState<ReadonlySet<KnowledgeView>>(new Set(['map']));
  const [focus, setFocus] = useState<FocusRequest | null>(null);
  const [adoptedSeq, setAdoptedSeq] = useState(-1);
  const [adoptedViewSeq, setAdoptedViewSeq] = useState(-1);
  const [selectedProcedure, setSelectedProcedure] = useState<string | null>(null);
  const headingId = useId();

  const show = (next: KnowledgeView) => {
    setView(next);
    if (!opened.has(next)) {
      setOpened(new Set([...opened, next]));
    }
  };

  const focusOn = (request: Omit<FocusRequest, 'seq'>) => {
    const next = { ...request, seq: (focus?.seq ?? 0) + 1 };
    setFocus(next);
    show(request.kind === 'procedure' ? 'map' : 'elements');
  };

  // A request from the Health tab is adopted once, while rendering, like the prompt that brings the Run tab forward
  if (requestedFocus && requestedFocus.seq !== adoptedSeq) {
    setAdoptedSeq(requestedFocus.seq);
    focusOn(requestedFocus);
  }
  if (requestedView && requestedView.seq !== adoptedViewSeq) {
    setAdoptedViewSeq(requestedView.seq);
    show(requestedView.view);
  }

  const showFailures = (procedureId: string) => {
    setSelectedProcedure(procedureId);
    show('failures');
  };

  return (
    <section className="knowledge" aria-labelledby={headingId}>
      <div className="knowledge-head card">
        <h2 id={headingId}><Waypoints size={20} />Knowledge graph</h2>
        {/* Focusable, so a keyboard scrolls the tiles where they scroll sideways */}
        <div className="readouts-scroller" role="region" aria-label="What the graph holds" tabIndex={0}>
          <Summary onUnauthorized={onUnauthorized} />
        </div>
      </div>
      <TabList label="Knowledge views" idPrefix={TAB_PREFIX} selected={view} onSelect={show} className="knowledge-tabs"
        tabs={KNOWLEDGE_VIEWS.map(({ id, label, Icon }) => ({ id, label: <><Icon size={16} />{label}</> }))} />
      <Suspense fallback={<p className="loading-note"><LoaderCircle size={18} className="spin" />Loading the view…</p>}>
        {KNOWLEDGE_VIEWS.filter(({ id }) => opened.has(id)).map(({ id }) => (
          <Activity key={id} mode={view === id ? 'visible' : 'hidden'}>
            <div {...tabPanelProps(TAB_PREFIX, id)} className="knowledge-view">
              <ErrorBoundary>
                {id === 'map' && (
                  <FocusView focus={focus?.kind === 'procedure' ? focus : null} testCaseName={run.testCaseName} activeProcedures={run.activeProcedures}
                    onSelectProcedure={setSelectedProcedure} onShowFailures={showFailures}
                    onOpenElement={(elementId) => focusOn({ kind: 'element', id: elementId })} onUnauthorized={onUnauthorized} />
                )}
                {id === 'elements' && (
                  <ElementsView focus={focus?.kind === 'element' ? focus : null}
                    onOpenProcedure={(procedureId) => focusOn({ kind: 'procedure', id: procedureId })} onUnauthorized={onUnauthorized} />
                )}
                {id === 'brain' && (
                  <BrainView activeProcedures={run.activeProcedures}
                    onOpenProcedure={(procedureId) => focusOn({ kind: 'procedure', id: procedureId })} onUnauthorized={onUnauthorized} />
                )}
                {id === 'trends' && <TrendsView onUnauthorized={onUnauthorized} />}
                {id === 'failures' && (
                  <FailuresView procedureId={selectedProcedure} onSelectProcedure={setSelectedProcedure} onShowMap={() => show('map')}
                    onUnauthorized={onUnauthorized} />
                )}
              </ErrorBoundary>
            </div>
          </Activity>
        ))}
      </Suspense>
    </section>
  );
}

const SKELETON_TILES = ['procedures', 'elements', 'test-cases', 'links'];

function Summary({ onUnauthorized }: { onUnauthorized: () => void }) {
  const { loaded } = useLoaded('summary', fetchSummary, onUnauthorized);
  if (loaded === null) {
    return (
      <div className="readouts" aria-busy="true">
        <p className="visually-hidden">Counting what the graph holds…</p>
        {SKELETON_TILES.map((tile) => <span key={tile} className="skeleton readout-skeleton" />)}
      </div>
    );
  }
  if (loaded.kind !== 'ok') {
    return <p className="alert" role="alert">Knowledge graph unavailable</p>;
  }
  const data = loaded.data;
  const count = (label: string) => data.nodesByLabel.find((entry) => entry.name === label)?.count ?? 0;
  return (
    <dl className="readouts">
      <Readout label="Procedures" value={count('Procedure')} />
      <Readout label="UI elements" value={count('UiElement')} />
      <Readout label="Test cases" value={count('TestCase')} />
      <Coverage label="Atomic steps with a target" part={data.atomicProceduresWithTarget} whole={data.atomicProcedures} />
      <Coverage label="Atomic steps with effects" part={data.atomicProceduresWithEffects} whole={data.atomicProcedures} />
      <div className="readout readout-quiet">
        <dt>Schema</dt>
        <dd>v{data.schemaVersion}</dd>
      </div>
    </dl>
  );
}

function Readout({ label, value }: { label: string; value: number }) {
  return (
    <div className="readout">
      <dt>{label}</dt>
      <dd>{value.toLocaleString()}</dd>
    </div>
  );
}

/** A ratio the operator can act on, as the share with a thin bar and the two counts behind it. */
function Coverage({ label, part, whole }: { label: string; part: number; whole: number }) {
  const share = whole > 0 ? Math.round((part / whole) * 100) : 0;
  const text = `${part} of ${whole}, ${share}%`;
  return (
    <div className="readout readout-coverage">
      <dt>{label}</dt>
      <dd>
        <span className="readout-share">{share}%</span>
        <Meter label={label} value={part} max={whole} valueText={text} />
        <span className="readout-detail">{part} of {whole}</span>
      </dd>
    </div>
  );
}
