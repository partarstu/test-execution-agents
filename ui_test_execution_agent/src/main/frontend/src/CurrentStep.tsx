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
import { ArrowRight, Check, ChevronRight, CircleCheck, CirclePause, Hand, Redo2, SquareX, X } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { duration } from './format';
import { Aspect, type Tone } from './kit/Aspect';
import { ClampedError } from './kit/ClampedError';
import { itemPosition } from './labels';
import {
  buildTree, currentLeaf, displayStatus, foldedByDefault, guideOf, leafCounts, leavesOf, nextLeaf, type DisplayStatus,
  type ProcedureTree, type TreeNode,
} from './procedureTree';
import type { ReceivedPrompt } from './prompts/Prompt';
import { Segments } from './RunProgress';
import type { RunState, TimelineItem } from './types';
import './CurrentStep.css';

/** Marks the element whose own scrolling brings the current action into view. */
export const SCROLLER = 'data-scroller';

interface Props {
  state: RunState;
  /** The compact form beside the large live view: smaller, everything off the current path folded, the current action kept in view. */
  dense: boolean;
  /** The confirmation around the current action's row while it waits, answered in place. */
  confirmation?: ((row: ReactNode) => ReactNode) | null;
}

const STATUS_TEXT: Record<DisplayStatus, string> = {
  PENDING: 'not run yet',
  RUNNING: 'executing',
  WAITING: 'waiting for your go',
  EXECUTED: 'executed, waiting for your go',
  ATTENTION: 'waiting for your answer',
  COMPLETED: 'done',
  SKIPPED: 'skipped',
  FAILED: 'failed',
  ABANDONED: 'not finished',
};

const SEGMENT_TONES: Record<DisplayStatus, string> = {
  PENDING: 'pending',
  RUNNING: 'running',
  WAITING: 'waiting',
  EXECUTED: 'waiting',
  ATTENTION: 'waiting',
  COMPLETED: 'completed',
  SKIPPED: 'skipped',
  FAILED: 'failed',
  ABANDONED: 'abandoned',
};

/** The item being executed: where it is in the run, the progress of its actions and the tree of its procedures. */
export function CurrentStep({ state, dense, confirmation = null }: Props) {
  const headingId = useId();
  const item = state.currentItem >= 0 ? state.items[state.currentItem] : null;
  const outline = state.outline?.item === state.currentItem ? state.outline : null;
  const tree = outline && buildTree(outline.procedures);
  const aspect = item && aspectOf(item, tree, state.prompt);
  const hostProps = dense ? { [SCROLLER]: '' } : {};
  return (
    <section className={`stage card stage-${aspect?.tone ?? 'neutral'}${dense ? ' stage-dense' : ''}`} aria-labelledby={headingId}
      {...hostProps}>
      <div className="stage-top">
        <header className="stage-head">
          <div className="stage-title">
            <p className="eyebrow">{item ? itemPosition(state.items, state.currentItem) : 'Current step'}</p>
            <h2 id={headingId}>{item?.description ?? 'No step is running'}</h2>
            {!dense && outline && outline.testData.length > 0 && (
              <p className="stage-data">
                <span>Test data</span>
                {outline.testData.map((value, index) => <span key={index} className="tag">{value}</span>)}
              </p>
            )}
          </div>
          {aspect && <Aspect tone={aspect.tone}>{aspect.text}</Aspect>}
        </header>
        {tree && <StepProgress tree={tree} prompt={state.prompt} />}
      </div>
      {tree ? (
        <ProcedureOutline key={`${state.currentItem}:${tree.root.node.id}`} tree={tree} dense={dense} prompt={state.prompt}
          activity={state.statusMessage} confirmation={confirmation} />
      ) : (
        <StepPlaceholder state={state} item={item} />
      )}
    </section>
  );
}

