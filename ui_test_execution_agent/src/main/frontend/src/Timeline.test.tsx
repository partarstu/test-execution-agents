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
import { describe, expect, it } from 'vitest';
import type { StepResolution } from './knowledge/model';
import { axeViolations, installDom } from './testing/dom';
import { Timeline } from './Timeline';
import type { TimelineItem } from './types';

installDom();

const RESOLUTION: StepResolution = {
  id: 'resolution-1', sequence: 0, itemType: 'TEST_STEP', itemDescription: 'Save the document', source: 'KNOWLEDGE', confidence: 'HIGH',
  candidates: [], wasDemoted: false, demotedDueToPrerequisites: false, chosenProcedureId: null, outcome: 'SUCCEEDED', durationMs: 1_200,
};

const ITEM: TimelineItem = { kind: 'STEP', description: 'Save the document', status: 'PASSED', errorMessage: null, resolution: RESOLUTION };

describe('Timeline', () => {
  it('points the explanation toggle at the explanation only while it is shown', async () => {
    render(<Timeline items={[ITEM]} currentItem={-1} />);
    const toggle = screen.getByRole('button', { name: 'Why this procedure' });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBeNull();

    fireEvent.click(toggle);

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(document.getElementById(toggle.getAttribute('aria-controls')!)).not.toBeNull();
    expect(await axeViolations()).toEqual([]);
  });

  it('cuts the error of an item to its first lines', () => {
    render(<Timeline items={[{ ...ITEM, status: 'ERROR', errorMessage: 'Element not found\nat Locator.find' }]} currentItem={-1} />);

    expect(screen.getByText(/Element not found/).classList).toContain('clamped-error-collapsed');
  });
});
