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
import { levelOf } from './LogTail';

const LOGS_KEY = 'panel.logs';

export const LOGS_PANEL_ID = 'run-logs';
export const LOGS_TOGGLE_ID = 'run-logs-toggle';

/** Whether this browser last left the logs panel open; closed when the browser keeps no storage. */
export function storedLogsOpen(): boolean {
  try {
    return localStorage.getItem(LOGS_KEY) === 'open';
  } catch {
    return false;
  }
}

export function storeLogsOpen(open: boolean): void {
  try {
    localStorage.setItem(LOGS_KEY, open ? 'open' : 'closed');
  } catch {
    // Without storage the choice lasts as long as the page
  }
}

/** The warnings and errors among the lines numbered from `seenUpTo` on, in a tail whose oldest line is numbered `firstLine`. */
export function unseenProblems(lines: string[], firstLine: number, seenUpTo: number): number {
  return lines.slice(Math.max(0, seenUpTo - firstLine)).filter((line) => levelOf(line) !== 'other').length;
}
