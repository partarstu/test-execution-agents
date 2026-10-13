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
import { focusOf } from './focusModel';
import type { GraphEdge, GraphNode } from './model';

const node = (id: string, kind: GraphNode['kind'] = 'ATOMIC'): GraphNode => ({ id, kind, label: id, flags: [], testCases: 0 });

const edge = (kind: GraphEdge['kind'], source: string, target: string, extra: Partial<GraphEdge> = {}): GraphEdge => ({
  source,
  target,
  kind,
  sequence: null,
  score: null,
  effectPhrase: null,
  prerequisitePhrase: null,
  ...extra,
});

const satisfies = (source: string, target: string, score: number, effectPhrase: string, prerequisitePhrase: string) =>
  edge('SATISFIES', source, target, { score, effectPhrase, prerequisitePhrase });

const ids = (nodes: GraphNode[]) => nodes.map((each) => each.id);

describe('focusOf', () => {
  it('groups the procedures that enable the focus by its prerequisite and the ones it enables by its effect, the closest first', () => {
    const slice = {
      nodes: [node('focus'), node('a'), node('b'), node('c'), node('d')],
      edges: [
        satisfies('a', 'focus', 0.6, 'menu open', 'menu is open'),
        satisfies('b', 'focus', 0.9, 'menu shown', 'menu is open'),
        satisfies('c', 'focus', 0.95, 'logged in', 'user is logged in'),
        satisfies('focus', 'd', 0.8, 'edit view open', 'editor shown'),
      ],
      capped: false,
    };

    const focus = focusOf(slice, 'focus')!;

    expect(focus.enabledBy.map(({ phrase, nodes }) => [phrase, ids(nodes)])).toEqual([
      ['user is logged in', ['c']],
      ['menu is open', ['b', 'a']],
    ]);
    expect(focus.enables.map(({ phrase, nodes }) => [phrase, ids(nodes)])).toEqual([['edit view open', ['d']]]);
  });

  it('orders the steps by their sequence and keeps a step shared with another parent', () => {
    const slice = {
      nodes: [node('focus', 'COMPOSITE'), node('other', 'COMPOSITE'), node('first'), node('second'), node('third')],
      edges: [
        edge('CONTAINS', 'focus', 'third', { sequence: 2 }),
        edge('CONTAINS', 'focus', 'first', { sequence: 0 }),
        edge('CONTAINS', 'focus', 'second', { sequence: 1 }),
        edge('CONTAINS', 'other', 'second', { sequence: 0 }),
      ],
      capped: false,
    };

    expect(ids(focusOf(slice, 'focus')!.steps)).toEqual(['first', 'second', 'third']);
    expect(ids(focusOf(slice, 'second')!.parents)).toEqual(['focus', 'other']);
  });

  it('lists the targets and test cases of a procedure but none of the relations of its neighbours', () => {
    const slice = {
      nodes: [node('focus'), node('child'), node('el', 'ELEMENT'), node('el-2', 'ELEMENT'), node('tc', 'TEST_CASE'), node('x')],
      edges: [
        edge('TARGETS', 'focus', 'el'),
        edge('TARGETS', 'child', 'el-2'),
        edge('USES_PROCEDURE', 'tc', 'focus'),
        satisfies('x', 'child', 0.9, 'a', 'b'),
        satisfies('focus', 'focus', 0.9, 'a', 'b'),
      ],
      capped: false,
    };

    const focus = focusOf(slice, 'focus')!;

    expect(ids(focus.targets)).toEqual(['el']);
    expect(ids(focus.testCases)).toEqual(['tc']);
    expect(focus.enabledBy).toEqual([]);
    expect(focus.enables).toEqual([]);
  });

  it('takes the procedures a test case uses as its steps', () => {
    const slice = {
      nodes: [node('tc', 'TEST_CASE'), node('open', 'COMPOSITE'), node('save', 'COMPOSITE'), node('nested')],
      edges: [edge('USES_PROCEDURE', 'tc', 'open'), edge('USES_PROCEDURE', 'tc', 'save'), edge('CONTAINS', 'open', 'nested', { sequence: 0 })],
      capped: false,
    };

    expect(ids(focusOf(slice, 'tc')!.steps)).toEqual(['open', 'save']);
  });

  it('returns null when the slice does not hold the focus', () => {
    expect(focusOf({ nodes: [], edges: [], capped: false }, 'focus')).toBeNull();
  });
});
