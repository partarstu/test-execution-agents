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
import type { GraphEdge, GraphNode, GraphSlice } from './model';

/** The procedures linked to the focus through one of its prerequisites or effects, the closest match first. */
export interface LinkGroup {
  phrase: string | null;
  nodes: GraphNode[];
}

/** One node and only its direct relations, which is all the focus view draws. */
export interface Focus {
  node: GraphNode;
  parents: GraphNode[];
  enabledBy: LinkGroup[];
  enables: LinkGroup[];
  steps: GraphNode[];
  targets: GraphNode[];
  testCases: GraphNode[];
}

/** Returns the focus of a slice, or null when the slice does not hold the node. */
export function focusOf(slice: GraphSlice, focusId: string): Focus | null {
  const nodes = new Map(slice.nodes.map((node) => [node.id, node]));
  const node = nodes.get(focusId);
  if (node === undefined) {
    return null;
  }
  const from = (kind: GraphEdge['kind']) => slice.edges.filter((edge) => edge.kind === kind && edge.source === focusId && edge.target !== focusId);
  const to = (kind: GraphEdge['kind']) => slice.edges.filter((edge) => edge.kind === kind && edge.target === focusId && edge.source !== focusId);
  const nodesOf = (ids: string[]) => [...new Set(ids)].flatMap((id) => nodes.get(id) ?? []);
  const steps = node.kind === 'TEST_CASE'
    ? from('USES_PROCEDURE')
    : from('CONTAINS').toSorted((a, b) => (a.sequence ?? Number.MAX_SAFE_INTEGER) - (b.sequence ?? Number.MAX_SAFE_INTEGER));
  return {
    node,
    parents: nodesOf(to('CONTAINS').map((edge) => edge.source)),
    enabledBy: grouped(to('SATISFIES'), (edge) => edge.prerequisitePhrase, (edge) => edge.source, nodes),
    enables: grouped(from('SATISFIES'), (edge) => edge.effectPhrase, (edge) => edge.target, nodes),
    steps: nodesOf(steps.map((edge) => edge.target)),
    targets: nodesOf(from('TARGETS').map((edge) => edge.target)),
    testCases: nodesOf(to('USES_PROCEDURE').map((edge) => edge.source)),
  };
}

function grouped(edges: GraphEdge[], phraseOf: (edge: GraphEdge) => string | null, otherEnd: (edge: GraphEdge) => string,
                 nodes: Map<string, GraphNode>): LinkGroup[] {
  const groups = new Map<string | null, GraphNode[]>();
  for (const edge of edges.toSorted((a, b) => (b.score ?? 0) - (a.score ?? 0))) {
    const node = nodes.get(otherEnd(edge));
    const group = groups.get(phraseOf(edge)) ?? [];
    if (node !== undefined && !group.includes(node)) {
      groups.set(phraseOf(edge), [...group, node]);
    }
  }
  return [...groups].map(([phrase, groupNodes]) => ({ phrase, nodes: groupNodes }));
}
