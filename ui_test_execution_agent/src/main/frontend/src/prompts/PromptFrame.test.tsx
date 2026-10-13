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
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, installDom, stubFetch } from '../testing/dom';
import type { EditorNode, PayloadOf, PromptType, ReceivedPrompt } from './Prompt';
import { PromptFrame } from './PromptFrame';

installDom();

const ITEM = { description: 'Save the document', testData: ['report.txt'], precondition: false };

const FIELDS = {
  description: 'Save the document', additionalInfo: null, atomic: false, optional: false, prerequisites: [], effects: ['Document saved'],
  negatedEffects: [], testData: [], expectedResults: 'The document is saved',
};

const SAVED_CHILD = {
  id: 'child-1', description: 'Open the File menu', atomic: true, precondition: false, effects: [], negatedEffects: [], screenshotId: null,
};

const ROOT: EditorNode = {
  id: 'root-1', precondition: false, fields: FIELDS, targetElement: null, parents: [], testCases: ['Save flow'],
  children: [SAVED_CHILD, { ...SAVED_CHILD, id: null, description: 'Click Save' }],
};

const CHILD: EditorNode = {
  id: 'child-1', precondition: false, fields: { ...FIELDS, description: 'Open the File menu', atomic: true }, targetElement: null,
  children: [], parents: ['Save the document'], testCases: [],
};

const PAYLOADS: { [T in PromptType]: PayloadOf<T> } = {
  'start-step': { steps: [{ description: 'Open the editor', testData: [], expectedResults: 'The editor is open' }, { description: 'Save', testData: [], expectedResults: null }] },
  confirmation: { procedureDescription: 'Save the document', ancestors: ['Edit a report'], item: ITEM, beforeExecution: true },
  'next-action': { message: 'No procedure fits this step.', itemDescription: ITEM.description, precondition: false, actions: ['CREATE', 'BROWSE', 'CANCEL'] },
  message: { title: 'Element not found', message: 'The Save button is not on the screen.', severity: 'WARNING', screenshotId: 'shot-1' },
  'verification-failure': { verification: 'The document is saved', reason: 'No saved notice appeared.', screenshotId: null },
  'failure-context': { category: 'TIMEOUT', symptom: 'The dialog did not open.', categories: ['TIMEOUT', 'UNKNOWN'] },
  'procedure-editor': { create: false, item: ITEM, node: ROOT },
};

function promptOf<T extends PromptType>(type: T, countdown = true): ReceivedPrompt {
  return {
    id: `prompt-${type}`, type, payload: PAYLOADS[type], remainingMillis: 60_000, waitedMillis: 0, countdown, countdownMillis: countdown ? 60_000 : 0,
    held: false, paused: false, hasDefault: true,
    receivedAt: performance.now(),
  } as ReceivedPrompt;
}

function stubActions() {
  stubFetch((path) => {
    switch (path.split('/').pop()) {
      case 'similar-children':
        return { indices: [1] };
      case 'search-procedures':
        return { hits: [{ id: 'proc-1', description: 'Save via the toolbar', atomic: true, precondition: false, satisfiedPrerequisites: 0, totalPrerequisites: 0 }] };
      case 'open-procedure':
        return CHILD;
      default:
        return undefined;
    }
  });
}

function renderPrompt(prompt: ReceivedPrompt) {
  stubActions();
  render(<PromptFrame prompt={prompt} activity="Looking for the Save button" searchDelayMillis={0} onUnauthorized={vi.fn()} />);
}

const answers = () => vi.mocked(fetch).mock.calls.filter(([url]) => String(url).endsWith('/answer'));

