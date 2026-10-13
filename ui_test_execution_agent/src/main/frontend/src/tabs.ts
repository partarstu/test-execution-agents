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

import type { PromptView } from './prompts/Prompt';

export type Tab = 'run' | 'health' | 'knowledge';

/** The selected tab and the last prompt that selected *Run*, so the same prompt sent again does not switch a second time. */
export interface TabState {
  selected: Tab;
  followedPromptId: string | null;
}

export const INITIAL_TABS: TabState = { selected: 'run', followedPromptId: null };

/** The id of the open prompt unless its countdown applies a default unanswered, which needs the operator on no tab. */
export function promptToFollow(prompt: Pick<PromptView, 'id' | 'countdown' | 'hasDefault'> | null): string | null {
  return prompt === null || (prompt.countdown && prompt.hasDefault) ? null : prompt.id;
}

/**
 * Selects *Run* for a new prompt, whose questions cannot be answered on a hidden tab; returns the same state when nothing changes,
 * so a snapshot or a hold re-sending the open prompt leaves the operator where they went.
 */
export function followPrompt(state: TabState, promptId: string | null): TabState {
  if (promptId === null || promptId === state.followedPromptId) {
    return state;
  }
  return { selected: 'run', followedPromptId: promptId };
}

export function selectTab(state: TabState, tab: Tab): TabState {
  return state.selected === tab ? state : { ...state, selected: tab };
}
