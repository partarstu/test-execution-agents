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
import { ChevronDown } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import './Popover.css';

interface Props {
  label: ReactNode;
  children: ReactNode;
}

/** Transient UI such as a filter or a menu on the Popover API, which dismisses it on Escape and on a click outside. */
export function Popover({ label, children }: Props) {
  const id = useId();
  return (
    <>
      <button type="button" className="popover-toggle quiet" popoverTarget={id}>
        {label}
        <ChevronDown size={16} />
      </button>
      <div id={id} popover="auto" className="popover">
        {children}
      </div>
    </>
  );
}
