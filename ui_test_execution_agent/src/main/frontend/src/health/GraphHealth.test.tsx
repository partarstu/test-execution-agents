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
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, installDom, stubFetch } from '../testing/dom';
import { bySeverity, GraphHealth } from './GraphHealth';
import type { GraphHealthReport, HealthCheckCategory, Severity } from './GraphHealthReport';

installDom();

function category(name: string, severity: Severity, findings = 0): HealthCheckCategory {
  return {
    name,
    description: `Checks ${name}`,
    severity,
    findings: Array.from({ length: findings }, (_, index) => ({ text: `${name} ${index}`, refs: [{ label: 'Procedure', id: `${name}-${index}` }] })),
  };
}

function renderReport(categories: HealthCheckCategory[]) {
  const report: GraphHealthReport = { categories, generatedAt: '2026-10-04T10:00:00Z' };
  stubFetch((path) => (path === '/api/health/graph' ? report : undefined));
  render(<GraphHealth onUnauthorized={vi.fn()} onOpenNode={vi.fn()} />);
}

const names = () => [...document.querySelectorAll('.category-name')].map((name) => name.textContent);

describe('bySeverity', () => {
  it('puts the critical categories first and the passed ones last, in the server order within one severity', () => {
    const sorted = bySeverity([category('A', 'OK'), category('B', 'WARNING'), category('C', 'CRITICAL'), category('D', 'OK'), category('E', 'CRITICAL')]);

    expect(sorted.map((sortedCategory) => sortedCategory.name)).toEqual(['C', 'E', 'B', 'A', 'D']);
  });
});

describe('GraphHealth', () => {
  it('summarises the report, lists the critical checks first and has no axe violations', async () => {
    renderReport([category('Orphans', 'WARNING', 2), category('Duplicate Atomic Procedures', 'CRITICAL', 1), category('Cycles', 'OK')]);

    expect(await screen.findByText('1 critical check')).toBeTruthy();
    expect(screen.getByText('1 check with warnings')).toBeTruthy();
    expect(screen.getByText('3 findings in 3 checks')).toBeTruthy();
    expect(names()).toEqual(['Duplicate Atomic Procedures', 'Orphans', 'Cycles']);
    expect(await axeViolations()).toEqual([]);
  });

  it('states the worst severity as the verdict and filters the list by the tile chosen', async () => {
    renderReport([category('Orphans', 'WARNING', 2), category('Duplicates', 'CRITICAL', 1), category('Cycles', 'OK')]);

    expect(await screen.findByText('The knowledge graph has critical findings')).toBeTruthy();
    fireEvent.click(screen.getByRole('radio', { name: 'Warnings 1' }));

    expect(names()).toEqual(['Orphans']);
    fireEvent.click(screen.getByRole('radio', { name: 'All checks 3' }));
    expect(names()).toEqual(['Duplicates', 'Orphans', 'Cycles']);
  });

  it('shows the all-clear instead of the list when no check found anything', async () => {
    renderReport([category('Orphans', 'OK'), category('Cycles', 'OK')]);

    expect(await screen.findByText('No findings')).toBeTruthy();
    expect(screen.getByText('The knowledge graph passed all 2 checks.')).toBeTruthy();
    expect(names()).toEqual([]);
    expect(await axeViolations()).toEqual([]);
  });
});
