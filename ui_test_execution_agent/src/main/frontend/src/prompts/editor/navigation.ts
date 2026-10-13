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
import type { ParentSummary } from '../Prompt';

/**
 * A view of the procedure editor. `path` is the position of the procedure form a view belongs to, as child indices from the
 * edited procedure down; the pickers and element views return to the form at their path.
 */
export type EditorView =
  | { kind: 'procedure' }
  | { kind: 'child'; path: number[] }
  | { kind: 'effectPicker'; path: number[] }
  | { kind: 'elementLookup'; path: number[] }
  | { kind: 'elementInfo'; path: number[] }
  | { kind: 'capture'; path: number[] }
  | { kind: 'parentChooser'; parents: ParentSummary[] };

/** The views from the edited procedure to the current one, which gives Back, the breadcrumb, and editing a parent as a pop. */
export interface Navigation {
  stack: EditorView[];
}

export type NavigationAction = { type: 'push'; view: EditorView } | { type: 'pop' } | { type: 'replace'; view: EditorView };

export const ROOT: EditorView = { kind: 'procedure' };

export const INITIAL_NAVIGATION: Navigation = { stack: [ROOT] };

export function navigate(state: Navigation, action: NavigationAction): Navigation {
  switch (action.type) {
    case 'push':
      return { stack: [...state.stack, action.view] };
    case 'pop':
      return state.stack.length > 1 ? { stack: state.stack.slice(0, -1) } : state;
    case 'replace':
      return { stack: [...state.stack.slice(0, -1), action.view] };
  }
}

export function currentView(state: Navigation): EditorView {
  return state.stack[state.stack.length - 1];
}

/** The path of the procedure form the view shows or returns to. */
export function formPathOf(view: EditorView): number[] {
  switch (view.kind) {
    case 'procedure':
    case 'parentChooser':
      return [];
    case 'child':
    case 'effectPicker':
    case 'elementLookup':
    case 'elementInfo':
    case 'capture':
      return view.path;
  }
}

export function pathKey(path: number[]): string {
  return path.join('/');
}

/** A stable key of a view, so a view keeps its mounted state while it stays on the stack. */
export function viewKey(view: EditorView): string {
  return view.kind === 'parentChooser' ? view.kind : `${view.kind}:${pathKey(formPathOf(view))}`;
}
