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

// Mirrors of the Java records in org.tarik.ta.dto.panel; PanelEventSerializationTest pins their JSON shape.
import type { ProcedureOutcome, StepResolution } from './knowledge/model';
import type { PromptView, ReceivedPrompt } from './prompts/Prompt';

export type ExecutionMode = 'SUPERVISED' | 'UNATTENDED';

export interface SessionInfo {
  executionMode: ExecutionMode;
  liveView: boolean;
  liveControl: boolean;
  searchDelayMillis: number;
}

export type RunStatus = 'IDLE' | 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR' | 'ABORTED';

export type ItemKind = 'PRECONDITION' | 'STEP';

export type ItemStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR';

export interface TimelineItem {
  kind: ItemKind;
  description: string;
  status: ItemStatus;
  errorMessage: string | null;
  resolution: StepResolution | null;
}

export type ProcedureNodeStatus = 'PENDING' | 'RUNNING' | ProcedureOutcome;

export interface ProcedureNode {
  id: string;
  description: string;
  expectedResults: string | null;
  atomic: boolean;
  optional: boolean;
  depth: number;
  status: ProcedureNodeStatus;
  durationMillis: number | null;
}

/** The decomposition of the timeline item at `item`, its procedures in execution order. */
export interface ItemOutline {
  item: number;
  testData: string[];
  procedures: ProcedureNode[];
}

export interface PanelState {
  runStatus: RunStatus;
  testCaseName: string | null;
  items: TimelineItem[];
  currentItem: number;
  statusMessage: string | null;
  resultMessage: string | null;
  logTail: string[];
  prompt: PromptView | null;
  activeProcedures: string[];
  outline: ItemOutline | null;
}

/** The run state the panel shows: the stream's state with the open prompt stamped with the moment it was received. */
export interface RunState extends Omit<PanelState, 'prompt'> {
  // How many lines have slid off the front of the log tail on the client, so each line keeps its key as the tail moves
  logOffset: number;
  prompt: ReceivedPrompt | null;
}

export interface Displaced {
  reason: 'taken-over' | 'other-tab';
  at: string;
  sourceIp: string;
  browser: string;
}
