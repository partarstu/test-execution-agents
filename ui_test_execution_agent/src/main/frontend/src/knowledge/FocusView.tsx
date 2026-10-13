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
import { ArrowLeft, ArrowRight, ChevronRight, Eraser, Info, LoaderCircle, Network, Route } from 'lucide-react';
import { useEffect, useEffectEvent, useId, useRef, useState } from 'react';
import { fetchNeighbourhood, fetchTestCaseLens, type Loaded } from './api';
import { DetailsPanel, type Selection } from './DetailsPanel';
import { focusOf, type Focus, type LinkGroup } from './focusModel';
import { KindLegendEntries } from './KindLegend';
import type { FocusRequest } from './KnowledgeTab';
import { TEST_CASE_ID_PREFIX, type GraphNode, type GraphSlice } from './model';
import { NodeSearch } from './NodeSearch';
import { FLAG_LABELS, flagSeverity, KIND_LABELS } from './theme';
import './FocusView.css';

const GROUP_PREVIEW = 5;

type Target = { kind: 'procedure'; id: string } | { kind: 'test-case'; name: string };

interface Lens {
  testCaseName: string;
  path: { id: string; label: string }[];
}

/** The ids the cards mark: the procedures running now and the ones the lens's latest run chose. */
interface Marks {
  active: ReadonlySet<string>;
  lens: ReadonlySet<string>;
}

type OpenNode = (node: GraphNode) => void;

interface Props {
  focus: FocusRequest | null;
  testCaseName: string | null;
  activeProcedures: string[];
  onSelectProcedure: (procedureId: string) => void;
  onShowFailures: (procedureId: string) => void;
  onOpenElement: (elementId: string) => void;
  onUnauthorized: () => void;
}

/** The procedure map as a focus: one procedure with its parents, steps, targets and the procedures linked to its prerequisites and effects. */
export function FocusView({ focus: request, testCaseName, activeProcedures, onSelectProcedure, onShowFailures, onOpenElement, onUnauthorized }: Props) {
  const [focus, setFocus] = useState<Focus | null>(null);
  const [current, setCurrent] = useState<Target | null>(null);
  const [history, setHistory] = useState<Target[]>([]);
  const [lens, setLens] = useState<Lens | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [busy, setBusy] = useState(false);
  // Only the newest request may change the view, so a slow answer never replaces a later focus
  const latestRequest = useRef(0);
  const handledFocus = useRef(-1);

  const show = (target: Target, slice: GraphSlice, nextHistory: Target[]) => {
    const next = focusOf(slice, target.kind === 'procedure' ? target.id : TEST_CASE_ID_PREFIX + target.name);
    if (next === null) {
      setNotice('That node is no longer in the knowledge graph.');
      return;
    }
    setFocus(next);
    setCurrent(target);
    setHistory(nextHistory);
    setNotice(slice.capped ? 'Its neighbourhood is too large to load in full, so some of its links may be missing.' : null);
    if (target.kind === 'procedure') {
      setSelection({ kind: 'procedure', id: target.id });
      onSelectProcedure(target.id);
    } else {
      setSelection({ kind: 'test-case', id: target.name });
    }
  };

  const open = (target: Target, nextHistory: Target[]) => {
    const requestId = ++latestRequest.current;
    setBusy(true);
    const settle = <T,>(result: Loaded<T>, onData: (data: T) => void) => {
      if (requestId !== latestRequest.current) {
        return;
      }
      setBusy(false);
      switch (result.kind) {
        case 'ok':
          onData(result.data);
          break;
        case 'not-found':
          setNotice('That node is no longer in the knowledge graph.');
          break;
        case 'unavailable':
          setNotice('Knowledge graph unavailable. Try again in a moment.');
          break;
        case 'unauthorized':
          onUnauthorized();
          break;
      }
    };
    if (target.kind === 'procedure') {
      void fetchNeighbourhood(target.id).then((result) => settle(result, (slice) => show(target, slice, nextHistory)));
    } else {
      void fetchTestCaseLens(target.name).then((result) =>
        settle(result, (data) => {
          const labels = new Map(data.slice.nodes.map((node) => [node.id, node.label]));
          setLens({ testCaseName: target.name, path: data.latestRunPath.map((id) => ({ id, label: labels.get(id) ?? id })) });
          show(target, data.slice, nextHistory);
        }),
      );
    }
  };

  const focusOn = (target: Target) => open(target, current === null ? history : [...history, current]);

  const back = () => open(history[history.length - 1], history.slice(0, -1));

  const openNode = (node: GraphNode) => {
    switch (node.kind) {
      case 'ELEMENT':
        setSelection({ kind: 'element', id: node.id });
        break;
      case 'TEST_CASE':
        focusOn({ kind: 'test-case', name: node.id.slice(TEST_CASE_ID_PREFIX.length) });
        break;
      default:
        focusOn({ kind: 'procedure', id: node.id });
    }
  };

  const openHit = (hit: GraphNode) => (hit.kind === 'ELEMENT' ? onOpenElement(hit.id) : openNode(hit));

  const clear = () => {
    latestRequest.current++;
    setBusy(false);
    setFocus(null);
    setCurrent(null);
    setHistory([]);
    setLens(null);
    setNotice(null);
    setSelection(null);
  };

  const onFocus = useEffectEvent((next: FocusRequest) => {
    // Showing the view again re-runs its effects, which must not load the same focus twice
    if (next.seq !== handledFocus.current) {
      handledFocus.current = next.seq;
      focusOn({ kind: 'procedure', id: next.id });
    }
  });

  useEffect(() => {
    if (request) {
      onFocus(request);
    }
  }, [request]);

  const marks: Marks = { active: new Set(activeProcedures), lens: new Set(lens?.path.map(({ id }) => id)) };
  return (
    <div className="map">
      <div className="map-side card">
        <NodeSearch label="Find a procedure, element or test case" disabled={busy} onOpen={openHit} onUnauthorized={onUnauthorized} />
        {lens && <LensPath lens={lens} onSelect={(id) => focusOn({ kind: 'procedure', id })} />}
      </div>
      <div className="map-canvas-column card">
        <div className="toolbar">
          {history.length > 0 && (
            <button type="button" className="small" disabled={busy} onClick={back}><ArrowLeft size={16} />Back</button>
          )}
          {busy && <span className="loading-note" role="status"><LoaderCircle size={16} className="spin" />Loading…</span>}
          {focus && (
            <span className="toolbar-end">
              <button type="button" className="small" onClick={clear}><Eraser size={16} />Clear the map</button>
            </span>
          )}
        </div>
        {notice && <p className="note" role="status"><Info size={18} />{notice}</p>}
        {focus === null ? (
          <div className="empty-state">
            <Network size={32} />
            <strong>Start the map</strong>
            <p>Search for a procedure, element or test case, or open a finding from the Health tab.</p>
            {testCaseName && (
              <button type="button" className="primary" disabled={busy} onClick={() => focusOn({ kind: 'test-case', name: testCaseName })}>
                <Route size={16} />
                Show the procedures of “{testCaseName}”
              </button>
            )}
          </div>
        ) : (
          <>
            <FocusStage focus={focus} marks={marks} onOpen={openNode} />
            <MapLegend />
          </>
        )}
      </div>
      <DetailsPanel selection={selection} onShowFailures={onShowFailures} onShowLens={(name) => focusOn({ kind: 'test-case', name })}
        onOpenElement={onOpenElement} onSelectProcedure={(id) => focusOn({ kind: 'procedure', id })} onUnauthorized={onUnauthorized} />
    </div>
  );
}

