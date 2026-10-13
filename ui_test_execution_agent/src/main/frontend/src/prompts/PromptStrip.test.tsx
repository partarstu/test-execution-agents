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
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { installDom } from '../testing/dom';
import type { ReceivedPrompt } from './Prompt';
import { PromptStrip } from './PromptStrip';

installDom();

const ITEM = { description: 'Save the document', testData: [], precondition: false };

function confirmation(beforeExecution: boolean, timing: Partial<ReceivedPrompt> = {}): ReceivedPrompt {
  return {
    id: 'confirm-1', type: 'confirmation', payload: { procedureDescription: 'Click the Save button', ancestors: [], item: ITEM, beforeExecution },
    remainingMillis: 5_000, waitedMillis: 0, countdown: true, countdownMillis: 10_000, held: false, paused: false, hasDefault: true,
    receivedAt: performance.now(), ...timing,
  } as ReceivedPrompt;
}

function stripText(prompt: ReceivedPrompt): string {
  render(<PromptStrip prompt={prompt} onAnswer={vi.fn()} />);
  return screen.getByRole('complementary').textContent ?? '';
}

describe('PromptStrip', () => {
  it('names the action about to run and when it runs, with the way to the run instead of an answer', () => {
    const text = stripText(confirmation(true));

    expect(text).toContain('Up next');
    expect(text).toContain('Click the Save button');
    expect(text).toContain('Runs in 5 s');
    expect(text).not.toContain('answer');
    expect(screen.getByRole('button').textContent).toBe('Go to the run');
  });

  it('names the executed action and when the run goes on', () => {
    const text = stripText(confirmation(false));

    expect(text).toContain('Just executed');
    expect(text).toContain('Click the Save button');
    expect(text).toContain('The run goes on in 5 s');
  });

  it('states that the run waits once the countdown is paused, held or off', () => {
    for (const timing of [{ paused: true }, { held: true }, { countdown: false }]) {
      const { unmount } = render(<PromptStrip prompt={confirmation(true, timing)} onAnswer={vi.fn()} />);

      expect(screen.getByRole('complementary').textContent).toContain('Waits for your go');
      unmount();
    }
  });

  it('keeps asking for an answer when the run needs one', () => {
    const prompt = {
      ...confirmation(true), type: 'verification-failure', payload: { verification: 'The document is saved', reason: 'No notice.', screenshotId: null },
    } as ReceivedPrompt;

    const text = stripText(prompt);

    expect(text).toContain('The run waits for you');
    expect(text).toContain('A verification failed');
    expect(text).toContain('5 s left to answer');
    expect(screen.getByRole('button').textContent).toBe('Answer');
  });
});
