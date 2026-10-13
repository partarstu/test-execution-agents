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
import { Aspect, RUN_STATUS_TONES } from './kit/Aspect';
import { RUN_STATUS_LABELS } from './labels';
import type { RunStatus, TimelineItem } from './types';
import './RunProgress.css';

export interface ItemCounts {
  passed: number;
  failed: number;
  errors: number;
  running: number;
  pending: number;
}

export function itemCounts(items: TimelineItem[]): ItemCounts {
  const counts: ItemCounts = { passed: 0, failed: 0, errors: 0, running: 0, pending: 0 };
  for (const { status } of items) {
    if (status === 'PASSED') {
      counts.passed += 1;
    } else if (status === 'FAILED') {
      counts.failed += 1;
    } else if (status === 'ERROR') {
      counts.errors += 1;
    } else if (status === 'RUNNING') {
      counts.running += 1;
    } else {
      counts.pending += 1;
    }
  }
  return counts;
}

export function progressText(counts: ItemCounts): string {
  const parts = [`${counts.passed} passed`];
  if (counts.failed > 0) {
    parts.push(`${counts.failed} failed`);
  }
  if (counts.errors > 0) {
    parts.push(counts.errors === 1 ? '1 error' : `${counts.errors} errors`);
  }
  if (counts.running > 0) {
    parts.push(`${counts.running} running`);
  }
  parts.push(`${counts.pending} not run yet`);
  return parts.join(', ');
}

export interface Segment {
  tone: string;
  current: boolean;
}

/** The run's items as one segment each and in order, so a failure shows where it happened; the caller states the counts as text. */
export function RunSegments({ items, currentItem }: { items: TimelineItem[]; currentItem: number }) {
  return <Segments groups={[items.map((item, index) => ({ tone: item.status.toLowerCase(), current: index === currentItem }))]} />;
}

/** Segments in order, in groups set apart by a wider gap; decorative, so the caller states the counts as text. */
export function Segments({ groups }: { groups: Segment[][] }) {
  const segments = (group: Segment[]) => group.map(({ tone, current }, index) => (
    <span key={index} className={`run-segment run-segment-${tone}${current ? ' run-segment-current' : ''}`} />
  ));
  if (groups.length === 1) {
    return <span className="run-segments" aria-hidden="true">{segments(groups[0])}</span>;
  }
  return (
    <span className="run-segments run-segments-grouped" aria-hidden="true">
      {groups.map((group, index) => (
        <span key={index} className="run-segment-group" style={{ flexGrow: group.length }}>{segments(group)}</span>
      ))}
    </span>
  );
}

interface ChipProps {
  runStatus: RunStatus;
  items: TimelineItem[];
  onOpen: () => void;
}

/** The run at a glance from the other tabs: its status and how many items have ended; it brings the Run tab forward. */
export function RunChip({ runStatus, items, onOpen }: ChipProps) {
  const counts = itemCounts(items);
  return (
    <button type="button" className="run-chip quiet" onClick={onOpen}>
      <Aspect tone={RUN_STATUS_TONES[runStatus]}>{RUN_STATUS_LABELS[runStatus]}</Aspect>
      {items.length > 0 && (
        <>
          <span className="run-chip-count" aria-hidden="true">{counts.passed + counts.failed + counts.errors}/{items.length}</span>
          <span className="visually-hidden">, {progressText(counts)}</span>
        </>
      )}
    </button>
  );
}
