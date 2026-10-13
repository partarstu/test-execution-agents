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
import './SignalMark.css';

/** The panel's mark: a signal head whose lit lamp follows the run, teal while it runs and amber while it waits for the operator. */
export function SignalMark({ waiting = false }: { waiting?: boolean }) {
  return (
    <svg className={waiting ? 'signal-mark signal-mark-waiting' : 'signal-mark'} viewBox="0 0 16 24" width="16" height="24" aria-hidden="true">
      <rect x="1" y="1" width="14" height="22" rx="7" className="signal-mark-head" />
      <circle cx="8" cy="7" r="3" className="signal-mark-lamp signal-mark-top" />
      <circle cx="8" cy="17" r="3" className="signal-mark-lamp signal-mark-bottom" />
    </svg>
  );
}
