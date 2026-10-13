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
import { describe, expect, it } from 'vitest';
import { applyEvent, INITIAL_STATE, LOG_TAIL_SIZE, type StateEvent } from './events';
import type { StepResolution } from './knowledge/model';
import type { PromptView } from './prompts/Prompt';
import type { ItemOutline, PanelState, ProcedureNode, RunState, TimelineItem } from './types';

const pending = (description: string): TimelineItem => ({ kind: 'STEP', description, status: 'PENDING', errorMessage: null, resolution: null });

const snapshot: PanelState = {
  runStatus: 'RUNNING',
  testCaseName: 'Checkout',
  items: [pending('Open the cart'), pending('Click Save')],
  currentItem: -1,
  statusMessage: null,
  resultMessage: null,
  logTail: ['first line'],
  prompt: null,
  activeProcedures: [],
  outline: null,
};

type WithoutStamp<E> = E extends unknown ? Omit<E, 'receivedAt'> : never;
type Delta = WithoutStamp<StateEvent>;

function node(id: string, atomic: boolean, depth: number): ProcedureNode {
  return { id, description: id, expectedResults: null, atomic, optional: false, depth, status: 'PENDING', durationMillis: null };
}

function replay(events: Delta[], from: RunState = INITIAL_STATE, receivedAt = 0): RunState {
  return events.map((event) => ({ ...event, receivedAt }) as StateEvent).reduce(applyEvent, from);
}