function aspectOf(item: TimelineItem, tree: ProcedureTree | null, prompt: ReceivedPrompt | null): { tone: Tone; text: string } {
  switch (item.status) {
    case 'PASSED':
      return { tone: 'passed', text: item.resolution ? `Passed in ${duration(item.resolution.durationMs)}` : 'Passed' };
    case 'FAILED':
      return { tone: 'failed', text: 'Failed' };
    case 'ERROR':
      return { tone: 'error', text: 'Error' };
    case 'PENDING':
      return { tone: 'neutral', text: 'Not run yet' };
    case 'RUNNING':
      if (prompt !== null) {
        return { tone: 'waiting', text: 'Waiting for you' };
      }
      return { tone: 'running', text: tree ? 'Executing' : 'Finding the procedure' };
  }
}

function StepProgress({ tree, prompt }: { tree: ProcedureTree; prompt: ReceivedPrompt | null }) {
  const current = currentLeaf(tree);
  const statusOf = (leaf: TreeNode) => displayStatus(leaf.node, leaf === current ? prompt : null);
  const groups = tree.root.leaf !== null ? [tree.root] : tree.root.children;
  const counts = leafCounts(tree.leaves);
  const pending = tree.leaves.filter((leaf) => leaf.node.status === 'PENDING').length;
  const parts = [`${counts.done} of ${counts.total} actions done${counts.skipped > 0 ? `, ${counts.skipped} skipped` : ''}`];
  if (current?.leaf != null) {
    parts.push(`action ${current.leaf + 1} ${STATUS_TEXT[statusOf(current)]}`);
  }
  if (pending > 0) {
    parts.push(`${pending} to go`);
  }
  return (
    <div className="stage-progress">
      <Segments groups={groups.map((group) => leavesOf(tree, group).map((leaf) => ({
        tone: SEGMENT_TONES[statusOf(leaf)], current: leaf === current,
      })))} />
      <p className="stage-counts">{parts.join(' · ')}</p>
    </div>
  );
}

function StepPlaceholder({ state, item }: { state: RunState; item: TimelineItem | null }) {
  if (item?.status === 'RUNNING') {
    return (
      <div className="stage-finding">
        <p className="stage-finding-title"><span className="stage-spinner" aria-hidden="true" />Finding the procedure for this step</p>
        {state.statusMessage && <p className="stage-finding-activity">{state.statusMessage}</p>}
      </div>
    );
  }
  if (item?.errorMessage) {
    return <div className="stage-error-message"><ClampedError text={item.errorMessage} /></div>;
  }
  const text = state.runStatus === 'IDLE' ? 'Waiting for a test case from the orchestrator.'
    : state.statusMessage ?? state.resultMessage ?? 'The step’s procedures appear here once the agent has found them.';
  return <p className="stage-empty">{text}</p>;
}

interface OutlineProps {
  tree: ProcedureTree;
  dense: boolean;
  prompt: ReceivedPrompt | null;
  activity: string | null;
  confirmation: ((row: ReactNode) => ReactNode) | null;
}

interface Branches extends OutlineProps {
  current: TreeNode | null;
  next: TreeNode | null;
  idPrefix: string;
  folded: (treeNode: TreeNode) => boolean;
  toggle: (treeNode: TreeNode) => void;
}

/** The procedures as a rail tree: the route to the current action lit, composites folding, the current action opened in place. */
function ProcedureOutline({ tree, dense, prompt, activity, confirmation }: OutlineProps) {
  const [overrides, setOverrides] = useState<ReadonlyMap<number, boolean>>(new Map());
  const list = useRef<HTMLOListElement>(null);
  const idPrefix = useId();
  const current = currentLeaf(tree);
  const currentIndex = current?.index ?? -1;

  useEffect(() => {
    const row = list.current?.querySelector<HTMLElement>('[aria-current="step"]');
    const scroller = row?.closest<HTMLElement>(`[${SCROLLER}]`);
    if (!row || !scroller) {
      return;
    }
    bringIntoView(row, scroller, dense);
    if (!dense) {
      return;
    }
    // The compact action grows when its confirmation arrives, which would push it below the short pane beside the large live view
    const observer = new ResizeObserver(() => bringIntoView(row, scroller, true));
    observer.observe(row);
    return () => observer.disconnect();
  }, [currentIndex, dense]);

  const folded = (treeNode: TreeNode) => overrides.get(treeNode.index) ?? foldedByDefault(treeNode, current, dense);
  const branches: Branches = {
    tree, dense, prompt, activity, confirmation, current, next: nextLeaf(tree), idPrefix, folded,
    toggle: (treeNode) => setOverrides(new Map(overrides).set(treeNode.index, !folded(treeNode))),
  };
  return (
    <ol ref={list} className={dense ? 'tree tree-dense' : 'tree'} aria-label="Procedures of this step">
      <Branch treeNode={tree.root} parent={null} branches={branches} />
    </ol>
  );
}

