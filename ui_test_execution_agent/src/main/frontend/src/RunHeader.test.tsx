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
import { INITIAL_STATE } from './events';
import { RunHeader, type LogsToggle } from './RunHeader';
import { axeViolations, installDom } from './testing/dom';
import type { RunState } from './types';

installDom();

const STATE: RunState = { ...INITIAL_STATE, runStatus: 'RUNNING', testCaseName: 'Save flow', statusMessage: 'Looking for the Save button' };
const LOGS: LogsToggle = { open: false, unseen: 0, onToggle: () => undefined };

describe('RunHeader', () => {
  it('leaves what the agent is doing to the current step while the run runs, and states it otherwise', () => {
    const { rerender } = render(<RunHeader state={STATE} mode="SUPERVISED" logs={LOGS} />);
    expect(screen.queryByText('Looking for the Save button')).toBeNull();

    rerender(<RunHeader state={INITIAL_STATE} mode="SUPERVISED" logs={LOGS} />);

    expect(screen.getByText('Waiting for a test case from the orchestrator.')).toBeTruthy();
  });

  it('toggles the logs and counts the warnings and errors that arrived while they were closed', async () => {
    const onToggle = vi.fn();
    const { rerender } = render(<RunHeader state={STATE} mode="SUPERVISED" logs={{ ...LOGS, unseen: 3, onToggle }} />);
    const toggle = screen.getByRole('button', { name: /^Logs, 3 new warnings or errors/ });

    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-keyshortcuts')).toBe('L');
    fireEvent.click(toggle);
    expect(onToggle).toHaveBeenCalledOnce();

    rerender(
      <>
        <RunHeader state={STATE} mode="SUPERVISED" logs={{ open: true, unseen: 0, onToggle }} />
        <aside id="run-logs" aria-label="Agent logs" />
      </>,
    );

    expect(screen.getByRole('button', { name: /^Hide logs/ }).getAttribute('aria-expanded')).toBe('true');
    expect(await axeViolations()).toEqual([]);
  });

  it('shows the finished items in short and every count for screen readers and on hover', () => {
    const items: RunState['items'] = (['PASSED', 'FAILED', 'RUNNING', 'PENDING'] as const)
      .map((status) => ({ kind: 'STEP', description: status, status, errorMessage: null, resolution: null }));

    render(<RunHeader state={{ ...STATE, items, currentItem: 2 }} mode="SUPERVISED" logs={LOGS} />);

    const counts = screen.getByText('2 of 4 done');
    expect(counts.getAttribute('aria-hidden')).toBe('true');
    expect(screen.getByText('1 passed, 1 failed, 1 running, 1 not run yet').classList).toContain('visually-hidden');
    expect(counts.closest('.run-header-progress')?.getAttribute('title')).toBe('1 passed, 1 failed, 1 running, 1 not run yet');
  });

  it('cuts the error of a run that ended in error to its first lines', () => {
    const ended: RunState = { ...STATE, runStatus: 'ERROR', statusMessage: null, resultMessage: 'Element not found\nat Locator.find' };

    render(<RunHeader state={ended} mode="SUPERVISED" logs={LOGS} />);

    expect(screen.getByText(/Element not found/).classList).toContain('clamped-error-collapsed');
  });
});
