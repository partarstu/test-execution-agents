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
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { INITIAL_STATE } from './events';
import type { PayloadOf, ReceivedPrompt } from './prompts/Prompt';
import { RunTab, type RunView } from './RunTab';
import { axeViolations, installDom, stubFetch } from './testing/dom';
import type { ItemOutline, ProcedureNode, ProcedureNodeStatus, RunState, SessionInfo, TimelineItem } from './types';

const { clients } = vi.hoisted(() => ({ clients: [] as { viewOnly: boolean }[] }));

vi.mock('@novnc/novnc', () => ({
  default: class extends EventTarget {
    viewOnly = true;
    scaleViewport = false;
    resizeSession = false;

    constructor() {
      super();
      clients.push(this);
    }

    disconnect() {
      // The test never reconnects
    }
  },
}));

installDom();

beforeEach(() => {
  clients.length = 0;
});

const INFO: SessionInfo = { executionMode: 'SUPERVISED', liveView: true, liveControl: true, searchDelayMillis: 0 };

const item = (description: string, status: TimelineItem['status']): TimelineItem => ({
  kind: 'STEP', description, status, errorMessage: null, resolution: null,
});

const node = (id: string, atomic: boolean, depth: number, status: ProcedureNodeStatus, expectedResults: string | null = null): ProcedureNode => ({
  id, description: id, expectedResults, atomic, optional: false, depth, status, durationMillis: status === 'COMPLETED' ? 1_200 : null,
});

// Log in > (Open the dialog > Click Sign in), (Fill in > Type the user name, Type the password), Dismiss the banner
const OUTLINE: ItemOutline = {
  item: 1,
  testData: ['admin'],
  procedures: [
    node('Log in', false, 0, 'RUNNING'),
    node('Open the dialog', false, 1, 'COMPLETED'),
    node('Click Sign in', true, 2, 'COMPLETED'),
    node('Fill in', false, 1, 'RUNNING'),
    node('Type the user name', true, 2, 'COMPLETED'),
    node('Type the password', true, 2, 'RUNNING', 'The password field is filled'),
    node('Dismiss the banner', true, 1, 'PENDING'),
  ],
};

const RUNNING: RunState = {
  ...INITIAL_STATE,
  runStatus: 'RUNNING',
  testCaseName: 'Create a user',
  items: [item('Open the console', 'PASSED'), item('Log in as admin', 'RUNNING'), item('Create the user', 'PENDING')],
  currentItem: 1,
  statusMessage: 'Typing the password',
  outline: OUTLINE,
};

function confirmation(beforeExecution: boolean): ReceivedPrompt {
  const payload: PayloadOf<'confirmation'> = {
    procedureDescription: 'Type the password', ancestors: ['Log in', 'Fill in'],
    item: { description: 'Log in as admin', testData: ['admin'], precondition: false }, beforeExecution,
  };
  return {
    id: 'confirm-1', type: 'confirmation', payload, remainingMillis: 30_000, waitedMillis: 0, countdown: true, countdownMillis: 30_000,
    held: false, paused: false, hasDefault: true, receivedAt: performance.now(),
  };
}

function renderRun(state: RunState, view: RunView = 'step', logsOpen = false) {
  stubFetch(() => undefined);
  const props = {
    info: INFO, liveViewAllowed: true, onToggleView: vi.fn(), onUnauthorized: vi.fn(),
    logs: { open: logsOpen, unseen: 0, onToggle: vi.fn(), onClose: vi.fn() },
  };
  const result = render(<RunTab state={state} view={view} {...props} />);
  return { ...result, props, rerenderRun: (next: RunState, nextView: RunView) => result.rerender(<RunTab state={next} view={nextView} {...props} />) };
}

const tree = () => screen.getByRole('list', { name: 'Procedures of this step' });

