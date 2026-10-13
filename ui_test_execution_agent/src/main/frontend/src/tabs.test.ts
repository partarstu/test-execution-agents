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
import { followPrompt, INITIAL_TABS, promptToFollow, selectTab } from './tabs';

describe('promptToFollow', () => {
  it('follows a prompt that waits for an answer', () => {
    expect(promptToFollow({ id: 'p1', countdown: false, hasDefault: true })).toBe('p1');
  });

  it('follows a countdown without a default', () => {
    expect(promptToFollow({ id: 'p1', countdown: true, hasDefault: false })).toBe('p1');
  });

  it('does not follow a countdown that applies its default unanswered', () => {
    expect(promptToFollow({ id: 'p1', countdown: true, hasDefault: true })).toBeNull();
  });

  it('follows nothing while no prompt is open', () => {
    expect(promptToFollow(null)).toBeNull();
  });
});

describe('followPrompt', () => {
  it('selects Run when a new prompt opens while Health is shown', () => {
    const onHealth = selectTab(INITIAL_TABS, 'health');

    expect(followPrompt(onHealth, 'p1').selected).toBe('run');
  });

  it('lets the operator go back to Health while the prompt stays open', () => {
    const followed = followPrompt(selectTab(INITIAL_TABS, 'health'), 'p1');

    const back = selectTab(followed, 'health');

    expect(followPrompt(back, 'p1').selected).toBe('health');
  });

  it('does not switch again for the same prompt re-sent by a snapshot or a hold', () => {
    const back = selectTab(followPrompt(INITIAL_TABS, 'p1'), 'health');

    expect(followPrompt(back, 'p1')).toBe(back);
  });

  it('switches again for the next prompt', () => {
    const back = selectTab(followPrompt(INITIAL_TABS, 'p1'), 'health');

    expect(followPrompt(back, 'p2').selected).toBe('run');
  });

  it('changes nothing while no prompt is open', () => {
    const onHealth = selectTab(INITIAL_TABS, 'health');

    expect(followPrompt(onHealth, null)).toBe(onHealth);
  });
});