function FocusStage({ focus, marks, onOpen }: { focus: Focus; marks: Marks; onOpen: OpenNode }) {
  const procedure = focus.node.kind !== 'TEST_CASE';
  return (
    <div className="focus-stage">
      {focus.parents.length > 0 && (
        <nav className="focus-parents" aria-label="Part of">
          <span className="focus-caption">Part of</span>
          {focus.parents.map((parent) => <NodeCard key={parent.id} node={parent} marks={marks} onOpen={onOpen} />)}
        </nav>
      )}
      <div className={procedure ? 'focus-flow' : 'focus-flow focus-flow-single'}>
        {procedure && (
          <LinkColumn title="Enabled by" caption="Their effects satisfy its prerequisites" groups={focus.enabledBy}
            empty="No procedure is linked to its prerequisites." marks={marks} onOpen={onOpen} />
        )}
        {procedure && <ArrowRight className="focus-arrow" size={20} aria-hidden="true" />}
        <FocusCentre focus={focus} marks={marks} onOpen={onOpen} />
        {procedure && <ArrowRight className="focus-arrow" size={20} aria-hidden="true" />}
        {procedure && (
          <LinkColumn title="Enables" caption="Its effects satisfy their prerequisites" groups={focus.enables}
            empty="No procedure is linked to its effects." marks={marks} onOpen={onOpen} />
        )}
      </div>
    </div>
  );
}

function FocusCentre({ focus, marks, onOpen }: { focus: Focus; marks: Marks; onOpen: OpenNode }) {
  const headingId = useId();
  const { node } = focus;
  return (
    <section className="focus-centre" aria-labelledby={headingId} {...cardMarks(node, marks)}>
      <p className="focus-caption">
        <span className={`kind-swatch kind-${node.kind.toLowerCase()}`} aria-hidden="true" />
        {KIND_LABELS[node.kind]}
      </p>
      <h3 id={headingId}>{node.label}</h3>
      <NodeFacts node={node} marks={marks} />
      <NodeList title={node.kind === 'TEST_CASE' ? 'Procedures it uses' : 'Steps, in order'} nodes={focus.steps} ordered marks={marks} onOpen={onOpen} />
      <NodeList title="Targets" nodes={focus.targets} marks={marks} onOpen={onOpen} />
      <NodeList title="Used by test cases" nodes={focus.testCases} marks={marks} onOpen={onOpen} />
    </section>
  );
}

