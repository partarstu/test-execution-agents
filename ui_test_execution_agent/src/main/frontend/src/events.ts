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
import type { PromptView } from './prompts/Prompt';
import type { ProcedureOutcome } from './knowledge/model';
import type { ItemOutline, PanelState, ProcedureNode, RunState, RunStatus, TimelineItem } from './types';

/**
 * The run-state events of the stream, each stamped with the moment it arrived; `displaced` ends the stream and is handled by the
 * hook, not the reducer.
 */
export type StateEvent = (
  | { name: 'snapshot'; data: { state: PanelState } }
  | { name: 'task-started'; data: { testCaseName: string; items: TimelineItem[] } }
  | { name: 'item-started'; data: { index: number; item: TimelineItem } }
  | { name: 'item-finished'; data: { index: number; item: TimelineItem } }
  | { name: 'log-line'; data: { line: string } }
  | { name: 'status'; data: { message: string | null } }
  | { name: 'prompt-opened'; data: { prompt: PromptView } }
  | { name: 'prompt-closed'; data: { id: string } }
  | { name: 'task-finished'; data: { status: RunStatus; message: string | null } }
  | { name: 'item-outlined'; data: { outline: ItemOutline } }
  | { name: 'procedure-started'; data: { procedureId: string; node: number } }
  | { name: 'procedure-finished'; data: { procedureId: string; outcome: ProcedureOutcome; node: number; durationMillis: number | null } }
  | { name: 'step-resolved'; data: { index: number; item: TimelineItem } }
) & { receivedAt: number };

export const STATE_EVENT_NAMES: StateEvent['name'][] = [
  'snapshot',
  'task-started',
  'item-started',
  'item-finished',
  'log-line',
  'status',
  'prompt-opened',
  'prompt-closed',
  'task-finished',
  'item-outlined',
  'procedure-started',
  'procedure-finished',
  'step-resolved',
];

// The server keeps the same bound, so a reconnecting tab and a long-open one show the same tail
export const LOG_TAIL_SIZE = 500;

export const INITIAL_STATE: RunState = {
  runStatus: 'IDLE',
  testCaseName: null,
  items: [],
  currentItem: -1,
  statusMessage: null,
  resultMessage: null,
  logTail: [],
  prompt: null,
  activeProcedures: [],
  outline: null,
  logOffset: 0,
};

export function applyEvent(state: RunState, event: StateEvent): RunState {
  switch (event.name) {
    case 'snapshot': {
      const { prompt, ...rest } = event.data.state;
      return { ...rest, prompt: prompt && { ...prompt, receivedAt: event.receivedAt }, logOffset: 0 };
    }
    case 'task-started':
      return {
        ...INITIAL_STATE,
        runStatus: 'RUNNING',
        testCaseName: event.data.testCaseName,
        items: event.data.items,
      };
    case 'item-started':
      return { ...state, items: withItem(state.items, event.data.index, event.data.item), currentItem: event.data.index, outline: null };
    case 'item-finished':
      return { ...state, items: withItem(state.items, event.data.index, event.data.item) };
    case 'log-line': {
      const logTail = [...state.logTail, event.data.line];
      const dropped = Math.max(0, logTail.length - LOG_TAIL_SIZE);
      return { ...state, logTail: logTail.slice(dropped), logOffset: state.logOffset + dropped };
    }
    case 'status':
      return { ...state, statusMessage: event.data.message };
    case 'prompt-opened':
      return { ...state, prompt: { ...event.data.prompt, receivedAt: event.receivedAt } };
    case 'prompt-closed':
      return state.prompt?.id === event.data.id ? { ...state, prompt: null } : state;
    case 'task-finished':
      return { ...state, runStatus: event.data.status, resultMessage: event.data.message, statusMessage: null, activeProcedures: [] };
    case 'item-outlined':
      return { ...state, outline: event.data.outline };
    case 'procedure-started':
      return {
        ...state,
        activeProcedures: [...state.activeProcedures, event.data.procedureId],
        outline: withNode(state.outline, event.data.node, { status: 'RUNNING', durationMillis: null }),
      };
    case 'procedure-finished':
      return {
        ...state,
        activeProcedures: withoutLast(state.activeProcedures, event.data.procedureId),
        outline: withNode(state.outline, event.data.node, { status: event.data.outcome, durationMillis: event.data.durationMillis }),
      };
    case 'step-resolved':
      return { ...state, items: withItem(state.items, event.data.index, event.data.item) };
  }
}

function withItem(items: TimelineItem[], index: number, item: TimelineItem): TimelineItem[] {
  const next = [...items];
  next[index] = item;
  return next;
}

function withNode(outline: ItemOutline | null, index: number, change: Pick<ProcedureNode, 'status' | 'durationMillis'>): ItemOutline | null {
  if (outline === null || !outline.procedures[index]) {
    return outline;
  }
  const procedures = [...outline.procedures];
  procedures[index] = { ...procedures[index], ...change };
  return { ...outline, procedures };
}

// The stack ends with the innermost procedure; a procedure can run twice in one chain, so the last start is the one that ends
function withoutLast(stack: string[], procedureId: string): string[] {
  const index = stack.lastIndexOf(procedureId);
  return index < 0 ? stack : [...stack.slice(0, index), ...stack.slice(index + 1)];
}