describe('RunTab', () => {
  it('shows the current step with its position, test data, progress and the executing action opened in the tree', async () => {
    renderRun(RUNNING);

    const stage = screen.getByRole('region', { name: 'Log in as admin' });
    expect(stage.textContent).toContain('Test step 2 of 3');
    expect(stage.textContent).toContain('admin');
    expect(stage.textContent).toContain('2 of 4 actions done · action 3 executing · 1 to go');
    const current = within(tree()).getAllByRole('listitem').find((row) => row.getAttribute('aria-current') === 'step')!;
    expect(current.textContent).toContain('Type the password');
    expect(current.textContent).toContain('Executing');
    expect(current.textContent).toContain('Action 3 of 4');
    expect(current.textContent).toContain('Typing the password');
    expect(await axeViolations()).toEqual([]);
  });

  it('folds the finished composites, marks the next action and lets the operator unfold them', () => {
    renderRun(RUNNING);

    const finished = screen.getByRole('button', { name: /^Open the dialog, done, 1 of 1 actions done/ });
    expect(finished.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText('Click Sign in')).toBeNull();
    expect(within(tree()).getByText('Next')).toBeTruthy();

    fireEvent.click(finished);

    expect(finished.getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByText('Click Sign in')).toBeTruthy();
  });

  it('asks the confirmation in the current action, with the expected result and Halt only, instead of a dialog', async () => {
    renderRun({ ...RUNNING, prompt: confirmation(true) });

    const current = within(tree()).getAllByRole('listitem').find((row) => row.getAttribute('aria-current') === 'step')!;
    const question = within(current).getByRole('region', { name: 'Run this action?' });
    expect(question.textContent).toContain('Type the password');
    expect(current.textContent).toContain('Waiting for your go');
    expect(current.textContent).toContain('The password field is filled');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(within(current).getAllByRole('button')).toEqual([within(current).getByRole('button', { name: 'Halt' })]);
    expect(current.contains(document.activeElement)).toBe(false);

    fireEvent.click(within(current).getByRole('button', { name: 'Halt' }));

    await waitFor(() => expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).endsWith('/answer'))).toBe(true));
    const answer = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith('/answer'))!;
    expect(JSON.parse(String(answer[1]?.body))).toEqual({ decision: 'HALTED' });
    expect(await axeViolations()).toEqual([]);
  });

  it('pauses the countdown while the pointer is anywhere on the current action, not only on the question', async () => {
    renderRun({ ...RUNNING, prompt: confirmation(true) });
    const calls = () => vi.mocked(fetch).mock.calls.map(([url]) => String(url).split('/').pop());
    const title = within(tree()).getByText('Type the password', { selector: '.tfocus-title' });

    fireEvent.pointerEnter(title);
    fireEvent.pointerLeave(title);

    await waitFor(() => expect(calls()).toEqual(['pause', 'resume']));
  });

  it('leaves the focus where the operator works when the confirmation opens', () => {
    const { rerenderRun } = renderRun(RUNNING, 'step', true);
    const search = screen.getByRole('searchbox', { name: 'Find in the log' });
    search.focus();

    rerenderRun({ ...RUNNING, prompt: confirmation(true) }, 'step');

    expect(document.activeElement).toBe(search);
  });

  it('keeps the question off the large live view, in the compact current action, and the same live view when switching', async () => {
    const { rerenderRun } = renderRun({ ...RUNNING, prompt: confirmation(false) });
    await waitFor(() => expect(clients).toHaveLength(1));
    expect(screen.getByRole('button', { name: /Open large/ })).toBeTruthy();

    rerenderRun({ ...RUNNING, prompt: confirmation(false) }, 'live');

    expect(screen.queryByRole('region', { name: 'Action executed. Go on?' })).toBeNull();
    const current = within(tree()).getAllByRole('listitem').find((row) => row.getAttribute('aria-current') === 'step')!;
    const halt = within(current).getByRole('region', { name: 'Halt after this action?' });
    expect(halt.textContent).toContain('Type the password');
    expect(halt.textContent).toContain('Executed · waiting');
    expect(within(halt).getAllByRole('button')).toEqual([within(halt).getByRole('button', { name: 'Halt' })]);
    expect(within(halt).getByRole('timer').textContent).toBe('The run goes on in 30 s.');
    expect(halt.querySelectorAll('.ring')).toHaveLength(1);
    expect(screen.getByRole('button', { name: /Back to the step/ })).toBeTruthy();
    expect(clients).toHaveLength(1);
    expect(await axeViolations()).toEqual([]);
  });

  it('pauses the countdown while the pointer is on the compact current action and halts from there', async () => {
    renderRun({ ...RUNNING, prompt: confirmation(true) }, 'live');
    const calls = () => vi.mocked(fetch).mock.calls.map(([url]) => String(url).split('/').pop());
    const halt = screen.getByRole('region', { name: 'Halt before this action?' });
    expect(halt.contains(document.activeElement)).toBe(false);

    fireEvent.keyDown(document.body, { key: '1' });
    fireEvent.pointerEnter(halt);
    fireEvent.pointerLeave(halt);
    await waitFor(() => expect(calls()).toEqual(['pause', 'resume']));

    fireEvent.click(within(halt).getByRole('button', { name: 'Halt' }));

    await waitFor(() => expect(calls()).toContain('answer'));
    const answer = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith('/answer'))!;
    expect(JSON.parse(String(answer[1]?.body))).toEqual({ decision: 'HALTED' });
  });

  it('keeps the compact current action fully in view as it grows, in the middle of its pane or under its heading', () => {
    let resized = () => undefined as void;
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: () => void) {
        resized = callback;
      }

      observe() {}
      disconnect() {}
    });
    renderRun({ ...RUNNING, prompt: confirmation(true) }, 'live');
    const pane = screen.getByRole('region', { name: 'Log in as admin' });
    const action = within(tree()).getAllByRole('listitem').find((row) => row.getAttribute('aria-current') === 'step')!;
    const scrollBy = vi.fn();
    Object.defineProperties(pane, { scrollHeight: { value: 900 }, clientHeight: { value: 300 }, scrollBy: { value: scrollBy } });
    pane.getBoundingClientRect = () => new DOMRect(0, 100, 300, 300);
    const actionAt = (top: number, height: number) => {
      action.getBoundingClientRect = () => new DOMRect(0, top, 300, height);
      resized();
    };

    actionAt(150, 100);
    expect(scrollBy).not.toHaveBeenCalled();

    actionAt(350, 100);
    expect(scrollBy).toHaveBeenLastCalledWith({ top: 150, behavior: 'smooth' });

    actionAt(350, 400);
    expect(scrollBy).toHaveBeenLastCalledWith({ top: 242, behavior: 'smooth' });
  });

  it('finds the procedure before there is an outline, and shows the other prompts above the step', () => {
    const nextAction: ReceivedPrompt = {
      ...confirmation(true), id: 'next-1', type: 'next-action',
      payload: { message: 'No procedure fits.', itemDescription: 'Log in as admin', precondition: false, actions: ['CREATE', 'CANCEL'] },
    } as ReceivedPrompt;

    renderRun({ ...RUNNING, outline: null, prompt: nextAction });

    expect(screen.getByText('Finding the procedure for this step')).toBeTruthy();
    expect(screen.getByRole('region', { name: 'How should the run go on?' })).toBeTruthy();
  });

  it('asks a confirmation without an executing action in a prompt of its own', () => {
    renderRun({ ...RUNNING, outline: null, prompt: confirmation(true) });

    expect(screen.getByRole('region', { name: 'Run this action?' }).classList).toContain('prompt');
  });

  it('shows the logs as a labelled side panel that closes from inside', () => {
    const { props } = renderRun(RUNNING, 'step', true);

    const panel = screen.getByRole('complementary', { name: 'Agent logs' });
    fireEvent.click(within(panel).getByRole('button', { name: 'Close the logs' }));

    expect(props.logs.onClose).toHaveBeenCalledOnce();
  });
});