function LinkColumn({ title, caption, groups, empty, marks, onOpen }: {
  title: string;
  caption: string;
  groups: LinkGroup[];
  empty: string;
  marks: Marks;
  onOpen: OpenNode;
}) {
  const headingId = useId();
  return (
    <section className="focus-column" aria-labelledby={headingId}>
      <h3 id={headingId}>{title}</h3>
      <p className="focus-caption">{caption}</p>
      {groups.length === 0 ? <p className="empty">{empty}</p> : groups.map((group) => (
        <LinkGroupList key={group.phrase ?? ''} group={group} marks={marks} onOpen={onOpen} />
      ))}
    </section>
  );
}

function LinkGroupList({ group, marks, onOpen }: { group: LinkGroup; marks: Marks; onOpen: OpenNode }) {
  const [expanded, setExpanded] = useState(false);
  const headingId = useId();
  const shown = expanded ? group.nodes : group.nodes.slice(0, GROUP_PREVIEW);
  return (
    <div className="focus-group" role="group" aria-labelledby={headingId}>
      <h4 id={headingId}>{group.phrase === null ? 'Without a recorded phrase' : `“${group.phrase}”`}</h4>
      <ul>
        {shown.map((node) => <li key={node.id}><NodeCard node={node} marks={marks} onOpen={onOpen} /></li>)}
      </ul>
      {group.nodes.length > GROUP_PREVIEW && (
        <button type="button" className="link" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
          {expanded ? 'Show fewer' : `Show ${group.nodes.length - GROUP_PREVIEW} more`}
        </button>
      )}
    </div>
  );
}

function NodeList({ title, nodes, ordered = false, marks, onOpen }: { title: string; nodes: GraphNode[]; ordered?: boolean; marks: Marks; onOpen: OpenNode }) {
  if (nodes.length === 0) {
    return null;
  }
  const items = nodes.map((node) => <li key={node.id}><NodeCard node={node} marks={marks} onOpen={onOpen} /></li>);
  return (
    <>
      <h4>{title}</h4>
      {ordered ? <ol className="focus-list">{items}</ol> : <ul className="focus-list">{items}</ul>}
    </>
  );
}

function NodeCard({ node, marks, onOpen }: { node: GraphNode; marks: Marks; onOpen: OpenNode }) {
  return (
    <button type="button" className="focus-card" onClick={() => onOpen(node)} {...cardMarks(node, marks)}>
      <span className={`kind-swatch kind-${node.kind.toLowerCase()}`} aria-hidden="true" />
      <span className="focus-card-text">
        <span className="focus-card-label">{node.label}</span>
        <NodeFacts node={node} marks={marks} />
      </span>
    </button>
  );
}

/** The kind, use and health of a node, spelled out so the ring and glow never carry meaning alone. */
function NodeFacts({ node, marks }: { node: GraphNode; marks: Marks }) {
  const facts = [
    marks.active.has(node.id) ? 'Running now' : null,
    marks.lens.has(node.id) ? 'Chosen by the latest run' : null,
    node.testCases > 0 ? `${node.testCases} test case${node.testCases === 1 ? '' : 's'}` : null,
    ...node.flags.map((flag) => FLAG_LABELS[flag]),
  ].filter((fact) => fact !== null);
  return (
    <span className="focus-facts">
      <span className="visually-hidden">{KIND_LABELS[node.kind]}. </span>
      {facts.join(' · ')}
    </span>
  );
}

function cardMarks(node: GraphNode, marks: Marks) {
  return {
    'data-severity': flagSeverity(node.flags) ?? undefined,
    'data-active': marks.active.has(node.id) || undefined,
    'data-lens': marks.lens.has(node.id) || undefined,
  };
}

function LensPath({ lens, onSelect }: { lens: Lens; onSelect: (id: string) => void }) {
  const headingId = useId();
  return (
    <section className="lens" aria-labelledby={headingId}>
      <h3 id={headingId}>{lens.testCaseName}</h3>
      {lens.path.length === 0 ? (
        <p className="empty">No recorded run yet.</p>
      ) : (
        <>
          <p>Procedures its latest run chose, in order:</p>
          <ol>
            {lens.path.map(({ id, label }, index) => (
              <li key={`${id}-${index}`}>
                <button type="button" className="link" onClick={() => onSelect(id)}>{label}</button>
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}

function MapLegend() {
  return (
    <details className="map-legend">
      <summary><ChevronRight size={16} className="disclosure" aria-hidden="true" />Legend</summary>
      <ul className="legend" aria-label="Map legend">
        <KindLegendEntries />
        <li><span className="ring-swatch ring-warning" aria-hidden="true" />Amber edge: missing target, effects or stability</li>
        <li><span className="ring-swatch ring-failed" aria-hidden="true" />Red edge: a recent anomaly</li>
        <li><span className="ring-swatch ring-active" aria-hidden="true" />Glow: running now</li>
        <li><span className="ring-swatch ring-lens" aria-hidden="true" />Dotted outline: chosen by the test case's latest run</li>
      </ul>
    </details>
  );
}
