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
import { currentView, formPathOf, INITIAL_NAVIGATION, navigate, ROOT, viewKey, type NavigationAction } from './navigation';

function replay(actions: NavigationAction[]) {
  return actions.reduce(navigate, INITIAL_NAVIGATION);
}

describe('navigate', () => {
  it('pushes a child and pops back to the procedure', () => {
    const inChild = replay([{ type: 'push', view: { kind: 'child', path: [1] } }]);

    expect(currentView(inChild)).toEqual({ kind: 'child', path: [1] });
    expect(currentView(navigate(inChild, { type: 'pop' }))).toEqual(ROOT);
  });

  it('returns a picker to the form it was opened from', () => {
    const picking = replay([
      { type: 'push', view: { kind: 'child', path: [0] } },
      { type: 'push', view: { kind: 'effectPicker', path: [0] } },
    ]);

    const back = navigate(picking, { type: 'pop' });

    expect(currentView(back)).toEqual({ kind: 'child', path: [0] });
    expect(formPathOf(currentView(picking))).toEqual([0]);
  });

  it('edits a parent as a pop of the nested child', () => {
    const nested = replay([
      { type: 'push', view: { kind: 'child', path: [0] } },
      { type: 'push', view: { kind: 'child', path: [0, 2] } },
    ]);

    expect(currentView(navigate(nested, { type: 'pop' }))).toEqual({ kind: 'child', path: [0] });
  });

  it('keeps the edited procedure when popping from it', () => {
    expect(navigate(INITIAL_NAVIGATION, { type: 'pop' })).toBe(INITIAL_NAVIGATION);
  });

  it('replaces the current view without growing the history, as after saving a child or moving up to a parent', () => {
    const chooser = replay([{ type: 'push', view: { kind: 'parentChooser', parents: [] } }]);

    const replaced = navigate(chooser, { type: 'replace', view: ROOT });

    expect(replaced.stack).toEqual([ROOT, ROOT]);
    expect(navigate(INITIAL_NAVIGATION, { type: 'replace', view: { kind: 'child', path: [3] } }).stack)
      .toEqual([{ kind: 'child', path: [3] }]);
  });

  it('keys the views by kind and form, so the same child view keeps its key', () => {
    expect(viewKey({ kind: 'child', path: [0, 2] })).toBe('child:0/2');
    expect(viewKey({ kind: 'effectPicker', path: [] })).toBe('effectPicker:');
  });
});
