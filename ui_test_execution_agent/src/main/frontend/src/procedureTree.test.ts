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
import {
  buildTree, currentLeaf, displayStatus, foldedByDefault, guideOf, leafCounts, leavesOf, nextLeaf, type ProcedureTree, type TreeNode,
} from './procedureTree';
import type { ReceivedPrompt } from './prompts/Prompt';
import type { ProcedureNode, ProcedureNodeStatus } from './types';

const node = (id: string, atomic: boolean, depth: number, status: ProcedureNodeStatus = 'PENDING'): ProcedureNode => ({
  id, description: id, expectedResults: null, atomic, optional: false, depth, status, durationMillis: null,
});

// Log in > (Open dialog > Click, Wait), (Fill > Type user, (Password > Click field, Type password)), Dismiss banner
function sampleTree(statuses: ProcedureNodeStatus[] = []): ProcedureTree {
  const procedures = [
    node('login', false, 0), node('open', false, 1), node('click', true, 2), node('wait', true, 2),
    node('fill', false, 1), node('user', true, 2), node('password', false, 2), node('field', true, 3), node('type', true, 3),
    node('banner', true, 1),
  ].map((procedure, index) => ({ ...procedure, status: statuses[index] ?? 'PENDING' }));
  return buildTree(procedures)!;
}

const byId = (tree: ProcedureTree, id: string): TreeNode => {
  const find = (treeNode: TreeNode): TreeNode | undefined =>
    treeNode.node.id === id ? treeNode : treeNode.children.map(find).find(Boolean);
  return find(tree.root)!;
};

const confirmation = (beforeExecution: boolean) => ({ type: 'confirmation', payload: { beforeExecution } }) as unknown as ReceivedPrompt;

describe('buildTree', () => {
  it('nests the pre-order nodes by depth and numbers the atomic actions in execution order', () => {
    const tree = sampleTree();

    expect(tree.root.node.id).toBe('login');
    expect(tree.root.children.map((child) => child.node.id)).toEqual(['open', 'fill', 'banner']);
    expect(byId(tree, 'password').children.map((child) => child.node.id)).toEqual(['field', 'type']);
    expect(tree.leaves.map((leaf) => leaf.node.id)).toEqual(['click', 'wait', 'user', 'field', 'type', 'banner']);
    expect(leavesOf(tree, byId(tree, 'fill')).map((leaf) => leaf.leaf)).toEqual([2, 3, 4]);
  });

  it('keeps a procedure used twice as two nodes', () => {
    const tree = buildTree([node('root', false, 0), node('save', true, 1), node('save', true, 1)])!;

    expect(tree.leaves.map((leaf) => leaf.index)).toEqual([1, 2]);
  });

  it('has no tree without procedures', () => {
    expect(buildTree([])).toBeNull();
  });
});

describe('displayStatus', () => {
  it('tells the executing action apart by the question about it', () => {
    const executing = node('type', true, 1, 'RUNNING');

    expect(displayStatus(executing, null)).toBe('RUNNING');
    expect(displayStatus(executing, confirmation(true))).toBe('WAITING');
    expect(displayStatus(executing, confirmation(false))).toBe('EXECUTED');
    expect(displayStatus(executing, { type: 'next-action' } as ReceivedPrompt)).toBe('ATTENTION');
    expect(displayStatus(node('fill', false, 0, 'RUNNING'), confirmation(true))).toBe('RUNNING');
    expect(displayStatus(node('user', true, 1, 'COMPLETED'), confirmation(true))).toBe('COMPLETED');
  });
});

describe('the route to the current action', () => {
  const running: ProcedureNodeStatus[] = [
    'RUNNING', 'COMPLETED', 'COMPLETED', 'COMPLETED', 'RUNNING', 'COMPLETED', 'RUNNING', 'COMPLETED', 'RUNNING', 'PENDING',
  ];

  it('finds the current and the next action', () => {
    const tree = sampleTree(running);

    expect(currentLeaf(tree)?.node.id).toBe('type');
    expect(nextLeaf(tree)?.node.id).toBe('banner');
  });

  it('has no next action once one has failed', () => {
    const failed = running.map((status, index) => (index === 8 ? 'FAILED' : status));

    expect(nextLeaf(sampleTree(failed))).toBeNull();
  });

  it('lights the guides up to the current action, the travelled ones apart from those still ahead', () => {
    const tree = sampleTree(running);
    const current = currentLeaf(tree);
    const guide = (child: string, parent: string) => guideOf(byId(tree, child), byId(tree, parent), current, tree);

    expect(guide('open', 'login')).toBe('trunk');
    expect(guide('fill', 'login')).toBe('path');
    expect(guide('banner', 'login')).toBe('ahead');
    expect(guide('click', 'open')).toBe('done');
    expect(guide('type', 'password')).toBe('path');
  });

  it('folds the finished composites in the full tree, and everything off the route in the dense one', () => {
    const tree = sampleTree(running);
    const current = currentLeaf(tree);

    expect(foldedByDefault(byId(tree, 'open'), current, false)).toBe(true);
    expect(foldedByDefault(byId(tree, 'fill'), current, false)).toBe(false);
    expect(foldedByDefault(byId(tree, 'fill'), current, true)).toBe(false);
    expect(foldedByDefault(byId(tree, 'open'), current, true)).toBe(true);
    expect(foldedByDefault(tree.root, null, true)).toBe(false);
    expect(foldedByDefault(byId(tree, 'fill'), null, true)).toBe(true);
  });

  it('counts the done and skipped actions', () => {
    const finished = sampleTree(running.map((_, index) => (index === 9 ? 'SKIPPED' : 'COMPLETED')));

    expect(leafCounts(finished.leaves)).toEqual({ done: 5, skipped: 1, total: 6 });
  });
});