describe('applyEvent', () => {
  it('starts from the snapshot and applies the deltas after it', () => {
    const state = replay([
      { name: 'snapshot', data: { state: snapshot } },
      { name: 'item-started', data: { index: 0, item: { ...pending('Open the cart'), status: 'RUNNING' } } },
      { name: 'item-finished', data: { index: 0, item: { ...pending('Open the cart'), status: 'FAILED', errorMessage: 'gone' } } },
      { name: 'log-line', data: { line: 'second line' } },
      { name: 'status', data: { message: 'Waiting for operator: confirmation' } },
    ]);

    expect(state.currentItem).toBe(0);
    expect(state.items[0]).toEqual({ kind: 'STEP', description: 'Open the cart', status: 'FAILED', errorMessage: 'gone', resolution: null });
    expect(state.items[1].status).toBe('PENDING');
    expect(state.logTail).toEqual(['first line', 'second line']);
    expect(state.statusMessage).toBe('Waiting for operator: confirmation');
  });

  it('replaces the whole state with a later snapshot', () => {
    const later: PanelState = { ...INITIAL_STATE, prompt: null, runStatus: 'PASSED', testCaseName: 'Login', logTail: ['done'] };

    const state = replay([
      { name: 'snapshot', data: { state: snapshot } },
      { name: 'log-line', data: { line: 'lost on reconnect' } },
      { name: 'snapshot', data: { state: later } },
    ]);

    expect(state).toEqual(later);
  });

  it('starts a new run with its items and clears what the previous run left', () => {
    const state = replay([
      { name: 'snapshot', data: { state: { ...snapshot, statusMessage: 'busy', logTail: ['old'] } } },
      { name: 'task-started', data: { testCaseName: 'Login', items: [pending('Type the password')] } },
    ]);

    expect(state).toEqual({ ...INITIAL_STATE, runStatus: 'RUNNING', testCaseName: 'Login', items: [pending('Type the password')] });
  });

  it('appends an item that was not planned', () => {
    const state = replay([
      { name: 'snapshot', data: { state: snapshot } },
      { name: 'item-started', data: { index: 2, item: { ...pending('Close the dialog'), status: 'RUNNING' } } },
    ]);

    expect(state.items.map((item) => item.description)).toEqual(['Open the cart', 'Click Save', 'Close the dialog']);
  });

  it('ends the run with its status and message and clears the status line', () => {
    const state = replay([
      { name: 'snapshot', data: { state: { ...snapshot, statusMessage: 'busy' } } },
      { name: 'task-finished', data: { status: 'ABORTED', message: null } },
    ]);

    expect(state.runStatus).toBe('ABORTED');
    expect(state.statusMessage).toBeNull();
  });

  it('keeps only the newest lines of the log', () => {
    const lines: Delta[] = Array.from({ length: LOG_TAIL_SIZE + 10 }, (_, i) => ({ name: 'log-line', data: { line: `line ${i}` } }));

    const state = replay(lines);

    expect(state.logTail).toHaveLength(LOG_TAIL_SIZE);
    expect(state.logTail[0]).toBe('line 10');
    expect(state.logOffset).toBe(10);
  });

  it('opens a prompt stamped with its arrival, and closes only that prompt', () => {
    const prompt: PromptView = {
      id: 'p1', type: 'message', payload: { title: 'Note', message: 'Read', severity: 'INFO', screenshotId: null },
      remainingMillis: 5_000, waitedMillis: 0, countdown: false, countdownMillis: 0, held: false, paused: false, hasDefault: true,
    };

    const opened = replay([{ name: 'prompt-opened', data: { prompt } }], INITIAL_STATE, 42);
    expect(opened.prompt).toEqual({ ...prompt, receivedAt: 42 });
    expect(replay([{ name: 'prompt-closed', data: { id: 'other' } }], opened).prompt).not.toBeNull();
    expect(replay([{ name: 'prompt-closed', data: { id: 'p1' } }], opened).prompt).toBeNull();
  });

  it('restores the open prompt from a snapshot, stamped with the snapshot arrival', () => {
    const prompt: PromptView = {
      id: 'p2', type: 'start-step', payload: { steps: [] }, remainingMillis: 1_000, waitedMillis: 0, countdown: false, countdownMillis: 0, held: false, paused: false, hasDefault: false,
    };

    const state = replay([{ name: 'snapshot', data: { state: { ...snapshot, prompt } } }], INITIAL_STATE, 7);

    expect(state.prompt).toEqual({ ...prompt, receivedAt: 7 });
  });

  it('clears the status line when the status message is cleared', () => {
    const state = replay([
      { name: 'status', data: { message: 'Locating the element' } },
      { name: 'status', data: { message: null } },
    ]);

    expect(state.statusMessage).toBeNull();
  });

  it('nests the started procedures, root first, and pops each one when it finishes', () => {
    const state = replay([
      { name: 'procedure-started', data: { procedureId: 'root', node: -1 } },
      { name: 'procedure-started', data: { procedureId: 'composite', node: -1 } },
      { name: 'procedure-started', data: { procedureId: 'atomic', node: -1 } },
      { name: 'procedure-finished', data: { procedureId: 'atomic', outcome: 'COMPLETED', node: -1, durationMillis: null } },
    ], INITIAL_STATE, 9);

    expect(state.activeProcedures).toEqual(['root', 'composite']);
  });

  it('keeps the outline of the item, marks its nodes as they start and end, and drops it when the next item starts', () => {
    const outline: ItemOutline = { item: 0, testData: ['admin'], procedures: [node('root', false, 0), node('atomic', true, 1), node('atomic', true, 1)] };
    const outlined = replay([
      { name: 'item-outlined', data: { outline } },
      { name: 'procedure-started', data: { procedureId: 'root', node: 0 } },
      { name: 'procedure-started', data: { procedureId: 'atomic', node: 1 } },
      { name: 'procedure-finished', data: { procedureId: 'atomic', outcome: 'COMPLETED', node: 1, durationMillis: 1500 } },
      { name: 'procedure-started', data: { procedureId: 'atomic', node: 2 } },
    ]);

    expect(outlined.outline?.testData).toEqual(['admin']);
    expect(outlined.outline?.procedures.map(({ status, durationMillis }) => [status, durationMillis])).toEqual([
      ['RUNNING', null], ['COMPLETED', 1500], ['RUNNING', null],
    ]);
    expect(outline.procedures[1].status).toBe('PENDING');
    expect(replay([{ name: 'item-started', data: { index: 1, item: pending('Click Save') } }], outlined).outline).toBeNull();
  });

  it('leaves the outline alone for a procedure without a node', () => {
    const outline: ItemOutline = { item: 0, testData: [], procedures: [node('root', true, 0)] };

    const state = replay([
      { name: 'item-outlined', data: { outline } },
      { name: 'procedure-started', data: { procedureId: 'other', node: -1 } },
    ]);

    expect(state.outline).toBe(outline);
  });

  it('clears the active procedures when the run ends', () => {
    const state = replay([
      { name: 'procedure-started', data: { procedureId: 'root', node: -1 } },
      { name: 'procedure-started', data: { procedureId: 'atomic', node: -1 } },
      { name: 'task-finished', data: { status: 'FAILED', message: 'gone' } },
    ]);

    expect(state.activeProcedures).toEqual([]);
  });

  it('restores the active procedures from a snapshot', () => {
    const state = replay([
      { name: 'procedure-started', data: { procedureId: 'stale', node: -1 } },
      { name: 'snapshot', data: { state: { ...snapshot, activeProcedures: ['root', 'atomic'] } } },
    ]);

    expect(state.activeProcedures).toEqual(['root', 'atomic']);
  });

  it('attaches the resolution of an item to it', () => {
    const resolution: StepResolution = {
      id: 'r1', sequence: 0, itemType: 'TEST_STEP', itemDescription: 'Open the cart', source: 'KNOWLEDGE', confidence: 'HIGH',
      candidates: [], wasDemoted: false, demotedDueToPrerequisites: false, chosenProcedureId: 'p1', outcome: 'SUCCEEDED', durationMs: 5,
    };

    const state = replay([
      { name: 'snapshot', data: { state: snapshot } },
      { name: 'step-resolved', data: { index: 0, item: { ...pending('Open the cart'), status: 'PASSED', resolution } } },
    ]);

    expect(state.items[0].resolution).toEqual(resolution);
  });
});