/**
 * Scrolls a current action that is not fully visible under the sticky step heading into view: in the middle of the visible area
 * when `centred` and it fits there, otherwise with its top just under the heading.
 */
function bringIntoView(row: HTMLElement, scroller: HTMLElement, centred: boolean) {
  if (scroller.scrollHeight <= scroller.clientHeight) {
    return;
  }
  const rowBox = row.getBoundingClientRect();
  const box = scroller.getBoundingClientRect();
  const top = box.top + (scroller.querySelector<HTMLElement>('.stage-top')?.offsetHeight ?? 0);
  if (rowBox.top >= top && rowBox.bottom <= box.bottom) {
    return;
  }
  const room = box.bottom - top;
  const gap = centred && rowBox.height < room ? (room - rowBox.height) / 2 : 8;
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
  scroller.scrollBy({ top: rowBox.top - top - gap, behavior: reducedMotion ? 'auto' : 'smooth' });
}

function Branch({ treeNode, parent, branches }: { treeNode: TreeNode; parent: TreeNode | null; branches: Branches }) {
  const isCurrent = treeNode === branches.current;
  const status = displayStatus(treeNode.node, isCurrent ? branches.prompt : null);
  const classes = ['tnode', treeNode.leaf === null ? 'tnode-group' : 'tnode-action', `s-${status.toLowerCase()}`,
    parent ? `g-${guideOf(treeNode, parent, branches.current, branches.tree)}` : '', isCurrent ? 'tnode-current' : '']
    .filter(Boolean).join(' ');
  if (treeNode.leaf !== null) {
    return (
      <li className={classes} aria-current={isCurrent ? 'step' : undefined}>
        {isCurrent ? <CurrentAction treeNode={treeNode} status={status} branches={branches} />
          : <ActionRow treeNode={treeNode} status={status} next={treeNode === branches.next} />}
      </li>
    );
  }
  const folded = branches.folded(treeNode);
  const listId = `${branches.idPrefix}-${treeNode.index}`;
  return (
    <li className={classes}>
      <GroupRow treeNode={treeNode} status={status} folded={folded} listId={listId} branches={branches} />
      {!folded && (
        <ol className="tchildren" id={listId}>
          {treeNode.children.map((child) => <Branch key={child.index} treeNode={child} parent={treeNode} branches={branches} />)}
        </ol>
      )}
    </li>
  );
}

function Marker({ status }: { status: DisplayStatus }) {
  let mark: ReactNode = null;
  if (status === 'COMPLETED' || status === 'EXECUTED') {
    mark = <Check size={11} strokeWidth={3.5} />;
  } else if (status === 'FAILED') {
    mark = <X size={11} strokeWidth={3.5} />;
  } else if (status === 'SKIPPED') {
    mark = <Redo2 size={10} strokeWidth={3} />;
  }
  return <span className="tmarker" aria-hidden="true">{mark}</span>;
}

function GroupRow({ treeNode, status, folded, listId, branches }: {
  treeNode: TreeNode; status: DisplayStatus; folded: boolean; listId: string; branches: Branches;
}) {
  const leaves = leavesOf(branches.tree, treeNode);
  const done = leaves.filter((leaf) => leaf.node.status === 'COMPLETED' || leaf.node.status === 'SKIPPED').length;
  const { durationMillis } = treeNode.node;
  return (
    <div className="trow">
      <Marker status={status} />
      <button type="button" className="tgroup-toggle" aria-expanded={!folded} aria-controls={folded ? undefined : listId}
        onClick={() => branches.toggle(treeNode)}>
        <ChevronRight size={16} strokeWidth={2.5} aria-hidden="true" />
        <span>{treeNode.node.description}</span>
        <span className="visually-hidden">, {STATUS_TEXT[status]}, {done} of {leaves.length} actions done</span>
      </button>
      <span className="tmeta" aria-hidden="true">
        {status === 'COMPLETED' && durationMillis !== null && <span className="tduration">{duration(durationMillis)}</span>}
        <span className="minibar"><span style={{ inlineSize: `${leaves.length === 0 ? 0 : (done / leaves.length) * 100}%` }} /></span>
        <span className="tcount">{done}/{leaves.length}</span>
      </span>
    </div>
  );
}

