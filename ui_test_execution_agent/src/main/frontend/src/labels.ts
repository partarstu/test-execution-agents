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
import type { ExecutionMode, ItemStatus, RunStatus, TimelineItem } from './types';

export const RUN_STATUS_LABELS: Record<RunStatus, string> = {
  IDLE: 'Idle',
  RUNNING: 'Running',
  PASSED: 'Passed',
  FAILED: 'Failed',
  ERROR: 'Error',
  ABORTED: 'Aborted',
};

export const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING: 'Not run yet',
  RUNNING: 'Running',
  PASSED: 'Passed',
  FAILED: 'Failed',
  ERROR: 'Error',
};

export const EXECUTION_MODE_LABELS: Record<ExecutionMode, string> = {
  SUPERVISED: 'Supervised',
  UNATTENDED: 'Unattended',
};

// Preconditions and steps are numbered separately, as in the test case: P1, P2, then 1, 2
export function itemNumber(items: TimelineItem[], index: number): string {
  const item = items[index];
  const position = items.slice(0, index + 1).filter((candidate) => candidate.kind === item.kind).length;
  return item.kind === 'PRECONDITION' ? `P${position}` : String(position);
}

/** E.g. "Test step 2 of 5", counting preconditions and steps apart as the test case does. */
export function itemPosition(items: TimelineItem[], index: number): string {
  const { kind } = items[index];
  const position = items.slice(0, index + 1).filter((candidate) => candidate.kind === kind).length;
  const total = items.filter((candidate) => candidate.kind === kind).length;
  return `${kind === 'PRECONDITION' ? 'Precondition' : 'Test step'} ${position} of ${total}`;
}

export function itemName(items: TimelineItem[], index: number): string {
  const number = itemNumber(items, index);
  return items[index].kind === 'PRECONDITION' ? `Precondition ${number.slice(1)}` : `Step ${number}`;
}
