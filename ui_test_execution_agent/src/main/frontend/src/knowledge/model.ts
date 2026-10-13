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

// Mirrors of the Java records in org.tarik.ta.dto.panel.knowledge and the run-history nodes; KnowledgeRoutesTest pins their JSON.

export type NodeKind = 'COMPOSITE' | 'ATOMIC' | 'ELEMENT' | 'TEST_CASE';

export type NodeFlag = 'NO_TARGET' | 'NO_EFFECTS' | 'LOW_STABILITY' | 'RECENT_ANOMALY';

export type EdgeKind = 'CONTAINS' | 'TARGETS' | 'SATISFIES' | 'USES_PROCEDURE';

export type ProcedureOutcome = 'COMPLETED' | 'SKIPPED' | 'FAILED' | 'ABANDONED';

export interface Count {
  name: string;
  count: number;
}

export interface KnowledgeSummary {
  nodesByLabel: Count[];
  relationshipsByType: Count[];
  schemaVersion: number;
  atomicProcedures: number;
  atomicProceduresWithTarget: number;
  atomicProceduresWithEffects: number;
}

export interface GraphNode {
  id: string;
  kind: NodeKind;
  label: string;
  flags: NodeFlag[];
  testCases: number;
}

export interface GraphEdge {
  source: string;
  target: string;
  kind: EdgeKind;
  sequence: number | null;
  score: number | null;
  effectPhrase: string | null;
  prerequisitePhrase: string | null;
}

export interface GraphSlice {
  nodes: GraphNode[];
  edges: GraphEdge[];
  capped: boolean;
}

export interface SearchResults {
  hits: GraphNode[];
  capped: boolean;
}

export interface TestCaseLens {
  slice: GraphSlice;
  latestRunId: string | null;
  latestRunPath: string[];
}

export interface ProcedureRef {
  id: string;
  description: string;
}

export interface TimingProfile {
  avgExecutionMs: number;
  avgVerificationDelayMs: number;
  maxVerificationDelayMs: number;
  lastTimingUpdate: string;
}

export interface Anomaly {
  kind: string;
  occurredAt: string;
  testCaseName: string | null;
  details: string;
}

export type ResolutionSource = 'KNOWLEDGE' | 'OPERATOR_SELECTED' | 'OPERATOR_CREATED' | 'UNRESOLVED';

export type StepOutcome = 'SUCCEEDED' | 'FAILED' | 'NOT_EXECUTED';

export interface Candidate {
  procedureId: string;
  description: string;
  semanticScore: number;
  satisfiedPrerequisites: number;
  totalPrerequisites: number;
  sharedParentCount: number;
  lowStability: boolean;
  unmetPrerequisites: string[];
}

export interface StepResolution {
  id: string;
  sequence: number;
  itemType: 'PRECONDITION' | 'TEST_STEP';
  itemDescription: string;
  source: ResolutionSource;
  confidence: 'HIGH' | 'LOW' | null;
  candidates: Candidate[];
  wasDemoted: boolean;
  demotedDueToPrerequisites: boolean;
  chosenProcedureId: string | null;
  outcome: StepOutcome;
  durationMs: number;
}

export interface ResolutionView {
  runId: string;
  testCaseName: string;
  runStartedAt: string;
  resolution: StepResolution;
}

export interface ProcedureDetails {
  id: string;
  description: string;
  atomic: boolean;
  precondition: boolean;
  optional: boolean;
  prerequisites: string[];
  effects: string[];
  negatedEffects: string[];
  testData: string[];
  expectedResults: string;
  additionalInfo: string | null;
  createdAt: string;
  updatedAt: string;
  successCount: number;
  timing: TimingProfile | null;
  parents: ProcedureRef[];
  children: ProcedureRef[];
  target: { id: string; name: string } | null;
  testCases: string[];
  recentAnomalies: Anomaly[];
  chosenFor: ResolutionView[];
}

export type ElementSort = 'stability' | 'updated' | 'retries';

export interface ElementTile {
  id: string;
  name: string;
  stability: number | null;
  avgLocationTimeMs: number | null;
  locationRetries: number | null;
  dataDependent: boolean;
  updatedAt: string | null;
  lastLocatedAt: string | null;
  hasScreenshot: boolean;
}

export interface ElementPage {
  elements: ElementTile[];
  offset: number;
  hasMore: boolean;
}

export interface Appearance {
  id: string;
  ownDescription: string;
  anchorsDescription: string;
  parentElementSummary: string;
  validFrom: string | null;
  validTo: string;
  hasScreenshot: boolean;
}

export interface ElementDetails {
  tile: ElementTile;
  description: string;
  locationDetails: string;
  parentElementSummary: string;
  targetedBy: ProcedureRef[];
  appearances: Appearance[];
}

export type AnomalyKind = 'ELEMENT_LOCATION_FAILED' | 'STEP_FAILED' | 'TIMING_OUTLIER';

export interface Week {
  start: string;
  proceduresCreated: number;
  anomalies: Partial<Record<AnomalyKind, number>>;
  resolutions: Partial<Record<ResolutionSource, number>>;
  outcomes: Partial<Record<StepOutcome, number>>;
}

export interface WeeklyTrends {
  weeks: Week[];
}

export interface FailureHint {
  id: string;
  category: string;
  symptom: string;
  resolution: string;
  occurrences: number;
  lastOccurred: string;
  expiredAt: string | null;
  mode: string;
}

export interface FailureHistory {
  procedure: ProcedureRef;
  staleDays: number;
  hints: FailureHint[];
}

export const TEST_CASE_ID_PREFIX = 'test-case:';
