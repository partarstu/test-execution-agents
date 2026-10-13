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
import { describe, expect, it } from 'vitest';
import { documentTitle, promptAnnouncement, runAnnouncement } from './attention';
import { INITIAL_STATE } from './events';
import type { PromptView } from './prompts/Prompt';
import type { RunState, TimelineItem } from './types';

const confirmation: PromptView = {
  id: 'p1', remainingMillis: 30_000, waitedMillis: 0, countdown: true, countdownMillis: 60_000, held: false, paused: false, hasDefault: true, type: 'confirmation',
  payload: { procedureDescription: 'Click Save', ancestors: [], item: { description: 'Save', testData: [], precondition: false }, beforeExecution: true },
};

describe('documentTitle', () => {
  it('leads with the question while the run waits', () => {
    expect(documentTitle({ prompt: confirmation, testCaseName: 'Login', runStatus: 'RUNNING' })).toBe('Waiting for you: Run this action? – Operator panel');
  });

  it('names the test case and its status while nothing waits', () => {
    expect(documentTitle({ prompt: null, testCaseName: 'Login', runStatus: 'FAILED' })).toBe('Failed: Login – Operator panel');
  });

  it('is the panel name before any test case', () => {
    expect(documentTitle({ prompt: null, testCaseName: null, runStatus: 'IDLE' })).toBe('Operator panel');
  });
});

describe('promptAnnouncement', () => {
  it('announces the question of an open prompt', () => {
    expect(promptAnnouncement(confirmation)).toBe('The run waits for your answer: Run this action?');
  });

  it('announces nothing without a prompt', () => {
    expect(promptAnnouncement(null)).toBe('');
  });
});

describe('runAnnouncement', () => {
  const items: TimelineItem[] = [
    { kind: 'PRECONDITION', description: 'The shop is open', status: 'PASSED', errorMessage: null, resolution: null },
    { kind: 'STEP', description: 'Log in', status: 'RUNNING', errorMessage: null, resolution: null },
  ];
  const running: RunState = { ...INITIAL_STATE, runStatus: 'RUNNING', testCaseName: 'Login', items, currentItem: 1 };

  it('names the running item by its number in the test case', () => {
    expect(runAnnouncement(running)).toBe('Step 1, running: Log in');
  });

  it('ignores the moment-to-moment activity', () => {
    expect(runAnnouncement({ ...running, statusMessage: 'Clicked at (4, 2)' })).toBe(runAnnouncement(running));
  });

  it('announces the start before the first item', () => {
    expect(runAnnouncement({ ...running, currentItem: -1 })).toBe('The run started: Login');
  });

  it('announces the end with the result', () => {
    expect(runAnnouncement({ ...running, runStatus: 'FAILED', resultMessage: 'Step 1 failed.' })).toBe('The run failed: Step 1 failed.');
  });

  it('is silent while idle', () => {
    expect(runAnnouncement(INITIAL_STATE)).toBe('');
  });
});
