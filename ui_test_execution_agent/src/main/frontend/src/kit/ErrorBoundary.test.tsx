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
import { axeViolations, installDom } from '../testing/dom';
import { ErrorBoundary } from './ErrorBoundary';

installDom();

let failing = true;

function FragileView() {
  if (failing) {
    throw new Error('Unexpected shape');
  }
  return <p>The view</p>;
}

function renderPanels() {
  failing = true;
  // React logs a caught render error; the test expects the error, so the log is silenced
  vi.spyOn(console, 'error').mockImplementation(() => {});
  render(
    <>
      <div><ErrorBoundary><FragileView /></ErrorBoundary></div>
      <div><ErrorBoundary><p>The open prompt</p></ErrorBoundary></div>
    </>,
  );
}

describe('ErrorBoundary', () => {
  it('shows the failure in place of the failing view while a sibling view stays rendered', async () => {
    renderPanels();

    expect(screen.getByRole('alert').textContent).toContain('This view failed');
    expect(screen.getByText('The open prompt')).toBeTruthy();
    expect(await axeViolations()).toEqual([]);
  });

  it('mounts the view afresh when the operator shows it again', () => {
    renderPanels();
    failing = false;

    fireEvent.click(screen.getByRole('button', { name: 'Show it again' }));

    expect(screen.getByText('The view')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
