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
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, installDom, stubFetch } from '../testing/dom';
import { FocusView } from './FocusView';
import { TEST_CASE_ID_PREFIX, type GraphEdge, type GraphNode, type GraphSlice, type TestCaseLens } from './model';

installDom();

const node = (id: string, kind: GraphNode['kind'] = 'ATOMIC', extra: Partial<GraphNode> = {}): GraphNode =>
  ({ id, kind, label: `Label of ${id}`, flags: [], testCases: 0, ...extra });

const edge = (kind: GraphEdge['kind'], source: string, target: string, extra: Partial<GraphEdge> = {}): GraphEdge =>
  ({ source, target, kind, sequence: null, score: null, effectPhrase: null, prerequisitePhrase: null, ...extra });

const satisfies = (source: string, target: string, effectPhrase: string, prerequisitePhrase: string, score = 0.9) =>
  edge('SATISFIES', source, target, { score, effectPhrase, prerequisitePhrase });

const MENU: GraphSlice = {
  nodes: [
    node('menu', 'ATOMIC', { flags: ['RECENT_ANOMALY'], testCases: 3 }),
    node('edit-view', 'COMPOSITE'),
    node('hover'),
    node('open-dashboard'),
    node('search-setting'),
    node('edit-button', 'ELEMENT'),
  ],
  edges: [
    edge('CONTAINS', 'edit-view', 'menu', { sequence: 1 }),
    satisfies('hover', 'menu', 'mouse over panel', 'panel menu visible'),
    satisfies('open-dashboard', 'menu', 'dashboard open', 'dashboard shown'),
    satisfies('menu', 'search-setting', 'panel menu open', 'menu is open'),
    edge('TARGETS', 'menu', 'edit-button'),
  ],
  capped: false,
};

const HOVER: GraphSlice = {
  nodes: [node('hover'), node('menu')],
  edges: [satisfies('hover', 'menu', 'mouse over panel', 'panel menu visible')],
  capped: false,
};

const renderView = (activeProcedures: string[] = [], testCaseName: string | null = null) =>
  render(<FocusView focus={{ kind: 'procedure', id: 'menu', seq: 1 }} testCaseName={testCaseName} activeProcedures={activeProcedures}
    onSelectProcedure={vi.fn()} onShowFailures={vi.fn()} onOpenElement={vi.fn()} onUnauthorized={vi.fn()} />);

const neighbourhoods = (slices: Record<string, GraphSlice>) => (path: string) =>
  slices[/^\/api\/knowledge\/procedures\/([^/]+)\/neighbourhood$/.exec(path)?.[1] ?? ''];

describe('FocusView', () => {
  it('shows only the direct relations of the procedure in focus, marking the running one, without axe violations', async () => {
    stubFetch(neighbourhoods({ menu: MENU }));
    renderView(['hover']);

    const centre = await screen.findByRole('region', { name: 'Label of menu' });

    expect(within(centre).getByText(/3 test cases · Recent anomaly/)).toBeTruthy();
    expect(within(centre).getByRole('button', { name: /Label of edit-button/ })).toBeTruthy();
    expect(within(screen.getByRole('navigation', { name: 'Part of' })).getByRole('button', { name: /Label of edit-view/ })).toBeTruthy();
    const enabledBy = screen.getByRole('region', { name: 'Enabled by' });
    expect(within(enabledBy).getByRole('group', { name: '“panel menu visible”' })).toBeTruthy();
    expect(within(enabledBy).getByRole('button', { name: /Label of hover.*Running now/ })).toBeTruthy();
    expect(within(enabledBy).getByRole('group', { name: '“dashboard shown”' })).toBeTruthy();
    const enables = screen.getByRole('region', { name: 'Enables' });
    expect(within(enables).getByRole('group', { name: '“panel menu open”' })).toBeTruthy();
    expect(within(enables).getByRole('button', { name: /Label of search-setting/ })).toBeTruthy();
    expect(await axeViolations()).toEqual([]);
  });

  it('puts a clicked procedure in focus and goes back to the previous one', async () => {
    stubFetch(neighbourhoods({ menu: MENU, hover: HOVER }));
    renderView();
    await screen.findByRole('region', { name: 'Label of menu' });

    fireEvent.click(within(screen.getByRole('region', { name: 'Enabled by' })).getByRole('button', { name: /Label of hover/ }));

    expect(await screen.findByRole('region', { name: 'Label of hover' })).toBeTruthy();
    expect(within(screen.getByRole('region', { name: 'Enables' })).getByRole('button', { name: /Label of menu/ })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(await screen.findByRole('region', { name: 'Label of menu' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Back' })).toBeNull();
  });

  it('shows the first five procedures of a long group and the rest on demand', async () => {
    const enablers = ['a', 'b', 'c', 'd', 'e', 'f', 'g'];
    stubFetch(neighbourhoods({
      menu: {
        nodes: [node('menu'), ...enablers.map((id) => node(id))],
        edges: enablers.map((id, index) => satisfies(id, 'menu', 'open', 'menu visible', 0.9 - index / 100)),
        capped: false,
      },
    }));
    renderView();
    const group = await screen.findByRole('group', { name: '“menu visible”' });

    expect(within(group).getAllByRole('listitem')).toHaveLength(5);

    fireEvent.click(within(group).getByRole('button', { name: 'Show 2 more' }));

    expect(within(group).getAllByRole('listitem')).toHaveLength(7);
    expect(within(group).getByRole('button', { name: 'Show fewer' }).getAttribute('aria-expanded')).toBe('true');
  });

  it('shows the procedures of the running test case and the path its latest run chose', async () => {
    const lens: TestCaseLens = {
      slice: {
        nodes: [node(`${TEST_CASE_ID_PREFIX}Rent a car`, 'TEST_CASE'), node('pick-up', 'COMPOSITE'), node('search', 'COMPOSITE')],
        edges: [edge('USES_PROCEDURE', `${TEST_CASE_ID_PREFIX}Rent a car`, 'pick-up'), edge('USES_PROCEDURE', `${TEST_CASE_ID_PREFIX}Rent a car`, 'search')],
        capped: false,
      },
      latestRunId: 'run-1',
      latestRunPath: ['search'],
    };
    stubFetch((path) => (path === '/api/knowledge/test-cases/procedures' ? lens : undefined));
    render(<FocusView focus={null} testCaseName="Rent a car" activeProcedures={[]} onSelectProcedure={vi.fn()} onShowFailures={vi.fn()}
      onOpenElement={vi.fn()} onUnauthorized={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /Show the procedures of “Rent a car”/ }));

    const centre = await screen.findByRole('region', { name: 'Label of test-case:Rent a car' });
    expect(within(centre).getByRole('button', { name: /Label of pick-up/ })).toBeTruthy();
    expect(within(centre).getByRole('button', { name: /Label of search.*Chosen by the latest run/ })).toBeTruthy();
    expect(screen.queryByRole('region', { name: 'Enabled by' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Label of search' })).toBeTruthy();
  });
});
