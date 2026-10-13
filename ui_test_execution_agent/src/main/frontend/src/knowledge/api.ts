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
import { load } from '../api';
import type {
  ElementDetails,
  ElementPage,
  ElementSort,
  FailureHistory,
  GraphSlice,
  KnowledgeSummary,
  ProcedureDetails,
  SearchResults,
  TestCaseLens,
  WeeklyTrends,
} from './model';

export type { Loaded } from '../api';

const BASE = '/api/knowledge';

const read = <T,>(path: string, signal?: AbortSignal) => load<T>(`${BASE}${path}`, signal);

const id = (value: string) => encodeURIComponent(value);

export const fetchSummary = (signal?: AbortSignal) => read<KnowledgeSummary>('/summary', signal);

export const searchKnowledge = (query: string) => read<SearchResults>(`/search?q=${encodeURIComponent(query)}`);

export const fetchNeighbourhood = (procedureId: string) => read<GraphSlice>(`/procedures/${id(procedureId)}/neighbourhood`);

export const fetchTestCaseLens = (name: string) => read<TestCaseLens>(`/test-cases/procedures?name=${encodeURIComponent(name)}`);

export const fetchProcedure = (procedureId: string, signal?: AbortSignal) => read<ProcedureDetails>(`/procedures/${id(procedureId)}`, signal);

export const fetchElements = (sort: ElementSort, offset: number) => read<ElementPage>(`/elements?sort=${sort}&offset=${offset}`);

export const fetchElement = (elementId: string, signal?: AbortSignal) => read<ElementDetails>(`/elements/${id(elementId)}`, signal);

export const elementScreenshotUrl = (elementId: string) => `${BASE}/elements/${id(elementId)}/screenshot`;

export const appearanceScreenshotUrl = (elementId: string, appearanceId: string) =>
  `${BASE}/elements/${id(elementId)}/appearances/${id(appearanceId)}/screenshot`;

export const fetchOverview = (signal?: AbortSignal) => read<GraphSlice>('/overview', signal);

export const fetchTrends = (weeks: number, signal?: AbortSignal) => read<WeeklyTrends>(`/trends?weeks=${weeks}`, signal);

export const fetchFailures = (procedureId: string, signal?: AbortSignal) =>
  read<FailureHistory>(`/failures?procedureId=${id(procedureId)}`, signal);
