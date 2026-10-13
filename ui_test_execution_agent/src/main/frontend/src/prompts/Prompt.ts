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

// Mirrors of the Java records in org.tarik.ta.dto.panel.prompt; PromptPayloadSerializationTest pins their JSON shape.

export type Severity = 'INFO' | 'WARNING' | 'ERROR';

export type SelectionAction = 'CREATE' | 'RETRY' | 'BROWSE' | 'CANCEL' | 'RERUN_TEST_STEP' | 'RERUN_ATOMIC_PROCEDURE' | 'PROCEED' | 'CONTINUE';

export type ErrorCategory = 'TERMINATION_BY_USER' | 'TRANSIENT_TOOL_ERROR' | 'NON_RETRYABLE_ERROR' | 'TIMEOUT' | 'UNKNOWN';

export interface ItemView {
  description: string;
  testData: string[];
  precondition: boolean;
}

export interface StepRow {
  description: string;
  testData: string[];
  expectedResults: string | null;
}

export interface EditorFields {
  description: string;
  additionalInfo: string | null;
  atomic: boolean;
  optional: boolean;
  prerequisites: string[];
  effects: string[];
  negatedEffects: string[];
  testData: string[];
  expectedResults: string;
}

export interface ElementCard {
  id: string;
  name: string;
  description: string;
  locationDetails: string;
  pageSummary: string;
  dataDependent: boolean;
  screenshotId: string | null;
}

/** A child step; `id` is null while it is only a suggested description that still has to be edited and saved. */
export interface ChildRow {
  id: string | null;
  description: string;
  atomic: boolean;
  precondition: boolean;
  effects: string[];
  negatedEffects: string[];
  screenshotId: string | null;
}

export interface EditorNode {
  id: string | null;
  precondition: boolean;
  fields: EditorFields;
  targetElement: ElementCard | null;
  children: ChildRow[];
  parents: string[];
  testCases: string[];
}

export interface SavedNode {
  id: string | null;
  precondition: boolean;
  fields: EditorFields;
  targetElementId: string | null;
  childIds: string[];
}

interface PromptTypes {
  'start-step': { payload: { steps: StepRow[] }; answer: { stepIndex: number } };
  confirmation: {
    payload: { procedureDescription: string; ancestors: string[]; item: ItemView; beforeExecution: boolean };
    answer: { decision: 'PROCEED' | 'HALTED' };
  };
  'next-action': {
    payload: { message: string; itemDescription: string; precondition: boolean; actions: SelectionAction[] };
    answer: { action: SelectionAction; procedureId?: string };
  };
  message: { payload: { title: string; message: string; severity: Severity; screenshotId: string | null }; answer: object };
  'verification-failure': { payload: { verification: string; reason: string; screenshotId: string | null }; answer: object };
  'failure-context': {
    payload: { category: ErrorCategory; symptom: string; categories: ErrorCategory[] };
    answer:
      | { choice: 'DISMISS' }
      | { choice: 'SUBMIT'; category: ErrorCategory; symptom: string; resolution: string };
  };
  'procedure-editor': {
    payload: { create: boolean; item: ItemView; node: EditorNode };
    answer:
      | { outcome: 'SAVE'; node: SavedNode }
      | { outcome: 'CANCEL' }
      | { outcome: 'DELETE'; procedureId: string };
  };
}

export type PromptType = keyof PromptTypes;

export type PayloadOf<T extends PromptType> = PromptTypes[T]['payload'];

export type AnswerOf<T extends PromptType> = PromptTypes[T]['answer'];

type TypedPrompt = { [T in PromptType]: { type: T; payload: PayloadOf<T> } }[PromptType];

/**
 * The open prompt as the server sends it; `remainingMillis` and `waitedMillis` are the time to its deadline (the frozen time left
 * while paused) and since it opened when it was sent, `countdownMillis` the whole countdown.
 */
export type PromptView = TypedPrompt & {
  id: string;
  remainingMillis: number;
  waitedMillis: number;
  countdown: boolean;
  countdownMillis: number;
  held: boolean;
  paused: boolean;
  hasDefault: boolean;
};

/** The open prompt with the moment it arrived, from which its countdown is derived. */
export type ReceivedPrompt = PromptView & { receivedAt: number };

export type PromptOfType<T extends PromptType> = ReceivedPrompt & { type: T };

// The requests and answers of the prompt actions, mirroring PromptActionMessages

export interface ChildRef {
  id: string | null;
  description: string;
}

export interface ProcedureHit {
  id: string;
  description: string;
  atomic: boolean;
  precondition: boolean;
  satisfiedPrerequisites: number;
  totalPrerequisites: number;
}

export interface Suggestions {
  atomic: boolean;
  childSteps: string[];
  prerequisites: string[];
  effects: string[];
  negatedEffects: string[];
  expectedResults: string;
}

export interface ElementResolution {
  element: ElementCard | null;
  failureReason: string | null;
}

export interface CapturedScreen {
  screenshotId: string;
  width: number;
  height: number;
}

export interface ParentSummary {
  id: string;
  description: string;
}

export interface PromptActions {
  'search-procedures': {
    request: { query: string; showAllTypes: boolean; procedureId: string | null; precondition: boolean; childIds: string[] };
    response: { hits: ProcedureHit[] };
  };
  'effects-before-child': { request: { levels: ChildRef[][] }; response: { effects: string[] } };
  'load-suggestions': { request: { description: string; precedingSteps: ChildRef[] }; response: Suggestions };
  'similar-children': { request: { procedureId: string | null; rows: ChildRef[] }; response: { indices: number[] } };
  'locate-element': { request: { description: string; testData: string[] }; response: ElementResolution };
  'create-and-locate': { request: { description: string; testData: string[] }; response: ElementResolution };
  'select-element': { request: { description: string }; response: { query: string } };
  'search-elements': { request: { query: string }; response: { elements: ElementCard[] } };
  'update-element-info': {
    request: { elementId: string; name: string; description: string; locationDetails: string; pageSummary: string; dataDependent: boolean };
    response: ElementCard;
  };
  'capture-screen': { request: Record<string, never>; response: CapturedScreen };
  'replace-element-screenshot': {
    request: { elementId: string; screenshotId: string; x: number; y: number; width: number; height: number };
    response: ElementCard;
  };
  'delete-element': { request: { elementId: string }; response: object };
  'open-procedure': { request: { procedureId: string }; response: EditorNode };
  'edit-parent': { request: { procedureId: string }; response: { parents: ParentSummary[] } };
  'save-child': { request: SavedNode; response: ChildRow };
  'delete-procedure': { request: { procedureId: string }; response: object };
}

export type ActionName = keyof PromptActions;
