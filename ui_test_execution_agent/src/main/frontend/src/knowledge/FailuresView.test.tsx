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
// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, installDom, stubFetch } from '../testing/dom';
import { FailuresView } from './FailuresView';
import type { FailureHistory, GraphNode, SearchResults } from './model';

installDom();

const node = (id: string, kind: GraphNode['kind'], label: string): GraphNode => ({ id, kind, label, flags: [], testCases: 0 });

const RESULTS: SearchResults = {
  hits: [node('proc-1', 'ATOMIC', 'Save the document'), node('el-1', 'ELEMENT', 'Save button'), node('proc-2', 'COMPOSITE', 'Save and close')],
  capped: false,
};

const HISTORY: FailureHistory = { procedure: { id: 'proc-1', description: 'Save the document' }, staleDays: 30, hints: [] };

function Harness() {
  const [procedureId, setProcedureId] = useState<string | null>(null);
  return <FailuresView procedureId={procedureId} onSelectProcedure={setProcedureId} onShowMap={vi.fn()} onUnauthorized={vi.fn()} />;
}

async function search(query: string) {
  fireEvent.change(screen.getByRole('searchbox', { name: 'Find a procedure' }), { target: { value: query } });
  fireEvent.click(screen.getByRole('button', { name: 'Search' }));
  await screen.findByRole('button', { name: /Save the document/ });
}

describe('FailuresView', () => {
  it('finds procedures only, without a detour through the map, and has no axe violations', async () => {
    stubFetch((path) => (path === '/api/knowledge/search' ? RESULTS : undefined));
    render(<Harness />);

    await search('save');

    expect(screen.getByRole('button', { name: /Save and close/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Save button/ })).toBeNull();
    expect(await axeViolations()).toEqual([]);
  });

  it('loads the failure history of the procedure chosen among the hits', async () => {
    stubFetch((path) => (path === '/api/knowledge/search' ? RESULTS : path === '/api/knowledge/failures' ? HISTORY : undefined));
    render(<Harness />);
    await search('save');

    fireEvent.click(screen.getByRole('button', { name: /Save the document/ }));

    expect(await screen.findByRole('heading', { name: 'Save the document' })).toBeTruthy();
    expect(screen.getByText('No failure has been recorded for this procedure.')).toBeTruthy();
    expect(vi.mocked(fetch).mock.calls.map(([url]) => String(url))).toContain('/api/knowledge/failures?procedureId=proc-1');
  });

  it('finds another procedure while one is shown', async () => {
    stubFetch((path) => (path === '/api/knowledge/search' ? RESULTS : path === '/api/knowledge/failures' ? HISTORY : undefined));
    render(<FailuresView procedureId="proc-1" onSelectProcedure={vi.fn()} onShowMap={vi.fn()} onUnauthorized={vi.fn()} />);
    await screen.findByRole('heading', { name: 'Save the document' });

    await search('save');

    expect(screen.getByRole('button', { name: /Save and close/ })).toBeTruthy();
    expect(await axeViolations()).toEqual([]);
  });

  it('says when the capped hits hold no procedure', async () => {
    stubFetch((path) => (path === '/api/knowledge/search' ? { hits: [node('el-1', 'ELEMENT', 'Save button')], capped: true } : undefined));
    render(<Harness />);

    fireEvent.change(screen.getByRole('searchbox', { name: 'Find a procedure' }), { target: { value: 'save' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText(/The first 50 matches are all of another kind/)).toBeTruthy();
  });
});
