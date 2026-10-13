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
import { afterEach, describe, expect, it, vi } from 'vitest';
import { COPY_BLOCKED } from './clipboard';
import { INITIAL_STATE } from './events';
import { PanelView } from './PanelView';
import { PROMPT_ANCHOR_ID } from './prompts/anchor';
import type { PromptView, ReceivedPrompt } from './prompts/Prompt';
import { axeViolations, installDom, stubFetch } from './testing/dom';
import type { RunState } from './types';

installDom();

const stream = vi.hoisted(() => ({ state: null as unknown as RunState }));

vi.mock('./useEvents', () => ({ useEvents: () => ({ state: stream.state, streamStatus: 'open' }) }));

const CONFIRMATION: PromptView = {
  id: 'prompt-1', type: 'confirmation', remainingMillis: 45_000, waitedMillis: 0, countdown: true, countdownMillis: 60_000, held: false, paused: false, hasDefault: true,
  payload: {
    procedureDescription: 'Save the document', ancestors: [], beforeExecution: true,
    item: { description: 'Save the document', testData: [], precondition: false },
  },
};

const INFO = { executionMode: 'SUPERVISED', liveView: false, liveControl: false, searchDelayMillis: 0 } as const;

function renderPanel(prompt: ReceivedPrompt | null) {
  stream.state = { ...INITIAL_STATE, runStatus: 'RUNNING', testCaseName: 'Save flow', prompt };
  stubFetch((path) => (path === '/api/health/graph' ? { categories: [], generatedAt: '2026-10-04T10:00:00Z' } : undefined));
  render(<PanelView info={INFO} onSignedOut={vi.fn()} />);
}

const answerButton = () => screen.queryByRole('button', { name: 'Go to the run' });

describe('PanelView prompt strip', () => {
  it('shows the open prompt with its time left on another tab only', async () => {
    renderPanel({ ...CONFIRMATION, receivedAt: performance.now() });
    expect(answerButton()).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: 'Health' }));

    expect(answerButton()).toBeTruthy();
    const strip = answerButton()!.closest('.prompt-strip')!;
    expect(strip.textContent).toContain('Save the document');
    expect(strip.textContent).toContain('Runs in 45 s');
    await screen.findByText('No findings');
    expect(await axeViolations()).toEqual([]);
  });

  it('brings the Run tab forward and moves the focus to the prompt when the operator answers from the strip', () => {
    renderPanel({ ...CONFIRMATION, receivedAt: performance.now() });
    fireEvent.click(screen.getByRole('tab', { name: 'Health' }));

    fireEvent.click(answerButton()!);

    expect(screen.getByRole('tab', { name: /Run/ }).getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement?.id).toBe(PROMPT_ANCHOR_ID);
    expect(answerButton()).toBeNull();
  });

  it('stays away while no prompt is open', () => {
    renderPanel(null);

    fireEvent.click(screen.getByRole('tab', { name: 'Health' }));

    expect(answerButton()).toBeNull();
  });
});

describe('PanelView copy command', () => {
  function copyFromPalette() {
    stream.state = { ...INITIAL_STATE, logTail: ['first', 'second'] };
    render(<PanelView info={INFO} onSignedOut={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Search and jump' }));
    const field = screen.getByRole('combobox', { name: 'Search and jump' });
    fireEvent.change(field, { target: { value: 'copy the log' } });
    fireEvent.keyDown(field, { key: 'Enter' });
  }

  it('confirms the copy of the log in a status message', async () => {
    const writeText = vi.fn(async () => undefined);
    vi.stubGlobal('navigator', { platform: navigator.platform, clipboard: { writeText } });

    copyFromPalette();

    expect((await screen.findByText('Copied the log')).getAttribute('role')).toBe('status');
    expect(writeText).toHaveBeenCalledWith('first\nsecond');
  });

  it('says that the browser blocked copying when the write fails', async () => {
    vi.stubGlobal('navigator', { platform: navigator.platform });

    copyFromPalette();

    expect((await screen.findByText(COPY_BLOCKED)).getAttribute('role')).toBe('status');
  });
});

describe('PanelView logs', () => {
  afterEach(() => localStorage.clear());

  it('opens the logs with L, remembers that, and gives the focus back to the toggle when they close from inside', () => {
    renderPanel(null);

    fireEvent.keyDown(document.body, { key: 'l' });
    expect(screen.getByRole('complementary', { name: 'Agent logs' })).toBeTruthy();
    expect(localStorage.getItem('panel.logs')).toBe('open');

    fireEvent.click(screen.getByRole('button', { name: 'Close the logs' }));

    expect(screen.queryByRole('complementary', { name: 'Agent logs' })).toBeNull();
    expect(document.activeElement?.id).toBe('run-logs-toggle');
    expect(localStorage.getItem('panel.logs')).toBe('closed');
  });

  it('opens on a page that left the logs open', () => {
    localStorage.setItem('panel.logs', 'open');

    renderPanel(null);

    expect(screen.getByRole('complementary', { name: 'Agent logs' })).toBeTruthy();
  });

  it('counts the warnings and errors the closed logs have not shown', () => {
    stream.state = { ...INITIAL_STATE, runStatus: 'RUNNING', logTail: ['10:00:00 WARN a - slow', '10:00:01 INFO a - fine', '10:00:02 ERROR a - gone'] };
    stubFetch(() => undefined);

    render(<PanelView info={INFO} onSignedOut={vi.fn()} />);

    expect(screen.getByRole('button', { name: /^Logs, 2 new warnings or errors/ })).toBeTruthy();
  });
});

describe('PanelView search trigger', () => {
  it('names the Command key on a Mac', async () => {
    vi.spyOn(navigator, 'platform', 'get').mockReturnValue('MacIntel');
    vi.resetModules();
    try {
      const { PanelView: MacPanelView } = await import('./PanelView');
      stream.state = { ...INITIAL_STATE };
      render(<MacPanelView info={INFO} onSignedOut={vi.fn()} />);

      expect(document.querySelector('.command-trigger-kbd')?.textContent).toBe('⌘ K');
    } finally {
      vi.restoreAllMocks();
      vi.resetModules();
    }
  });
});