function ActionRow({ treeNode, status, next }: { treeNode: TreeNode; status: DisplayStatus; next: boolean }) {
  const { description, optional, durationMillis } = treeNode.node;
  let meta: ReactNode = null;
  if (status === 'COMPLETED' && durationMillis !== null) {
    meta = <span className="tduration">{duration(durationMillis)}</span>;
  } else if (status === 'SKIPPED') {
    meta = 'Skipped – not needed';
  } else if (status === 'FAILED') {
    meta = <span className="tnote-failed">Failed</span>;
  } else if (status === 'ABANDONED') {
    meta = 'Not finished';
  } else if (next) {
    meta = <span className="next-pill"><ArrowRight size={12} strokeWidth={2.5} />Next</span>;
  }
  return (
    <div className="trow">
      <Marker status={status} />
      <span className="tlabel">
        {description}
        {optional && <span className="tag tag-optional">Optional</span>}
        <span className="visually-hidden">, {next ? 'next, ' : ''}{STATUS_TEXT[status]}</span>
      </span>
      <span className="tmeta" aria-hidden="true">{meta}</span>
    </div>
  );
}

function CurrentAction({ treeNode, status, branches }: { treeNode: TreeNode; status: DisplayStatus; branches: Branches }) {
  const { description, optional, expectedResults } = treeNode.node;
  const { dense, activity, confirmation, tree } = branches;
  const expected = expectedResults?.trim() ? expectedResults : null;
  const waiting = status === 'WAITING' || status === 'EXECUTED';
  const row = (
    <>
      <Marker status={status} />
      <div className="tfocus">
        <p className="tfocus-title">
          {description}
          {optional && <span className="tag tag-optional">Optional</span>}
        </p>
        <div className="tfocus-meta">
          <PhaseChip status={status} dense={dense} />
          <span className="tfocus-count">Action {(treeNode.leaf ?? 0) + 1} of {tree.leaves.length}</span>
        </div>
        {status === 'RUNNING' && activity && (
          <p className="tfocus-activity"><span className="run-activity-dot" aria-hidden="true" />{activity}</p>
        )}
        {status === 'WAITING' && expected && !dense && (
          <dl className="tfocus-facts"><dt>Expected</dt><dd>{expected}</dd></dl>
        )}
        {status === 'EXECUTED' && expected && (
          <p className="tfocus-result"><CircleCheck size={16} strokeWidth={2.25} aria-hidden="true" /><span><strong>Verified:</strong> {expected}</span></p>
        )}
        {status === 'ATTENTION' && <p className="tfocus-note">The run waits for your answer.</p>}
      </div>
    </>
  );
  return waiting && confirmation ? confirmation(row) : <div className="trow">{row}</div>;
}

function PhaseChip({ status, dense }: { status: DisplayStatus; dense: boolean }) {
  switch (status) {
    case 'WAITING':
      return <span className="phase-chip phase-waiting"><CirclePause size={14} strokeWidth={2.5} />{dense ? 'Waiting for you' : 'Waiting for your go'}</span>;
    case 'EXECUTED':
      return (
        <span className="phase-chip phase-waiting">
          <CircleCheck size={14} strokeWidth={2.5} />{dense ? 'Executed · waiting' : 'Executed – waiting for your go'}
        </span>
      );
    case 'ATTENTION':
      return <span className="phase-chip phase-waiting"><Hand size={14} strokeWidth={2.5} />Waiting for you</span>;
    case 'FAILED':
      return <span className="phase-chip phase-failed"><SquareX size={14} strokeWidth={2.5} />Failed</span>;
    default:
      return <span className="phase-chip phase-running"><span className="phase-pulse" aria-hidden="true" />Executing</span>;
  }
}
