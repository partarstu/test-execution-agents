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
import type { PromptView } from './Prompt';

export function promptTitle(prompt: PromptView): string {
  switch (prompt.type) {
    case 'start-step':
      return 'Where should the run start?';
    case 'confirmation':
      return prompt.payload.beforeExecution ? 'Run this action?' : 'Action executed. Go on?';
    case 'next-action':
      return 'How should the run go on?';
    case 'message':
      return prompt.payload.title;
    case 'verification-failure':
      return 'A verification failed';
    case 'failure-context':
      return 'What went wrong?';
    case 'procedure-editor':
      return prompt.payload.create ? 'Teach the agent a new procedure' : 'Edit the procedure';
  }
}
