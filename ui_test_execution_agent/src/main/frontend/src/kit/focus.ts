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
const START = '[data-autofocus], h3, input:not([type="hidden"]), textarea, select, button';

/**
 * Moves the focus to the start of a view that replaced the focused one, unless a control of the view already took it on mount;
 * a heading becomes focusable for this only.
 */
export function focusViewStart(view: Element | null | undefined): void {
  if (!view || view.contains(document.activeElement)) {
    return;
  }
  const start = view.querySelector<HTMLElement>(START);
  if (start?.tagName === 'H3') {
    start.tabIndex = -1;
  }
  start?.focus();
}