describe('PromptFrame', () => {
  it('states what the agent last reported doing, which the run header leaves out while the run runs', () => {
    renderPrompt(promptOf('next-action'));

    expect(screen.getByRole('region', { name: 'How should the run go on?' }).textContent).toContain('Looking for the Save button');
  });

  it('keeps the page behind a modal prompt readable', () => {
    renderPrompt(promptOf('failure-context'));

    expect(screen.getByRole('dialog', { name: 'What went wrong?' }).classList).toContain('modal-see-through');
  });

  it('shows a confirmation in place, without a dialog, with Halt as its only answer, unnumbered and unfocused', () => {
    renderPrompt(promptOf('confirmation'));

    expect(screen.queryByRole('dialog')).toBeNull();
    const prompt = screen.getByRole('region', { name: 'Run this action?' });
    expect(within(prompt).getAllByRole('button')).toEqual([within(prompt).getByRole('button', { name: 'Halt' })]);
    expect(within(prompt).getByRole('button', { name: 'Halt' }).hasAttribute('aria-keyshortcuts')).toBe(false);
    expect(prompt.contains(document.activeElement)).toBe(false);

    fireEvent.keyDown(document.body, { key: '1' });

    expect(answers()).toHaveLength(0);
  });

  it('puts the start buttons above the steps, so the focused first button leaves the first steps in sight', () => {
    renderPrompt(promptOf('start-step'));

    const start = screen.getByRole('button', { name: 'Start from step 1' });
    expect(document.activeElement).toBe(start);
    expect(start.compareDocumentPosition(screen.getByRole('table')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it.each(Object.keys(PAYLOADS) as PromptType[])('renders the %s prompt without axe violations', async (type) => {
    renderPrompt(promptOf(type));

    if (type === 'procedure-editor') {
      expect(await screen.findByText('Similar procedures exist')).toBeTruthy();
    }
    expect(await axeViolations()).toEqual([]);
  });

  it.each([
    ['PROCEED', 'Proceed with this step'],
    ['CONTINUE', 'Go on with the next step'],
  ] as const)('answers %s when the operator goes on from a halt', async (action, label) => {
    renderPrompt({ ...promptOf('next-action'), payload: { ...PAYLOADS['next-action'], actions: [action, 'RERUN_TEST_STEP', 'CANCEL'] } } as ReceivedPrompt);

    fireEvent.click(screen.getByRole('button', { name: label }));

    await waitFor(() => expect(answers()).toHaveLength(1));
    expect(JSON.parse(String(answers()[0][1]?.body))).toEqual({ action });
  });

  it('moves the focus to the procedure search when the operator browses existing procedures', async () => {
    renderPrompt(promptOf('next-action'));

    fireEvent.click(screen.getByRole('button', { name: 'Browse existing procedures' }));

    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('searchbox', { name: 'Search procedures' })));
    expect(await screen.findByText('Save via the toolbar')).toBeTruthy();
    expect(await axeViolations()).toEqual([]);
  });

  it('moves the focus into an opened child step and back to its Edit button when the operator leaves it', async () => {
    renderPrompt(promptOf('procedure-editor', false));
    await screen.findByText('Similar procedures exist');
    const edit = screen.getByRole('button', { name: 'Edit' });
    edit.focus();

    fireEvent.click(edit);

    await waitFor(() => expect(screen.getByRole('textbox', { name: 'Description' })).toHaveProperty('value', 'Open the File menu'));
    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('textbox', { name: 'Description' })));

    fireEvent.click(screen.getByRole('button', { name: 'Back to the parent' }));

    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Edit' })));
  });

  describe('countdown', () => {
    const countdownCalls = () => vi.mocked(fetch).mock.calls.map(([url]) => String(url).split('/').pop())
      .filter((call) => ['hold', 'pause', 'resume'].includes(call ?? ''));

    it('pauses while the pointer is on the prompt and resumes once it leaves, in that order', async () => {
      renderPrompt(promptOf('confirmation'));
      const prompt = screen.getByRole('region', { name: 'Run this action?' });

      fireEvent.pointerEnter(prompt);
      fireEvent.pointerLeave(prompt);

      await waitFor(() => expect(countdownCalls()).toEqual(['pause', 'resume']));
      expect(screen.queryByRole('button', { name: /hold/i })).toBeNull();
    });

    it('holds the countdown once the keyboard moves the focus through the prompt', async () => {
      renderPrompt(promptOf('confirmation'));

      fireEvent.keyDown(screen.getByRole('button', { name: 'Halt' }), { key: 'Tab' });

      await waitFor(() => expect(countdownCalls()).toEqual(['hold']));
    });

    it('states the frozen time left while paused', () => {
      renderPrompt({ ...promptOf('confirmation'), paused: true, remainingMillis: 4_000 });

      expect(screen.getByRole('timer').textContent).toContain('The action proceeds in 4 s once the pointer leaves the prompt.');
    });

    it('leaves a prompt without a countdown alone', () => {
      renderPrompt(promptOf('confirmation', false));

      fireEvent.pointerEnter(screen.getByRole('region', { name: 'Run this action?' }));

      expect(countdownCalls()).toEqual([]);
    });
  });

  describe('answer keys', () => {
    it('answers with the number of a choice', async () => {
      renderPrompt(promptOf('next-action'));

      fireEvent.keyDown(document.activeElement ?? document.body, { key: '1' });

      await waitFor(() => expect(answers()).toHaveLength(1));
      expect(JSON.parse(String(answers()[0][1]?.body))).toEqual({ action: 'CREATE' });
    });

    it('leaves a number typed into a field alone', async () => {
      renderPrompt(promptOf('next-action'));
      fireEvent.click(screen.getByRole('button', { name: 'Browse existing procedures' }));
      const search = await screen.findByRole('searchbox', { name: 'Search procedures' });

      fireEvent.keyDown(search, { key: '1' });

      expect(await screen.findByText('Save via the toolbar')).toBeTruthy();
      expect(answers()).toHaveLength(0);
    });

    it('does not answer from a confirmation the prompt opened over itself', async () => {
      renderPrompt(promptOf('next-action'));
      fireEvent.keyDown(document.activeElement ?? document.body, { key: '3' });
      const keep = await screen.findByRole('button', { name: 'Keep it running' });

      fireEvent.keyDown(keep, { key: '1' });

      expect(answers()).toHaveLength(0);
    });
  });
});
